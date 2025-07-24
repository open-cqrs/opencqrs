package com.opencqrs.esdb.client.tracing;

import com.opencqrs.esdb.client.EventCandidate;

/**
 * Interface specifying methods for handling tracing data retrieved from the application's context
 */
public interface TracingContextProvider {

    /**
     * Retrieves the current tracing data and adds it to a given {@link EventCandidate} if not already present
     * @param candidate the {@link EventCandidate} to be enriched with tracing data
     * @return a new {@link EventCandidate} instance with tracing data set or the original instance if it already had tracing data
     */
    EventCandidate enrichWithTracingData(EventCandidate candidate);
}
