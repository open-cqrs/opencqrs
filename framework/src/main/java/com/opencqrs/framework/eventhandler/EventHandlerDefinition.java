/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@link EventHandler} definition suitable for being processed by an event processor.
 *
 * @param group group identifier for {@link EventHandler} belonging to the same processing group
 * @param eventClass the Java event type to be handled, may be {@link Object} to handle <b>all</b> events
 * @param handler the actual event handler
 * @param <E> the generic Java event type
 */
public record EventHandlerDefinition<E>(
        @NotBlank String group, @NotNull Class<E> eventClass, @NotNull EventHandler<E> handler) {}
