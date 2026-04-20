/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;

/** OpenTelemetry-based implementation of {@link EventTracingContextExtractor} */
public class OpenTelemetryEventTracingContextExtractor implements EventTracingContextExtractor {

    private final TextMapPropagator propagator;
    private final TextMapGetter<Event> textMapGetter;

    public OpenTelemetryEventTracingContextExtractor(OpenTelemetry openTelemetry, TextMapGetter<Event> textMapGetter) {
        this(openTelemetry.getPropagators().getTextMapPropagator(), textMapGetter);
    }

    protected OpenTelemetryEventTracingContextExtractor(
            TextMapPropagator propagator, TextMapGetter<Event> textMapGetter) {
        this.propagator = propagator;
        this.textMapGetter = textMapGetter;
    }

    /**
     * Extracts and restores a trace from the event trail and runs a piece of logic within it
     *
     * @param event the {@link Event} from which to extract the tracing data
     * @param runnable a closure containing logic to be executed within the resurrected trace
     */
    @Override
    public void extractAndRestoreContextFromEvent(Event event, Runnable runnable) {
        try (Scope unused =
                propagator.extract(Context.current(), event, textMapGetter).makeCurrent()) {

            runnable.run();
        }
    }
}
