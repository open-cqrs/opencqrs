/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import com.opencqrs.framework.optimisticlocking.OptimisticLockingCommandInterceptor;

/**
 * Well-known {@link org.springframework.core.annotation.Order @Order} values for the framework-provided command
 * {@link CommandInterceptor}s, so their relative nesting is defined in one place and user interceptors can position
 * themselves around them.
 *
 * <p>Spring sorts the {@code List<CommandInterceptor>} injected into the
 * {@link com.opencqrs.framework.command.CommandRouter} by {@code @Order}; {@code CommandRouter} treats index {@code 0}
 * as the outermost interceptor. Hence, by convention here, <strong>lower = further outside</strong> (wraps more).
 * Interceptor beans without an explicit order sort at {@link org.springframework.core.Ordered#LOWEST_PRECEDENCE}
 * &mdash; i.e. innermost.
 *
 * <p>The negative range is reserved for future outermost provided interceptors (e.g. observability, security); the
 * values are spaced to leave integer room for user interceptors to interleave, e.g.
 * {@code @Order(CommandInterceptorOrders.OPTIMISTIC_LOCKING - 10)} to sit just outside optimistic locking.
 */
public final class CommandInterceptorOrders {

    private CommandInterceptorOrders() {}

    /** Order of the {@link OptimisticLockingCommandInterceptor}. */
    public static final int OPTIMISTIC_LOCKING = 0;
}
