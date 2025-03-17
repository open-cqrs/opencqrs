/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.framework.CqrsFrameworkException;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** {@link EventDataMarshaller} implementation that uses a configurable {@link ObjectMapper} for marshalling. */
public class JacksonEventDataMarshaller implements EventDataMarshaller {

    private final ObjectMapper objectMapper;

    public JacksonEventDataMarshaller(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <E> Map<String, ?> serialize(EventData<E> data) {
        try {
            Map payload = objectMapper.convertValue(data.payload(), Map.class);
            Map metaData = objectMapper.convertValue(data.metaData(), Map.class);
            return Map.of("payload", payload, "metadata", metaData);
        } catch (IllegalArgumentException e) {
            throw new CqrsFrameworkException.NonTransientException("failed to serialize: " + data, e.getCause());
        }
    }

    @Override
    public <E> EventData<E> deserialize(Map<String, ?> json, Class<E> clazz) {
        try {
            JacksonData<E> deserialized = objectMapper.convertValue(
                    json, objectMapper.getTypeFactory().constructParametricType(JacksonData.class, clazz));
            return new EventData<>(deserialized.metadata(), deserialized.payload());
        } catch (IllegalArgumentException e) {
            throw new CqrsFrameworkException.NonTransientException("failed to deserialize: " + json, e.getCause());
        }
    }

    record JacksonData<E>(@NotNull Map<String, ?> metadata, @NotNull E payload) {}
}
