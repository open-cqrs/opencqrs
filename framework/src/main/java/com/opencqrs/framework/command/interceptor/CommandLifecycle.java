/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import com.opencqrs.framework.interceptor.StageObserver;
import com.opencqrs.framework.interceptor.StageTransformer;

/**
 * Registration surface for the interior stages of a command execution, handed to {@link CommandInterceptor#intercept}.
 * Register the hooks you need <strong>before</strong> the root continuation proceeds; the lifecycle freezes once
 * interior execution begins, and a later registration throws
 * {@link com.opencqrs.framework.interceptor.InterceptorContractViolation}.
 *
 * <p>Multiple registrations on the same hook nest in <strong>registration (call) order</strong> &mdash; first
 * registered = outermost. This is registration only: it exposes no getters (data travels as join-point arguments to
 * each advice).
 *
 * @param <R> the command result type threaded through {@link #handler(StageTransformer)}
 */
public interface CommandLifecycle<R> {

    /**
     * Registers an around of the whole state-rebuilding stage (store read + all replays). Fires once, before the
     * handler.
     *
     * @param advice the observer advice
     */
    void sourcing(StageObserver<Sourcing> advice);

    /**
     * Registers an around of each replayed-event state-apply. Fires once per (sourced event × matching state-rebuilding
     * handler), nested inside {@link #sourcing(StageObserver) sourcing}.
     *
     * @param advice the observer advice
     */
    void sourcedEvent(StageObserver<SourcedEventInvocation> advice);

    /**
     * Registers an around of the command handler invocation. Fires once. Being a transformer, an advice may substitute
     * the result without proceeding (short-circuit) or transform it.
     *
     * @param advice the transformer advice threading the command result {@code R} (which may itself be {@code null})
     */
    void handler(StageTransformer<CommandHandlerInvocation, R> advice);

    /**
     * Registers an around of each emitted-event state-apply. Fires once per (emitted event × matching state-rebuilding
     * handler), nested inside {@link #handler(StageTransformer) the handler}.
     *
     * @param advice the observer advice
     */
    void publishedEvent(StageObserver<PublishedEventInvocation> advice);

    /**
     * Registers a transformer over the {@link Publish append request}. Fires once, after the handler, only when at
     * least one event is appended. An advice may rewrite the request (events / preconditions) before the append, or
     * veto by throwing; the framework appends the returned request.
     *
     * @param advice the transformer advice threading the {@link Publish} request
     */
    void publish(StageTransformer<Publish, Publish> advice);
}
