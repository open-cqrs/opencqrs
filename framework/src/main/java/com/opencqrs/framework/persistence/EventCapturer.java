/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.persistence;

import com.opencqrs.esdb.client.Precondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-memory implementation of {@link EventPublisher} using {@link CapturedEvent}s.
 *
 * @see #getEvents()
 */
public class EventCapturer implements EventPublisher {

    private final List<CapturedEvent> events = new ArrayList<>();

    /**
     * Retrieves the list of events captured by this {@link EventPublisher}.
     *
     * @return the list of captured events
     */
    public List<CapturedEvent> getEvents() {
        return events;
    }

    @Override
    public <E> void publish(String subject, E event, Map<String, ?> metaData, List<Precondition> preconditions) {
        events.add(new CapturedEvent(subject, event, metaData, preconditions));
    }
}
