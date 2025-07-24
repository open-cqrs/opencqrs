/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import com.opencqrs.esdb.client.EventCandidate;

/** A trivial implementation of {@link TracingContextualizer} which does not retrieve any tracing data */
public class NoOpTracingContextualizer implements TracingContextualizer {

    /**
     * Trivial default implementation of the method which simply returns the given {@link EventCandidate}
     *
     * @param candidate the given {@link EventCandidate}
     * @return the given {@link EventCandidate}
     */
    @Override
    public EventCandidate enrichWithTracingData(EventCandidate candidate) {
        return candidate;
    }
}
