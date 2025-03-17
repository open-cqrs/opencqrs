/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.framework.CqrsFrameworkException;

/** Exception thrown if {@link Command.SubjectCondition#EXISTS} is violated. */
public class CommandSubjectDoesNotExistException extends CqrsFrameworkException.TransientException {

    private final Command command;

    public CommandSubjectDoesNotExistException(String message, Command command) {
        super(message);
        this.command = command;
    }

    public Command getCommand() {
        return command;
    }
}
