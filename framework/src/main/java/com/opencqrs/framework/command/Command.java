/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;

/**
 * Interface to be implemented by commands that can be handled by {@link CommandHandler}s.
 *
 * @see CommandRouter#send(Command)
 */
public interface Command {

    /**
     * Specifies the subject of this command, needed to source the necessary events for the command execution.
     *
     * @return the subject path
     */
    String getSubject();

    /**
     * Specifies the condition to check for the given {@link #getSubject()} before a {@link CommandHandler} will be
     * executed.
     *
     * @return the subject condition to check for
     */
    default SubjectCondition getSubjectCondition() {
        return SubjectCondition.NONE;
    }

    /** The {@linkplain #getSubject() subject} condition checked before {@link CommandHandler} execution. */
    enum SubjectCondition {

        /** No condition checks apply to the given {@linkplain #getSubject() subject}. */
        NONE,

        /**
         * Assures that the given {@linkplain #getSubject() subject} does not exist, that is no {@link Event} was
         * sourced with that specific {@link Event#subject()}, in spite of any {@linkplain SourcingMode#RECURSIVE
         * recursive} subjects. Otherwise {@link CommandSubjectAlreadyExistsException} will be thrown.
         *
         * <p><strong>The condition cannot be checked properly, if {@link SourcingMode#NONE} is used.</strong>
         */
        PRISTINE,

        /**
         * Assures that the given {@linkplain #getSubject() subject} exists, that is at least one {@link Event} was
         * sourced with that specific {@link Event#subject()}, in spite of any {@linkplain SourcingMode#RECURSIVE
         * recursive} subjects. Otherwise {@link CommandSubjectDoesNotExistException} will be thrown.
         *
         * <p><strong>The condition cannot be checked properly, if {@link SourcingMode#NONE} is used.</strong>
         */
        EXISTS,
    }
}
