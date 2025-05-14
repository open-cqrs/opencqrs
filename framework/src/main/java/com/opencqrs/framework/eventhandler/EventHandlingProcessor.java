/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import com.opencqrs.esdb.client.ClientException;
import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.Option;
import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.client.ClientInterruptedException;
import com.opencqrs.framework.eventhandler.partitioning.EventSequenceResolver;
import com.opencqrs.framework.eventhandler.partitioning.PartitionKeyResolver;
import com.opencqrs.framework.eventhandler.progress.Progress;
import com.opencqrs.framework.eventhandler.progress.ProgressTracker;
import com.opencqrs.framework.persistence.EventReader;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.types.EventTypeResolver;
import com.opencqrs.framework.upcaster.EventUpcasters;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.http.HttpRequest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private final AtomicInteger threadNum = new AtomicInteger();
    private final AtomicReference<ExecutorService> running = new AtomicReference<>();
    private final String groupId;
    private final long partition;
    final String subject;
    final Boolean recursive;
    private final EventReader eventReader;
    final ProgressTracker progressTracker;
    final EventSequenceResolver eventSequenceResolver;
    private final PartitionKeyResolver partitionKeyResolver;
    private final List<EventHandlerDefinition> eventHandlerDefinitions;
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
     * <p><strong>This method is assumed to be started within a thread pool with one additional spare thread, which is
     * used to dispatch any raw {@link Event} received via {@link EventReader#consumeRaw(EventReader.ClientRequestor,
     * BiConsumer)}. This effectively offloads event upcasting, type resolution, deserialization, and the actual event
     * handling from the underlying {@link java.net.http.HttpClient} {@link java.net.http.HttpResponse.BodySubscriber}
     * thread, in order to be able to {@link #stop()} {@code this} properly.</strong>
     */
    @Override
    public void run() {
        var skipEvent = new AtomicBoolean(false);
        var retryHandler = new RetryHandler();
        var executorService = Optional.ofNullable(this.running.get())
                .orElseThrow(() -> new IllegalStateException(eventProcessorForLogs() + " not running"));

        log.info(() -> eventProcessorForLogs() + " entering event handling loop");
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
                                    executorService
                                            .submit(() -> {
                                                progressTracker.proceed(groupId, partition, () -> {
                                                    if (!skipEvent.getAndSet(false)) {
                                                        var rawEventRelevant =
                                                                switch (eventSequenceResolver) {
                                                                    case EventSequenceResolver.ForRawEvent esr ->
                                                                        partitionKeyResolver.resolve(
                                                                                        esr.sequenceIdFor(raw))
                                                                                == partition;
                                                                    case EventSequenceResolver
                                                                                    .ForObjectAndMetaDataAndRawEvent
                                                                            ignored -> true;
                                                                };
                                                        if (rawEventRelevant) {
                                                            rawCallback.upcast((upcastedCallback, upcasted) ->
                                                                    upcastedCallback.convert((metadata, event) -> {
                                                                        var convertedEventRelevant =
                                                                                switch (eventSequenceResolver) {
                                                                                    case EventSequenceResolver
                                                                                                    .ForRawEvent
                                                                                            ignored -> true;
                                                                                    case EventSequenceResolver
                                                                                                    .ForObjectAndMetaDataAndRawEvent
                                                                                            esr ->
                                                                                        partitionKeyResolver.resolve(
                                                                                                        esr
                                                                                                                .sequenceIdFor(
                                                                                                                        event,
                                                                                                                        metadata))
                                                                                                == partition;
                                                                                };
                                                                        if (convertedEventRelevant) {
                                                                            eventHandlerDefinitions.stream()
                                                                                    .filter(
                                                                                            ehd -> ehd.eventClass()
                                                                                                    .isAssignableFrom(
                                                                                                            upcastedCallback
                                                                                                                    .getEventJavaClass()))
                                                                                    .forEach(ehd -> {
                                                                                        try {
                                                                                            switch (ehd.handler()) {
                                                                                                case EventHandler
                                                                                                                .ForObject
                                                                                                        handler ->
                                                                                                    handler.handle(
                                                                                                            event);
                                                                                                case EventHandler
                                                                                                                .ForObjectAndMetaData
                                                                                                        handler ->
                                                                                                    handler.handle(
                                                                                                            event,
                                                                                                            metadata);
                                                                                                case EventHandler
                                                                                                                .ForObjectAndMetaDataAndRawEvent
                                                                                                        handler ->
                                                                                                    handler.handle(
                                                                                                            event,
                                                                                                            metadata,
                                                                                                            raw);
                                                                                            }
                                                                                        } catch (Error
                                                                                                | RuntimeException e) {
                                                                                            throw new WrappedEventHandlingException(
                                                                                                    raw, e);
                                                                                        }
                                                                                    });
                                                                        }
                                                                    }));
                                                        }

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
                                            })
                                            .get();
                                } catch (InterruptedException | RejectedExecutionException e) {
                                    throw new WrappedEventHandlingException(raw, e);
                                } catch (ExecutionException e) {
                                    if (e.getCause() instanceof WrappedEventHandlingException) {
                                        throw (WrappedEventHandlingException) e.getCause();
                                    } else {
                                        throw new WrappedEventHandlingException(raw, e.getCause());
                                    }
                                }
                            });
                } catch (WrappedEventHandlingException e) {
                    Throwable cause = e.getCause();
                    switch (cause) {
                        case InterruptedException ignored -> {
                            log.log(
                                    Level.INFO,
                                    eventProcessorForLogs()
                                            + " interrupted or shut down, terminating event handling loop",
                                    cause);
                            return;
                        }
                        case RejectedExecutionException ignored -> {
                            log.log(
                                    Level.INFO,
                                    eventProcessorForLogs()
                                            + " interrupted or shut down, terminating event handling loop",
                                    cause);
                            return;
                        }
                        case UndeclaredThrowableException ex -> {
                            switch (ex.getCause()) {
                                case CqrsFrameworkException.NonTransientException undeclared -> throw undeclared;
                                default -> skipEvent.set(retryHandler.handle(e.event, ex.getCause()));
                            }
                        }
                        case CqrsFrameworkException.NonTransientException ignored -> throw cause;
                        default -> skipEvent.set(retryHandler.handle(e.event, cause));
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
    }

    /**
     * Starts {@code this} using a {@link Executors#newFixedThreadPool(int)} with size {@code 2}. The first thread
     * within the pool is used to start the {@linkplain #run() event processing loop}, while the second one is used for
     * the raw {@link Event} dispatching.
     *
     * @return a future for the event processing loop to determine, when it ends (prematurely)
     */
    public Future<?> start() {
        var es = Executors.newFixedThreadPool(
                2,
                runnable -> new Thread(
                        runnable,
                        "event-processor-" + getGroupId() + "-" + getPartition() + "-worker-"
                                + threadNum.getAndIncrement()));
        if (!running.compareAndSet(null, es)) {
            throw new IllegalStateException(eventProcessorForLogs() + " already started");
        }
        log.info("starting " + eventProcessorForLogs());
        return es.submit(this);
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
        private BackOff.Execution execution;

        boolean isRetryExecution() {
            return execution != null;
        }

        boolean handle(Event event, Throwable t) throws InterruptedException {
            if (!isRetryExecution()) {
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

        private String eventIdForLog(Event e) {
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
     * Internal {@linkplain ClientException exception} used to capture exceptions from the
     * {@link com.opencqrs.esdb.client.EsdbClient}s event consumer callback.
     *
     * @see com.opencqrs.esdb.client.HttpRequestErrorHandler#handle(HttpRequest, Function)
     * @see com.opencqrs.esdb.client.EsdbClient#observe(String, Set, Consumer)
     */
    private static class WrappedEventHandlingException extends ClientException {

        private final Event event;

        WrappedEventHandlingException(Event event, Throwable cause) {
            super(cause);
            this.event = event;
        }
    }

    interface Delayer {
        void delay(long millis) throws InterruptedException;
    }
}
