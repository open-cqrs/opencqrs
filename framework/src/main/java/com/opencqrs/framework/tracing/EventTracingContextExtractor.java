/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.tracing;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.EventCandidate;

/**
 * Interface for extracting W3C Trace Context-conforming tracing data from {@link Event} records
 *
 * @see <a href="https://www.w3.org/TR/trace-context/">official W3C "Trace Context" standard</a>
 */
public interface EventTracingContextExtractor {

    /**
     * Method for extracting tracing data from a given {@link Event} and resurrecting the trace for further processing
     *
     * @param event the {@link Event} from which to extract the tracing data
     * @param runnable a closure containing logic to be executed within the resurrected trace
     */
    void extractAndRestoreContextFromEvent(Event event, Runnable runnable);
}
