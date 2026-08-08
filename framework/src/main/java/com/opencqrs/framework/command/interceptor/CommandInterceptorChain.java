/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.interceptor.InterceptorChains;
import com.opencqrs.framework.interceptor.StageWork;
import com.opencqrs.framework.interceptor.ValueContinuation;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Framework-internal driver composing the interceptor around-tree for a single command execution and exposing the
 * interior stage seams to {@link com.opencqrs.framework.command.CommandRouter}.
 *
 * <p>{@link #execute(CommandInvocation, CommandInterior)} composes the given interceptors (index {@code 0} = outermost)
 * as nested roots; the innermost root {@linkplain DefaultCommandLifecycle#freeze() freezes} the lifecycle and runs the
 * supplied {@link CommandInterior}. The interior then wraps each stage via {@link #sourcing}, {@link #sourcedEvent},
 * {@link #handler}, {@link #publishedEvent}, and {@link #publish}, whose composed advice comes from the interceptors'
 * lifecycle registrations.
 *
 * @param <R> the command result type
 */
public final class CommandInterceptorChain<R> {

    @SuppressWarnings("rawtypes")
    private final List<CommandInterceptor> interceptors;

    private final DefaultCommandLifecycle<R> lifecycle = new DefaultCommandLifecycle<>();

    /**
     * @param interceptors the applicable interceptors, already filtered by {@link CommandInterceptor#commandClass()}
     *     and ordered outermost-first
     */
    public CommandInterceptorChain(@SuppressWarnings("rawtypes") List<CommandInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    /**
     * Composes the interceptor roots around {@code interior} and runs the whole chain.
     *
     * @param invocation the command entry data
     * @param interior the framework core's command-execution body
     * @param <C> the command type
     * @return the command result
     * @throws Exception any exception propagated from an interceptor or a wrapped stage
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <C extends Command> @Nullable R execute(CommandInvocation<C> invocation, CommandInterior<R> interior)
            throws Exception {
        ValueContinuation<R> chain = () -> {
            lifecycle.freeze();
            return interior.execute(this);
        };
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            CommandInterceptor interceptor = interceptors.get(i);
            ValueContinuation<R> inner = chain;
            chain = () -> (R) interceptor.intercept(invocation, lifecycle, inner);
        }
        return chain.proceed();
    }

    /**
     * Wraps the state-rebuilding stage with the registered {@code sourcing} advice.
     *
     * @param joinPoint the sourcing join point
     * @param work the actual store read + replay
     * @throws Exception any exception propagated from an advice or the stage
     */
    public void sourcing(Sourcing joinPoint, StageWork work) throws Exception {
        InterceptorChains.observerChain(lifecycle.sourcing, joinPoint, work).proceed();
    }

    /**
     * Wraps a single replayed-event state-apply with the registered {@code sourcedEvent} advice.
     *
     * @param joinPoint the sourced-event join point
     * @param work the actual state-rebuilding handler invocation
     * @throws Exception any exception propagated from an advice or the stage
     */
    public void sourcedEvent(SourcedEventInvocation joinPoint, StageWork work) throws Exception {
        InterceptorChains.observerChain(lifecycle.sourcedEvent, joinPoint, work).proceed();
    }

    /**
     * Wraps the command handler invocation with the registered {@code handler} advice, threading the result.
     *
     * @param joinPoint the handler join point
     * @param work the actual command handler invocation
     * @return the (possibly substituted) command result
     * @throws Exception any exception propagated from an advice or the stage
     */
    public @Nullable R handler(CommandHandlerInvocation joinPoint, ValueContinuation<R> work) throws Exception {
        return InterceptorChains.transformerChain(lifecycle.handler, joinPoint, work)
                .proceed();
    }

    /**
     * Wraps a single emitted-event state-apply with the registered {@code publishedEvent} advice.
     *
     * @param joinPoint the published-event join point
     * @param work the actual state-rebuilding handler invocation
     * @throws Exception any exception propagated from an advice or the stage
     */
    public void publishedEvent(PublishedEventInvocation joinPoint, StageWork work) throws Exception {
        InterceptorChains.observerChain(lifecycle.publishedEvent, joinPoint, work)
                .proceed();
    }

    /**
     * Transforms the {@link Publish append request} with the registered {@code publish} advice, threading the request.
     * The actual atomic append is performed by the framework core using the returned request &mdash; it is not wrapped
     * by advice.
     *
     * <p>The returned request is guaranteed non-{@code null}: a {@code publish} advice that returns {@code null}
     * violates the transformer contract and fails the command with a
     * {@link CqrsFrameworkException.NonTransientException}.
     *
     * @param joinPoint the initial append request
     * @param work yields the request to append (an identity terminal)
     * @return the (possibly rewritten) append request, never {@code null}
     * @throws Exception any exception propagated from an advice
     */
    public Publish publish(Publish joinPoint, ValueContinuation<Publish> work) throws Exception {
        Publish request = InterceptorChains.transformerChain(lifecycle.publish, joinPoint, work)
                .proceed();
        if (request == null) {
            throw new CqrsFrameworkException.NonTransientException(
                    "a publish command interceptor returned a null append request");
        }
        return request;
    }
}
