/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import com.opencqrs.esdb.client.EventCandidate;

/**
 * Interface for {@link EventCandidate} W3C Trace Context enrichment.
 *
 * @see <a href="https://www.w3.org/TR/trace-context/">official W3C "Trace Context" standard</a>
 */
@FunctionalInterface
public interface TracingEventEnricher {

    /**
     * Method for enriching a given {@link EventCandidate} with tracing data, that is populating
     * {@link EventCandidate#traceParent()} and {@link EventCandidate#traceState()}.
     *
     * @param candidate the {@link EventCandidate} to be enriched with tracing data
     * @return a new {@link EventCandidate} instance with tracing data set or the original instance if already enriched
     *     or not tracing information available.
     */
    EventCandidate enrichWithTracingData(EventCandidate candidate);
}
