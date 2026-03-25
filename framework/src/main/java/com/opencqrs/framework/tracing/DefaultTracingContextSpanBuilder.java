/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class DefaultTracingContextSpanBuilder implements TracingContextSpanBuilder {
    @Override
    public void executeRunnableWithNewSpan(Map<String, String> spanInfo, Runnable runnable) {
        runnable.run();
    }

    @Override
    public void executeRunnableWithNewSpan(
            Map<String, String> spanInfo, Runnable runnable, UnaryOperator<Map<String, String>> spanInfoPostProcessor) {
        runnable.run();
    }

    @Override
    public <R> R executeSupplierWithNewSpan(Map<String, String> spanInfo, Supplier<R> supplier) {
        return supplier.get();
    }

    @Override
    public <R> R executeSupplierWithNewSpan(
            Map<String, String> spanInfo,
            Supplier<R> supplier,
            BiFunction<R, Map<String, String>, Map<String, String>> spanInfoPostProcessor) {
        return supplier.get();
    }
}
