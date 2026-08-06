/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

import org.jspecify.annotations.Nullable;

/**
 * Around advice for a stage whose success value may legitimately be substituted (the command handler, the command
 * result). The advice may call {@link ValueContinuation#proceed()} to run the wrapped stage and return its value,
 * <em>or</em> return a substitute value <strong>without</strong> proceeding to short-circuit the stage while still
 * reporting success (the canonical case being idempotency: return a cached result instead of running the handler).
 *
 * <p>As with {@link StageObserver}, throwing aborts the whole operation.
 *
 * @param <J> the join-point type carrying the stage's phase-new data
 * @param <V> the result type of the wrapped stage
 * @see StageObserver
 */
@FunctionalInterface
public interface StageTransformer<J, V> {

    /**
     * Wraps the stage identified by {@code joinPoint}.
     *
     * @param joinPoint the phase-new data for the wrapped stage
     * @param continuation the at-least-once continuation running the wrapped stage
     * @return the (possibly substituted) result value
     * @throws Exception to abort the operation, or any exception propagated from the wrapped stage
     */
    @Nullable
    V around(J joinPoint, ValueContinuation<V> continuation) throws Exception;
}
