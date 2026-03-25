/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class OpenTelemetryTracingContextSpanBuilder implements TracingContextSpanBuilder {

    private final Tracer tracer;

    public OpenTelemetryTracingContextSpanBuilder(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("scope-name"); // TODO: Better solution than hard-coded
    }

    protected OpenTelemetryTracingContextSpanBuilder(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void executeRunnableWithNewSpan(Map<String, String> spanInfo, Runnable runnable) {
        executeRunnableWithNewSpan(spanInfo, runnable, (si) -> si);
    }

    @Override
    public void executeRunnableWithNewSpan(
            Map<String, String> spanInfo, Runnable runnable, UnaryOperator<Map<String, String>> spanInfoPostProcessor) {

        var span = buildAndStartNewSpan(spanInfo);
        var success = false;

        try (Scope spanScope = span.makeCurrent()) {
            runnable.run();
            success = true;
        } finally {
            setAttributesAndEndSpan(spanInfoPostProcessor.apply(spanInfo), span, success);
        }
    }

    @Override
    public <R> R executeSupplierWithNewSpan(Map<String, String> spanInfo, Supplier<R> supplier) {
        return executeSupplierWithNewSpan(spanInfo, supplier, (r, si) -> si);
    }

    @Override
    public <R> R executeSupplierWithNewSpan(
            Map<String, String> spanInfo,
            Supplier<R> supplier,
            BiFunction<R, Map<String, String>, Map<String, String>> spanInfoPostProcessor) {

        var span = buildAndStartNewSpan(spanInfo);
        var success = false;
        R result = null;

        try (Scope spanScope = span.makeCurrent()) {
            result = supplier.get();
            success = true;
        } finally {
            setAttributesAndEndSpan(spanInfoPostProcessor.apply(result, spanInfo), span, success);
        }

        return result;
    }

    private Span buildAndStartNewSpan(Map<String, String> spanInfo) {

        SpanBuilder spanBuilder = tracer.spanBuilder(spanInfo.getOrDefault("span.name", "span-name"));

        return spanBuilder.startSpan();
    }

    private void setAttributesAndEndSpan(Map<String, String> spanInfo, Span span, boolean success) {
        for (var entry : spanInfo.entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }

        span.setAttribute("handler.success", success);

        span.end();
    }
}
