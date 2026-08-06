/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.Option;
import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.client.ClientInterruptedException;
import com.opencqrs.framework.eventhandler.interceptor.Delivery;
import com.opencqrs.framework.eventhandler.interceptor.EventHandlerInvocation;
import com.opencqrs.framework.eventhandler.interceptor.EventInterceptor;
import com.opencqrs.framework.eventhandler.interceptor.EventInterceptorChain;
import com.opencqrs.framework.eventhandler.interceptor.EventInterior;
import com.opencqrs.framework.eventhandler.interceptor.EventInvocation;
import com.opencqrs.framework.eventhandler.interceptor.Relevance;
import com.opencqrs.framework.eventhandler.partitioning.EventSequenceResolver;
import com.opencqrs.framework.eventhandler.partitioning.PartitionKeyResolver;
import com.opencqrs.framework.eventhandler.progress.Progress;
import com.opencqrs.framework.eventhandler.progress.ProgressTracker;
import com.opencqrs.framework.interceptor.InterceptorExecutionException;
import com.opencqrs.framework.persistence.EventReader;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.types.EventTypeResolver;
import com.opencqrs.framework.upcaster.EventUpcasters;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * {@linkplain Runnable#run() Asynchronous} event processor
 * {@linkplain com.opencqrs.esdb.client.EsdbClient#observe(String, Set, Consumer) observing an event stream} to be
 * handled by matching {@link EventHandlerDefinition}s all belonging to the same
 * {@linkplain EventHandlerDefinition#group() processing group and partition} with configurable
 * {@linkplain ProgressTracker progress tracking} and {@linkplain BackOff retry} in case of errors.
 *
 * @see #run()
 * @see #start()
 * @see #stop()
 */
public class EventHandlingProcessor implements Runnable {

    private static final Logger log = Logger.getLogger(EventHandlingProcessor.class.getName());

    private final AtomicReference<@Nullable ExecutorService> running = new AtomicReference<>();
    private final String groupId;
    private final long partition;
    final String subject;
    final Boolean recursive;
    private final EventReader eventReader;
    final ProgressTracker progressTracker;
    final EventSequenceResolver eventSequenceResolver;
    private final PartitionKeyResolver partitionKeyResolver;
    private final List<EventHandlerDefinition> eventHandlerDefinitions;
    final List<EventInterceptor> eventInterceptors;
    final BackOff backoff;
    private final Delayer delayer;

    EventHandlingProcessor(
            long partition,
            String subject,
            Boolean recursive,
            EventReader eventReader,
            ProgressTracker progressTracker,
            EventSequenceResolver eventSequenceResolver,
            PartitionKeyResolver partitionKeyResolver,
            List<EventHandlerDefinition> eventHandlerDefinitions,
            List<EventInterceptor> eventInterceptors,
            BackOff backoff,
            Delayer delayer) {
        if (eventHandlerDefinitions.isEmpty()) {
            throw new IllegalStateException("list of event handler definitions must not be empty");
        }
        this.groupId = eventHandlerDefinitions.stream()
                .map(EventHandlerDefinition::group)
                .reduce((a, b) -> {
                    if (!a.equals(b)) {
                        throw new IllegalStateException(
                                "all event handler definitions must have the same group id within the same processor, but found: "
                                        + a + " and " + b);
                    }
                    return a;
                })
                .orElseThrow(() -> new IllegalStateException("at least one event handler definition must be supplied"));
        this.partition = partition;
        this.subject = subject;
        this.recursive = recursive;
        this.eventReader = eventReader;
        this.progressTracker = progressTracker;
        this.eventSequenceResolver = eventSequenceResolver;
        this.partitionKeyResolver = partitionKeyResolver;
        this.eventHandlerDefinitions = eventHandlerDefinitions;
        this.eventInterceptors = eventInterceptors;
        this.backoff = backoff;
        this.delayer = delayer;
    }

    /**
     * Creates a pre-configured instance of {@code this}.
     *
     * @param partition the partition number handled by {@code this} with respect to the processing group
     * @param subject the subject to {@linkplain com.opencqrs.esdb.client.EsdbClient#observe(String, Set, Consumer)
     *     observe}
     * @param recursive whether the subject should be observed recursively, that is including child subjects
     * @param eventReader the event source
     * @param progressTracker the progress tracker to maintain the progress within the observed event stream
     * @param eventSequenceResolver the event sequence resolver to determine the event sequence id
     * @param partitionKeyResolver the partition key resolver to determine if the event needs to be handled {@code this}
     * @param eventHandlerDefinitions a list of {@link EventHandlerDefinition} to dispatch events to
     * @param eventInterceptors an already-ordered (index {@code 0} = outermost) list of {@link EventInterceptor}s
     *     wrapping the event processing
     * @param backoff a configurable back-off strategy for retryable errors
     */
    public EventHandlingProcessor(
            long partition,
            String subject,
            Boolean recursive,
            EventReader eventReader,
            ProgressTracker progressTracker,
            EventSequenceResolver eventSequenceResolver,
            PartitionKeyResolver partitionKeyResolver,
            List<EventHandlerDefinition> eventHandlerDefinitions,
            List<EventInterceptor> eventInterceptors,
            BackOff backoff) {
        this(
                partition,
                subject,
                recursive,
                eventReader,
                progressTracker,
                eventSequenceResolver,
                partitionKeyResolver,
                eventHandlerDefinitions,
                eventInterceptors,
                backoff,
                Thread::sleep);
    }

    public long getPartition() {
        return partition;
    }

    public String getGroupId() {
        return groupId;
    }

    String eventProcessorForLogs() {
        return "event processor [group=" + groupId + ", partition=" + partition + "]";
    }

    /**
     * Enters the <i>event processing loop</i>, running infinitely unless interrupted or
     * {@link CqrsFrameworkException.NonTransientException} is thrown. This involves:
     *
     * <ol>
     *   <li>fetching the {@linkplain ProgressTracker#current(String, long)} current progress} for the configured
     *       processing group and partition
     *   <li>{@linkplain com.opencqrs.esdb.client.EsdbClient#observe(String, Set, Consumer) observing} the event stream
     *       for the configured subject starting from the current progress
     *   <li>checking if the {@linkplain EventSequenceResolver raw event's sequence id} is
     *       {@linkplain PartitionKeyResolver#resolve(String) relevant for this partition}, otherwise skip it
     *   <li>{@linkplain EventUpcasters#upcast(Event) upcasting} any observed event
     *   <li>{@linkplain EventTypeResolver#getJavaClass(String) resolving} the Java event type
     *   <li>{@linkplain EventDataMarshaller#deserialize(Map, Class) converting} the upcasted event to a Java object
     *   <li>checking if the {@linkplain EventSequenceResolver converted event's sequence id} is
     *       {@linkplain PartitionKeyResolver#resolve(String) relevant for this partition}, otherwise skip it
     *   <li>passing the event (and associated information) to each {@linkplain EventHandlerDefinition#eventClass()
     *       matching} {@link EventHandler}
     *   <li>{@linkplain ProgressTracker#proceed(String, long, Supplier)} proceeding the progress} of the event handling
     *       loop iteration (also for non-relevant events previously skipped)
     * </ol>
     *
     * Errors or exceptions occurring throughout the event processing loop are handled as follows:
     *
     * <ul>
     *   <li>{@link CqrsFrameworkException.NonTransientException}s thrown by any matching {@link EventHandler} won't be
     *       retried and will terminate the processing loop unrecoverably
     *   <li>any other {@link Throwable} thrown by any matching {@link EventHandler} is subject to retry
     *   <li>{@link CqrsFrameworkException.TransientException}s thrown by framework components are subject to retry
     *   <li>any thread interruption before or after calling the {@link EventHandler} will terminate the processing
     *       loop, assuming {@code this} was {@linkplain #stop() stopped}
     *   <li>any other {@link Throwable} thrown by any of the framework components won't be retried and will terminate
     *       the processing loop unrecoverably
     * </ul>
     *
     * Retry of failed {@link EventHandler}s or framework components will cause the event processor to {@link BackOff
     * back off} from the event processing loop, {@link Thread#sleep(long) waiting} before retrying the failed event
     * according to the aforementioned <i>event processing loop</i>. Once the {@link BackOff back off} is
     * {@linkplain BackOff.Execution#next() exhausted} the erroneous event will be skipped, continuing with the next
     * observable event, once available.
     *
     * <p>The per-event processing &mdash; relevance evaluation, upcasting/conversion, and handler dispatch (steps
     * 3&ndash;8) &mdash; is wrapped by the applicable {@linkplain EventInterceptor event interceptors} (ordered
     * outermost-first), inside the {@linkplain ProgressTracker#proceed(String, long, Supplier) progress-tracked} loop
     * iteration. Each interceptor's root is (re-)invoked <strong>once per attempt</strong> &mdash; including every
     * retry &mdash; and its {@linkplain com.opencqrs.framework.eventhandler.interceptor.EventLifecycle#handler handler}
     * advice wraps each matching {@link EventHandler} invocation. Which interceptors fire is decided by their
     * {@linkplain EventInterceptor#delivery() delivery} level against the event's {@linkplain Relevance relevance} and
     * whether it is actionable; an event skipped after back-off exhaustion fires no interceptors. An interceptor that
     * throws participates in the same error handling as an {@link EventHandler} above &mdash; a
     * {@link CqrsFrameworkException.NonTransientException} (including an
     * {@linkplain com.opencqrs.framework.interceptor.InterceptorContractViolation interceptor-contract violation})
     * terminates the loop unrecoverably; anything else is retried.
     *
     * <p>Event upcasting, type resolution, deserialization, and the actual event handling all run synchronously on the
     * event-processor thread (the thread {@link #start()} submits {@code this} to), since
     * {@link com.opencqrs.esdb.client.EsdbClient#observe(String, Set, Consumer)} consumes the event stream on the
     * calling thread. {@link #stop()} {@linkplain java.util.concurrent.ExecutorService#shutdownNow() interrupts} that
     * thread to terminate the loop.
     */
    @Override
    public void run() {
        var skipEvent = new AtomicBoolean(false);
        var retryHandler = new RetryHandler();

        log.info(() -> eventProcessorForLogs() + " entering event handling loop");
        try {
            while (true) {
                try {
                    try {
                        Set<Option> options = new HashSet<>();
                        if (recursive) {
                            options.add(new Option.Recursive());
                        }
                        Optional.of(progressTracker.current(groupId, partition))
                                .map(progress -> switch (progress) {
                                    case Progress.Success success -> success.id();
                                    case Progress.None ignored -> null;
                                })
                                .map(Option.LowerBoundExclusive::new)
                                .ifPresent(options::add);

                        eventReader.consumeRaw(
                                (client, eventConsumer) -> client.observe(subject, options, eventConsumer),
                                (rawCallback, raw) -> {
                                    try {
                                        progressTracker.proceed(groupId, partition, () -> {
                                            if (!skipEvent.getAndSet(false)) {
                                                dispatch(raw, rawCallback);

                                                if (retryHandler.isRetryExecution()) {
                                                    log.log(
                                                            Level.INFO,
                                                            () -> eventProcessorForLogs()
                                                                    + " successfully recovered for event id: "
                                                                    + raw.id());
                                                }
                                            } else {
                                                log.log(
                                                        Level.INFO,
                                                        () -> eventProcessorForLogs() + " skipped event id: "
                                                                + raw.id());
                                            }
                                            retryHandler.reset();
                                            return new Progress.Success(raw.id());
                                        });
                                    } catch (Error | RuntimeException e) {
                                        throw new EventProcessingFailure(raw, e);
                                    }
                                });
                    } catch (EventProcessingFailure e) {
                        Throwable cause = e.getCause();
                        switch (cause) {
                            case UndeclaredThrowableException ex -> {
                                switch (ex.getCause()) {
                                    case CqrsFrameworkException.NonTransientException undeclared -> throw undeclared;
                                    case null, default ->
                                        skipEvent.set(
                                                retryHandler.handle(e.event, Objects.requireNonNull(ex.getCause())));
                                }
                            }
                            case CqrsFrameworkException.NonTransientException ignored -> throw cause;
                            case null, default ->
                                skipEvent.set(retryHandler.handle(e.event, Objects.requireNonNull(cause)));
                        }
                    } catch (ClientInterruptedException e) {
                        log.log(
                                Level.INFO,
                                eventProcessorForLogs() + " interrupted or shut down, terminating event handling loop",
                                e.getCause());
                        return;
                    } catch (CqrsFrameworkException.TransientException e) {
                        skipEvent.set(retryHandler.handle(null, e));
                    }
                } catch (InterruptedException e) {
                    log.log(
                            Level.INFO,
                            eventProcessorForLogs() + " interrupted or shut down, terminating event handling loop",
                            e);
                    return;
                } catch (Throwable t) {
                    log.log(Level.SEVERE, t, () -> eventProcessorForLogs() + " giving up on unrecoverable error");
                    return;
                }
            }
        } finally {
            stop();
        }
    }

    /**
     * Dispatches the given raw event to its matching handlers, wrapped by the applicable {@link EventInterceptor}s.
     * When none apply the chain is empty &mdash; the handlers still run, but no interceptor root or handler advice
     * fires (empty chain pass-through).
     *
     * <p>A wrong-partition {@link EventSequenceResolver.ForRawEvent} event is decided from the raw event alone, so it
     * is <strong>not</strong> upcast (preserving the non-intercepted fast path) &mdash; only {@link Delivery#ALL}-level
     * interceptors observe it, with a no-op handler stage. Otherwise the upcast fan-out is buffered once &mdash; this
     * introduces no <em>additional</em> upcasting beyond what handling already requires &mdash; so the aggregate
     * {@link Relevance} and whether the event is actionable are known before the root fires; the buffered
     * partitionRelevant events are then dispatched to their handlers inside the chain.
     */
    private void dispatch(Event raw, EventReader.RawCallback rawCallback) {
        if (eventSequenceResolver instanceof EventSequenceResolver.ForRawEvent esr
                && !partitionRelevant(esr.sequenceIdFor(raw))) {
            executeChain(raw, Relevance.NO, false, chain -> {});
            return;
        }

        List<ConvertedEvent> fanOut = new ArrayList<>();
        rawCallback.upcast((upcastedCallback, upcasted) ->
                upcastedCallback.convert((metadata, event) -> fanOut.add(new ConvertedEvent(
                        upcastedCallback.getEventJavaClass(),
                        event,
                        metadata,
                        switch (eventSequenceResolver) {
                            case EventSequenceResolver.ForRawEvent ignored -> true;
                            case EventSequenceResolver.ForObjectAndMetaDataAndRawEvent esr ->
                                partitionRelevant(esr.sequenceIdFor(event, metadata));
                        }))));

        Relevance relevance =
                switch (eventSequenceResolver) {
                    case EventSequenceResolver.ForRawEvent ignored -> Relevance.YES;
                    case EventSequenceResolver.ForObjectAndMetaDataAndRawEvent<?> ignored -> {
                        long relevant = fanOut.stream()
                                .filter(ConvertedEvent::partitionRelevant)
                                .count();
                        if (relevant == 0) {
                            yield Relevance.NO;
                        } else if (relevant == fanOut.size()) {
                            yield Relevance.YES;
                        } else {
                            yield Relevance.PARTIAL;
                        }
                    }
                };

        boolean actionable = fanOut.stream()
                .anyMatch(c -> c.partitionRelevant()
                        && eventHandlerDefinitions.stream()
                                .anyMatch(ehd -> ehd.eventClass().isAssignableFrom(c.javaClass())));

        executeChain(raw, relevance, actionable, chain -> {
            for (ConvertedEvent converted : fanOut) {
                if (!converted.partitionRelevant()) {
                    continue;
                }
                for (EventHandlerDefinition<?> ehd : eventHandlerDefinitions) {
                    if (ehd.eventClass().isAssignableFrom(converted.javaClass())) {
                        chain.handler(
                                new EventHandlerInvocation<>(ehd, converted.event(), converted.metadata()),
                                () -> invokeHandler(ehd, converted.event(), converted.metadata(), raw));
                    }
                }
            }
        });
    }

    /** Single converted event, after upcast fan-out, including its partition relevance. */
    private record ConvertedEvent(
            Class<?> javaClass, Object event, Map<String, ?> metadata, boolean partitionRelevant) {}

    private boolean partitionRelevant(String eventSequence) {
        return partitionKeyResolver.resolve(eventSequence) == partition;
    }

    /**
     * Composes the interceptors whose {@link Delivery} admits an event of the given {@code relevance}/actionability
     * around {@code interior} and runs the chain. The chain is always built &mdash; an empty applicable list is a
     * passthrough that still runs {@code interior} (so handlers fire) but composes no root. Checked exceptions from
     * advice terminate the processor as an {@link InterceptorExecutionException}; unchecked ones propagate to the
     * loop's error classification.
     */
    private void executeChain(Event raw, Relevance relevance, boolean actionable, EventInterior interior) {
        List<EventInterceptor> applicable = eventInterceptors.stream()
                .filter(interceptor -> switch (interceptor.delivery()) {
                    case ACTIONABLE -> relevance != Relevance.NO && actionable;
                    case PARTITIONED -> relevance != Relevance.NO;
                    case ALL -> true;
                })
                .toList();
        try {
            new EventInterceptorChain(applicable)
                    .execute(new EventInvocation(raw, groupId, partition, relevance), interior);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new InterceptorExecutionException(
                    "event interceptor raised a checked exception for event id: " + raw.id(), e);
        }
    }

    private static void invokeHandler(EventHandlerDefinition ehd, Object event, Map<String, ?> metadata, Event raw) {
        switch (ehd.handler()) {
            case EventHandler.ForObject handler -> handler.handle(event);
            case EventHandler.ForObjectAndMetaData handler -> handler.handle(event, metadata);
            case EventHandler.ForObjectAndMetaDataAndRawEvent handler -> handler.handle(event, metadata, raw);
        }
    }

    /**
     * Starts {@code this} using a {@link Executors#newThreadPerTaskExecutor(ThreadFactory)}. A single thread within the
     * pool runs the {@linkplain #run() event processing loop}, including raw {@link Event} dispatching and handling.
     *
     * @return a future for the event processing loop to determine, when it ends (prematurely), in which case
     *     {@code this} is stopped
     */
    public Future<?> start() {
        var es = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .name("event-processor-" + getGroupId() + "-" + getPartition())
                .factory());
        if (!running.compareAndSet(null, es)) {
            throw new IllegalStateException(eventProcessorForLogs() + " already started");
        }
        log.info("starting " + eventProcessorForLogs());
        return es.submit(this);
    }

    /**
     * Retrieves the current execution state.
     *
     * @return {@code true} if currently running, {@code false} otherwise.
     */
    public boolean isRunning() {
        return running.get() != null;
    }

    /**
     * Stops {@code this} by {@linkplain ExecutorService#shutdownNow() shutting down} the thread pool initialized during
     * {@link #start()}.
     */
    public void stop() {
        var es = running.getAndSet(null);
        if (es != null) {
            log.info("stopping " + eventProcessorForLogs());
            es.shutdownNow();
        }
    }

    private class RetryHandler {
        private BackOff.@Nullable Execution execution;

        boolean isRetryExecution() {
            return execution != null;
        }

        boolean handle(@Nullable Event event, Throwable t) throws InterruptedException {
            if (execution == null) {
                execution = backoff.start();
            }

            var interval = execution.next();
            if (interval == -1) {
                log.log(
                        Level.WARNING,
                        t,
                        () -> eventProcessorForLogs() + " won't retry anymore" + eventIdForLog(event));
                return true;
            } else {
                log.log(
                        Level.WARNING,
                        t,
                        () -> eventProcessorForLogs() + " going to wait " + interval + "ms before retry"
                                + eventIdForLog(event));
                delayer.delay(interval);
                return false;
            }
        }

        private String eventIdForLog(@Nullable Event e) {
            if (e != null) {
                return " for event id: " + e.id();
            } else {
                return switch (progressTracker.current(groupId, partition)) {
                    case Progress.None ignored -> "";
                    case Progress.Success success -> " for last event id: " + success.id();
                };
            }
        }

        void reset() {
            execution = null;
        }
    }

    /**
     * Internal unchecked exception marking a failure that occurred while processing a specific event &mdash; the event
     * handler, upcasting/type resolution/deserialization, or the {@linkplain ProgressTracker#proceed(String, long,
     * Supplier) progress commit} &mdash; carrying the affected {@link Event}.
     *
     * <p>It is intentionally <strong>not</strong> one of the {@link com.opencqrs.esdb.client.ClientException} subtypes
     * mapped by the {@link com.opencqrs.framework.client.ClientRequestErrorMapper}, so it propagates back out through
     * {@link com.opencqrs.esdb.client.EsdbClient#observe(String, Set, Consumer)} unmapped, allowing {@link #run()} to
     * classify its {@linkplain #getCause() cause} for retry or termination.
     *
     * @see com.opencqrs.esdb.client.EsdbClient#observe(String, Set, Consumer)
     */
    private static class EventProcessingFailure extends RuntimeException {

        private final Event event;

        EventProcessingFailure(Event event, Throwable cause) {
            super(cause);
            this.event = event;
        }
    }

    interface Delayer {
        void delay(long millis) throws InterruptedException;
    }
}
