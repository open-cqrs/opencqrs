/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import org.jspecify.annotations.Nullable;

/**
 * The framework core's command-execution body, supplied by {@link com.opencqrs.framework.command.CommandRouter} and run
 * at the innermost point of the interceptor root chain (after the lifecycle has frozen). It drives the actual stages
 * through the {@link CommandInterceptorChain} it is handed &mdash; wrapping {@linkplain Sourcing sourcing},
 * {@linkplain SourcedEventInvocation each sourcing state-apply}, the {@linkplain CommandHandlerInvocation handler},
 * {@linkplain PublishedEventInvocation each published state-apply}, and the {@linkplain Publish publish} with their
 * registered advice(s).
 *
 * @param <R> the command result type
 */
@FunctionalInterface
public interface CommandInterior<R> {

    /**
     * Runs the command-execution body.
     *
     * @param chain the frozen chain exposing the stage-wrapping seams
     * @return the command result
     * @throws Exception any exception from the wrapped stages
     */
    @Nullable
    R execute(CommandInterceptorChain<R> chain) throws Exception;
}
