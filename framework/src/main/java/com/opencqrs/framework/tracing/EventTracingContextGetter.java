/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom implementation of the {@link io.opentelemetry.context.propagation.TextMapGetter} interface to handle the
 * propagation of the 'traceparent' and 'tracestate' headers persisted in an event.
 */
public class EventTracingContextGetter implements TextMapGetter<Event> {

    private final String TRACE_PARENT = "traceparent";
    private final String TRACE_STATE = "tracestate";

    @Override
    public Iterable<String> keys(Event event) {
        List<String> keyList = new ArrayList<>();
        if (event.traceParent() != null) {
            keyList.add(TRACE_PARENT);

            if (event.traceState() != null) {
                keyList.add(TRACE_STATE);
            }
        }
        return keyList;
    }

    @Override
    public String get(Event event, String s) {
        return switch (s) {
            case TRACE_PARENT -> event.traceParent();
            case TRACE_STATE -> event.traceState();
            default -> "";
        };
    }
}
