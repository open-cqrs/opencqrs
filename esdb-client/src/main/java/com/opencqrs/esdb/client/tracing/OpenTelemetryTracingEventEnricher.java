/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import com.opencqrs.esdb.client.EventCandidate;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenTelemetry-based implementation of the {@link TracingEventEnricher} interface which works in conjunction with
 * OTel's official java instrumentation
 *
 * @see <a href="https://opentelemetry.io/docs/zero-code/java/">Official documentation for OpenTelemetry Java
 *     instrumentation</a>
 */
public class OpenTelemetryTracingEventEnricher implements TracingEventEnricher {

    private final TextMapPropagator propagator;

    public OpenTelemetryTracingEventEnricher(OpenTelemetry openTelemetry) {
        this(openTelemetry.getPropagators().getTextMapPropagator());
    }

    protected OpenTelemetryTracingEventEnricher(TextMapPropagator propagator) {
        this.propagator = propagator;
    }

    /**
     * Retrieves 'traceparent' and 'tracestate' headers from current context and enriches a given {@link EventCandidate}
     * with them
     *
     * @param candidate the {@link EventCandidate} to be enriched with tracing data
     * @return the enriched {@link EventCandidate}
     */
    @Override
    public EventCandidate enrichWithTracingData(EventCandidate candidate) {
        var headers = getHeaders();
        return new EventCandidate(
                candidate.source(),
                candidate.subject(),
                candidate.type(),
                candidate.data(),
                candidate.traceParent() != null ? candidate.traceParent() : headers.getOrDefault("traceparent", null),
                candidate.traceState() != null ? candidate.traceState() : headers.getOrDefault("tracestate", null));
    }

    protected Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        Context context = Context.current();
        propagator.inject(context, headers, Map::put);
        return headers;
    }
}
