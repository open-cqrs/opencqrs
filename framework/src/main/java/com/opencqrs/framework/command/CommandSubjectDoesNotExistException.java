/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

/** Exception thrown if {@link Command.SubjectCondition#EXISTS} is violated. */
public class CommandSubjectDoesNotExistException extends CommandSubjectConditionViolatedException {

    public CommandSubjectDoesNotExistException(String message, Command command) {
        super(message, command, Command.SubjectCondition.EXISTS);
    }
}
