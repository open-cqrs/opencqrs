/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.optimisticlocking;

import com.opencqrs.esdb.client.IdUtil;
import com.opencqrs.framework.command.interceptor.CommandInterceptor;
import com.opencqrs.framework.command.interceptor.CommandInvocation;
import com.opencqrs.framework.command.interceptor.CommandLifecycle;
import com.opencqrs.framework.interceptor.ValueContinuation;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Framework-provided {@link CommandInterceptor} implementing <em>optimistic locking</em> for
 * {@link EventIdExpectingCommand}s. Before the command handler runs, it evaluates the command's
 * {@linkplain EventIdExpectingCommand#expectedEventId() EventIdExpectation} against the
 * {@linkplain com.opencqrs.framework.command.interceptor.CommandHandlerInvocation#latestSourcedEventId() head of the
 * sourced event stream} and throws an {@link OptimisticLockingException} if it is violated &mdash; rejecting a command
 * whose caller acted on a stale read.
 *
 * <p>This closes the <em>client-read &rarr; command</em> window the store's write-time
 * {@link com.opencqrs.esdb.client.Precondition.SubjectIsOnEventId} preconditions cannot: the command sources the
 * <em>current</em> state, so the store would happily append against that current head, but the caller's decision may
 * have been based on an older version.
 *
 * <p>Because event ids are {@linkplain com.opencqrs.esdb.client.IdUtil globally monotonic}, the default
 * {@link EventIdExpectation.AtMost} variant compares by order (reject only if the head is strictly newer than the
 * caller's read), which stays correct even when the command's sourcing scope is narrower than the read the caller acted
 * on &mdash; e.g. a {@link com.opencqrs.framework.command.SourcingMode#LOCAL} command against a subject last observed
 * through a broader {@link com.opencqrs.framework.command.SourcingMode#RECURSIVE} read.
 * {@link EventIdExpectation.Exactly} is the strict variant, {@link EventIdExpectation.None} opts out.
 *
 * <p>Caveats: it requires sourcing &mdash; under {@link com.opencqrs.framework.command.SourcingMode#NONE} no head is
 * exposed, so {@code AtMost} never rejects and {@code Exactly} always rejects; under
 * {@link com.opencqrs.framework.command.SourcingMode#RECURSIVE} the head is that of the whole sourced subject
 * hierarchy.
 *
 * @see EventIdExpectingCommand
 * @see EventIdExpectation
 * @see OptimisticLockingException
 */
public class OptimisticLockingCommandInterceptor implements CommandInterceptor<EventIdExpectingCommand> {

    @Override
    public Class<EventIdExpectingCommand> commandClass() {
        return EventIdExpectingCommand.class;
    }

    @Override
    public <R> @Nullable R intercept(
            CommandInvocation<EventIdExpectingCommand> invocation,
            CommandLifecycle<R> lifecycle,
            ValueContinuation<R> continuation)
            throws Exception {
        lifecycle.handler((joinPoint, handlerContinuation) -> {
            String actual = joinPoint.latestSourcedEventId();
            String subject = invocation.command().getSubject();
            switch (invocation.command().expectedEventId()) {
                case EventIdExpectation.None ignored -> {}
                case EventIdExpectation.Exactly exactly -> {
                    if (!Objects.equals(actual, exactly.eventId())) {
                        throw new OptimisticLockingException(subject, exactly, actual);
                    }
                }
                case EventIdExpectation.AtMost atMost -> {
                    // globally monotonic ids: a pristine (null) head is never newer; otherwise reject only if strictly
                    // newer than expected, tolerating an older head from a narrower sourcing scope
                    if (actual != null && IdUtil.fromEventId(actual) > IdUtil.fromEventId(atMost.eventId())) {
                        throw new OptimisticLockingException(subject, atMost, actual);
                    }
                }
            }
            return handlerContinuation.proceed();
        });
        return continuation.proceed();
    }
}
