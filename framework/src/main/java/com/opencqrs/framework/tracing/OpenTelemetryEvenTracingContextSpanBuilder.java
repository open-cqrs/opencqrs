/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.Map;

public class OpenTelemetryEvenTracingContextSpanBuilder implements EventTracingContextSpanBuilder {

    private final Tracer tracer;

    public OpenTelemetryEvenTracingContextSpanBuilder(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("scope-name"); // TODO: Better solution than hard-coded
    }

    protected OpenTelemetryEvenTracingContextSpanBuilder(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void executeRunnableWithNewSpan(Map<String, String> spanInfo, Runnable runnable) {

        SpanBuilder spanBuilder = tracer.spanBuilder(spanInfo.getOrDefault("span.name", "span-name"));

        // TODO: Set attributes from spanInfo
        spanInfo.forEach(spanBuilder::setAttribute);

        Span span = spanBuilder.startSpan();

        try (Scope spanScope = span.makeCurrent()) {
            runnable.run();
        } finally {
            span.end();
        }
    }
}
