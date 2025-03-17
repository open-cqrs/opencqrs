/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.upcaster;

import com.opencqrs.esdb.client.Event;
import java.util.stream.Stream;

/**
 * {@link EventUpcaster} implementation that drops an {@link Event} if the {@link Event#type()} matches the configured
 * type.
 */
public class NoEventUpcaster implements EventUpcaster {

    private final String type;

    public NoEventUpcaster(String type) {
        this.type = type;
    }

    @Override
    public boolean canUpcast(Event event) {
        return event.type().equals(type);
    }

    @Override
    public Stream<Result> upcast(Event event) {
        return Stream.empty();
    }
}
