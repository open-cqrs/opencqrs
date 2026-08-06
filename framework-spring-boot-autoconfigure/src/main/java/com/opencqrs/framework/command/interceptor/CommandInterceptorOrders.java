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
 * <p>The negative range holds the <em>outermost</em> provided interceptors &mdash; those whose transform must apply
 * last (in the {@code publish} stage, the outermost advice runs last) or which must wrap everything (future
 * observability, security). The values are spaced to leave integer room for user interceptors to interleave, e.g.
 * {@code @Order(CommandInterceptorOrders.OPTIMISTIC_LOCKING - 10)} to sit just outside optimistic locking.
 */
public final class CommandInterceptorOrders {

    private CommandInterceptorOrders() {}

    /**
     * Order of the {@link com.opencqrs.framework.metadata.MetaDataPropagatingCommandInterceptor}. Outermost among the
     * provided interceptors so its event meta-data enrichment applies <strong>last</strong> at the {@code publish}
     * stage &mdash; resilient against user interceptors that also rewrite event meta-data.
     */
    public static final int META_DATA_PROPAGATION = -1000;

    /** Order of the {@link OptimisticLockingCommandInterceptor}. */
    public static final int OPTIMISTIC_LOCKING = 0;
}
