/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenTelemetryTracingContextSpanBuilderTest {

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

    @Mock
    private Supplier<String> supplier;

    private OpenTelemetryTracingContextSpanBuilder contextSpanBuilder;

    @BeforeEach
    void setUp() {
        when(openTelemetry.getTracer(anyString())).thenReturn(tracer);
        contextSpanBuilder = new OpenTelemetryTracingContextSpanBuilder(openTelemetry);
    }

    @Test
    void executeRunnableInNewSpan() {
        Map<String, String> spanInfo = Map.ofEntries(Map.entry("test.info", "test-data"));

        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);

        contextSpanBuilder.executeRunnableWithNewSpan(spanInfo, runnable);

        verify(spanBuilder).startSpan();
        verify(span).makeCurrent();
        verify(runnable).run();

        verify(scope).close();

        verify(span).setAttribute("test.info", "test-data");
        verify(span).setAttribute("handler.success", true);
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

        verify(span).setAttribute("handler.success", false);
        verify(span).end();
        verify(scope).close();
    }

    @Test
    void executeSupplierInNewSpan() {
        Map<String, String> spanInfo = Map.ofEntries(Map.entry("test.info", "test-data"));

        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);
        when(supplier.get()).thenReturn("got");

        var result = contextSpanBuilder.executeSupplierWithNewSpan(spanInfo, supplier);

        verify(spanBuilder).startSpan();
        verify(span).makeCurrent();
        verify(supplier).get();

        verify(scope).close();

        verify(span).setAttribute("test.info", "test-data");
        verify(span).setAttribute("handler.success", true);
        verify(span).end();

        assertEquals("got", result);
    }

    @Test
    void executeSupplierInNewSpanAndPostProcessSpanInfo() {
        Map<String, String> spanInfo = Map.ofEntries(Map.entry("test.info", "test-data"));

        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);
        when(supplier.get()).thenReturn("got");

        var result = contextSpanBuilder.executeSupplierWithNewSpan(spanInfo, supplier, (r, si) -> {
            var newMap = new HashMap<>(si);
            newMap.put("test.result", r);

            return newMap;
        });

        verify(spanBuilder).startSpan();
        verify(span).makeCurrent();
        verify(supplier).get();

        verify(scope).close();

        verify(span).setAttribute("test.info", "test-data");
        verify(span).setAttribute("handler.success", true);
        verify(span).setAttribute("test.result", result);
        verify(span).end();

        assertEquals("got", result);
    }
}
