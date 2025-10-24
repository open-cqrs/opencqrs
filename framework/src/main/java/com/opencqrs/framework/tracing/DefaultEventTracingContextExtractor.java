/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;

public class DefaultEventTracingContextExtractor implements EventTracingContextExtractor {
    @Override
    public void extractAndRestoreContextFromEvent(Event event, Runnable runnable) {
        runnable.run();
    }
}
