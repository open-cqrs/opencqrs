/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.interceptor;

/**
 * The set of events an {@link EventInterceptor} wants its root {@link EventInterceptor#intercept intercept} fired for.
 * The levels are strictly nested: {@link #ACTIONABLE} &sub; {@link #PARTITIONED} &sub; {@link #ALL} &mdash; each admits
 * every event the previous one admits, plus more.
 *
 * <p>Two independent rules govern delivery &mdash; do not conflate them:
 *
 * <ul>
 *   <li><strong>Each interceptor is filtered by its own level</strong> (who gets called). An interceptor sees only the
 *       events its own {@code Delivery} admits, regardless of what other interceptors are registered. A broader sibling
 *       does <em>not</em> widen a narrower interceptor's visibility: on a {@link Relevance#NO} event, an
 *       {@link #ACTIONABLE} interceptor's root is <strong>not</strong> fired even when an {@link #ALL} interceptor in
 *       the same chain is fired for it.
 *   <li><strong>The union of all registered levels sets the evaluation depth</strong> (how deep the framework looks).
 *       The framework computes relevance (and materializes the upcast fan-out) only as deep as the broadest registered
 *       level requires &mdash; so a broader sibling makes the machinery run deeper without changing rule 1. With
 *       {@link #ACTIONABLE} registered throughout (or no interceptors at all) the union stays at {@link #ACTIONABLE}
 *       and the current fast path is preserved exactly.
 * </ul>
 *
 * @see Relevance
 */
public enum Delivery {

    /**
     * Only partition-relevant events ({@link Relevance#YES} / {@link Relevance#PARTIAL}) that also have at least one
     * matching handler &mdash; i.e. events that cause real work. This is the {@linkplain EventInterceptor#delivery()
     * default} and the narrowest level.
     */
    ACTIONABLE,

    /**
     * Everything {@link #ACTIONABLE} admits, plus partition-relevant events that have <em>no</em> matching handler
     * ("owned, but nothing to do").
     */
    PARTITIONED,

    /**
     * Everything {@link #PARTITIONED} admits, plus events that do not belong to this processor's partition
     * ({@link Relevance#NO} &mdash; a wrong-partition event, or one whose upcast produced no events). This is the
     * broadest level.
     */
    ALL,
}
