/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.opencqrs.esdb.client.Event;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OpenTelemetryEventTracingContextExtractorTest {

    private String TRACE_PARENT = "00-11111111111111111111111111111111-2222222222222222-01";
    private String TRACE_STATE = "congo=t61rcWkgMzE";

    private TextMapPropagator propagator;
    private TextMapGetter<Event> textMapGetter;

    private OpenTelemetryEventTracingContextExtractor subject;

    @BeforeEach
    void setUp() {
        propagator = mock(TextMapPropagator.class);
        textMapGetter = mock(EventTracingContextGetter.class);

        subject = new OpenTelemetryEventTracingContextExtractor(propagator, textMapGetter);
    }

    @Test
    void shouldExtractTraceContext() {

        Event mocked = Mockito.mock();

        Context initialCtx = Context.current();
        Context expectedCtx = initialCtx
                .with(ContextKey.named("traceparent"), TRACE_PARENT)
                .with(ContextKey.named("tracestate"), TRACE_STATE);

        when(propagator.extract(eq(initialCtx), any(), any())).thenReturn(expectedCtx);

        subject.extractAndRestoreContextFromEvent(mocked, () -> assertEquals(expectedCtx, Context.current()));

        assertEquals(initialCtx, Context.current());
    }
}
