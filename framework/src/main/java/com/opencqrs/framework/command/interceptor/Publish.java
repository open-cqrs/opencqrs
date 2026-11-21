/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import com.opencqrs.esdb.client.Precondition;
import com.opencqrs.framework.persistence.CapturedEvent;
import java.util.List;

/**
 * {@link CommandInterceptor} transformer join point over the <strong>append request</strong> &mdash; the events and
 * preconditions the framework is about to write atomically. Fires <strong>once</strong> per command, after the handler,
 * only when there is at least one captured event to append.
 *
 * <p>Being a {@link com.opencqrs.framework.interceptor.StageTransformer transformer}, an advice may <em>rewrite</em>
 * the request before the append &mdash; e.g. enrich event meta-data or add preconditions &mdash; by returning a
 * modified {@code Publish}, or veto the whole command by throwing. The framework performs the atomic append using the
 * returned request.
 *
 * <p>{@code events} are those captured through the framework's
 * {@link com.opencqrs.framework.command.CommandEventPublisher} (the atomic command-append set). Events a handler writes
 * out-of-band via a raw {@link com.opencqrs.framework.persistence.ImmediateEventPublisher} are discouraged, are not
 * part of this request, and are neither atomic with the command nor visible to interceptors.
 *
 * @param events the captured events to append, each carrying its own {@link CapturedEvent#preconditions()}
 * @param additionalPreconditions the framework-assembled guards for the append, in addition to each event's own
 *     preconditions: the {@link com.opencqrs.framework.command.Command.SubjectCondition subject condition}, per-subject
 *     pristine guards for newly-created subjects, and per-subject
 *     {@link com.opencqrs.esdb.client.Precondition.SubjectIsOnEventId} optimistic-concurrency guards. Named to match
 *     the {@code additionalPreconditions} parameter of
 *     {@link com.opencqrs.framework.persistence.ImmediateEventPublisher}.
 */
public record Publish(List<CapturedEvent> events, List<Precondition> additionalPreconditions) {

    /**
     * @param events the replacement events
     * @return a copy of {@code this} with its {@code events} replaced
     */
    public Publish withEvents(List<CapturedEvent> events) {
        return new Publish(events, additionalPreconditions);
    }

    /**
     * @param additionalPreconditions the replacement additional preconditions
     * @return a copy of {@code this} with its {@code additionalPreconditions} replaced
     */
    public Publish withAdditionalPreconditions(List<Precondition> additionalPreconditions) {
        return new Publish(events, additionalPreconditions);
    }
}
