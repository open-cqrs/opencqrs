/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

import org.jspecify.annotations.Nullable;

/**
 * Value continuation handed to a {@link StageTransformer} (and to the interceptor roots): calling {@link #proceed()}
 * runs the wrapped stage and returns its real result value.
 *
 * <p>Unlike the observer {@link Continuation} it is <strong>at-least-once</strong>: it may be skipped entirely to
 * short-circuit with a substitute value, or invoked more than once to retry the wrapped stage.
 *
 * @param <V> the result type of the wrapped stage
 * @see StageTransformer
 */
@FunctionalInterface
public interface ValueContinuation<V> {

    /**
     * Runs the wrapped stage and returns its result. May be called zero times (short-circuit) or more than once
     * (retry).
     *
     * @return the result of the wrapped stage
     * @throws Exception any exception propagated from the wrapped stage
     */
    @Nullable
    V proceed() throws Exception;
}
