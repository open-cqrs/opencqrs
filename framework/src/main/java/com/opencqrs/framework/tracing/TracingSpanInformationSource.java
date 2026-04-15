/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import java.util.Map;

/**
 * Mixin for Command/Event/State Rebuilding Handlers to provide additional information for tracing
 */
public interface TracingSpanInformationSource {

    /**
     *
     * @return simple name of the handler's enclosing class
     */
    String getHandlingClassSimpleName();

    /**
     *
     * @return full name of the handler's enclosing class
     */
    String getHandlingClassFullName();

    /**
     *
     * @return signature of the handling method
     */
    String getHandlingMethodSignature();

    /**
     *
     * @return map containing additional span information composed of the various getter-methods' results
     */
    default Map<String, String> getEventHandlingSpanInformation() {
        return Map.ofEntries(
                Map.entry("handling.class", getHandlingClassFullName()),
                Map.entry("handling.method", getHandlingMethodSignature()));
    }
}
