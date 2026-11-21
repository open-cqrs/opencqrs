/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import com.opencqrs.framework.command.CommandHandlerDefinition;
import org.jspecify.annotations.Nullable;

/**
 * {@link CommandInterceptor} join point wrapping the single invocation of the
 * {@link com.opencqrs.framework.command.CommandHandler}. Fires <strong>once</strong> per command, after
 * {@link Sourcing}. This is the rebuilt-state / pre-handler point &mdash; the natural place for state-dependent checks
 * (deny by throwing).
 *
 * <p>Wrapped by a {@link com.opencqrs.framework.interceptor.StageTransformer}: an advice may substitute the command
 * result without proceeding (e.g. idempotency) or transform it after proceeding.
 *
 * <p>{@code latestSourcedEventId} is the head of the sourced event stream &mdash; the id of the most recent
 * {@link com.opencqrs.esdb.client.Event} that contributed to {@code instance} during {@link Sourcing}, including events
 * served from the {@link com.opencqrs.framework.command.cache.StateRebuildingCache} (which are <em>not</em> replayed
 * through the {@link SourcedEventInvocation sourcedEvent} hook). It is thus the effective <em>version</em> of the
 * rebuilt state, and the same token the framework asserts as a
 * {@link com.opencqrs.esdb.client.Precondition.SubjectIsOnEventId} precondition at append time &mdash; the natural
 * value for an optimistic-locking check to compare a client-supplied expectation against. Under
 * {@link com.opencqrs.framework.command.SourcingMode#RECURSIVE} it is the head of the whole sourced subject hierarchy;
 * {@code null} when nothing was sourced (a pristine subject or
 * {@link com.opencqrs.framework.command.SourcingMode#NONE}).
 *
 * @param definition the command handler definition being executed
 * @param instance the rebuilt instance state, may be {@code null} (populated even for {@code ForCommand} handlers)
 * @param latestSourcedEventId the head event id of the sourced stream, or {@code null} if nothing was sourced
 */
public record CommandHandlerInvocation(
        CommandHandlerDefinition<?, ?, ?> definition,
        @Nullable Object instance,
        @Nullable String latestSourcedEventId) {}
