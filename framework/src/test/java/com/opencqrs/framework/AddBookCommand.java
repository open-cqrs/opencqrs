/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework;

import com.opencqrs.framework.command.Command;

public record AddBookCommand(String isbn) implements Command {

    @Override
    public String getSubject() {
        return "/books/" + isbn;
    }

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.PRISTINE;
    }
}
