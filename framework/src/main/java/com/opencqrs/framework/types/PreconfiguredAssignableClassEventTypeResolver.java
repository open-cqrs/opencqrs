/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.types;

import static java.util.stream.Collectors.toMap;

import com.opencqrs.framework.serialization.EventDataMarshaller;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link EventTypeResolver} implementation that can be pre-configured using a specific mapping between event types and
 * Java classes.
 *
 * <p>This implementation takes into account {@linkplain Class#isAssignableFrom(Class) class assignability} and hence
 * may be used to configure event types for abstract or sealed super classes, as well. This reduces the effort of
 * maintaining explicit mappings for each subclass, as long as the {@link EventDataMarshaller} is capable of
 * {@linkplain EventDataMarshaller#deserialize(Map, Class) deserializing} class hierarchies.
 */
public class PreconfiguredAssignableClassEventTypeResolver implements EventTypeResolver {

    private final Map<String, Class<?>> typesToClasses;
    private final Map<Class<?>, String> classesToTypes;

    public PreconfiguredAssignableClassEventTypeResolver(Map<String, Class<?>> typesToClasses) {
        this.typesToClasses = typesToClasses;
        this.classesToTypes = typesToClasses.entrySet().stream().collect(toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    @Override
    public String getEventType(Class<?> clazz) {
        return classesToTypes.keySet().stream()
                .filter(c -> c.isAssignableFrom(clazz))
                .reduce((a, b) -> {
                    throw new EventTypeResolutionException(
                            "ambiguous assignable classes found for event class (" + clazz + "): " + List.of(a, b));
                })
                .map(classesToTypes::get)
                .orElseThrow(() ->
                        new EventTypeResolutionException("no assignable type configured for event class: " + clazz));
    }

    @Override
    public Class<?> getJavaClass(String eventType) {
        return Optional.ofNullable(typesToClasses.get(eventType))
                .orElseThrow(
                        () -> new EventTypeResolutionException("failed to resolve event class for type: " + eventType));
    }
}
