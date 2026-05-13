/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Interface for creating new trace spans to run logic in */
public interface TracingContextSpanBuilder {

    /**
     * Shorthand for {@link TracingContextSpanBuilder#executeRunnableWithNewSpan(Map, Runnable, UnaryOperator)} when no
     * post-processing is needed
     */
    void executeRunnableWithNewSpan(Map<String, String> spanInfo, Runnable runnable);

    /**
     * Executes a given piece of logic in a newly created span
     *
     * @param spanInfo contains metadata to enrich the span with
     * @param runnable closure containing the logic to be executed within the new span
     * @param spanInfoPostProcessor function returning new or updated span information after the runnable has been
     *     executed
     */
    void executeRunnableWithNewSpan(
            Map<String, String> spanInfo, Runnable runnable, UnaryOperator<Map<String, String>> spanInfoPostProcessor);

    /**
     * Shorthand for {@link TracingContextSpanBuilder#executeSupplierWithNewSpan(Map, Supplier, BiFunction)} when no
     * post-processing is needed
     */
    <R> R executeSupplierWithNewSpan(Map<String, String> spanInfo, Supplier<R> supplier);

    /**
     * Executes a given piece of logic in a newly created span and returns its return value
     *
     * @param spanInfo contains metadata to enrich the span with
     * @param supplier closure containing the logic to be executed within the new span
     * @param spanInfoPostProcessor function returning new or updated span information after the runnable has been
     *     executed
     * @return the supplier's result
     * @param <R> return type of supplier function
     */
    <R> R executeSupplierWithNewSpan(
            Map<String, String> spanInfo,
            Supplier<R> supplier,
            BiFunction<R, Map<String, String>, Map<String, String>> spanInfoPostProcessor);
}
