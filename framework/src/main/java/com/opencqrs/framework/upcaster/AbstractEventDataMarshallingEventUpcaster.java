/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.upcaster;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.serialization.EventData;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Template implementation of {@link EventUpcaster} that uses a delegate {@link EventDataMarshaller} to allow subclasses
 * to upcast {@link EventData#metaData()} and {@link EventData#payload()} as pre-extracted JSON-like {@link Map}s.
 *
 * @see #doUpcast(Event, Map, Map)
 */
public abstract class AbstractEventDataMarshallingEventUpcaster implements EventUpcaster {

    private final EventDataMarshaller eventDataMarshaller;

    /**
     * Constructor for implementations of {@code this}.
     *
     * @param eventDataMarshaller the marshaller used to extract {@link EventData} from {@link Event#data()}
     */
    protected AbstractEventDataMarshallingEventUpcaster(EventDataMarshaller eventDataMarshaller) {
        this.eventDataMarshaller = eventDataMarshaller;
    }

    @Override
    public final Stream<Result> upcast(Event event) {
        EventData<Map> deserialized = eventDataMarshaller.deserialize(event.data(), Map.class);
        return doUpcast(event, deserialized.metaData(), (Map<String, ?>) deserialized.payload())
                .map(o -> {
                    Map<String, ?> serialized = eventDataMarshaller.serialize(new EventData<>(o.metaData, o.payload));
                    return new Result(o.type, serialized);
                });
    }

    /**
     * Template method to be implemented by subclasses to upcast the given meta-data and payload.
     *
     * @param event the event from which meta-data and payload have been
     *     {@linkplain EventDataMarshaller#deserialize(Map, Class) extracted}
     * @param metaData the meta-data
     * @param payload the payload as JSON-like map
     * @return a stream of {@link MetaDataAndPayloadResult} carrying the upcasted representations
     * @see EventUpcaster#upcast(Event)
     */
    protected abstract Stream<MetaDataAndPayloadResult> doUpcast(
            Event event, Map<String, ?> metaData, Map<String, ?> payload);

    /**
     * Captures upcasted {@link Event#type()}, meta-data, and payload.
     *
     * @param type the upcasted {@link Event#type()}
     * @param metaData the upcasted meta-data
     * @param payload the upcasted payload
     */
    public record MetaDataAndPayloadResult(
            @NotBlank String type, @NotNull Map<String, ?> metaData, @NotNull Map<String, ?> payload) {}
}
