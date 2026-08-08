/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.opencqrs.esdb.client.EsdbClient;
import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.BookAddedEvent;
import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.eventhandler.interceptor.Delivery;
import com.opencqrs.framework.eventhandler.interceptor.EventInterceptor;
import com.opencqrs.framework.eventhandler.interceptor.EventInvocation;
import com.opencqrs.framework.eventhandler.interceptor.EventLifecycle;
import com.opencqrs.framework.eventhandler.interceptor.Relevance;
import com.opencqrs.framework.eventhandler.partitioning.EventSequenceResolver;
import com.opencqrs.framework.eventhandler.partitioning.PartitionKeyResolver;
import com.opencqrs.framework.eventhandler.progress.Progress;
import com.opencqrs.framework.eventhandler.progress.ProgressTracker;
import com.opencqrs.framework.interceptor.Continuation;
import com.opencqrs.framework.interceptor.InterceptorContractViolation;
import com.opencqrs.framework.interceptor.Proceeded;
import com.opencqrs.framework.persistence.EventReader;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit test for {@link EventHandlingProcessor}'s {@link EventInterceptor} integration &mdash; relevance/delivery
 * gating, the no-upcast fast path for wrong-partition raw events, retry-vs-terminate error classification, and
 * per-attempt root firing. Chain mechanics themselves are covered by
 * {@link com.opencqrs.framework.eventhandler.interceptor.EventInterceptorChainTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EventHandlingProcessorInterceptorTest {

    private record OtherEvent(String id) {}

    private final AtomicLong eventId = new AtomicLong();
    private final String groupId = "test-1";
    private final String subjectPath = "/test";

    @Mock
    private EventReader eventReader;

    @Mock
    private EsdbClient client;

    @Mock
    private ProgressTracker progressTracker;

    @Mock
    private PartitionKeyResolver partitionKeyResolver;

    @Mock
    private BackOff backOff;

    @Mock
    private BackOff.Execution backOffExecution;

    @Mock
    private EventHandlingProcessor.Delayer delayer;

    @Mock
    private EventHandler.ForObject<BookAddedEvent> handler;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final BlockingQueue<Event> queue = new ArrayBlockingQueue<>(10);
    private final Map<Event, List<Object>> fanOut = new HashMap<>();
    private final AtomicInteger upcastInvocations = new AtomicInteger();

    private volatile Progress lastProgress;

    private Event submit(Object... payloads) {
        Event raw = new Event(
                "test",
                subjectPath,
                "raw",
                Map.of(),
                "spec-version",
                String.valueOf(eventId.getAndIncrement()),
                Instant.now(),
                "content-type",
                "1",
                "0");
        fanOut.put(raw, List.of(payloads));
        assertThat(queue.add(raw)).as("could not submit event").isTrue();
        return raw;
    }

    private EventHandlingProcessor processor(EventSequenceResolver resolver, EventInterceptor... interceptors) {
        return new EventHandlingProcessor(
                0,
                subjectPath,
                false,
                eventReader,
                progressTracker,
                resolver,
                partitionKeyResolver,
                List.of(new EventHandlerDefinition<>(groupId, BookAddedEvent.class, handler)),
                List.of(interceptors),
                backOff,
                delayer);
    }

    private static EventInterceptor interceptor(Delivery delivery, EventInterceptor delegate) {
        return new EventInterceptor() {
            @Override
            public Delivery delivery() {
                return delivery;
            }

            @Override
            public Proceeded intercept(EventInvocation invocation, EventLifecycle lifecycle, Continuation continuation)
                    throws Exception {
                return delegate.intercept(invocation, lifecycle, continuation);
            }
        };
    }

    @BeforeEach
    public void setup() throws InterruptedException {
        doAnswer(invocation -> {
                    Consumer<Event> eventConsumer = invocation.getArgument(2);
                    while (true) {
                        Event event = queue.take();
                        try {
                            eventConsumer.accept(event);
                        } catch (Exception e) {
                            List<Event> drained = new ArrayList<>();
                            drained.add(event);
                            queue.drainTo(drained);
                            drained.forEach(queue::add);
                            throw e;
                        }
                    }
                })
                .when(client)
                .observe(eq(subjectPath), any(), any());

        doAnswer(invocation -> {
                    EventReader.ClientRequestor requestor = invocation.getArgument(0);
                    BiConsumer<EventReader.RawCallback, Event> rawConsumer = invocation.getArgument(1);
                    requestor.request(client, raw -> {
                        EventReader.RawCallback rawCallback = upcastConsumer -> {
                            upcastInvocations.incrementAndGet();
                            for (Object payload : fanOut.getOrDefault(raw, List.of())) {
                                upcastConsumer.accept(
                                        new EventReader.UpcastedCallback() {
                                            @Override
                                            public Class<?> getEventJavaClass() {
                                                return payload.getClass();
                                            }

                                            @Override
                                            public void convert(BiConsumer<Map<String, ?>, Object> eventConsumer) {
                                                eventConsumer.accept(Map.of(), payload);
                                            }
                                        },
                                        raw);
                            }
                        };
                        rawConsumer.accept(rawCallback, raw);
                    });
                    return null;
                })
                .when(eventReader)
                .consumeRaw(any(), any());

        doReturn(backOffExecution).when(backOff).start();
        doReturn(10L).when(backOffExecution).next();
        doNothing().when(delayer).delay(anyLong());
        doReturn(new Progress.None()).when(progressTracker).current(any(), eq(0L));
        doAnswer(invocation -> {
                    lastProgress =
                            (Progress) invocation.getArgument(2, Supplier.class).get();
                    return null;
                })
                .when(progressTracker)
                .proceed(any(), eq(0L), any());
    }

    @AfterEach
    public void tearDown() {
        executor.shutdownNow();
    }

    /** A {@link EventSequenceResolver.ForRawEvent} whose events are all relevant for partition 0. */
    private EventSequenceResolver.ForRawEvent relevantRawResolver() {
        EventSequenceResolver.ForRawEvent resolver = rawEvent -> "seq";
        doReturn(0L).when(partitionKeyResolver).resolve("seq");
        return resolver;
    }

    private EventInterceptor recording(String id, List<String> trace) {
        EventInterceptor recording = (invocation, lifecycle, continuation) -> {
            trace.add("root-before-" + id);
            lifecycle.handler((jp, c) -> {
                trace.add("handler-before-" + id);
                Proceeded p = c.proceed();
                trace.add("handler-after-" + id);
                return p;
            });
            Proceeded result = continuation.proceed();
            trace.add("root-after-" + id);
            return result;
        };
        return recording;
    }

    @Test
    public void interceptorsJoinPointsAndHandlersCalledInCorrectOrder() {
        var trace = new CopyOnWriteArrayList<String>();
        BookAddedEvent event = new BookAddedEvent("4711");
        doAnswer(i -> {
                    trace.add("handle");
                    return null;
                })
                .when(handler)
                .handle(event);

        EventInterceptor a = recording("A", trace);
        EventInterceptor b = recording("B", trace);

        Event raw = submit(event);
        executor.submit(processor(relevantRawResolver(), a, b));

        await().untilAsserted(() -> {
            assertThat(trace)
                    .containsExactly(
                            "root-before-A",
                            "root-before-B",
                            "handler-before-A",
                            "handler-before-B",
                            "handle",
                            "handler-after-B",
                            "handler-after-A",
                            "root-after-B",
                            "root-after-A");
            assertThat(lastProgress).isEqualTo(new Progress.Success(raw.id()));
        });
    }

    @ParameterizedTest
    @CsvSource({
        "ACTIONABLE, false",
        "PARTITIONED, true",
        "ALL, true",
    })
    public void interceptorInvokedAccordingToDeliveryForPartitionRelevantEventWithNonExistentHandler(
            Delivery delivery, Boolean invoked) {
        var invocations = new AtomicInteger();
        var seenRelevance = new AtomicReference<Relevance>();
        EventInterceptor interceptor = interceptor(delivery, (invocation, lifecycle, continuation) -> {
            invocations.incrementAndGet();
            seenRelevance.set(invocation.relevance());
            return continuation.proceed();
        });

        // OtherEvent is partition-relevant but has no matching handler → "partitioned", below ACTIONABLE
        Event raw = submit(new OtherEvent("x"));
        executor.submit(processor(relevantRawResolver(), interceptor));

        await().untilAsserted(() -> assertThat(lastProgress).isEqualTo(new Progress.Success(raw.id())));
        assertThat(invocations).hasValue(invoked ? 1 : 0);
        if (invoked) {
            assertThat(seenRelevance).hasValue(Relevance.YES);
        }
        verifyNoInteractions(handler);
    }

    @ParameterizedTest
    @CsvSource({
        "ACTIONABLE, false",
        "PARTITIONED, false",
        "ALL, true",
    })
    public void interceptorInvokedAccordingToDeliveryForPartitionIrrelevantRawEvent(
            Delivery delivery, Boolean invoked) {
        var invocations = new AtomicInteger();
        EventInterceptor interceptor = interceptor(delivery, (invocation, lifecycle, continuation) -> {
            invocations.incrementAndGet();
            return continuation.proceed();
        });

        EventSequenceResolver.ForRawEvent resolver = mock();
        doReturn("seq").when(resolver).sequenceIdFor(any());
        doReturn(42L).when(partitionKeyResolver).resolve("seq"); // wrong partition

        Event raw = submit(new BookAddedEvent("4711"));
        executor.submit(processor(resolver, interceptor));

        await().untilAsserted(() -> assertThat(lastProgress).isEqualTo(new Progress.Success(raw.id())));
        assertThat(invocations).hasValue(invoked ? 1 : 0);
        assertThat(upcastInvocations)
                .as("no upcast expected for raw event not partition relevant")
                .hasValue(0);
        verifyNoInteractions(handler);
    }

    @Test
    public void relevanceIsPartialForMixedConvertedFanOut() {
        var seenRelevance = new AtomicReference<Relevance>();
        EventInterceptor all = interceptor(Delivery.ALL, (invocation, lifecycle, continuation) -> {
            seenRelevance.set(invocation.relevance());
            return continuation.proceed();
        });

        BookAddedEvent relevant = new BookAddedEvent("relevant");
        BookAddedEvent irrelevant = new BookAddedEvent("irrelevant");
        EventSequenceResolver.ForObjectAndMetaDataAndRawEvent resolver = mock();
        doReturn("rel").when(resolver).sequenceIdFor(eq(relevant), any());
        doReturn("irrel").when(resolver).sequenceIdFor(eq(irrelevant), any());
        doReturn(0L).when(partitionKeyResolver).resolve("rel");
        doReturn(42L).when(partitionKeyResolver).resolve("irrel");

        Event raw = submit(relevant, irrelevant);
        executor.submit(processor(resolver, all));

        await().untilAsserted(() -> assertThat(lastProgress).isEqualTo(new Progress.Success(raw.id())));
        assertThat(seenRelevance).hasValue(Relevance.PARTIAL);
        verify(handler).handle(relevant);
        verify(handler, never()).handle(irrelevant);
    }

    @Test
    public void interceptorExecutedPerRetryAttempt() {
        var invocations = new AtomicInteger();
        BookAddedEvent event = new BookAddedEvent("4711");
        doThrow(new RuntimeException("boom")).doNothing().when(handler).handle(event);

        EventInterceptor counting = (invocation, lifecycle, continuation) -> {
            invocations.incrementAndGet();
            return continuation.proceed();
        };

        Event raw = submit(event);
        executor.submit(processor(relevantRawResolver(), counting));

        await().untilAsserted(() -> assertThat(lastProgress).isEqualTo(new Progress.Success(raw.id())));
        // first attempt (handler throws) + second attempt (recovered) → the root wraps each attempt
        assertThat(invocations).hasValue(2);
        verify(backOff).start();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                CqrsFrameworkException.TransientException.class,
                CqrsFrameworkException.class,
                RuntimeException.class,
                Error.class
            })
    public void interceptorThrowingTransientErrorsRetried(Class<? extends Throwable> clazz) {
        BookAddedEvent event = new BookAddedEvent("4711");
        var attempts = new AtomicInteger();
        EventInterceptor flaky = (invocation, lifecycle, continuation) -> {
            if (attempts.getAndIncrement() == 0) {
                var error = Mockito.mock(clazz);
                if (error instanceof Error) {
                    throw (Error) error;
                }
                if (error instanceof RuntimeException) {
                    throw (RuntimeException) error;
                }
            }
            return continuation.proceed();
        };

        Event raw = submit(event);
        executor.submit(processor(relevantRawResolver(), flaky));

        await().untilAsserted(() -> assertThat(lastProgress).isEqualTo(new Progress.Success(raw.id())));
        verify(backOff).start();
        verify(handler).handle(event);
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                CqrsFrameworkException.NonTransientException.class,
                InterceptorContractViolation.class,
            })
    public void interceptorThrowingNonTransientErrorsTerminates(Class<? extends Exception> clazz) throws Exception {
        EventInterceptor terminating = (invocation, lifecycle, continuation) -> {
            throw Mockito.mock(clazz);
        };

        submit(new BookAddedEvent("4711"));
        Future<?> future = executor.submit(processor(relevantRawResolver(), terminating));

        future.get(5, TimeUnit.SECONDS); // terminates → run() returns
        verifyNoInteractions(backOff, handler);
    }
}
