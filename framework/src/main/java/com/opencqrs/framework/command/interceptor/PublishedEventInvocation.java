/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * {@link CommandInterceptor} join point wrapping a <strong>single</strong> emitted-event state-apply &mdash; one
 * state-rebuilding handler being invoked for one event the command handler just published. Fires once per (emitted
 * event × matching state-rebuilding handler), nested inside {@link CommandHandlerInvocation the handler}.
 *
 * <p>Unlike {@link SourcedEventInvocation} there is <strong>no</strong> {@code rawEvent} &mdash; the event has not been
 * persisted yet. The {@code metaData} and {@code subject} are the event's.
 *
 * @param definition the state-rebuilding handler definition being applied
 * @param inputInstance the instance state <em>before</em> this apply, may be {@code null}
 * @param event the emitted event being applied
 * @param metaData the event's meta-data
 * @param subject the event's subject
 */
public record PublishedEventInvocation(
        StateRebuildingHandlerDefinition<?, ?> definition,
        @Nullable Object inputInstance,
        Object event,
        Map<String, ?> metaData,
        String subject) {}
