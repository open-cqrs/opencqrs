/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;

/** Default implementation of {@link EventTracingContextExecutor} */
public class NoEventTracingContextExecutor implements EventTracingContextExecutor {

    /**
     * Default implementation of {@link EventTracingContextExecutor#executeInRestoreContextFromEvent(Event, Runnable)}
     * that does not process any tracing data in the event trail
     *
     * @param event some {@link Event}
     * @param runnable closure containing logic
     */
    @Override
    public void executeInRestoreContextFromEvent(Event event, Runnable runnable) {
        runnable.run();
    }
}
