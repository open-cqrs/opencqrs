/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import com.opencqrs.esdb.client.eventql.EventQuery;
import com.opencqrs.esdb.client.eventql.EventQueryErrorHandler;
import com.opencqrs.esdb.client.eventql.EventQueryRowHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Client SDK for the <a href="https://www.eventsourcingdb.io">EventSourcingDB</a>. */
public final class EsdbClient implements AutoCloseable {

    private static final Set<Class<? extends Option>> VALID_READ_OPTIONS = Set.of(
            Option.Recursive.class,
            Option.Order.class,
            Option.LowerBoundInclusive.class,
            Option.LowerBoundExclusive.class,
            Option.UpperBoundInclusive.class,
            Option.UpperBoundExclusive.class,
            Option.FromLatestEvent.class);

    private static final Set<Class<? extends Option>> VALID_OBSERVE_OPTIONS = Set.of(
            Option.Recursive.class,
            Option.LowerBoundInclusive.class,
            Option.LowerBoundExclusive.class,
            Option.FromLatestEvent.class);

    private final URI serverUri;
    private final String accessToken;
    private final Marshaller marshaller;
    private final HttpClient httpClient;
    private final HttpRequestErrorHandler httpRequestErrorHandler;

    public EsdbClient(URI serverUri, String accessToken, Marshaller marshaller, HttpClient.Builder httpClientBuilder) {
        this.serverUri = serverUri;
        this.accessToken = accessToken;
        this.marshaller = marshaller;
        this.httpClient = httpClientBuilder.build();
        this.httpRequestErrorHandler = new HttpRequestErrorHandler(this.httpClient);
    }

    /**
     * Pings the configured event store, ensuring the server is running
     *
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     * @throws ClientException.MarshallingException in case of serialization errors, typically caused by the associated
     *     {@link Marshaller}
     */
    public void ping() throws ClientException {
        HttpRequest httpRequest =
                HttpRequest.newBuilder(serverUri.resolve("/api/v1/ping")).GET().build();

        var response = httpRequestErrorHandler.handle(
                httpRequest, headers -> HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(headers)));

        if (!"io.eventsourcingdb.api.ping-received"
                .equals(marshaller.fromJsonResponse(response).get("type"))) {
            throw new ClientException.HttpException.HttpClientException("no ping received", 200);
        }
    }

    /**
     * Authenticates against the configured event store, ensuring the correct api token has been configured.
     *
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code, e.g. 401 if not
     *     authenticated successfully
     * @throws ClientException.MarshallingException in case of serialization errors, typically caused by the associated
     *     {@link Marshaller}
     */
    public void authenticate() throws ClientException {
        HttpRequest httpRequest = newJsonRequest("/api/v1/verify-api-token")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        var response = httpRequestErrorHandler.handle(
                httpRequest, headers -> HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(headers)));

        if (!"io.eventsourcingdb.api.api-token-verified"
                .equals(marshaller.fromJsonResponse(response).get("type"))) {
            throw new ClientException.HttpException.HttpClientException("api token could not be verified", 200);
        }
    }

    /**
     * Checks the healthiness of the configured event store.
     *
     * @return the health status
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     * @throws ClientException.MarshallingException in case of serialization errors, typically caused by the associated
     *     {@link Marshaller}
     */
    public Health health() throws ClientException {
        HttpRequest httpRequest = newJsonRequest("/api/v1/health").GET().build();

        var response = httpRequestErrorHandler.handle(
                httpRequest, headers -> HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(headers)));
        return marshaller.fromHealthResponse(response);
    }

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
    public List<Event> write(List<EventCandidate> eventCandidates, List<Precondition> preconditions)
            throws ClientException {
        HttpRequest httpRequest = newJsonRequest("/api/v1/write-events")
                .POST(HttpRequest.BodyPublishers.ofString(
                        marshaller.toWriteEventsRequest(eventCandidates, preconditions)))
                .build();

        var response = httpRequestErrorHandler.handle(
                httpRequest, headers -> HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(headers)));
        List<Event> fromWriteEventsResponse = marshaller.fromWriteEventsResponse(response);
        return IntStream.range(0, fromWriteEventsResponse.size())
                .mapToObj(index -> {
                    var eventCandidate = eventCandidates.get(index);
                    var eventResponse = fromWriteEventsResponse.get(index);

                    return new Event(
                            eventResponse.source(),
                            eventResponse.subject(),
                            eventResponse.type(),
                            eventResponse.data(),
                            eventResponse.specVersion(),
                            eventResponse.id(),
                            eventResponse.time(),
                            eventResponse.dataContentType(),
                            eventResponse.hash(),
                            eventResponse.predecessorHash());
                })
                .toList();
    }

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
    public void observe(String subject, Set<Option> options, Consumer<Event> eventConsumer) throws ClientException {
        checkValidOptions(VALID_OBSERVE_OPTIONS, options);

        readOrObserve("/api/v1/observe-events", subject, options, eventConsumer);
        throw new ClientException.TransportException("Event observation stopped unexpectedly");
    }

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
    public void read(String subject, Set<Option> options, Consumer<Event> eventConsumer) throws ClientException {
        checkValidOptions(VALID_READ_OPTIONS, options);

        readOrObserve("/api/v1/read-events", subject, options, eventConsumer);
    }

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
    public List<Event> read(String subject, Set<Option> options) throws ClientException {
        var consumed = new ArrayList<Event>();
        read(subject, options, consumed::add);
        return consumed;
    }

    /**
     * Queries the underlying event store using <a
     * href="https://docs.eventsourcingdb.io/reference/eventql/">EventQL</a>.
     *
     * @param query the {@link EventQuery} to execute
     * @param rowHandler callback for successfully queried and transformed rows (called per row)
     * @param errorHandler callback for non successfully queried or transformed rows (called per row)
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     * @throws ClientException.MarshallingException in case of serialization errors <strong>regarding the request not
     *     the individual row</strong>, typically caused by the associated {@link Marshaller}
     * @see EventQueryRowHandler
     * @see EventQueryErrorHandler
     */
    public void query(EventQuery query, EventQueryRowHandler rowHandler, EventQueryErrorHandler errorHandler)
            throws ClientException {
        var httpRequest = newJsonRequest("/api/v1/run-eventql-query")
                .POST(HttpRequest.BodyPublishers.ofString(marshaller.toQueryRequest(query.queryString())))
                .build();

        httpRequestErrorHandler.handle(
                httpRequest,
                headers -> HttpResponse.BodySubscribers.fromLineSubscriber(
                        new AbstractLineSubscriber() {
                            @Override
                            public void onNext(String item) {
                                switch (marshaller.fromQueryResponseLine(item)) {
                                    case Marshaller.QueryResponseElement.Error error ->
                                        errorHandler.queryProcessingError(error.payload());
                                    case Marshaller.QueryResponseElement.Row row ->
                                        row.deferredHandler().accept(rowHandler, errorHandler);
                                }
                            }
                        },
                        s -> null,
                        Util.fromHttpHeaders(headers),
                        null));
    }

    private void checkValidOptions(Set<Class<? extends Option>> supported, Set<Option> requested) {
        Set<Class<? extends Option>> requestedOptionClasses =
                requested.stream().map(Option::getClass).collect(Collectors.toSet());

        if (!supported.containsAll(requestedOptionClasses)) {
            requestedOptionClasses.removeAll(supported);
            throw new ClientException.InvalidUsageException("unsupported option(s) used: " + requestedOptionClasses);
        }

        Set<Class<? extends Option>> invalidLowerBounds =
                Set.of(Option.LowerBoundInclusive.class, Option.LowerBoundExclusive.class);
        if (requestedOptionClasses.containsAll(invalidLowerBounds)) {
            throw new ClientException.InvalidUsageException("invalid option combination: " + invalidLowerBounds);
        }

        Set<Class<? extends Option>> invalidUpperBounds =
                Set.of(Option.UpperBoundInclusive.class, Option.UpperBoundExclusive.class);
        if (requestedOptionClasses.containsAll(invalidUpperBounds)) {
            throw new ClientException.InvalidUsageException("invalid option combination: " + invalidUpperBounds);
        }
    }

    private HttpRequest.Builder newJsonRequest(String path) {
        return HttpRequest.newBuilder(serverUri.resolve(path))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json");
    }

    private void readOrObserve(String path, String subject, Set<Option> options, Consumer<Event> eventConsumer)
            throws ClientException {
        HttpRequest httpRequest = newJsonRequest(path)
                .POST(HttpRequest.BodyPublishers.ofString(marshaller.toReadOrObserveEventsRequest(subject, options)))
                .build();

        httpRequestErrorHandler.handle(
                httpRequest,
                headers -> HttpResponse.BodySubscribers.fromLineSubscriber(
                        new AbstractLineSubscriber() {
                            @Override
                            public void onNext(String item) {
                                Marshaller.ResponseElement element = marshaller.fromReadOrObserveResponseLine(item);
                                if (element instanceof Event) {
                                    eventConsumer.accept((Event) element);
                                }
                            }
                        },
                        s -> null,
                        Util.fromHttpHeaders(headers),
                        null));
    }

    @Override
    public void close() throws Exception {
        httpClient.shutdownNow();
    }

    private abstract static class AbstractLineSubscriber implements Flow.Subscriber<String> {

        @Override
        public final void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public final void onError(Throwable throwable) {
            // intentionally left blank
        }

        @Override
        public final void onComplete() {
            // intentionally left blank
        }
    }
}
