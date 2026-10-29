/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

/**
 * Fail-fast guard backing the <em>one-shot</em> lifecycle-registration contract: interior hooks must be registered
 * <strong>before</strong> the terminal {@code proceed()} reaches the framework core. Once interior execution begins the
 * guard is {@linkplain #freeze() frozen}, and any later registration &mdash; the classic
 * <em>register-after-proceed</em> mistake, or registering from inside another hook's advice &mdash; is rejected with an
 * {@link InterceptorContractViolation} rather than silently never firing.
 *
 * <p>This is the runtime-guarded inverse of the structural no-stall guarantee: it cannot be made type-safe without
 * breaking the per-invocation closure-state pattern, so it is a fail-fast check instead.
 */
public final class RegistrationGuard {

    private boolean frozen = false;

    /**
     * Verifies that registration is still open.
     *
     * @throws InterceptorContractViolation if {@link #freeze()} has already been called
     */
    public void ensureOpen() {
        if (frozen) {
            throw new InterceptorContractViolation(
                    "interceptor lifecycle is frozen; interior hooks must be registered before proceed()");
        }
    }

    /** Freezes the lifecycle; all subsequent {@link #ensureOpen()} calls fail. Idempotent. */
    public void freeze() {
        this.frozen = true;
    }

    /** @return {@code true} once {@link #freeze()} has been called */
    public boolean isFrozen() {
        return frozen;
    }
}
