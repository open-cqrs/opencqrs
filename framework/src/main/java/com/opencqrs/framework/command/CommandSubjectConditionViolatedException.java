/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.framework.CqrsFrameworkException;

/** Exception thrown if {@link Command.SubjectCondition} is violated. */
public abstract class CommandSubjectConditionViolatedException extends CqrsFrameworkException.NonTransientException {

    private final Command command;
    private final Command.SubjectCondition subjectCondition;

    public CommandSubjectConditionViolatedException(
            String message, Command command, Command.SubjectCondition subjectCondition) {
        super(message);
        this.command = command;
        this.subjectCondition = subjectCondition;
    }

    public Command getCommand() {
        return command;
    }

    public Command.SubjectCondition getSubjectCondition() {
        return subjectCondition;
    }
}
