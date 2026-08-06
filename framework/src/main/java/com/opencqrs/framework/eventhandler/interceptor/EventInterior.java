/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.interceptor;

/**
 * The framework core's event-processing body, supplied by
 * {@link com.opencqrs.framework.eventhandler.EventHandlingProcessor} and run at the innermost point of the interceptor
 * root chain (after the lifecycle has frozen). It drives the actual per-converted-event handler dispatch through the
 * {@link EventInterceptorChain} it is handed &mdash; wrapping {@linkplain EventHandlerInvocation each matching handler
 * invocation} with its registered advice.
 */
@FunctionalInterface
public interface EventInterior {

    /**
     * Runs the event-processing body.
     *
     * @param chain the frozen chain exposing the {@code handler} stage-wrapping seam
     * @throws Exception any exception from the wrapped stages
     */
    void execute(EventInterceptorChain chain) throws Exception;
}
