/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import java.util.Map;

public interface TracingSpanInformationSource {

    String getHandlingClassSimpleName();

    String getHandlingClassFullName();

    String getHandlingMethodSignature();

    default Map<String, String> getEventHandlingSpanInformation() {
        return Map.ofEntries(
                Map.entry("handling.class", getHandlingClassFullName()),
                Map.entry("handling.method", getHandlingMethodSignature()));
    }
}
