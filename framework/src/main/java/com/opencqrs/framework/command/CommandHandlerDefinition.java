/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

/**
 * {@link CommandHandler} definition suitable for being processed by the {@link CommandRouter}.
 *
 * @param instanceClass instance type used for {@linkplain StateRebuildingHandler state rebuilding}
 * @param commandClass command type to be executed
 * @param handler command handler to be executed
 * @param sourcingMode the event-sourcing mode for the {@link CommandHandler}
 * @param <I> the generic type of the instance to be event sourced before handling the command
 * @param <C> the command type
 * @param <R> the command execution result type
 */
public record CommandHandlerDefinition<I, C extends Command, R>(
        Class<I> instanceClass, Class<C> commandClass, CommandHandler<I, C, R> handler, SourcingMode sourcingMode) {

    /**
     * Convenience constructor using {@link SourcingMode#RECURSIVE}.
     *
     * @param instanceClazz instance type used for state rebuilding
     * @param commandClazz command type to be executed
     * @param commandHandler command handler to be executed
     */
    public CommandHandlerDefinition(
            Class<I> instanceClazz, Class<C> commandClazz, CommandHandler<I, C, R> commandHandler) {
        this(instanceClazz, commandClazz, commandHandler, SourcingMode.RECURSIVE);
    }
}
