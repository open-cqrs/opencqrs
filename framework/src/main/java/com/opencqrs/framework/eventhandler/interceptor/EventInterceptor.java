/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.interceptor;

import com.opencqrs.framework.eventhandler.EventHandlingProcessor;
import com.opencqrs.framework.interceptor.Continuation;
import com.opencqrs.framework.interceptor.Proceeded;

/**
 * Around interceptor for event handling. The root {@link #intercept} wraps the whole processing of one raw event by an
 * {@link com.opencqrs.framework.eventhandler.EventHandlingProcessor}; it is an
 * {@linkplain com.opencqrs.framework.interceptor.StageObserver observer} &mdash; it <strong>must</strong> proceed
 * (observe, time, count, or guard, but never silently substitute the outcome) and return the {@link Proceeded} token
 * {@link Continuation#proceed()} produced.
 *
 * <p>Event interceptors are not per-se type gated; they choose their noisiness via {@link #delivery()} and gate
 * individual interior hooks per converted-event type when registering on the {@link EventLifecycle}.
 *
 * <p>Event interceptors share the event handlers' exception-based control flow:
 *
 * <ul>
 *   <li>throw {@link com.opencqrs.framework.CqrsFrameworkException.NonTransientException} &mdash; the processing loop
 *       terminates unrecoverably;
 *   <li>throw anything else &mdash; the event is retried with backoff, then skipped on exhaustion.
 * </ul>
 *
 * @see EventHandlingProcessor#run()
 */
public interface EventInterceptor {

    /**
     * Which events this interceptor wants its root {@link #intercept} fired for. Defaults to the narrowest level
     * ({@link Delivery#ACTIONABLE} &mdash; real work only).
     *
     * @return the delivery level; never {@code null}
     */
    default Delivery delivery() {
        return Delivery.ACTIONABLE;
    }

    /**
     * Wraps the whole processing of one {@link com.opencqrs.esdb.client.Event raw event}. Register interior hooks on
     * {@code lifecycle} before calling {@code continuation.proceed()}; do work before and after, and return the
     * {@link Proceeded} token that {@code proceed()} produced. Throw to end the loop (see the type-level termination
     * notes).
     *
     * @param invocation the immutable entry data
     * @param lifecycle the interior-stage registration surface
     * @param continuation the exactly-once continuation running the wrapped event processing
     * @return the {@link Proceeded} token returned by {@link Continuation#proceed()}
     * @throws Exception to terminate or fail the event processing, or any exception propagated from the wrapped
     *     processing
     */
    Proceeded intercept(EventInvocation invocation, EventLifecycle lifecycle, Continuation continuation)
            throws Exception;
}
