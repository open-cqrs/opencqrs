/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;

public interface EventTracingContextExtractor {

    void extractAndRestoreContextFromEvent(Event event, Runnable runnable);
}
