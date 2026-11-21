/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

import com.opencqrs.framework.CqrsFrameworkException;

/**
 * Signals that interceptor advice violated the framework's interceptor contract &mdash; either registering an interior
 * hook after the lifecycle {@linkplain RegistrationGuard#freeze() froze} (register-after-proceed), or calling
 * {@link Continuation#proceed()} more than once on an exactly-once observer continuation.
 *
 * <p>These are <strong>deterministic programming bugs</strong>, not runtime conditions: retrying cannot fix them. It is
 * therefore a {@link CqrsFrameworkException.NonTransientException} &mdash; the framework's canonical
 * <em>non-recoverable</em> signal &mdash; so the event-handling processor
 * {@linkplain com.opencqrs.framework.eventhandler.EventHandlingProcessor terminates} on it through the same
 * classification as any other non-transient error, rather than retrying it forever. A deliberate
 * {@link IllegalStateException} thrown by a handler or interceptor remains transient (retried), so the two are never
 * conflated.
 */
public final class InterceptorContractViolation extends CqrsFrameworkException.NonTransientException {

    public InterceptorContractViolation(String message) {
        super(message);
    }
}
