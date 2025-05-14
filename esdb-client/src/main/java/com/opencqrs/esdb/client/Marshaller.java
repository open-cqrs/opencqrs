/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import com.opencqrs.esdb.client.jackson.JacksonMarshaller;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Interface specifying operations for HTTP request/response marshalling.
 *
 * @see JacksonMarshaller
 */
public interface Marshaller {

    /**
     * Used by {@link EsdbClient#health()} to transform the HTTP response body to a {@link Health}.
     *
     * @param response the JSON HTTP response body as string
     * @return the {@link Health}
     */
    Health fromHealthResponse(String response);

    /**
     * Used by {@link EsdbClient#write(List, List)} to transform the given {@link EventCandidate}s and
     * {@link Precondition}s to a valid HTTP request body to be sent to the event store.
     *
     * @param eventCandidates the list of event candidates to include within the request body
     * @param preconditions the preconditions to include within the request body
     * @return the JSON HTTP request body as string
     */
    String toWriteEventsRequest(List<EventCandidate> eventCandidates, List<Precondition> preconditions);

    /**
     * Used by {@link EsdbClient#write(List, List)} to transform the HTTP response body to a list of {@link Event}s.
     *
     * @param response the JSON HTTP response body as string
     * @return the list of unmarshalled {@link Event}s
     */
    List<Event> fromWriteEventsResponse(String response);

    /**
     * Used by {@link EsdbClient} to transform the given parameters into a valid HTTP request body toe be sent to the
     * event store for read or observe operations.
     *
     * @param subject the subject to include within the request body
     * @param options the options to include within the request body
     * @return the JSON HTTP request body as string
     * @see EsdbClient#read(String, Set, Consumer)
     * @see EsdbClient#read(String, Set)
     * @see EsdbClient#observe(String, Set, Consumer)
     */
    String toReadOrObserveEventsRequest(String subject, Set<Option> options);

    /**
     * Used by {@link EsdbClient} to transform an ND-JSON line from the HTTP response stream to a
     * {@link ResponseElement}.
     *
     * @param line the ND-JSON element as string
     * @return an unmarshalled {@link ResponseElement}
     */
    ResponseElement fromReadOrObserveResponseLine(String line);

    /**
     * Sealed interface representing a deserialized ND-JSON response line transformed via
     * {@link #fromReadOrObserveResponseLine(String)}.
     *
     * @see Event
     */
    sealed interface ResponseElement permits Marshaller.ResponseElement.Heartbeat, Event {

        /**
         * Represents a heart beat returned from the event store to ensure the underlying HTTP connection is kept alive.
         */
        record Heartbeat() implements ResponseElement {}
    }
}
