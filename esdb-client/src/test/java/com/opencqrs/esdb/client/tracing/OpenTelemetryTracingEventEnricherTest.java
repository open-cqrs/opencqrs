/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.opencqrs.esdb.client.EventCandidate;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    /** Stellt die geforderten Test-Kombinationen als Stream zur Verfügung. Argumente: (traceparent, tracestate) */
    static Stream<Arguments> tracingDataCombinations() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("00-11111111111111111111111111111111-2222222222222222-01", null),
                Arguments.of("00-11111111111111111111111111111111-2222222222222222-01", "congo=t61rcWkgMzE"));
    }

    @ParameterizedTest
    @MethodSource("tracingDataCombinations")
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

        assertEquals("source", enrichedCandidate.source());
        assertEquals("subject", enrichedCandidate.subject());
        assertEquals("type", enrichedCandidate.type());
        assertEquals(Collections.emptyMap(), enrichedCandidate.data());

        assertEquals(expectedTraceParent, enrichedCandidate.traceParent());
        assertEquals(expectedTraceState, enrichedCandidate.traceState());
    }

    @ParameterizedTest
    @MethodSource("tracingDataCombinations")
    void shouldKeepExistingTracingHeaders(String existingTraceParent, String existingTraceState) {

        EventCandidate originalCandidate = new EventCandidate(
                "source", "subject", "type", Collections.emptyMap(), existingTraceParent, existingTraceState);

        EventCandidate enrichedCandidate = enricher.enrichWithTracingData(originalCandidate);

        assertEquals(existingTraceParent, enrichedCandidate.traceParent());
        assertEquals(existingTraceState, enrichedCandidate.traceState());
    }
}
