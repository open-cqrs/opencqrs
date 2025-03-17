/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.partitioning;

import com.opencqrs.esdb.client.Event;

/**
 * {@link EventSequenceResolver.ForRawEvent} implementation which uses {@link Event#subject()} as the event sequence
 * identifier.
 */
public class PerSubjectEventSequenceResolver implements EventSequenceResolver.ForRawEvent {

    @Override
    public String sequenceIdFor(Event rawEvent) {
        return rawEvent.subject();
    }
}
