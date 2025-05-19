/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import com.opencqrs.esdb.client.eventql.ErrorHandler;
import com.opencqrs.esdb.client.eventql.QueryProcessingError;
import com.opencqrs.esdb.client.eventql.RowHandler;
import com.opencqrs.esdb.client.jackson.JacksonMarshaller;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Interface specifying operations for HTTP request/response marshalling.
 *
 * @see JacksonMarshaller
 */
public interface Marshaller {

    /**
     * Used by {@link EsdbClient} operations to transform any generic HTTP JSON response.
     *
     * @param response the JSON HTTP response body as string
     * @return a map representing the JSON response content
     */
    Map<String, Object> fromJsonResponse(String response);

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

    /**
     * Used by {@link EsdbClient} to transform the given parameters into a valid HTTP request body to be sent to the
     * event store for query operations.
     *
     * @param query the query
     * @return the JSON HTTP request body as string
     * @see EsdbClient#query(String, RowHandler, ErrorHandler)
     */
    String toQueryRequest(String query);

    /**
     * Used by {@link EsdbClient} to transform an ND-JSON line from the HTTP response stream to a
     * {@link QueryResponseElement}.
     *
     * @param line the ND-JSON element as string
     * @return an unmarshalled {@link QueryResponseElement}
     */
    QueryResponseElement fromQueryResponseLine(String line);

    /**
     * Sealed interface representing a deserialized ND-JSON response line transformed via
     * {@link #fromQueryResponseLine(String)}.
     */
    sealed interface QueryResponseElement {

        /**
         * Represents a <code>row</code> returned from the event store, which may be processed by the deferred
         * {@link BiConsumer}. The consumer is needed, because rows returned from the event store may still fail to
         * deserialize correctly to the target type, as defined by {@link RowHandler}.
         *
         * @param deferredHandler the deferred bi-consumer
         */
        record Row(BiConsumer<RowHandler, ErrorHandler> deferredHandler) implements QueryResponseElement {}

        /**
         * Represents an <code>error</code> returned from the event store, typically caused by an invalid query or
         * missing data in the result set.
         *
         * @param payload the error payload
         */
        record Error(QueryProcessingError payload) implements QueryResponseElement {}
    }
}
