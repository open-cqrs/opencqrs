/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.partitioning;

import com.opencqrs.esdb.client.Event;

/**
 * {@link EventSequenceResolver.ForRawEvent} implementation to be used if <strong>no</strong> event sequencing is
 * required, i.e. all events may be processed in any order.
 */
public class NoEventSequenceResolver implements EventSequenceResolver.ForRawEvent {

    @Override
    public String sequenceIdFor(Event rawEvent) {
        return rawEvent.id();
    }
}
