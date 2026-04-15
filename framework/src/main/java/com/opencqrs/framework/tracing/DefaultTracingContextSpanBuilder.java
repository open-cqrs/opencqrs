/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Default implementation of {@link TracingContextSpanBuilder}
 */
public class DefaultTracingContextSpanBuilder implements TracingContextSpanBuilder {

    /**
     *
     * see: {@link DefaultTracingContextSpanBuilder#executeRunnableWithNewSpan(Map, Runnable, UnaryOperator)}
     *
     * @param spanInfo
     * @param runnable
     */
    @Override
    public void executeRunnableWithNewSpan(Map<String, String> spanInfo, Runnable runnable) {
        runnable.run();
    }

    /**
     * Default implementation which simply runs the runnable
     *
     * @param spanInfo contains metadata to enrich the span with (unused)
     * @param runnable closure containing the logic to be executed
     * @param spanInfoPostProcessor function returning new or updated span information after the runnable has been executed (unused)
     */
    @Override
    public void executeRunnableWithNewSpan(
            Map<String, String> spanInfo, Runnable runnable, UnaryOperator<Map<String, String>> spanInfoPostProcessor) {
        runnable.run();
    }

    /**
     * 
     * see: {@link DefaultTracingContextSpanBuilder#executeSupplierWithNewSpan(Map, Supplier, BiFunction)}
     * 
     * @param spanInfo
     * @param supplier
     * @return
     * @param <R>
     */
    @Override
    public <R> R executeSupplierWithNewSpan(Map<String, String> spanInfo, Supplier<R> supplier) {
        return supplier.get();
    }

    /**
     *
     * Default implementation which simply runs the supplier and returns its result
     *
     * @param spanInfo contains metadata to enrich the span with (unused)
     * @param supplier closure containing the logic to be executed
     * @param spanInfoPostProcessor  function returning new or updated span information after the runnable has been executed (unused)
     * @return the result of the supplier function
     * @param <R> return type of the supplier
     */
    @Override
    public <R> R executeSupplierWithNewSpan(
            Map<String, String> spanInfo,
            Supplier<R> supplier,
            BiFunction<R, Map<String, String>, Map<String, String>> spanInfoPostProcessor) {
        return supplier.get();
    }
}
