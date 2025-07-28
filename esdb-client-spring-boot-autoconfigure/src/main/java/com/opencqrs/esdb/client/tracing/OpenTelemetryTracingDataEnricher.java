package com.opencqrs.esdb.client.tracing;

import com.opencqrs.esdb.client.EventCandidate;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.util.HashMap;
import java.util.Map;

public class OpenTelemetryTracingDataEnricher implements TracingDataEnricher {

    private final TextMapPropagator propagator;

    public OpenTelemetryTracingDataEnricher(OpenTelemetry openTelemetry) {
        this(openTelemetry.getPropagators().getTextMapPropagator());
    }

    protected OpenTelemetryTracingDataEnricher(TextMapPropagator propagator) {
        this.propagator = propagator;
    }

    @Override
    public EventCandidate enrichWithTracingData(EventCandidate candidate) {

        if (candidate.traceParent() == null) {
            Map<String, String> headers = new HashMap<>();
            Context context = Context.current();

            propagator.inject(context, headers, Map::put);

            var traceParent = headers.getOrDefault("traceparent", null);
            var traceState = headers.getOrDefault("tracestate", null);

            return new EventCandidate(
                    candidate.source(),
                    candidate.subject(),
                    candidate.type(),
                    candidate.data(),
                    traceParent,
                    traceState
            );
        } else {
            return candidate;
        }
    }
}
