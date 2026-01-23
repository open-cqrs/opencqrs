/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import java.util.Map;

public interface EventHandlingSpanInformationSource {

    String getEventHandlingClass();

    String getEventHandlingMethod();

    default Map<String, String> getEventHandlingSpanInformation() {
        return Map.ofEntries(
                Map.entry("event.handling.class", getEventHandlingClass()),
                Map.entry("event.handling.method", getEventHandlingMethod()));
    }
}
