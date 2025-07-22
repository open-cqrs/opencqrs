/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.Option;
import com.opencqrs.esdb.client.eventql.EventQuery;

public sealed interface CommandHandling {
    /**
     * @param sourcingMode specifies the condition to check for the given {@link Command#getSubject()} before a
     *     {@link CommandHandler} will be executed.
     * @param subjectCondition
     */
    record UsingSubject(SourcingMode sourcingMode, SubjectCondition subjectCondition) implements CommandHandling {
        public UsingSubject() {
            this(SourcingMode.RECURSIVE, SubjectCondition.NONE);
        }

        /** The type of sourcing affecting which events will be sourced for {@link CommandHandler}s. */
        public enum SourcingMode {

            /** No events will be fetched for the {@link Command#getSubject()}. */
            NONE,

            /** Events will be fetched for the {@link Command#getSubject()} non-recursively. */
            LOCAL,

            /**
             * Events will be fetched for the {@link Command#getSubject()} recursively.
             *
             * @see Option.Recursive
             */
            RECURSIVE
        }

        /** The {@linkplain Command#getSubject() subject} condition checked before {@link CommandHandler} execution. */
        public enum SubjectCondition {

            /** No condition checks apply to the given {@linkplain Command#getSubject() subject}. */
            NONE,

            /**
             * Assures that the given {@linkplain Command#getSubject() subject} does not exist, that is no {@link Event} was
             * sourced with that specific {@link Event#subject()}, in spite of any {@linkplain SourcingMode#RECURSIVE
             * recursive} subjects. Otherwise {@link CommandSubjectAlreadyExistsException} will be thrown.
             *
             * <p><strong>The condition cannot be checked properly, if {@link SourcingMode#NONE} is used.</strong>
             */
            PRISTINE,

            /**
             * Assures that the given {@linkplain Command#getSubject() subject} exists, that is at least one {@link Event} was
             * sourced with that specific {@link Event#subject()}, in spite of any {@linkplain SourcingMode#RECURSIVE
             * recursive} subjects. Otherwise {@link CommandSubjectDoesNotExistException} will be thrown.
             *
             * <p><strong>The condition cannot be checked properly, if {@link SourcingMode#NONE} is used.</strong>
             */
            EXISTS,
        }
    }

    @FunctionalInterface
    non-sealed interface UsingQuery<C extends Command> extends CommandHandling {
        EventQuery query(C command);
    }
}
