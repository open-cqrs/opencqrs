package com.opencqrs.esdb.client.tracing;

import com.opencqrs.esdb.client.EventCandidate;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

import java.util.HashMap;
import java.util.Map;

public class OTLPTracingContextProvider implements TracingContextProvider {

    private final TextMapPropagator propagator;

    public OTLPTracingContextProvider(OpenTelemetry openTelemetry) {
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
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
