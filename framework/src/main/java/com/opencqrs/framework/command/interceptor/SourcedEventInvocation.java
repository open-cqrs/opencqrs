/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * {@link CommandInterceptor} join point wrapping a <strong>single</strong> replayed-event state-apply &mdash; one
 * {@link com.opencqrs.framework.command.StateRebuildingHandler state-rebuilding handler} being invoked for one sourced
 * event. Fires once per (sourced event × matching state-rebuilding handler), nested inside {@link Sourcing}.
 *
 * <p>The {@code metaData} and {@code subject} are the <strong>event's</strong>, not the command's &mdash; under
 * {@link com.opencqrs.framework.command.SourcingMode#RECURSIVE} the subject can differ from the command subject.
 *
 * @param definition the state-rebuilding handler definition being applied
 * @param inputInstance the instance state <em>before</em> this apply, may be {@code null}
 * @param event the deserialized event being applied
 * @param metaData the event's meta-data
 * @param subject the event's subject
 * @param rawEvent the raw sourced {@link Event} (always present on the source side)
 */
public record SourcedEventInvocation(
        StateRebuildingHandlerDefinition<?, ?> definition,
        @Nullable Object inputInstance,
        Object event,
        Map<String, ?> metaData,
        String subject,
        Event rawEvent) {}
