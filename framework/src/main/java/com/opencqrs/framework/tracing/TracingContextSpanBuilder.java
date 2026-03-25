/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface TracingContextSpanBuilder {

    void executeRunnableWithNewSpan(Map<String, String> spanInfo, Runnable runnable);

    void executeRunnableWithNewSpan(
            Map<String, String> spanInfo, Runnable runnable, UnaryOperator<Map<String, String>> spanInfoPostProcessor);

    <R> R executeSupplierWithNewSpan(Map<String, String> spanInfo, Supplier<R> supplier);

    <R> R executeSupplierWithNewSpan(
            Map<String, String> spanInfo,
            Supplier<R> supplier,
            BiFunction<R, Map<String, String>, Map<String, String>> spanInfoPostProcessor);
}
