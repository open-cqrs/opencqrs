/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import com.opencqrs.esdb.client.EventCandidate;

/** {@link TracingEventEnricher} implementation which does not enrich any tracing information. */
public class NoTracingEventEnricher implements TracingEventEnricher {

    /**
     * Identity method returning the original {@link EventCandidate} without altering or enriching any tracing headers.
     *
     * @param candidate the given {@link EventCandidate}
     * @return the given {@link EventCandidate}
     */
    @Override
    public EventCandidate enrichWithTracingData(EventCandidate candidate) {
        return candidate;
    }
}
