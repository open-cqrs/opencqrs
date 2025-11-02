/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.opencqrs.esdb.client.*;
import com.opencqrs.framework.BookAddedEvent;
import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.client.ClientRequestErrorMapper;
import com.opencqrs.framework.serialization.EventData;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.types.EventTypeResolver;
import com.opencqrs.framework.upcaster.EventUpcasters;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EventRepositoryTest {

    @Spy
    private final EventSource eventSource = new EventSource("test");

    @Mock
    private ClientRequestErrorMapper clientRequestErrorMapper;

    @Mock
    private EsdbClient client;

    @Mock
    private EventTypeResolver eventTypeResolver;

    @Mock
    private EventDataMarshaller eventDataMarshaller;

    @Mock
    private EventUpcasters eventUpcasters;

    @InjectMocks
    private EventRepository subject;

    @BeforeEach
    public void stub() {
        doAnswer(invocation -> invocation.getArgument(0, Supplier.class).get())
                .when(clientRequestErrorMapper)
                .handleMappingExceptionsIfNecessary(any());
    }

    @Nested
    @DisplayName("EventReader")
    public class EventReaderTests {

        private EventReader.ClientRequestor clientRequestor =
                (c, eventConsumer) -> c.read("/test", Set.of(new Option.LowerBoundInclusive("0815")), eventConsumer);

        private Instant rawEventTime = Instant.now();
        private Event rawEvent = new Event(
                eventSource.source(),
                "/test/1",
                "raw-type",
                Map.of("raw", 42),
                "1.0",
                "0",
                rawEventTime,
                "application/json",
                "1",
                "0",
                rawEventTime.toString());

        private Instant upcastedEventTime = Instant.now();
        private Event upcastedEvent = new Event(
                eventSource.source(),
                "/test/1",
                "upcasted-type",
                Map.of("upcasted", 43),
                "1.0",
                "0",
                upcastedEventTime,
                "application/json",
                "1",
                "0",
                upcastedEventTime.toString());

        private BookAddedEvent convertedEvent = new BookAddedEvent("4711");
        private Map<String, ?> convertedMetaData = Map.of("purpose", "test");

        @BeforeEach
        public void stub() {
            doAnswer(invocation -> {
                        invocation.getArgument(2, Consumer.class).accept(rawEvent);
                        return null;
                    })
                    .when(client)
                    .read(eq("/test"), eq(Set.of(new Option.LowerBoundInclusive("0815"))), any());

            doReturn(Stream.of(upcastedEvent)).when(eventUpcasters).upcast(rawEvent);
            doReturn(convertedEvent.getClass()).when(eventTypeResolver).getJavaClass(upcastedEvent.type());
            doReturn(new EventData<>(convertedMetaData, convertedEvent))
                    .when(eventDataMarshaller)
                    .deserialize(upcastedEvent.data(), convertedEvent.getClass());
        }

        @Nested
        @DisplayName("consumeRaw()")
        public class ConsumeRaw {

            @Test
            public void rawEventConsumed() {
                AtomicReference<Event> eventRef = new AtomicReference<>();

                subject.consumeRaw(clientRequestor, (rawCallback, event) -> eventRef.set(event));

                assertThat(eventRef).hasValue(rawEvent);
            }

            @Test
            public void rawEventCanBeUpcasted() {
                AtomicReference<Event> eventRef = new AtomicReference<>();

                subject.consumeRaw(
                        clientRequestor,
                        (rawCallback, event) ->
                                rawCallback.upcast((upcastedCallback, upcasted) -> eventRef.set(upcasted)));

                assertThat(eventRef).hasValue(upcastedEvent);
            }

            @Test
            public void rawEventCanBeUpcastedAndConverted() {
                AtomicReference<Object> eventRef = new AtomicReference<>();

                subject.consumeRaw(
                        clientRequestor,
                        (rawCallback, event) -> rawCallback.upcast(
                                (upcastedCallback, upcasted) -> upcastedCallback.convert(eventRef::set)));

                assertThat(eventRef).hasValue(convertedEvent);
            }

            @Test
            public void clientRequestErrorMapperUsed() {
                var frameworkException = mock(CqrsFrameworkException.class);

                doThrow(frameworkException).when(clientRequestErrorMapper).handleMappingExceptionsIfNecessary(any());

                assertThatThrownBy(() -> subject.consumeRaw(clientRequestor, (callback, event) -> {}))
                        .isSameAs(frameworkException);
            }
        }

        @Nested
        @DisplayName("consumeUpcasted()")
        public class ConsumeUpcasted {

            @Test
            public void upcastedEventConsumed() {
                AtomicReference<Event> eventRef = new AtomicReference<>();

                subject.consumeUpcasted(clientRequestor, (upcastedCallback, event) -> eventRef.set(event));

                assertThat(eventRef).hasValue(upcastedEvent);
            }

            @Test
            public void upcastedEventCanBeConverted() {
                AtomicReference<Object> eventRef = new AtomicReference<>();

                subject.consumeUpcasted(
                        clientRequestor, (upcastedCallback, event) -> upcastedCallback.convert(eventRef::set));

                assertThat(eventRef).hasValue(convertedEvent);
            }

            @Test
            public void clientRequestErrorMapperUsed() {
                var frameworkException = mock(CqrsFrameworkException.class);

                doThrow(frameworkException).when(clientRequestErrorMapper).handleMappingExceptionsIfNecessary(any());

                assertThatThrownBy(() -> subject.consumeUpcasted(clientRequestor, (callback, event) -> {}))
                        .isSameAs(frameworkException);
            }
        }

        @Nested
        @DisplayName("consumeAsObject()")
        public class ConsumeAsObject {

            @Test
            public void convertedEventCanBeConsumed() {
                AtomicReference<Object> eventRef = new AtomicReference<>();

                subject.consumeAsObject(clientRequestor, eventRef::set);

                assertThat(eventRef).hasValue(convertedEvent);
            }

            @Test
            public void convertedMetaDataCanBeConsumed() {
                AtomicReference<Map<String, ?>> metaDataRef = new AtomicReference<>();

                subject.consumeAsObject(clientRequestor, (metaData, o) -> metaDataRef.set(metaData));

                assertThat(metaDataRef).hasValue(convertedMetaData);
            }

            @Test
            public void clientRequestErrorMapperUsed() {
                var frameworkException = mock(CqrsFrameworkException.class);

                doThrow(frameworkException).when(clientRequestErrorMapper).handleMappingExceptionsIfNecessary(any());

                assertThatThrownBy(() -> subject.consumeAsObject(clientRequestor, o -> {}))
                        .isSameAs(frameworkException);
            }
        }
    }

    @Nested
    @DisplayName("ImmediateEventPublisher")
    public class ImmediateEventPublisherTests {

        @Test
        public void multipleEventsPublishedAtOnce() {
            var event1 = new BookAddedEvent("4711");
            var metaData1 = Map.of("trace", "001");
            var serialized1 = Map.of("payload", "{ isbn=4711 }", "metadata", "{ trace=001 }");
            Instant time1 = Instant.now();
            var published1 = new Event(
                    "source",
                    "subject/4711",
                    "type",
                    serialized1,
                    "1.0",
                    "0",
                    time1,
                    "json",
                    "hash",
                    "predecessor",
                    time1.toString());

            var event2 = new BookAddedEvent("4712");
            var serialized2 = Map.of("payload", "{ isbn=4712 }", "metadata", "{}");
            Instant time2 = Instant.now();
            var published2 = new Event(
                    "source",
                    "subject/4712",
                    "type",
                    serialized1,
                    "1.0",
                    "0",
                    time2,
                    "json",
                    "hash",
                    "predecessor",
                    time2.toString());

            doReturn("book-added.v1").when(eventTypeResolver).getEventType(BookAddedEvent.class);
            doReturn(serialized1).when(eventDataMarshaller).serialize(new EventData<>(metaData1, event1));
            doReturn(serialized2).when(eventDataMarshaller).serialize(new EventData<>(Map.of(), event2));

            when(client.write(
                            List.of(
                                    new EventCandidate(
                                            eventSource.source(), "/books/4711", "book-added.v1", serialized1),
                                    new EventCandidate(
                                            eventSource.source(), "/books/4712", "book-added.v1", serialized2)),
                            List.of(
                                    new Precondition.SubjectIsOnEventId("/additional", "42"),
                                    new Precondition.SubjectIsPristine("/pristine"))))
                    .thenReturn(List.of(published1, published2));

            List<Event> published = subject.publish(
                    eventPublisher -> {
                        eventPublisher.publish("/books/4711", event1, metaData1);
                        eventPublisher.publish(
                                "/books/4712",
                                event2,
                                Map.of(),
                                List.of(new Precondition.SubjectIsPristine("/pristine")));
                    },
                    List.of(new Precondition.SubjectIsOnEventId("/additional", "42")));

            assertThat(published).containsExactly(published1, published2);
        }

        @Test
        public void capturedEventsPublishedAtOnce() {
            var event1 = new BookAddedEvent("4711");
            var metaData1 = Map.of("trace", "001");
            var serialized1 = Map.of("payload", "{ isbn=4711 }", "metadata", "{ trace=001 }");
            Instant time1 = Instant.now();
            var published1 = new Event(
                    "source",
                    "subject/4711",
                    "type",
                    serialized1,
                    "1.0",
                    "0",
                    time1,
                    "json",
                    "hash",
                    "predecessor",
                    time1.toString());

            var event2 = new BookAddedEvent("4712");
            var serialized2 = Map.of("payload", "{ isbn=4712 }", "metadata", "{}");
            Instant time2 = Instant.now();
            var published2 = new Event(
                    "source",
                    "subject/4712",
                    "type",
                    serialized1,
                    "1.0",
                    "0",
                    time2,
                    "json",
                    "hash",
                    "predecessor",
                    time2.toString());

            doReturn("book-added.v1").when(eventTypeResolver).getEventType(BookAddedEvent.class);
            doReturn(serialized1).when(eventDataMarshaller).serialize(new EventData<>(metaData1, event1));
            doReturn(serialized2).when(eventDataMarshaller).serialize(new EventData<>(Map.of(), event2));

            when(client.write(
                            List.of(
                                    new EventCandidate(
                                            eventSource.source(), "/books/4711", "book-added.v1", serialized1),
                                    new EventCandidate(
                                            eventSource.source(), "/books/4712", "book-added.v1", serialized2)),
                            List.of(
                                    new Precondition.SubjectIsOnEventId("/additional", "42"),
                                    new Precondition.SubjectIsPristine("/pristine"))))
                    .thenReturn(List.of(published1, published2));

            List<Event> published = subject.publish(
                    List.of(
                            new CapturedEvent("/books/4711", event1, metaData1, List.of()),
                            new CapturedEvent(
                                    "/books/4712",
                                    event2,
                                    Map.of(),
                                    List.of(new Precondition.SubjectIsPristine("/pristine")))),
                    List.of(new Precondition.SubjectIsOnEventId("/additional", "42")));

            assertThat(published).containsExactly(published1, published2);
        }
    }
}
