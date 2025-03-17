/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.types;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.EventCandidate;

/**
 * Interface implemented for resolving Java {@link Class} to {@link EventCandidate#type()} or {@link Event#type()} and
 * vice versa.
 */
public interface EventTypeResolver {

    /**
     * Resolves the given event type for the given {@link Class}.
     *
     * @param clazz the Java event {@link Class} to be resolved
     * @return the event type to be used for {@link EventCandidate#type()} or {@link Event#type()}
     * @throws EventTypeResolutionException in case the type cannot be resolved
     */
    String getEventType(Class<?> clazz) throws EventTypeResolutionException;

    /**
     * Resolved the Java {@link Class} for the given event type.
     *
     * @param eventType the event type to be resolved
     * @return the Java event {@link Class}
     * @throws EventTypeResolutionException in case the {@link Class} cannot be resolved
     */
    Class<?> getJavaClass(String eventType) throws EventTypeResolutionException;
}
