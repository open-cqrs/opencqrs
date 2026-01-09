/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Precondition;
import com.opencqrs.framework.persistence.EventPublisher;
import java.util.List;
import java.util.Map;

/**
 * Extension to {@link EventPublisher} providing additional operations for publishing events relative to the
 * {@link Command#getSubject()} being {@linkplain CommandHandler handled}.
 *
 * <p>Implementations may defer event publication, typically to the end of a successful command execution, in order to
 * publish all captured events atomically.
 *
 * @param <I> the instance type as defined by the {@link CommandHandler}
 */
public interface CommandEventPublisher<I> extends EventPublisher {

    /**
     * Publishes an event to the subject specified by {@link Command#getSubject()} and applies it to any
     * {@linkplain StateRebuildingHandlerDefinition#eventClass() assignable} {@link StateRebuildingHandler}s
     * participating in the command execution. No meta-data, i.e. an empty map, is published with the event.
     *
     * @param event the event to be published
     * @return an updated or new instance with all events applied
     * @param <E> the event type
     */
    default <E> I publish(E event) {
        return publish(event, Map.of());
    }

    /**
     * Publishes an event and its meta-data to the subject specified by {@link Command#getSubject()} and applies it to
     * any {@linkplain StateRebuildingHandlerDefinition#eventClass() assignable} {@link StateRebuildingHandler}s
     * participating in the command execution.
     *
     * @param event the event to be published
     * @param metaData the event meta-data to be published
     * @return an updated or new instance with all events applied
     * @param <E> the event type
     */
    default <E> I publish(E event, Map<String, ?> metaData) {
        return publish(event, metaData, List.of());
    }

    /**
     * Publishes an event and its meta-data to the subject specified by {@link Command#getSubject()} and applies it to
     * any {@linkplain StateRebuildingHandlerDefinition#eventClass() assignable} {@link StateRebuildingHandler}s
     * participating in the command execution. Additionally, the given preconditions will be applied after command
     * execution succeeds, in addition to those applied by the {@link CommandRouter} implicitly.
     *
     * @param event the event to be published
     * @param metaData the event meta-data to be published
     * @param preconditions the preconditions that must not be violated
     * @return an updated or new instance with all events applied
     * @param <E> the event type
     */
    <E> I publish(E event, Map<String, ?> metaData, List<Precondition> preconditions);

    /**
     * Publishes an event to the subject specified by {@link Command#getSubject()} appended with the specified suffix
     * and applies it to any {@linkplain StateRebuildingHandlerDefinition#eventClass() assignable}
     * {@link StateRebuildingHandler}s participating in the command execution. No meta-data, i.e. an empty map, is
     * published with the event.
     *
     * @param subjectSuffix the suffix to be appended to the {@link Command#getSubject()} currently executed, must not
     *     start with {@code /}
     * @param event the event to be published
     * @return an updated or new instance with all events applied
     * @param <E> the event type
     */
    default <E> I publishRelative(String subjectSuffix, E event) {
        return publishRelative(subjectSuffix, event, Map.of());
    }

    /**
     * Publishes an event and its meta-data to the subject specified by {@link Command#getSubject()} appended with the
     * specified suffix and applies it to any {@linkplain StateRebuildingHandlerDefinition#eventClass() assignable}
     * {@link StateRebuildingHandler}s participating in the command execution.
     *
     * @param subjectSuffix the suffix to be appended to the {@link Command#getSubject()} currently executed, must not
     *     start with {@code /}
     * @param event the event to be published
     * @param metaData the event meta-data to be published
     * @return an updated or new instance with all events applied
     * @param <E> the event type
     */
    default <E> I publishRelative(String subjectSuffix, E event, Map<String, ?> metaData) {
        return publishRelative(subjectSuffix, event, metaData, List.of());
    }

    /**
     * Publishes an event and its meta-data to the subject specified by {@link Command#getSubject()} appended with the
     * specified suffix and applies it to any {@linkplain StateRebuildingHandlerDefinition#eventClass() assignable}
     * {@link StateRebuildingHandler}s participating in the command execution. Additionally, the given preconditions
     * will be applied after command execution succeeds, in addition to those applied by the {@link CommandRouter}
     * implicitly.
     *
     * @param subjectSuffix the suffix to be appended to the {@link Command#getSubject()} currently executed, must not
     *     start with {@code /}
     * @param event the event to be published
     * @param metaData the event meta-data to be published
     * @param preconditions the preconditions that must not be violated
     * @return an updated or new instance with all events applied
     * @param <E> the event type
     */
    <E> I publishRelative(String subjectSuffix, E event, Map<String, ?> metaData, List<Precondition> preconditions);
}
