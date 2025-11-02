/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.opencqrs.esdb.client.EsdbClient;
import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.BookAddedEvent;
import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.MyEvent;
import com.opencqrs.framework.client.ClientInterruptedException;
import com.opencqrs.framework.eventhandler.partitioning.EventSequenceResolver;
import com.opencqrs.framework.eventhandler.partitioning.PartitionKeyResolver;
import com.opencqrs.framework.eventhandler.progress.Progress;
import com.opencqrs.framework.eventhandler.progress.ProgressTracker;
import com.opencqrs.framework.persistence.EventReader;
import com.opencqrs.framework.serialization.EventData;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EventHandlingProcessorTest {

    private AtomicLong eventId = new AtomicLong();
    private final String groupId = "test-1";
    private final String observeSubject = "/test";

    @Mock
    private EventReader eventReader;

    @Mock
    private EsdbClient client;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Mock
    private ProgressTracker progressTracker;

    @Mock
    private EventSequenceResolver.ForRawEvent eventSequenceResolver;

    @Mock
    private PartitionKeyResolver partitionKeyResolver;

    @Mock
    private BackOff.Execution backOffExecution;

    @Mock
    private BackOff backOff;

    @Mock
    private EventHandlingProcessor.Delayer delayer;

    @Mock
    private EventHandler.ForObject<BookAddedEvent> eventHandler1;

    @Mock
    private EventHandler.ForObjectAndMetaData<MyEvent> eventHandler2;

    @Mock
    private EventHandler.ForObjectAndMetaDataAndRawEvent<MyEvent> eventHandler3;

    private EventHandlingProcessor subject;
    private Boolean subjectFinished = false;

    private BlockingQueue<Event> eventQueue = new ArrayBlockingQueue<>(10);

    private Map<Event, EventData<Object>> submittedEvents = new HashMap<>();

    private Progress lastProgress;

    private Event submitEvent(Object payload, Map<String, ?> metaData) {
        Instant time = Instant.now();
        Event raw = new Event(
                "test",
                observeSubject,
                "raw",
                Map.of("irrelevant", true),
                "spec-version",
                String.valueOf(eventId.getAndIncrement()),
                time,
                "content-type",
                "1",
                "0",
                time.toString());
        submittedEvents.put(raw, new EventData<>(metaData, payload));
        assertThat(eventQueue.add(raw)).as("could not submit event to queue").isTrue();
        return raw;
    }

    @BeforeEach
    public void setup() throws InterruptedException {
        subject = new EventHandlingProcessor(
                0,
                observeSubject,
                false,
                eventReader,
                progressTracker,
                eventSequenceResolver,
                partitionKeyResolver,
                List.of(
                        new EventHandlerDefinition<>(groupId, BookAddedEvent.class, eventHandler1),
                        new EventHandlerDefinition<>(groupId, MyEvent.class, eventHandler2),
                        new EventHandlerDefinition<>(groupId, MyEvent.class, eventHandler3)),
                backOff,
                delayer);

        doAnswer(invocation -> {
                    Consumer<Event> eventConsumer = invocation.getArgument(2);
                    while (true) {
                        Event event = eventQueue.take();
                        try {
                            eventConsumer.accept(event);
                        } catch (Exception e) {
                            List<Event> drained = new ArrayList<>();
                            drained.add(event);
                            eventQueue.drainTo(drained);
                            drained.forEach(drainedEvent ->
                                    assertThat(eventQueue.add(drainedEvent)).isTrue());
                            throw e;
                        }
                    }
                })
                .when(client)
                .observe(eq(observeSubject), eq(Set.of()), any());

        doAnswer(invocation -> {
                    EventReader.ClientRequestor clientRequestor =
                            invocation.getArgument(0, EventReader.ClientRequestor.class);
                    BiConsumer<EventReader.RawCallback, Event> rawConsumer = invocation.getArgument(1);

                    clientRequestor.request(client, raw -> {
                        rawConsumer.accept(
                                upcastedConsumer -> upcastedConsumer.accept(
                                        new EventReader.UpcastedCallback() {

                                            @Override
                                            public Class<?> getEventJavaClass() {
                                                return submittedEvents
                                                        .get(raw)
                                                        .payload()
                                                        .getClass();
                                            }

                                            @Override
                                            public void convert(BiConsumer<Map<String, ?>, Object> eventConsumer) {
                                                EventData<Object> data = submittedEvents.get(raw);
                                                eventConsumer.accept(data.metaData(), data.payload());
                                            }
                                        },
                                        // upcasting is simulated by copying the event using a different type
                                        new Event(
                                                raw.source(),
                                                raw.subject(),
                                                "upcasted",
                                                raw.data(),
                                                raw.specVersion(),
                                                raw.id(),
                                                raw.time(),
                                                raw.dataContentType(),
                                                raw.hash(),
                                                raw.predecessorHash(),
                                                raw.time().toString())),
                                raw);
                    });
                    return null;
                })
                .when(eventReader)
                .consumeRaw(any(), any());

        doReturn(backOffExecution).when(backOff).start();
        doReturn(10L).when(backOffExecution).next();
        doNothing().when(delayer).delay(anyLong());
        doReturn(new Progress.None()).when(progressTracker).current(any(), eq(0L));
        doAnswer(invocationOnMock -> {
                    lastProgress = (Progress)
                            invocationOnMock.getArgument(2, Supplier.class).get();
                    return null;
                })
                .when(progressTracker)
                .proceed(any(), eq(0L), any());
        doReturn("seq01").when(eventSequenceResolver).sequenceIdFor(any());
        doReturn(0L).when(partitionKeyResolver).resolve("seq01");
    }

    private void startSubject() {
        executor.submit(() -> {
            subject.start().get();
            subjectFinished = true;
            return null;
        });
    }

    @AfterEach
    public void tearDown() {
        executor.shutdownNow();
    }

    @Test
    public void eventHandledSuccessfullyByHandlersAssignableToEventClass() {
        BookAddedEvent event = new BookAddedEvent("4711");
        Map<String, ?> metaData = Map.of("purpose", "testing");
        Event raw = submitEvent(event, metaData);

        startSubject();

        await().untilAsserted(() -> {
            Stream.of(eventHandler1, eventHandler2, eventHandler3).forEach(eh -> {
                EventHandler<? extends MyEvent> eventHandler = eh;
                switch (eh) {
                    case EventHandler.ForObject handler -> verify(handler).handle(event);
                    case EventHandler.ForObjectAndMetaData handler ->
                        verify(handler).handle(event, metaData);
                    case EventHandler.ForObjectAndMetaDataAndRawEvent handler ->
                        verify(handler).handle(event, metaData, raw);
                }
            });

            verifyNoInteractions(backOff, backOffExecution);
            assertThat(lastProgress).isEqualTo(new Progress.Success(raw.id()));
        });
    }

    @Test
    public void rawEventSkippedIfNotRelevantForPartition() {
        BookAddedEvent event = new BookAddedEvent("4711");
        Map<String, ?> metaData = Map.of("purpose", "testing");
        Event raw = submitEvent(event, metaData);

        doReturn(42L).when(partitionKeyResolver).resolve("seq01");

        startSubject();

        await().untilAsserted(() -> {
            verifyNoInteractions(eventHandler1, eventHandler2, eventHandler3);
            assertThat(lastProgress).isEqualTo(new Progress.Success(raw.id()));
        });
    }

    @Test
    public void convertedEventSkippedIfNotRelevantForPartition() {
        BookAddedEvent event = new BookAddedEvent("4711");
        Map<String, ?> metaData = Map.of("purpose", "testing");
        Event raw = submitEvent(event, metaData);

        EventSequenceResolver.ForObjectAndMetaDataAndRawEvent esr = mock();
        var ehp = new EventHandlingProcessor(
                0,
                observeSubject,
                false,
                eventReader,
                progressTracker,
                esr,
                partitionKeyResolver,
                List.of(
                        new EventHandlerDefinition<>(groupId, BookAddedEvent.class, eventHandler1),
                        new EventHandlerDefinition<>(groupId, MyEvent.class, eventHandler2),
                        new EventHandlerDefinition<>(groupId, MyEvent.class, eventHandler3)),
                backOff,
                delayer);
        doReturn("seq02").when(esr).sequenceIdFor(any(), any());
        doReturn(43L).when(partitionKeyResolver).resolve("seq02");

        executor.execute(ehp::start);

        await().untilAsserted(() -> {
            verifyNoInteractions(eventHandler1, eventHandler2, eventHandler3);
            assertThat(lastProgress).isEqualTo(new Progress.Success(raw.id()));
        });
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                CqrsFrameworkException.TransientException.class,
                CqrsFrameworkException.class,
                RuntimeException.class,
                Error.class
            })
    public void eventRetriedAndRecoveredSuccessfullyForTransientErrors(Class<? extends Throwable> clazz) {
        BookAddedEvent event = new BookAddedEvent("4711");
        Map<String, ?> metaData = Map.of("purpose", "testing");
        doThrow(clazz).doNothing().when(eventHandler1).handle(event);

        Event raw = submitEvent(event, metaData);

        startSubject();

        await().untilAsserted(() -> {
            InOrder order1 = inOrder(eventHandler1, backOff, backOffExecution);
            order1.verify(eventHandler1).handle(event);
            order1.verify(backOff).start();
            order1.verify(backOffExecution).next();
            order1.verify(eventHandler1).handle(event);
            order1.verifyNoMoreInteractions();

            verify(eventHandler2, atLeast(1)).handle(event, metaData);
            assertThat(lastProgress).isEqualTo(new Progress.Success(raw.id()));
        });
    }

    @Test
    public void eventSkippedAfterExhaustedRetries() {
        BookAddedEvent event1 = new BookAddedEvent("4711");
        doThrow(RuntimeException.class).when(eventHandler1).handle(event1);
        doReturn(10L, -1L).when(backOffExecution).next();

        BookAddedEvent event2 = new BookAddedEvent("4712");

        Event raw1 = submitEvent(event1, Map.of());
        Event raw2 = submitEvent(event2, Map.of());

        startSubject();

        await().untilAsserted(() -> {
            InOrder order = inOrder(eventHandler1, backOff, backOffExecution);
            order.verify(eventHandler1).handle(event1);
            order.verify(backOff).start();
            order.verify(backOffExecution).next();

            order.verify(eventHandler1).handle(event1);
            order.verify(backOffExecution).next();

            order.verify(eventHandler1).handle(event2);
            order.verifyNoMoreInteractions();
            assertThat(lastProgress).isEqualTo(new Progress.Success(raw2.id()));

            // no verification is performed for eventHandler2 to avoid assumptions on the execution order of both
            // handlers
        });
    }

    @Test
    public void eventProcessorTerminatingOnUnrecoverableNonTransientError() {
        BookAddedEvent event = new BookAddedEvent("4711");
        doThrow(CqrsFrameworkException.NonTransientException.class)
                .when(eventHandler1)
                .handle(event);

        submitEvent(event, Map.of());

        startSubject();

        await().untilAsserted(() -> {
            assertThat(subjectFinished).isTrue();
            assertThat(lastProgress).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                InterruptedException.class,
                RejectedExecutionException.class,
                ClientInterruptedException.class,
                CqrsFrameworkException.TransientException.class,
                CqrsFrameworkException.class,
                RuntimeException.class,
                Error.class
            })
    public void undeclaredThrowableFromAnnotatedEventHandlersRetriedIfTransient(Class<? extends Throwable> clazz) {
        BookAddedEvent event = new BookAddedEvent("4711");
        doThrow(new UndeclaredThrowableException(mock(clazz)))
                .when(eventHandler1)
                .handle(event);

        submitEvent(event, Map.of());

        startSubject();

        await().untilAsserted(() -> {
            verify(eventHandler1, atLeast(1)).handle(event);
            verify(backOffExecution, atLeast(1)).next();
            assertThat(lastProgress).isNull();
        });
    }

    @Test
    public void eventProcessorTerminatingOnUnrecoverableNonTransientErrorFromAnnotatedEventHandlers() {
        BookAddedEvent event = new BookAddedEvent("4711");
        doThrow(new UndeclaredThrowableException(mock(CqrsFrameworkException.NonTransientException.class)))
                .when(eventHandler1)
                .handle(event);

        submitEvent(event, Map.of());

        startSubject();

        await().untilAsserted(() -> {
            assertThat(subjectFinished).isTrue();

            assertThat(lastProgress).isNull();
        });
    }

    @Test
    public void eventReaderRetriedOnTransientError() {
        BookAddedEvent event = new BookAddedEvent("4711");
        doThrow(CqrsFrameworkException.TransientException.class)
                .when(eventReader)
                .consumeRaw(any(), any());

        submitEvent(event, Map.of());

        startSubject();

        await().untilAsserted(() -> {
            verify(eventReader, atLeast(2)).consumeRaw(any(), any());
            verify(backOffExecution, atLeast(1)).next();
            verifyNoInteractions(eventHandler1, eventHandler2);

            assertThat(lastProgress).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                ClientInterruptedException.class,
                CqrsFrameworkException.NonTransientException.class,
                CqrsFrameworkException.class,
                RuntimeException.class,
                Error.class
            })
    public void eventProcessorTerminatingOnUnrecoverableNonTransientErrorFromEventReader(
            Class<? extends Throwable> clazz) {
        BookAddedEvent event = new BookAddedEvent("4711");
        doThrow(clazz).when(eventReader).consumeRaw(any(), any());

        submitEvent(event, Map.of());

        startSubject();

        await().untilAsserted(() -> {
            assertThat(subjectFinished).isTrue();

            verify(eventReader, atMost(1)).consumeRaw(any(), any());
            verifyNoInteractions(eventHandler1, eventHandler2, backOffExecution);

            assertThat(lastProgress).isNull();
        });
    }

    @Test
    public void eventProcessorTerminatingOnRetryHandlerInterruption() throws InterruptedException {
        BookAddedEvent event = new BookAddedEvent("4711");
        doThrow(new RuntimeException()).when(eventHandler1).handle(event);
        doThrow(InterruptedException.class).when(delayer).delay(anyLong());

        submitEvent(event, Map.of());

        startSubject();

        await().untilAsserted(() -> {
            assertThat(subjectFinished).isTrue();

            assertThat(lastProgress).isNull();
        });
    }
}
