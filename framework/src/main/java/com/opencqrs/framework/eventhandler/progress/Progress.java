/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.progress;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.eventhandler.EventHandlingProcessor;

/**
 * Sealed interface for subclasses representing the <strong>progress</strong> of {@linkplain EventHandlingProcessor
 * event handling} the event stream for a processing group.
 */
public sealed interface Progress {

    /** States no progress for the event processing group is known. */
    record None() implements Progress {}

    /**
     * Represents the last {@link Event#id()} that has been <strong>successfully</strong> processed by the event
     * processing group.
     *
     * @param id the event id
     */
    record Success(String id) implements Progress {}
}
