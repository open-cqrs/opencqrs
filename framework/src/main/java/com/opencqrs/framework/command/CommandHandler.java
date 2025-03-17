/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.util.Map;

/**
 * Sealed base interface for inherited {@link FunctionalInterface} variants encapsulating command-handling logic.
 * Implementations of this will have to encapsulated into {@link CommandHandlerDefinition}s to be executable
 * {@link CommandRouter#send(Command)} or {@link CommandRouter#send(Command)}.
 *
 * @param <I> the generic type of the instance to be event sourced before handling the command
 * @param <C> the command type
 * @param <R> the command execution result type
 */
public sealed interface CommandHandler<I, C extends Command, R>
        permits CommandHandler.ForCommand,
                CommandHandler.ForInstanceAndCommand,
                CommandHandler.ForInstanceAndCommandAndMetaData {

    /**
     * {@link FunctionalInterface} to be implemented, if only the {@link Command} is needed for execution. This is
     * typically used for {@link Command.SubjectCondition#PRISTINE} commands, for which no instance state is available.
     * <strong>However, the usage of this interface does not imply {@linkplain Command.SubjectCondition#PRISTINE
     * pristine} semantics. Make sure to implement your {@link Command} accordingly, if needed.</strong>
     *
     * @param <I> the generic type of the instance to be event sourced before handling the command
     * @param <C> the command type
     * @param <R> the command execution result type
     */
    @FunctionalInterface
    non-sealed interface ForCommand<I, C extends Command, R> extends CommandHandler<I, C, R> {

        /**
         * Handles the given command without having access to the event-sourced instance representing the current state
         * (aka write model). The given {@link CommandEventPublisher} enables the publication of new events upon
         * successful command execution.
         *
         * @param command the command payload to be executed
         * @param commandEventPublisher a callback to publish new events
         * @return a generic command execution result, may be {@code null}
         */
        R handle(C command, CommandEventPublisher<I> commandEventPublisher);
    }

    /**
     * {@link FunctionalInterface} to be implemented, if {@link Command} and event-sourced instance are needed for
     * execution.
     *
     * @param <I> the generic type of the instance to be event sourced before handling the command
     * @param <C> the command type
     * @param <R> the command execution result type
     */
    @FunctionalInterface
    non-sealed interface ForInstanceAndCommand<I, C extends Command, R> extends CommandHandler<I, C, R> {

        /**
         * Handles the given command on an event-sourced instance representing the current state (aka write model). The
         * instance is sourced prior to calling this method based on {@link CommandHandlerDefinition#sourcingMode()} and
         * a set of configured {@link StateRebuildingHandler}s. The given {@link CommandEventPublisher} enables the
         * publication of new events upon successful command execution.
         *
         * @param instance the event-sourced instance the command is targeted at
         * @param command the command payload to be executed
         * @param commandEventPublisher a callback to publish new events
         * @return a generic command execution result, may be {@code null}
         */
        R handle(I instance, C command, CommandEventPublisher<I> commandEventPublisher);
    }

    /**
     * {@link FunctionalInterface} to be implemented, if {@link Command}, meta-data, and event-sourced instance are
     * needed for execution.
     *
     * @param <I> the generic type of the instance to be event sourced before handling the command
     * @param <C> the command type
     * @param <R> the command execution result type
     */
    @FunctionalInterface
    non-sealed interface ForInstanceAndCommandAndMetaData<I, C extends Command, R> extends CommandHandler<I, C, R> {

        /**
         * Handles the given command and meta-data on an event-sourced instance representing the current state (aka
         * write model). The instance is sourced prior to calling this method based on
         * {@link CommandHandlerDefinition#sourcingMode()} and a set of configured {@link StateRebuildingHandler}s. The
         * given {@link CommandEventPublisher} enables the publication of new events upon successful command execution.
         *
         * @param instance the event-sourced instance the command is targeted at
         * @param command the command payload to be executed
         * @param metaData the command meta-data, may be empty
         * @param commandEventPublisher a callback to publish new events
         * @return a generic command execution result, may be {@code null}
         */
        R handle(I instance, C command, Map<String, ?> metaData, CommandEventPublisher<I> commandEventPublisher);
    }
}
