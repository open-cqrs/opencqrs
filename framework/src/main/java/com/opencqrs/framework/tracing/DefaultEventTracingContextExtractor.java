/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;

/**
 * Default implementation of {@link EventTracingContextExtractor}
 */
public class DefaultEventTracingContextExtractor implements EventTracingContextExtractor {

    /**
     * Default implementation of {@link EventTracingContextExtractor#extractAndRestoreContextFromEvent(Event, Runnable)} that does not process any tracing data in the event trail
     *
     * @param event some {@link Event}
     * @param runnable closure containing logic
     */
    @Override
    public void extractAndRestoreContextFromEvent(Event event, Runnable runnable) {
        runnable.run();
    }
}
