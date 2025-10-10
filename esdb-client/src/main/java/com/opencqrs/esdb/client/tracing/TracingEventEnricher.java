/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import com.opencqrs.esdb.client.EventCandidate;

/**
 * Interface for adding W3C Trace Context-conforming tracing data to {@link EventCandidate} records
 *
 * @see <a href="https://www.w3.org/TR/trace-context/">official W3C "Trace Context" standard</a>
 */
public interface TracingEventEnricher {

    /**
     * Method for enriching a given {@link EventCandidate} with tracing data
     *
     * @param candidate the {@link EventCandidate} to be enriched with tracing data
     * @return a new {@link EventCandidate} instance with tracing data set or the original instance if it already had
     *     tracing data
     */
    EventCandidate enrichWithTracingData(EventCandidate candidate);
}
