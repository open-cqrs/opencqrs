package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.persistence.EventReader;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.util.function.BiConsumer;

public class OpenTelemetryTracingAwareEventReader implements TracingAwareEventReader {

    private final EventReader eventReader;
    private final TextMapPropagator propagator;
    private final TextMapGetter<Event> textMapGetter;

    public OpenTelemetryTracingAwareEventReader(EventReader eventReader, OpenTelemetry openTelemetry, TextMapGetter<Event> textMapGetter) {
        this.eventReader = eventReader;
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        this.textMapGetter = textMapGetter;
    }

    @Override
    public void consumeRaw(EventReader.ClientRequestor clientRequestor, BiConsumer<EventReader.RawCallback, Event> eventConsumer) {
        // TODO: warn/fail if trace already active? or preserve/restore existing trace
        eventReader.consumeRaw(clientRequestor, (rawCallback,event) -> {
            // TODO: what happens here, if no trace present on Event?
            try (Scope unused = propagator.extract(Context.current(), event, textMapGetter).makeCurrent()) {
                eventConsumer.accept(rawCallback, event);
            }
        });
    }
}
