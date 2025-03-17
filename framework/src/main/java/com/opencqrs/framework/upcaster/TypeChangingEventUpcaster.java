/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.upcaster;

import com.opencqrs.esdb.client.Event;
import java.util.stream.Stream;

/**
 * {@link EventUpcaster} implementation that changes the {@link Event#type()} to a new type if it matches the configured
 * source type.
 */
public class TypeChangingEventUpcaster implements EventUpcaster {

    private final String sourceType;
    private final String targetType;

    public TypeChangingEventUpcaster(String sourceType, String targetType) {
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    @Override
    public boolean canUpcast(Event event) {
        return event.type().equals(sourceType);
    }

    @Override
    public Stream<Result> upcast(Event event) {
        return Stream.of(new Result(targetType, event.data()));
    }
}
