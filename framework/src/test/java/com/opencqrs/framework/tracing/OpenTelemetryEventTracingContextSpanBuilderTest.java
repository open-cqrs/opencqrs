/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenTelemetryEventTracingContextSpanBuilderTest {

    @Mock
    private OpenTelemetry openTelemetry;

    @Mock
    private Tracer tracer;

    @Mock
    private SpanBuilder spanBuilder;

    @Mock
    private Span span;

    @Mock
    private Scope scope;

    @Mock
    private Runnable runnable;

    private OpenTelemetryEvenTracingContextSpanBuilder contextSpanBuilder;

    @BeforeEach
    void setUp() {
        when(openTelemetry.getTracer(anyString())).thenReturn(tracer);
        contextSpanBuilder = new OpenTelemetryEvenTracingContextSpanBuilder(openTelemetry);
    }

    @Test
    void executeRunnableInNewSpan() {
        // TODO: Use non-empty info (?)
        Map<String, String> spanInfo = Collections.emptyMap();

        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);

        contextSpanBuilder.executeRunnableWithNewSpan(spanInfo, runnable);

        verify(spanBuilder).startSpan();
        verify(span).makeCurrent();
        verify(runnable).run();

        verify(scope).close();
        verify(span).end();
    }

    @Test
    void closeSpanWhenExceptionIsThrown() {
        Map<String, String> spanInfo = Collections.emptyMap();

        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);

        doThrow(new RuntimeException("Error")).when(runnable).run();

        try {
            contextSpanBuilder.executeRunnableWithNewSpan(spanInfo, runnable);
        } catch (RuntimeException e) {
        }

        verify(span).end();
        verify(scope).close();
    }

    @Test
    void verifyExecutionOrder() {
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);

        contextSpanBuilder.executeRunnableWithNewSpan(Collections.emptyMap(), runnable);

        InOrder inOrder = inOrder(span, scope, runnable);

        inOrder.verify(span).makeCurrent();
        inOrder.verify(runnable).run();
        inOrder.verify(scope).close(); // AutoCloseable im try-with-resources
        inOrder.verify(span).end(); // Im finally Block
    }
}
