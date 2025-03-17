/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.persistence;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.Precondition;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Interface specifying operations for immediate, atomic event publication. */
public interface ImmediateEventPublisher {

    /**
     * Publishes the given event onto the given subject.
     *
     * @param subject the absolute subject path
     * @param event the event object
     * @return the published {@link Event}
     * @param <E> the generic event type
     */
    default <E> Event publish(String subject, E event) {
        return publish(subject, event, Map.of(), List.of());
    }

    /**
     * Publishes the given event including its meta-data onto the given subject with preconditions.
     *
     * @param subject the absolute subject path
     * @param event the event object
     * @param metaData the event meta-data
     * @param preconditions the preconditions that must not be violated
     * @return the published {@link Event}
     * @param <E> the generic event type
     */
    default <E> Event publish(String subject, E event, Map<String, ?> metaData, List<Precondition> preconditions) {
        return publish(eventPublisher -> eventPublisher.publish(subject, event, metaData, preconditions))
                .getFirst();
    }

    /**
     * Atomically publishes all events captured from the given {@link EventPublisher} consumer.
     *
     * @param handler callback capturing all events to be published atomically
     * @return the list of published {@link Event}s
     */
    default List<Event> publish(Consumer<EventPublisher> handler) {
        return publish(handler, List.of());
    }

    /**
     * Atomically publishes all events captured from the given {@link EventPublisher} consumer together with the
     * additional preconditions.
     *
     * @param handler callback capturing all events to be published atomically
     * @param additionalPreconditions additional preconditions that must not be violated
     * @return the list of published {@link Event}s
     */
    List<Event> publish(Consumer<EventPublisher> handler, List<Precondition> additionalPreconditions);

    /**
     * Atomically publishes the given events together with the additional preconditions.
     *
     * @param events a list of captured events to be published atomically
     * @param additionalPreconditions additional preconditions that must not be violated
     * @return the list of published {@link Event}s
     */
    List<Event> publish(List<CapturedEvent> events, List<Precondition> additionalPreconditions);
}
