/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.upcaster.EventUpcasters;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Sealed base interface for inherited {@link FunctionalInterface} variants encapsulating the event-sourcing logic
 * needed to reconstruct instance state from an event stream.
 *
 * <p>Implementations of {@code this} will be used during {@link Command} {@linkplain CommandRouter#send(Command)
 * execution} as follows:
 *
 * <ol>
 *   <li>The instance state will be reconstructed based on the {@link Event}s sourced for the
 *       {@link Command#getSubject()} before the {@link CommandHandler} execution, after they have been successfully
 *       {@linkplain EventUpcasters#upcast(Event) upcasted} and {@linkplain EventDataMarshaller#deserialize(Map, Class)
 *       converted} to their appropriate Java object type. This phase allows access to the raw {@link Event} read from
 *       the event store.
 *   <li>Any event published during command execution via {@link CommandEventPublisher} will be applied to update the
 *       instance state before the events will {@linkplain com.opencqrs.esdb.client.EsdbClient#write(List, List) be
 *       written} to the event store. Hence, this phase <strong>does not</strong> have access to the raw {@link Event}.
 * </ol>
 *
 * @param <I> the instance type
 * @param <E> the event type to be sourced
 */
public sealed interface StateRebuildingHandler<I, E> {

    /**
     * {@link FunctionalInterface} to be implemented, if only the Java event is needed to reconstruct the instance
     * state.
     *
     * @param <I> the instance type
     * @param <E> the event type to be sourced
     */
    @FunctionalInterface
    non-sealed interface FromObject<I, E> extends StateRebuildingHandler<I, E> {

        /**
         * Applies the given event to the given instance (state).
         *
         * @param instance the instance to apply the event to, may be {@code null}
         * @param event the event to apply
         * @return an instance with the event applied, ideally an immutable copy of the original instance
         */
        I on(I instance, E event);
    }

    /**
     * {@link FunctionalInterface} to be implemented, if the Java event and the raw {@link Event} is needed to
     * reconstruct the instance state.
     *
     * @param <I> the instance type
     * @param <E> the event type to be sourced
     */
    @FunctionalInterface
    non-sealed interface FromObjectAndRawEvent<I, E> extends StateRebuildingHandler<I, E> {

        /**
         * Applies the given event and optional raw event to the given instance (state).
         *
         * @param instance the instance to apply the event to, may be {@code null}
         * @param event the event to apply
         * @param rawEvent the raw event, {@code null} if the {@code event} is being {@linkplain CommandEventPublisher
         *     published}
         * @return an instance with the event and raw event applied, ideally an immutable copy of the original instance
         */
        I on(I instance, E event, @Nullable Event rawEvent);
    }

    /**
     * {@link FunctionalInterface} to be implemented, if the Java event and its meta-data is needed to reconstruct the
     * instance state.
     *
     * @param <I> the instance type
     * @param <E> the event type to be sourced
     */
    @FunctionalInterface
    non-sealed interface FromObjectAndMetaData<I, E> extends StateRebuildingHandler<I, E> {

        /**
         * Applies the given event and its meta-data to the given instance (state).
         *
         * @param instance the instance to apply the event to, may be {@code null}
         * @param event the event to apply
         * @param metaData the event meta-data, may be empty
         * @return an instance with the event and meta-data applied, ideally an immutable copy of the original instance
         */
        I on(I instance, E event, Map<String, ?> metaData);
    }

    /**
     * {@link FunctionalInterface} to be implemented, if the Java event, its meta-data, and subject is needed to
     * reconstruct the instance state.
     *
     * @param <I> the instance type
     * @param <E> the event type to be sourced
     */
    @FunctionalInterface
    non-sealed interface FromObjectAndMetaDataAndSubject<I, E> extends StateRebuildingHandler<I, E> {

        /**
         * Applies the given event, its meta-data, and subject to the given instance (state).
         *
         * @param instance the instance to apply the event to, may be {@code null}
         * @param event the event to apply
         * @param metaData the event meta-data, may be empty
         * @param subject the subject the event was published to
         * @return an instance with the event, meta-data, and subject applied, ideally an immutable copy of the original
         *     instance
         */
        I on(I instance, E event, Map<String, ?> metaData, String subject);
    }

    /**
     * {@link FunctionalInterface} to be implemented, if the Java event, its meta-data, subject, and optional raw event
     * is needed to reconstruct the instance state.
     *
     * @param <I> the instance type
     * @param <E> the event type to be sourced
     */
    @FunctionalInterface
    non-sealed interface FromObjectAndMetaDataAndSubjectAndRawEvent<I, E> extends StateRebuildingHandler<I, E> {

        /**
         * Applies the given event, its meta-data, and subject to the given instance (state).
         *
         * @param instance the instance to apply the event to, may be {@code null}
         * @param event the event to apply
         * @param metaData the event meta-data, may be empty
         * @param subject the subject the event was published to
         * @param rawEvent the raw event, {@code null} if the {@code event} is being {@linkplain CommandEventPublisher
         *     published}
         * @return an instance with the event, meta-data, subject, and raw event applied, ideally an immutable copy of
         *     the original instance
         */
        I on(I instance, E event, Map<String, ?> metaData, String subject, @Nullable Event rawEvent);
    }
}
