/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.optimisticlocking;

import com.opencqrs.framework.CqrsFrameworkException;
import org.jspecify.annotations.Nullable;

/**
 * {@link ConcurrencyException} thrown by {@link OptimisticLockingCommandInterceptor} when the head of the sourced event
 * stream violates an {@link EventIdExpectingCommand}'s {@linkplain EventIdExpectingCommand#expectedEventId()
 * EventIdExpectation} &mdash; i.e. the caller acted on a stale read. Detected <em>before</em> the command handler runs;
 * no events are published.
 *
 * <p>The violated {@link #getExpectation() expectation} is captured in full (never {@link EventIdExpectation.None},
 * which cannot be violated), so handlers can tell <em>which</em> semantic failed &mdash; e.g. distinguish a strict
 * {@link EventIdExpectation.Exactly} mismatch from an {@link EventIdExpectation.AtMost} that saw a strictly newer head.
 *
 * @see OptimisticLockingCommandInterceptor
 */
public class OptimisticLockingException extends CqrsFrameworkException.TransientException.ConcurrencyException {

    private final String subject;

    private final EventIdExpectation expectation;

    private final @Nullable String actualEventId;

    public OptimisticLockingException(String subject, EventIdExpectation expectation, @Nullable String actualEventId) {
        super(message(subject, expectation, actualEventId));
        this.subject = subject;
        this.expectation = expectation;
        this.actualEventId = actualEventId;
    }

    private static String message(String subject, EventIdExpectation expectation, @Nullable String actualEventId) {
        String expected =
                switch (expectation) {
                    case EventIdExpectation.None ignored -> "no expectation";
                    case EventIdExpectation.Exactly exactly -> "exactly '" + exactly.eventId() + "'";
                    case EventIdExpectation.AtMost atMost -> "at most '" + atMost.eventId() + "'";
                };
        return "optimistic locking conflict on subject '" + subject + "': expected " + expected
                + " but the sourced head is '" + actualEventId + "'";
    }

    /** @return the {@linkplain com.opencqrs.framework.command.Command#getSubject() subject} of the rejected command */
    public String getSubject() {
        return subject;
    }

    /** @return the violated {@link EventIdExpectation} (never {@link EventIdExpectation.None}) */
    public EventIdExpectation getExpectation() {
        return expectation;
    }

    /** @return the actual sourced head event id, or {@code null} if the subject was pristine */
    public @Nullable String getActualEventId() {
        return actualEventId;
    }
}
