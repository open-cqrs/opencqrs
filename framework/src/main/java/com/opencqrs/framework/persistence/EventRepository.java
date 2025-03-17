/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.persistence;

import static java.util.stream.Collectors.toCollection;

import com.opencqrs.esdb.client.Client;
import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.EventCandidate;
import com.opencqrs.esdb.client.Precondition;
import com.opencqrs.framework.client.ClientRequestErrorMapper;
import com.opencqrs.framework.serialization.EventData;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.types.EventTypeResolver;
import com.opencqrs.framework.upcaster.EventUpcasters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Default implementation of {@link EventReader} and {@link ImmediateEventPublisher}. */
public class EventRepository implements EventReader, ImmediateEventPublisher {

    private final ClientRequestErrorMapper clientRequestErrorMapper;
    private final Client client;
    private final EventSource eventSource;
    private final EventTypeResolver eventTypeResolver;
    private final EventDataMarshaller eventDataMarshaller;
    private final EventUpcasters eventUpcasters;

    EventRepository(
            ClientRequestErrorMapper clientRequestErrorMapper,
            Client client,
            EventSource eventSource,
            EventTypeResolver eventTypeResolver,
            EventDataMarshaller eventDataMarshaller,
            EventUpcasters eventUpcasters) {
        this.clientRequestErrorMapper = clientRequestErrorMapper;
        this.client = client;
        this.eventSource = eventSource;
        this.eventTypeResolver = eventTypeResolver;
        this.eventDataMarshaller = eventDataMarshaller;
        this.eventUpcasters = eventUpcasters;
    }

    public EventRepository(
            Client client,
            EventSource eventSource,
            EventTypeResolver eventTypeResolver,
            EventDataMarshaller eventDataMarshaller,
            EventUpcasters eventUpcasters) {
        this(
                new ClientRequestErrorMapper(),
                client,
                eventSource,
                eventTypeResolver,
                eventDataMarshaller,
                eventUpcasters);
    }

    private RawCallback forRawEvent(Event event) {
        return eventConsumer -> eventUpcasters
                .upcast(event)
                .forEach(upcasted -> eventConsumer.accept(forUpcastedEvent(upcasted), upcasted));
    }

    private UpcastedCallback forUpcastedEvent(Event event) {
        return new UpcastedCallback() {

            @Override
            public Class<?> getEventJavaClass() {
                return eventTypeResolver.getJavaClass(event.type());
            }

            @Override
            public void convert(BiConsumer<Map<String, ?>, Object> eventConsumer) {
                EventData<?> deserialized = eventDataMarshaller.deserialize(event.data(), getEventJavaClass());
                eventConsumer.accept(deserialized.metaData(), deserialized.payload());
            }
        };
    }

    @Override
    public void consumeRaw(ClientRequestor clientRequestor, BiConsumer<RawCallback, Event> eventConsumer) {
        clientRequestErrorMapper.handleMappingExceptionsIfNecessary(() -> {
            clientRequestor.request(client, event -> eventConsumer.accept(forRawEvent(event), event));
            return null;
        });
    }

    @Override
    public List<Event> publish(Consumer<EventPublisher> handler, List<Precondition> additionalPreconditions) {
        var capturer = new EventCapturer();

        handler.accept(capturer);

        return publish(capturer.getEvents(), additionalPreconditions);
    }

    @Override
    public List<Event> publish(List<CapturedEvent> events, List<Precondition> additionalPreconditions) {
        List<EventCandidate> eventCandidates = events.stream()
                .map(e -> new EventCandidate(
                        eventSource.source(),
                        e.subject(),
                        eventTypeResolver.getEventType(e.event().getClass()),
                        eventDataMarshaller.serialize(new EventData<>(e.metaData(), e.event()))))
                .toList();

        List<Precondition> preconditions = new ArrayList<>(additionalPreconditions);

        events.stream().flatMap(e -> e.preconditions().stream()).collect(toCollection(() -> preconditions));

        return clientRequestErrorMapper.handleMappingExceptionsIfNecessary(
                () -> client.write(eventCandidates, preconditions));
    }
}
