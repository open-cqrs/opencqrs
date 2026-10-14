package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.persistence.EventReader;

import java.util.function.BiConsumer;

public class NoTracingAwareEventReader implements TracingAwareEventReader {

    private final EventReader eventReader;

    public NoTracingAwareEventReader(EventReader eventReader) {
        this.eventReader = eventReader;
    }

    @Override
    public void consumeRaw(EventReader.ClientRequestor clientRequestor, BiConsumer<EventReader.RawCallback, Event> eventConsumer) {
        eventReader.consumeRaw(clientRequestor, eventConsumer);
    }
}
