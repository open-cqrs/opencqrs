/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

/**
 * Observer continuation handed to a {@link StageObserver}: calling {@link #proceed()} runs the wrapped stage and yields
 * the opaque {@link Proceeded} token the observer must return.
 *
 * <p>It is <strong>exactly-once</strong>: a second {@link #proceed()} throws {@link InterceptorContractViolation}
 * <em>before</em> re-running the stage, because re-observing a side-effecting stage is never legitimate.
 *
 * @see StageObserver
 * @see Proceeded
 */
@FunctionalInterface
public interface Continuation {

    /**
     * Runs the wrapped stage exactly once.
     *
     * @return the opaque proof that the stage was executed
     * @throws InterceptorContractViolation if called more than once
     * @throws Exception any exception propagated from the wrapped stage
     */
    Proceeded proceed() throws Exception;
}
