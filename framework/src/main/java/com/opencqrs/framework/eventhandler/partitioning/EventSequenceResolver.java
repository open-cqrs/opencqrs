/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.partitioning;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.eventhandler.EventHandlingProcessor;
import java.util.Map;

/**
 * Sealed base interface for inherited {@link FunctionalInterface} variants encapsulating the logic to derive a
 * <strong>sequence identifier</strong> for an event.
 *
 * <p>An event's sequence identifier is a {@link String} determining, if two consecutive events must be handled in
 * order. Two events with the same sequence identifier will be handled with respect to their order within the event
 * stream, while events with different sequence identifiers may be handled in parallel with no ordering constraints.
 *
 * @param <E> the generic Java event type
 * @see EventHandlingProcessor#run()
 */
public sealed interface EventSequenceResolver<E> {

    /**
     * {@link FunctionalInterface} to be implemented, if an event's sequence identifier can be derived from a raw
     * {@link Event}, that is without any upcasting or Java object deserialization.
     */
    @FunctionalInterface
    non-sealed interface ForRawEvent extends EventSequenceResolver<Object> {

        /**
         * Determines the sequence identifier from a raw {@link Event}.
         *
         * @param rawEvent the raw event
         * @return the event's sequence identifier
         */
        String sequenceIdFor(Event rawEvent);
    }

    /**
     * {@link FunctionalInterface} to be implemented, if an event's sequence identifier can only be derived from a fully
     * upcasted and deserialized Java event object.
     *
     * @param <E> the generic Java event type
     */
    @FunctionalInterface
    non-sealed interface ForObjectAndMetaDataAndRawEvent<E> extends EventSequenceResolver<E> {

        /**
         * Determines the sequence identifier from an upcasted and deserialized {@link Event}.
         *
         * @param event the Java event object
         * @param metaData the event meta-data
         * @return the event's sequence identifier
         */
        String sequenceIdFor(E event, Map<String, ?> metaData);
    }
}
