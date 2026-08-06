/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.interceptor;

import com.opencqrs.framework.interceptor.StageObserver;

/**
 * Registration surface for the interior stages of a raw event's processing, handed to
 * {@link EventInterceptor#intercept}. Register the hooks you need <strong>before</strong> the root continuation
 * proceeds; the lifecycle freezes once interior execution begins, and a later registration throws
 * {@link com.opencqrs.framework.interceptor.InterceptorContractViolation}.
 *
 * <p>Multiple registrations on the same hook nest in <strong>registration (call) order</strong> &mdash; first
 * registered = outermost. This is registration only: it exposes no getters (data travels as join-point arguments to
 * each advice).
 */
public interface EventLifecycle {

    /**
     * Registers an around of each matching event-handler invocation, gated by {@linkplain Class#isInstance(Object)
     * assignability} of the converted event to {@code eventClass}. Fires once per matching
     * {@link com.opencqrs.framework.eventhandler.EventHandlerDefinition} for each relevant converted event, nested
     * inside the root {@link EventInterceptor#intercept intercept}.
     *
     * @param eventClass the most general converted-event type this advice applies to; the assignability gate
     * @param advice the observer advice
     * @param <E> the targeted converted-event type
     */
    <E> void handler(Class<E> eventClass, StageObserver<EventHandlerInvocation<E>> advice);

    /**
     * Registers an around of <strong>every</strong> matching event-handler invocation, regardless of converted-event
     * type. Convenience for {@link #handler(Class, StageObserver) handler(Object.class, advice)}.
     *
     * @param advice the observer advice
     */
    default void handler(StageObserver<EventHandlerInvocation<Object>> advice) {
        handler(Object.class, advice);
    }
}
