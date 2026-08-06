/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.interceptor.ValueContinuation;
import org.jspecify.annotations.Nullable;

/**
 * Around interceptor for command execution. The framework composes an interceptor into a command's chain only when
 * {@link #commandClass()} {@linkplain Class#isAssignableFrom(Class) is assignable from} the command's runtime type, so
 * {@link CommandInvocation#command()} is statically typed as {@code C}.
 *
 * <p>All-commands interceptors declare {@code CommandInterceptor<Command>} returning {@code Command.class}. For
 * non-type predicates, target a broad base and refine with {@code instanceof} inside {@link #intercept}.
 *
 * @param <C> the command type this interceptor targets
 */
public interface CommandInterceptor<C extends Command> {

    /**
     * The most general command type this interceptor applies to; the assignability gate.
     *
     * @return the targeted command class
     */
    Class<C> commandClass();

    /**
     * Wraps the whole command execution. Register interior hooks on {@code lifecycle} before calling
     * {@code continuation.proceed()}; return (possibly transformed) the result, short-circuit by returning a substitute
     * without proceeding, or throw to fail the command.
     *
     * @param invocation the immutable entry data
     * @param lifecycle the interior-stage registration surface
     * @param continuation the continuation proceeding the owning {@link CommandInterceptorChain}, finally running the
     *     wrapped command execution
     * @param <R> the command result type
     * @return the command result
     * @throws Exception to fail the command, or any exception propagated from the wrapped execution
     */
    <R> @Nullable R intercept(
            CommandInvocation<C> invocation, CommandLifecycle<R> lifecycle, ValueContinuation<R> continuation)
            throws Exception;
}
