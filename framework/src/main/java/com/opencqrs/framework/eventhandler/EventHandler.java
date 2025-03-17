/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.types.EventTypeResolver;
import com.opencqrs.framework.upcaster.EventUpcaster;
import com.opencqrs.framework.upcaster.EventUpcasters;
import java.util.Map;

/**
 * Sealed base interface for inherited {@link FunctionalInterface} variants encapsulating encapsulating {@link Event}
 * handling logic.
 *
 * <p>Implementations of {@code this} will be called after the raw {@link Event} retrieved from the event stream has
 * been successfully {@linkplain EventUpcasters#upcast(Event) upcasted} and
 * {@linkplain EventDataMarshaller#deserialize(Map, Class) converted} to their appropriate Java object type.<strong>This
 * implies that implemented {@link FunctionalInterface} methods may be called multiple times or not at all for the same
 * raw event, depending on the configured {@link EventUpcaster}s.</strong>
 *
 * @param <E> the generic Java type of the {@link Event} being handled
 * @see EventUpcasters#upcast(Event)
 * @see EventTypeResolver#getJavaClass(String)
 * @see EventDataMarshaller#deserialize(Map, Class)
 */
public sealed interface EventHandler<E>
        permits EventHandler.ForObject,
                EventHandler.ForObjectAndMetaData,
                EventHandler.ForObjectAndMetaDataAndRawEvent {

    /**
     * {@link FunctionalInterface} to be implemented, if only the Java event is needed for processing.
     *
     * @param <E> the event type
     */
    @FunctionalInterface
    non-sealed interface ForObject<E> extends EventHandler<E> {

        /**
         * Handles the given event.
         *
         * @param event the Java event object
         */
        void handle(E event);
    }

    /**
     * {@link FunctionalInterface} to be implemented, if the Java event and its meta-data is needed for processing.
     *
     * @param <E> the event type
     */
    @FunctionalInterface
    non-sealed interface ForObjectAndMetaData<E> extends EventHandler<E> {

        /**
         * Handles the given event and its meta-data.
         *
         * @param event the Java event object
         * @param metaData the event meta-data, may be empty
         */
        void handle(E event, Map<String, ?> metaData);
    }

    /**
     * {@link FunctionalInterface} to be implemented, if the Java event, its meta-data, and the raw event is needed for
     * processing.
     *
     * @param <E> the event type
     */
    @FunctionalInterface
    non-sealed interface ForObjectAndMetaDataAndRawEvent<E> extends EventHandler<E> {

        /**
         * Handles the given event, its meta-data, and the raw event.
         *
         * @param event the Java event object
         * @param metaData the event meta-data, may be empty
         * @param rawEvent the raw {@link Event}
         */
        void handle(E event, Map<String, ?> metaData, Event rawEvent);
    }
}
