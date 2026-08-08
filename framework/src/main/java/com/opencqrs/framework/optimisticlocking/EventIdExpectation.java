/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.optimisticlocking;

/**
 * The optimistic-locking semantic an {@link EventIdExpectingCommand} declares over the head of its
 * {@linkplain com.opencqrs.framework.command.interceptor.CommandHandlerInvocation#latestSourcedEventId() sourced event
 * stream}, evaluated by {@link OptimisticLockingCommandInterceptor} before the command handler runs.
 *
 * <p>Event ids form a <strong>globally monotonic</strong> sequence (see {@link com.opencqrs.esdb.client.IdUtil}): any
 * event appended later &mdash; on any subject &mdash; receives a strictly higher id. The sourced head {@code S} is
 * therefore comparable by order against an expected id {@code X}, which is what lets the check tolerate a command whose
 * sourced scope is <em>narrower</em> than the read model the caller acted on (e.g. a {@code LOCAL} command against a
 * subject the caller last observed through a broader {@code RECURSIVE} read).
 *
 * @see EventIdExpectingCommand
 * @see OptimisticLockingCommandInterceptor
 */
public sealed interface EventIdExpectation
        permits EventIdExpectation.None, EventIdExpectation.AtMost, EventIdExpectation.Exactly {

    /** No expectation &mdash; the command is never rejected on concurrency grounds (opt out per invocation). */
    record None() implements EventIdExpectation {}

    /**
     * The sourced head must be <strong>no newer than</strong> {@code eventId} ({@code S <= X}) &mdash; the command is
     * rejected only if an event <em>newer</em> than the caller's read exists in the sourced scope. This is the general,
     * scope-robust optimistic lock ("unmodified since {@code eventId}"): it tolerates a sourced head that is older than
     * {@code eventId} (a narrower sourcing scope than the caller's read) and a pristine subject.
     *
     * @param eventId the newest {@linkplain com.opencqrs.esdb.client.Event#id() event id} the caller's read reflected
     */
    record AtMost(String eventId) implements EventIdExpectation {}

    /**
     * The sourced head must be <strong>exactly</strong> {@code eventId} ({@code S == X}). Strictest variant; rejects a
     * sourced head that is older <em>or</em> newer. Prefer {@link AtMost} unless the caller's read scope is known to
     * match the command's sourcing scope and zero tolerance is required.
     *
     * @param eventId the exact {@linkplain com.opencqrs.esdb.client.Event#id() event id} the caller's read reflected
     */
    record Exactly(String eventId) implements EventIdExpectation {}
}
