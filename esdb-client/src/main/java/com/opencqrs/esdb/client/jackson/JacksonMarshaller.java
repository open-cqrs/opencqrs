/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.esdb.client.*;
import com.opencqrs.esdb.client.eventql.EventQueryProcessingError;
import com.opencqrs.esdb.client.eventql.EventQueryRowHandler;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** {@link ObjectMapper} based {@link Marshaller} implementation. */
public class JacksonMarshaller implements Marshaller {

    private final ObjectMapper objectMapper;

    public JacksonMarshaller(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> fromJsonResponse(String response) {
        try {
            return objectMapper.readValue(response, Map.class);
        } catch (JsonProcessingException e) {
            throw new ClientException.MarshallingException(e);
        }
    }

    @Override
    public Health fromHealthResponse(String response) {
        try {
            return objectMapper.readValue(response, Health.class);
        } catch (JsonProcessingException e) {
            throw new ClientException.MarshallingException(e);
        }
    }

    @Override
    public String toWriteEventsRequest(List<EventCandidate> eventCandidates, List<Precondition> preconditions) {
        var jacksonPreconditions = preconditions.stream().map(this::toJackson).toList();

        try {
            return objectMapper.writeValueAsString(
                    Map.of("events", eventCandidates, "preconditions", jacksonPreconditions));
        } catch (JsonProcessingException e) {
            throw new ClientException.MarshallingException(e);
        }
    }

    @Override
    public List<Event> fromWriteEventsResponse(String response) {
        try {
            List<JacksonResponseElement.Event.Payload> deserialized = objectMapper.readValue(
                    response,
                    objectMapper
                            .getTypeFactory()
                            .constructCollectionLikeType(List.class, JacksonResponseElement.Event.Payload.class));
            return deserialized.stream()
                    .map(e -> new Event(
                            e.source,
                            e.subject,
                            e.type,
                            e.data,
                            e.specversion,
                            e.id,
                            e.time,
                            e.datacontenttype,
                            e.hash,
                            e.predecessorhash))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new ClientException.MarshallingException(e);
        }
    }

    @Override
    public String toReadOrObserveEventsRequest(String subject, Set<Option> options) {
        JacksonOptions jacksonOptions = new JacksonOptions(
                mapOptionIfPresent(options, Option.Recursive.class, o -> true).orElse(false),
                mapOptionIfPresentOrNull(
                        options, Option.Order.class, o -> o.type().name().toLowerCase()),
                mapOptionIfPresent(
                                options,
                                Option.LowerBoundInclusive.class,
                                b -> new JacksonOptions.Bound(b.id(), JacksonOptions.Bound.Type.inclusive))
                        .orElseGet(() -> mapOptionIfPresentOrNull(
                                options,
                                Option.LowerBoundExclusive.class,
                                b -> new JacksonOptions.Bound(b.id(), JacksonOptions.Bound.Type.exclusive))),
                mapOptionIfPresent(
                                options,
                                Option.UpperBoundInclusive.class,
                                b -> new JacksonOptions.Bound(b.id(), JacksonOptions.Bound.Type.inclusive))
                        .orElseGet(() -> mapOptionIfPresentOrNull(
                                options,
                                Option.UpperBoundExclusive.class,
                                b -> new JacksonOptions.Bound(b.id(), JacksonOptions.Bound.Type.exclusive))),
                mapOptionIfPresentOrNull(
                        options,
                        Option.FromLatestEvent.class,
                        o -> new JacksonOptions.FromLatestEvent(
                                o.subject(),
                                o.type(),
                                switch (o.ifEventIsMissing()) {
                                    case READ_NOTHING -> "read-nothing";
                                    case READ_EVERYTHING -> "read-everything";
                                })));

        try {
            return objectMapper.writeValueAsString(Map.of("subject", subject, "options", jacksonOptions));
        } catch (JsonProcessingException e) {
            throw new ClientException.MarshallingException(e);
        }
    }

    @Override
    public ResponseElement fromReadOrObserveResponseLine(String line) {
        try {
            JacksonResponseElement jacksonResponseElement = objectMapper.readValue(line, JacksonResponseElement.class);
            return switch (jacksonResponseElement) {
                case JacksonResponseElement.Heartbeat heartbeat -> new ResponseElement.Heartbeat();
                case JacksonResponseElement.Event event ->
                    new Event(
                            event.payload.source,
                            event.payload.subject,
                            event.payload.type,
                            event.payload.data,
                            event.payload.specversion,
                            event.payload.id,
                            event.payload.time,
                            event.payload.datacontenttype,
                            event.payload.hash,
                            event.payload.predecessorhash);
            };
        } catch (JsonProcessingException e) {
            throw new ClientException.MarshallingException(e);
        }
    }

    private JacksonPrecondition toJackson(Precondition precondition) {
        return switch (precondition) {
            case Precondition.SubjectIsPristine p ->
                new JacksonPrecondition.IsPristine(
                        "isSubjectPristine", new JacksonPrecondition.IsPristine.Payload(p.subject()));

            case Precondition.SubjectIsOnEventId p ->
                new JacksonPrecondition.IsOnEventId(
                        "isSubjectOnEventId", new JacksonPrecondition.IsOnEventId.Payload(p.subject(), p.eventId()));

            case Precondition.SubjectIsPopulated p ->
                new JacksonPrecondition.IsPopulated(
                        "isSubjectPopulated", new JacksonPrecondition.IsPopulated.Payload(p.subject()));

            case Precondition.EventQlQueryIsTrue p ->
                new JacksonPrecondition.EventQlQueryIsTrue(
                        "isEventQlQueryTrue", new JacksonPrecondition.EventQlQueryIsTrue.Payload(p.query().queryString()));
        };
    }

    interface JacksonPrecondition {
        record IsPristine(String type, Payload payload) implements JacksonPrecondition {
            record Payload(String subject) {}
        }

        record IsOnEventId(String type, Payload payload) implements JacksonPrecondition {
            record Payload(String subject, String eventId) {}
        }

        record IsPopulated(String type, Payload payload) implements JacksonPrecondition {
            record Payload(String subject) {}
        }

        record EventQlQueryIsTrue(String type, Payload payload) implements JacksonPrecondition {
            record Payload(String query) {}
        }
    }

    private <T, O extends Option> Optional<T> mapOptionIfPresent(
            Set<Option> options, Class<O> optionClass, Function<O, T> mapper) {
        return options.stream()
                .filter(o -> o.getClass().equals(optionClass))
                .findAny()
                .map(o -> mapper.apply((O) o));
    }

    private <T, O extends Option> T mapOptionIfPresentOrNull(
            Set<Option> options, Class<O> optionClass, Function<O, T> mapper) {
        return mapOptionIfPresent(options, optionClass, mapper).orElse(null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record JacksonOptions(
            Boolean recursive, String order, Bound lowerBound, Bound upperBound, FromLatestEvent fromLatestEvent) {
        record Bound(String id, Type type) {
            enum Type {
                inclusive,
                exclusive,
            }
        }

        record FromLatestEvent(String subject, String type, String ifEventIsMissing) {}
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "type",
            visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = JacksonResponseElement.Heartbeat.class, name = "heartbeat"),
        @JsonSubTypes.Type(value = JacksonResponseElement.Event.class, name = "event"),
    })
    sealed interface JacksonResponseElement {
        record Heartbeat() implements JacksonResponseElement {}

        record Event(@NotNull Payload payload) implements JacksonResponseElement {
            record Payload(
                    @NotBlank String source,
                    @NotBlank String subject,
                    @NotBlank String type,
                    @NotNull Map<String, ?> data,
                    @NotBlank String specversion,
                    @NotBlank String id,
                    @NotNull Instant time,
                    @NotBlank String datacontenttype,
                    @NotBlank String hash,
                    @NotBlank String predecessorhash) {}
        }
    }

    @Override
    public String toQueryRequest(String query) {
        try {
            return objectMapper.writeValueAsString(Map.of("query", query));
        } catch (JsonProcessingException e) {
            throw new ClientException.MarshallingException(e);
        }
    }

    @Override
    public QueryResponseElement fromQueryResponseLine(String line) {
        try {
            JacksonQueryResponseElement jacksonResponseElement =
                    objectMapper.readValue(line, JacksonQueryResponseElement.class);
            return switch (jacksonResponseElement) {
                case JacksonQueryResponseElement.Row row ->
                    new QueryResponseElement.Row((rowHandler, errorHandler) -> {
                        try {
                            switch (rowHandler) {
                                case EventQueryRowHandler.AsEvent consumer -> {
                                    var jacksonEvent = objectMapper.convertValue(
                                            row.payload(), JacksonResponseElement.Event.Payload.class);
                                    consumer.accept(new Event(
                                            jacksonEvent.source,
                                            jacksonEvent.subject,
                                            jacksonEvent.type,
                                            jacksonEvent.data,
                                            jacksonEvent.specversion,
                                            jacksonEvent.id,
                                            jacksonEvent.time,
                                            jacksonEvent.datacontenttype,
                                            jacksonEvent.hash,
                                            jacksonEvent.predecessorhash));
                                }
                                case EventQueryRowHandler.AsMap consumer ->
                                    consumer.accept((Map<String, ?>) row.payload);
                                case EventQueryRowHandler.AsObject consumer -> {
                                    var object = objectMapper.convertValue(row.payload(), consumer.type());
                                    consumer.accept(object);
                                }
                                case EventQueryRowHandler.AsScalar consumer -> consumer.accept(row.payload());
                            }
                        } catch (ClassCastException e) {
                            errorHandler.marshallingError(new ClientException.MarshallingException(e), line);
                        } catch (IllegalArgumentException e) {
                            errorHandler.marshallingError(new ClientException.MarshallingException(e.getCause()), line);
                        }
                    });
                case JacksonQueryResponseElement.Error error -> new QueryResponseElement.Error(error.payload());
            };

        } catch (JsonProcessingException e) {
            throw new ClientException.MarshallingException(e);
        }
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "type",
            visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = JacksonQueryResponseElement.Row.class, name = "row"),
        @JsonSubTypes.Type(value = JacksonQueryResponseElement.Error.class, name = "error"),
    })
    sealed interface JacksonQueryResponseElement {
        record Row(Object payload) implements JacksonQueryResponseElement {}

        record Error(EventQueryProcessingError payload) implements JacksonQueryResponseElement {}
    }
}
