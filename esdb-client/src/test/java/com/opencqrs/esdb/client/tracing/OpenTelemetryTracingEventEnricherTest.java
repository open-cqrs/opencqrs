/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.opencqrs.esdb.client.EventCandidate;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenTelemetryTracingEventEnricherTest {

    private TextMapPropagator propagator;
    private OpenTelemetryTracingEventEnricher enricher;

    @BeforeEach
    void setUp() {
        propagator = mock(TextMapPropagator.class);
        enricher = new OpenTelemetryTracingEventEnricher(propagator);
    }

    @Test
    void shouldEnrichFromPropagatedTracingContext() {
        doAnswer(invocation -> {
                    Map<String, String> carrier = invocation.getArgument(1);
                    carrier.put("traceparent", "00-11111111111111111111111111111111-2222222222222222-01");
                    carrier.put("tracestate", "congo=t61rcWkgMzE");
                    return null;
                })
                .when(propagator)
                .inject(any(Context.class), any(Map.class), any());

        EventCandidate originalCandidate =
                new EventCandidate("source", "subject", "type", Collections.emptyMap(), null, null);

        EventCandidate enrichedCandidate = enricher.enrichWithTracingData(originalCandidate);

        assertEquals("source", enrichedCandidate.source());
        assertEquals("subject", enrichedCandidate.subject());
        assertEquals("type", enrichedCandidate.type());
        assertEquals(Collections.emptyMap(), enrichedCandidate.data());
        assertEquals("00-11111111111111111111111111111111-2222222222222222-01", enrichedCandidate.traceParent());
        assertEquals("congo=t61rcWkgMzE", enrichedCandidate.traceState());
    }

    @Test
    void shouldKeepExistingTracingHeaders() {
        EventCandidate originalCandidate = new EventCandidate(
                "source", "subject", "type", Collections.emptyMap(), "existing-traceparent", "existing-tracestate");

        EventCandidate enrichedCandidate = enricher.enrichWithTracingData(originalCandidate);

        assertEquals("existing-traceparent", enrichedCandidate.traceParent());
        assertEquals("existing-tracestate", enrichedCandidate.traceState());
    }
}
