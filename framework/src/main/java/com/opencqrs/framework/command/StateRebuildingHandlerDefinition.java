/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

/**
 * {@link StateRebuildingHandler} definition suitable for event-sourcing an instance prior to {@link CommandHandler
 * command handling}.
 *
 * @param instanceClass the instance type being event-sourced
 * @param eventClass the event type to be applied to any prior instance (state)
 * @param handler the actual state rebuilding handler
 * @param <I> the instance type
 * @param <E> the event type to be sourced
 */
public record StateRebuildingHandlerDefinition<I, E>(
        Class<I> instanceClass, Class<E> eventClass, StateRebuildingHandler<I, E> handler) {}
