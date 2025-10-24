/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.ArrayList;
import java.util.List;

public class EventTracingContextGetter implements TextMapGetter<Event> {
    @Override
    public Iterable<String> keys(Event event) {
        List<String> keyList = new ArrayList<>();
        if (event.traceParent() != null) {
            keyList.add("traceparent");

            if (event.traceState() != null) {
                keyList.add("tracestate");
            }
        }
        return keyList;
    }

    @Override
    public String get(Event event, String s) {
        return switch (s) {
            case "traceparent" -> event.traceParent();
            case "tracestate" -> event.traceState();
            default -> "";
        };
    }
}
