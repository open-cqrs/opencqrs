/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import com.opencqrs.esdb.client.eventql.EventQuery;
import com.opencqrs.esdb.client.eventql.EventQueryErrorHandler;
import com.opencqrs.esdb.client.eventql.EventQueryProcessingError;
import com.opencqrs.esdb.client.eventql.EventQueryRowHandler;
import io.thenativeweb.eventsourcingdb.Bound;
import io.thenativeweb.eventsourcingdb.BoundType;
import io.thenativeweb.eventsourcingdb.Client;
import io.thenativeweb.eventsourcingdb.ClientOptions;
import io.thenativeweb.eventsourcingdb.DbApiException;
import io.thenativeweb.eventsourcingdb.EventSourcingDbException;
import io.thenativeweb.eventsourcingdb.IsEventQlQueryTruePrecondition;
import io.thenativeweb.eventsourcingdb.IsSubjectOnEventIdPrecondition;
import io.thenativeweb.eventsourcingdb.IsSubjectPopulatedPrecondition;
import io.thenativeweb.eventsourcingdb.IsSubjectPristinePrecondition;
import io.thenativeweb.eventsourcingdb.ObserveEventsOptions;
import io.thenativeweb.eventsourcingdb.ObserveFromLatestEvent;
import io.thenativeweb.eventsourcingdb.ObserveIfEventIsMissing;
import io.thenativeweb.eventsourcingdb.ReadEventsOptions;
import io.thenativeweb.eventsourcingdb.ReadFromLatestEvent;
import io.thenativeweb.eventsourcingdb.ReadIfEventIsMissing;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client SDK for the <a href="https://www.eventsourcingdb.io">EventSourcingDB</a>.
 *
 * <p>This variant keeps the API of the OpenCQRS client, but delegates every request to the official Java client SDK for
 * EventSourcingDB ({@code io.thenativeweb:eventsourcingdb}). Only {@link #health()} still sends its own request, since
 * the official client SDK has no health check.
 */
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

    public static final String EVENT_TYPE_PING_RECEIVED = "io.eventsourcingdb.api.ping-received";
    public static final String EVENT_TYPE_API_TOKEN_VERIFIED = "io.eventsourcingdb.api.api-token-verified";

    // The official client SDK reports an error line within the stream of a query as an exception that carries only
    // the text of the error.
    private static final String QUERY_ERROR_PREFIX = "failed to run EventQL query, got error: ";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final URI serverUri;
    private final String accessToken;
    private final Marshaller marshaller;
    private final HttpClient httpClient;
    private final HttpRequestErrorHandler httpRequestErrorHandler;
    private final Client client;

    public EsdbClient(URI serverUri, String accessToken, Marshaller marshaller, HttpClient.Builder httpClientBuilder) {
        this.serverUri = serverUri;
        this.accessToken = accessToken;
        this.marshaller = marshaller;
        this.httpClient = httpClientBuilder.build();
        this.httpRequestErrorHandler = new HttpRequestErrorHandler(this.httpClient);
        // The official client sends its requests with the configured HTTP client, so that its settings, e.g.
        // timeouts or a proxy, keep applying.
        this.client = new Client(serverUri, accessToken, new ClientOptions().withHttpClient(this.httpClient));
    }

    /**
     * Pings the configured event store, ensuring the server is running
     *
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     */
    public void ping() throws ClientException {
        call(() -> {
            client.ping();
            return null;
        });
    }

    /**
     * Authenticates against the configured event store, ensuring the correct api token has been configured.
     *
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code, e.g. 401 if not
     *     authenticated successfully
     */
    public void authenticate() throws ClientException {
        call(() -> {
            client.verifyApiToken();
            return null;
        });
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
        HttpRequest httpRequest = HttpRequest.newBuilder(serverUri.resolve("/api/v1/health"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .GET()
                .build();

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
     * @return a list of {@link Event}s with all fields populated
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     * @throws ClientException.MarshallingException in case of serialization errors
     */
    public List<Event> write(List<EventCandidate> eventCandidates, List<Precondition> preconditions)
            throws ClientException {
        var candidates = eventCandidates.stream().map(EsdbClient::toCandidate).toList();
        var conditions = preconditions.stream().map(EsdbClient::toPrecondition).toList();

        return call(() -> client.writeEvents(candidates, conditions)).stream()
                .map(EsdbClient::toEvent)
                .toList();
    }

    /**
     * Streams existing {@link Event}s from the underlying event store, waiting for any new events to be published.
     * <i>This method will block infinitely, if no exception or error occurs.</i> Any observed event will be passed the
     * given event consumer synchronously, to maintain the natural event order.
     *
     * @param subject the subject to observe
     * @param options a set of options controlling the result set
     * @param eventConsumer a consumer callback for the observed events
     * @throws ClientException.InvalidUsageException in case of an invalid {@link Option} used
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     */
    public void observe(String subject, Set<Option> options, Consumer<Event> eventConsumer) throws ClientException {
        checkValidOptions(VALID_OBSERVE_OPTIONS, options);

        consume(() -> client.observeEvents(subject, toObserveOptions(options)), eventConsumer);
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
     */
    public void read(String subject, Set<Option> options, Consumer<Event> eventConsumer) throws ClientException {
        checkValidOptions(VALID_READ_OPTIONS, options);

        consume(() -> client.readEvents(subject, toReadOptions(options)), eventConsumer);
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
     */
    public List<Event> read(String subject, Set<Option> options) throws ClientException {
        var consumed = new ArrayList<Event>();
        read(subject, options, consumed::add);
        return consumed;
    }

    /**
     * Reads all subjects from the underlying event store that are descendants of the given base subject. The base
     * subject itself is included in the result.
     *
     * @param baseSubject the base subject to read from
     * @return a list of subject strings, may be empty
     * @throws ClientException.TransportException in case of connection or network errors
     * @throws ClientException.HttpException in case of errors depending on the HTTP status code
     */
    public List<String> readSubjects(String baseSubject) throws ClientException {
        return call(() -> {
            try (var subjects = client.readSubjects(baseSubject)) {
                return subjects.toList();
            }
        });
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
     */
    public void query(EventQuery query, EventQueryRowHandler rowHandler, EventQueryErrorHandler errorHandler)
            throws ClientException {
        try {
            call(() -> {
                try (var rows = client.runEventQlQuery(query.queryString())) {
                    rows.forEach(row -> handleRow(row, rowHandler, errorHandler));
                }
                return null;
            });
        } catch (ClientException.TransportException ex) {
            if (ex.getCause() instanceof EventSourcingDbException cause
                    && cause.getMessage() != null
                    && cause.getMessage().startsWith(QUERY_ERROR_PREFIX)) {
                errorHandler.queryProcessingError(new EventQueryProcessingError(
                        cause.getMessage().substring(QUERY_ERROR_PREFIX.length()), null, null));
                return;
            }
            throw ex;
        }
    }

    private static void handleRow(JsonNode row, EventQueryRowHandler rowHandler, EventQueryErrorHandler errorHandler) {
        try {
            switch (rowHandler) {
                case EventQueryRowHandler.AsEvent consumer -> consumer.accept(toEvent(row));
                case EventQueryRowHandler.AsMap consumer -> consumer.accept(JSON.convertValue(row, MAP_TYPE));
                case EventQueryRowHandler.AsObject<?> consumer -> acceptObject(consumer, row);
                case EventQueryRowHandler.AsScalar<?> consumer -> acceptScalar(consumer, row);
            }
        } catch (ClassCastException | IllegalArgumentException | JacksonException ex) {
            errorHandler.marshallingError(new ClientException.MarshallingException(ex), row.toString());
        }
    }

    private static <T> void acceptObject(EventQueryRowHandler.AsObject<T> consumer, JsonNode row) {
        consumer.accept(JSON.treeToValue(row, consumer.type()));
    }

    @SuppressWarnings("unchecked")
    private static void acceptScalar(EventQueryRowHandler.AsScalar<?> consumer, JsonNode row) {
        ((EventQueryRowHandler.AsScalar<Object>) consumer).accept(JSON.treeToValue(row, Object.class));
    }

    private void consume(Supplier<Stream<io.thenativeweb.eventsourcingdb.Event>> open, Consumer<Event> eventConsumer) {
        call(() -> {
            try (var events = open.get()) {
                events.forEach(event -> eventConsumer.accept(toEvent(event)));
            }
            return null;
        });
    }

    // Maps the exceptions of the official client SDK to the ones this client has always thrown, so that callers,
    // e.g. the ClientRequestErrorMapper of the framework, keep working unchanged.
    private static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (ClientException ex) {
            throw ex;
        } catch (DbApiException ex) {
            var statusCode = ex.statusCode();
            if (statusCode >= 400 && statusCode < 500) {
                throw new ClientException.HttpException.HttpClientException(
                        "HTTP client request error: " + ex.reason(), statusCode);
            }
            if (statusCode >= 500 && statusCode <= 599) {
                throw new ClientException.HttpException.HttpServerException(
                        "HTTP server request error: " + ex.reason(), statusCode);
            }
            throw new ClientException.HttpException("unexpected HTTP status error: " + ex.reason(), statusCode);
        } catch (CancellationException ex) {
            throw new ClientException.InterruptedException("request interrupted", ex);
        } catch (JacksonException ex) {
            throw new ClientException.MarshallingException(ex);
        } catch (EventSourcingDbException ex) {
            throw new ClientException.TransportException(
                    Objects.requireNonNullElse(ex.getMessage(), "request failed"), ex);
        } catch (RuntimeException ex) {
            throw new ClientException.TransportException("failed to process response", ex);
        }
    }

    private static io.thenativeweb.eventsourcingdb.EventCandidate toCandidate(EventCandidate candidate) {
        return new io.thenativeweb.eventsourcingdb.EventCandidate(
                candidate.source(), candidate.subject(), candidate.type(), candidate.data());
    }

    private static io.thenativeweb.eventsourcingdb.Precondition toPrecondition(Precondition precondition) {
        return switch (precondition) {
            case Precondition.SubjectIsPristine p -> new IsSubjectPristinePrecondition(p.subject());
            case Precondition.SubjectIsPopulated p -> new IsSubjectPopulatedPrecondition(p.subject());
            case Precondition.SubjectIsOnEventId p -> new IsSubjectOnEventIdPrecondition(p.subject(), p.eventId());
            case Precondition.EventQlQueryIsTrue p ->
                new IsEventQlQueryTruePrecondition(p.query().queryString());
        };
    }

    private static Event toEvent(io.thenativeweb.eventsourcingdb.Event event) {
        return new Event(
                event.source(),
                event.subject(),
                event.type(),
                JSON.convertValue(event.data(), MAP_TYPE),
                event.specVersion(),
                event.id(),
                event.time(),
                event.dataContentType(),
                event.hash(),
                event.predecessorHash());
    }

    private static Event toEvent(JsonNode row) {
        var payload = JSON.treeToValue(row, EventPayload.class);

        return new Event(
                payload.source(),
                payload.subject(),
                payload.type(),
                payload.data(),
                payload.specversion(),
                payload.id(),
                payload.time(),
                payload.datacontenttype(),
                payload.hash(),
                payload.predecessorhash());
    }

    private record EventPayload(
            String source,
            String subject,
            String type,
            Map<String, Object> data,
            String specversion,
            String id,
            Instant time,
            String datacontenttype,
            String hash,
            String predecessorhash) {}

    private static ReadEventsOptions toReadOptions(Set<Option> options) {
        var recursive = false;
        io.thenativeweb.eventsourcingdb.@Nullable Order order = null;
        @Nullable Bound lowerBound = null;
        @Nullable Bound upperBound = null;
        @Nullable ReadFromLatestEvent fromLatestEvent = null;

        for (var option : options) {
            switch (option) {
                case Option.Recursive r -> recursive = true;
                case Option.Order o ->
                    order = o.type() == Option.Order.Type.CHRONOLOGICAL
                            ? io.thenativeweb.eventsourcingdb.Order.CHRONOLOGICAL
                            : io.thenativeweb.eventsourcingdb.Order.ANTICHRONOLOGICAL;
                case Option.LowerBoundInclusive b -> lowerBound = new Bound(b.id(), BoundType.INCLUSIVE);
                case Option.LowerBoundExclusive b -> lowerBound = new Bound(b.id(), BoundType.EXCLUSIVE);
                case Option.UpperBoundInclusive b -> upperBound = new Bound(b.id(), BoundType.INCLUSIVE);
                case Option.UpperBoundExclusive b -> upperBound = new Bound(b.id(), BoundType.EXCLUSIVE);
                case Option.FromLatestEvent f ->
                    fromLatestEvent = new ReadFromLatestEvent(
                            f.subject(),
                            f.type(),
                            f.ifEventIsMissing() == Option.FromLatestEvent.IfEventIsMissing.READ_NOTHING
                                    ? ReadIfEventIsMissing.READ_NOTHING
                                    : ReadIfEventIsMissing.READ_EVERYTHING);
            }
        }

        return new ReadEventsOptions(recursive, order, lowerBound, upperBound, fromLatestEvent);
    }

    // Observing knows no "read nothing": the closest is to wait for an event of the given type.
    private static ObserveEventsOptions toObserveOptions(Set<Option> options) {
        var recursive = false;
        @Nullable Bound lowerBound = null;
        @Nullable ObserveFromLatestEvent fromLatestEvent = null;

        for (var option : options) {
            switch (option) {
                case Option.Recursive r -> recursive = true;
                case Option.LowerBoundInclusive b -> lowerBound = new Bound(b.id(), BoundType.INCLUSIVE);
                case Option.LowerBoundExclusive b -> lowerBound = new Bound(b.id(), BoundType.EXCLUSIVE);
                case Option.FromLatestEvent f ->
                    fromLatestEvent = new ObserveFromLatestEvent(
                            f.subject(),
                            f.type(),
                            f.ifEventIsMissing() == Option.FromLatestEvent.IfEventIsMissing.READ_NOTHING
                                    ? ObserveIfEventIsMissing.WAIT_FOR_EVENT
                                    : ObserveIfEventIsMissing.READ_EVERYTHING);
                default -> throw new ClientException.InvalidUsageException("unsupported option: " + option);
            }
        }

        return new ObserveEventsOptions(recursive, lowerBound, fromLatestEvent);
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

    @Override
    public void close() throws Exception {
        // Closing the official client ends running requests, e.g. observations. It leaves the HTTP client it was
        // given alone, so that is shut down here.
        client.close();
        httpClient.shutdownNow();
    }
}
