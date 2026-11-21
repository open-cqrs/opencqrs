/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.optimisticlocking;

import com.opencqrs.framework.command.Command;

/**
 * Marker mix-in for {@link Command}s that participate in {@linkplain OptimisticLockingCommandInterceptor optimistic
 * locking}. A command implementing {@code this} declares an {@link EventIdExpectation} over the head of its sourced
 * event stream; the interceptor rejects the command (before the handler runs) if that expectation is violated.
 *
 * <p>Being an interface, it composes onto any command as a mix-in, e.g. {@code record BorrowBookCommand(...) implements
 * BookCommand, EventIdExpectingCommand}.
 *
 * <p>Return {@link EventIdExpectation.None} to opt out for a given invocation. Asserting that the subject is
 * <em>new</em> is <strong>not</strong> this mechanism's job &mdash; use {@link Command.SubjectCondition#PRISTINE} for
 * that (it also adds a store-side append precondition, which a pre-handler check cannot).
 *
 * <p>This is only meaningful when the command is <em>handled with event sourcing</em>
 * ({@link com.opencqrs.framework.command.SourcingMode#LOCAL} or
 * {@link com.opencqrs.framework.command.SourcingMode#RECURSIVE}). Under
 * {@link com.opencqrs.framework.command.SourcingMode#NONE} no events are sourced, so there is no head to compare
 * against: {@link EventIdExpectation.AtMost} never rejects and {@link EventIdExpectation.Exactly} always rejects
 * &mdash; neither is meaningful.
 *
 * @see EventIdExpectation
 * @see OptimisticLockingCommandInterceptor
 * @see OptimisticLockingException
 */
public interface EventIdExpectingCommand extends Command {

    /**
     * The optimistic-locking expectation over the
     * {@linkplain com.opencqrs.framework.command.interceptor.CommandHandlerInvocation#latestSourcedEventId() sourced
     * head event id}.
     *
     * @return the expectation; {@link EventIdExpectation.None} to opt out
     */
    EventIdExpectation expectedEventId();
}
