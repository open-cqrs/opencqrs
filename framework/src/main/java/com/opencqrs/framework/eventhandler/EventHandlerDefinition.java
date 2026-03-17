/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import org.jspecify.annotations.NonNull;

/**
 * {@link EventHandler} definition suitable for being processed by an event processor.
 *
 * @param group group identifier for {@link EventHandler} belonging to the same processing group
 * @param eventClass the Java event type to be handled, may be {@link Object} to handle <b>all</b> events
 * @param handler the actual event handler
 * @param <E> the generic Java event type
 */
public record EventHandlerDefinition<E>(String group, Class<E> eventClass, EventHandler<E> handler) {}
