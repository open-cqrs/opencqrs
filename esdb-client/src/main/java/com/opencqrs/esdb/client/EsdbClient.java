/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/** {@link Client} implementation for the event sourcing database. */
public final class EsdbClient implements AutoCloseable, Client {

    private static final Set<Class<? extends Option>> VALID_READ_OPTIONS = Set.of(
            Option.Recursive.class,
            Option.Order.class,
            Option.LowerBoundId.class,
            Option.UpperBoundId.class,
            Option.FromLatestEvent.class);

    private static final Set<Class<? extends Option>> VALID_OBSERVE_OPTIONS =
            Set.of(Option.Recursive.class, Option.LowerBoundId.class, Option.FromLatestEvent.class);

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

    @Override
    public Health health() throws ClientException {
        HttpRequest httpRequest = newJsonRequest("/api/health").GET().build();

        var response = httpRequestErrorHandler.handle(
                httpRequest, headers -> HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(headers)));
        return marshaller.fromHealthResponse(response);
    }

    @Override
    public List<Event> write(List<EventCandidate> eventCandidates, List<Precondition> preconditions)
            throws ClientException {
        HttpRequest httpRequest = newJsonRequest("/api/write-events")
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
                            eventCandidate.data(),
                            eventResponse.specVersion(),
                            eventResponse.id(),
                            eventResponse.time(),
                            eventResponse.dataContentType(),
                            // hash is not yet available from write response
                            null,
                            eventResponse.predecessorHash());
                })
                .toList();
    }

    @Override
    public void observe(String subject, Set<Option> options, Consumer<Event> eventConsumer) throws ClientException {
        checkValidOptions(VALID_OBSERVE_OPTIONS, options);

        readOrObserve("/api/observe-events", subject, options, eventConsumer);
        throw new ClientException.TransportException("Event observation stopped unexpectedly");
    }

    @Override
    public void read(String subject, Set<Option> options, Consumer<Event> eventConsumer) throws ClientException {
        checkValidOptions(VALID_READ_OPTIONS, options);

        readOrObserve("/api/read-events", subject, options, eventConsumer);
    }

    @Override
    public List<Event> read(String subject, Set<Option> options) throws ClientException {
        var consumed = new ArrayList<Event>();
        read(subject, options, consumed::add);
        return consumed;
    }

    private void checkValidOptions(Set<Class<? extends Option>> supported, Set<Option> requested) {
        List<? extends Class<? extends Option>> unsupported = requested.stream()
                .map(Option::getClass)
                .filter(o -> !supported.contains(o))
                .toList();

        if (!unsupported.isEmpty()) {
            throw new ClientException.InvalidUsageException("unsupported option(s) used: " + unsupported);
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
                        new Flow.Subscriber<String>() {
                            @Override
                            public void onSubscribe(Flow.Subscription subscription) {
                                subscription.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(String item) {
                                Marshaller.ResponseElement element = marshaller.fromReadOrObserveResponseLine(item);
                                if (element instanceof Event) {
                                    eventConsumer.accept((Event) element);
                                }
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                // intentionally left blank
                            }

                            @Override
                            public void onComplete() {
                                // intentionally left blank
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
}
