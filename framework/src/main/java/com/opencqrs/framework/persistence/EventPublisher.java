/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.persistence;

import com.opencqrs.esdb.client.Client;
import com.opencqrs.esdb.client.Precondition;
import java.util.List;
import java.util.Map;

/**
 * Interface specifying operations for publishing Java event objects. Implementations typically either capture these
 * events in-memory for further (or deferred) processing or immediately convert and pass them to
 * {@link Client#write(List, List)}.
 */
public interface EventPublisher {

    /**
     * Publishes the given event onto the given subject. No meta-data, i.e. an empty map, is published with the event.
     *
     * @param subject the absolute subject path
     * @param event the event object
     * @param <E> the generic event type
     */
    default <E> void publish(String subject, E event) {
        publish(subject, event, Map.of());
    }

    /**
     * Publishes the given event and its meta-data onto the given subject.
     *
     * @param subject the absolute subject path
     * @param event the event object
     * @param metaData the event meta-data
     * @param <E> the generic event type
     */
    default <E> void publish(String subject, E event, Map<String, ?> metaData) {
        publish(subject, event, metaData, List.of());
    }

    /**
     * Publishes the given event and its meta-data onto the given subject with preconditions.
     *
     * @param subject the absolute subject path
     * @param event the event object
     * @param metaData the event meta-data
     * @param preconditions the preconditions that must not be violated
     * @param <E> the generic event type
     */
    <E> void publish(String subject, E event, Map<String, ?> metaData, List<Precondition> preconditions);
}
