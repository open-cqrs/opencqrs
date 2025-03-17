/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.framework.CqrsFrameworkException;

/** Exception thrown if {@link Command.SubjectCondition#PRISTINE} is violated. */
public class CommandSubjectAlreadyExistsException extends CqrsFrameworkException.NonTransientException {

    private final Command command;

    public CommandSubjectAlreadyExistsException(String message, Command command) {
        super(message);
        this.command = command;
    }

    public Command getCommand() {
        return command;
    }
}
