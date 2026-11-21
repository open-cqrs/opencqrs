/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

/**
 * Opaque proof-of-proceed returned by a {@link StageObserver}. The <strong>only</strong> way to obtain an instance is
 * to call {@link Continuation#proceed()} &mdash; it cannot be fabricated by user code. Requiring an observer to return
 * a {@code Proceeded} therefore structurally enforces that it actually proceeded: an observer can neither forget to
 * proceed nor stall the chain.
 *
 * @see StageObserver
 * @see Continuation
 */
public final class Proceeded {

    /** The single framework-minted token, handed back up the chain by {@link Continuation#proceed()}. */
    static final Proceeded INSTANCE = new Proceeded();

    private Proceeded() {}
}
