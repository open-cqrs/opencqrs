/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Interface specifying operations for interacting with an event store conforming to <a
 * href="https://github.com/cloudevents/spec">Cloud Events Specification</a>.
 *
 * @see EsdbClient
 */
public interface Client {

    /**
     * Authenticates against the configured event store, ensuring the correct api token has been configured.
     *
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code, e.g. 401 if not
     *     authenticated successfully
     * @throws ClientException.MarshallingException in case of serialization errors, typically caused by the associated
     *     {@link Marshaller}
     */
    void authenticate() throws ClientException;

    /**
     * Checks the healthiness of the configured event store.
     *
     * @return the health status
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     * @throws ClientException.MarshallingException in case of serialization errors, typically caused by the associated
     *     {@link Marshaller}
     */
    Health health() throws ClientException;

    /**
     * Publishes the given {@link EventCandidate}s to the underlying event store. The given preconditions must be
     * fulfilled in order for the publication to be applied. The publication is guaranteed to be an atomic operation,
     * that is event candidates will be published all or nothing, if preconditions hold.
     *
     * @param eventCandidates the candidate events to be published together
     * @param preconditions preconditions that must be fulfilled, otherwise
     *     {@link ClientException.HttpException.HttpClientException} with status code {@code 409} will be thrown
     * @return a list of {@link Event}s with all fields populated, except for {@link Event#hash()}
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     * @throws ClientException.MarshallingException in case of serialization errors, typically caused by the associated
     *     {@link Marshaller}
     */
    List<Event> write(List<EventCandidate> eventCandidates, List<Precondition> preconditions) throws ClientException;

    /**
     * Streams existing {@link Event}s from the underlying event store, waiting for any new events to be published.
     * <i>This method will block infinitely, if no exception or error occurs.</i> Any observed event will be passed the
     * given event consumer synchronously, to maintain the natural event order.
     *
     * <p>In order to observe <b>all</b> events from the underlying event store {@code subject} should be set to
     * {@code /} together with {@link Option.Recursive}.
     *
     * @param subject the subject to observe
     * @param options a set of options controlling the result set
     * @param eventConsumer a consumer callback for the observed events
     * @throws ClientException.InvalidUsageException in case of an invalid {@link Option} used
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     * @throws ClientException.MarshallingException in case of serialization errors, typically caused by the associated
     *     {@link Marshaller}
     */
    void observe(String subject, Set<Option> options, Consumer<Event> eventConsumer) throws ClientException;

    /**
     * Reads existing {@link Event}s from the underlying event store. All events will be passed the given event consumer
     * synchronously, to maintain the natural event order.
     *
     * @param subject the subject to read from
     * @param options a set of options controlling the result set
     * @param eventConsumer a consumer callback for the read events
     * @throws ClientException.InvalidUsageException in case of an invalid {@link Option} used
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     * @throws ClientException.MarshallingException in case of serialization errors, typically caused by the associated
     *     {@link Marshaller}
     */
    void read(String subject, Set<Option> options, Consumer<Event> eventConsumer) throws ClientException;

    /**
     * Reads existing {@link Event}s from the underlying event store.
     *
     * @param subject the subject to read from
     * @param options a set of options controlling the result set
     * @return a list of {@link Event}s, may be empty
     * @throws ClientException.InvalidUsageException in case of an invalid {@link Option} used
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     * @throws ClientException.MarshallingException in case of serialization errors, typically caused by the associated
     *     {@link Marshaller}
     */
    List<Event> read(String subject, Set<Option> options) throws ClientException;
}
