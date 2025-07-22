/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

/** Exception thrown if {@link CommandHandling.UsingSubject.SubjectCondition#PRISTINE} is violated. */
public class CommandSubjectAlreadyExistsException extends CommandSubjectConditionViolatedException {

    public CommandSubjectAlreadyExistsException(String message, Command command) {
        super(message, command, CommandHandling.UsingSubject.SubjectCondition.PRISTINE);
    }
}
