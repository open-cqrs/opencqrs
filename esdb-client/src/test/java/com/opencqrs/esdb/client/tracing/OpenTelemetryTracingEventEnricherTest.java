/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.opencqrs.esdb.client.EventCandidate;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenTelemetryTracingEventEnricherTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private OpenTelemetry openTelemetry;

    @InjectMocks
    private OpenTelemetryTracingEventEnricher enricher;

    @ParameterizedTest
    @CsvSource(
            value = {
                "null, null",
                "traceParent, null",
                "traceParent, traceState",
            },
            nullValues = {"null"})
    void shouldEnrichFromPropagatedTracingContext(String expectedTraceParent, String expectedTraceState) {

        var propagator = openTelemetry.getPropagators().getTextMapPropagator();

        doAnswer(invocation -> {
                    Map<String, String> carrier = invocation.getArgument(1);
                    if (expectedTraceParent != null) carrier.put("traceparent", expectedTraceParent);
                    if (expectedTraceState != null) carrier.put("tracestate", expectedTraceState);
                    return null;
                })
                .when(propagator)
                .inject(any(Context.class), any(Map.class), any());

        EventCandidate originalCandidate =
                new EventCandidate("source", "subject", "type", Collections.emptyMap(), null, null);

        EventCandidate enrichedCandidate = enricher.enrichWithTracingData(originalCandidate);

        assertThat(enrichedCandidate)
                .usingRecursiveComparison()
                .ignoringFields("traceParent", "traceState")
                .isEqualTo(originalCandidate);
        assertThat(enrichedCandidate.traceParent()).isEqualTo(expectedTraceParent);
        assertThat(enrichedCandidate.traceState()).isEqualTo(expectedTraceState);
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "traceParent, null",
                "traceParent, traceState",
                "null, traceState",
            },
            nullValues = {"null"})
    void shouldKeepExistingTracingHeaders(String existingTraceParent, String existingTraceState) {

        var propagator = openTelemetry.getPropagators().getTextMapPropagator();

        doAnswer(invocation -> {
                    Map<String, String> carrier = invocation.getArgument(1);
                    carrier.put("traceparent", "00-11111111111111111111111111111111-2222222222222222-01");
                    carrier.put("tracestate", "congo=t61rcWkgMzE");
                    return null;
                })
                .when(propagator)
                .inject(any(Context.class), any(Map.class), any());

        EventCandidate originalCandidate = new EventCandidate(
                "source", "subject", "type", Collections.emptyMap(), existingTraceParent, existingTraceState);

        EventCandidate enrichedCandidate = enricher.enrichWithTracingData(originalCandidate);

        assertThat(enrichedCandidate).isEqualTo(originalCandidate);
    }
}
