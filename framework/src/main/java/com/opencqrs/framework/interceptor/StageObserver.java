/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

/**
 * Around advice for a stage that <strong>must</strong> be executed &mdash; observe, time, count, or guard it, but never
 * silently substitute its outcome. The advice does its work, calls {@link Continuation#proceed()} to run the wrapped
 * stage, does more work, and returns the {@link Proceeded} token that {@code proceed()} produced.
 *
 * <p>To abort the whole operation (e.g. a security denial or validation failure) an observer simply {@code throw}s; the
 * exception propagates up the chain and fails the command/event.
 *
 * @param <J> the join-point type carrying the stage's phase-new data
 * @see StageTransformer
 */
@FunctionalInterface
public interface StageObserver<J> {

    /**
     * Wraps the stage identified by {@code joinPoint}.
     *
     * @param joinPoint the phase-new data for the wrapped stage
     * @param continuation the exactly-once continuation running the wrapped stage
     * @return the {@link Proceeded} token returned by {@link Continuation#proceed()}
     * @throws Exception to abort the operation, or any exception propagated from the wrapped stage
     */
    Proceeded around(J joinPoint, Continuation continuation) throws Exception;
}
