/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book.api;

import java.util.UUID;

public record BorrowBookCommand(String isbn, UUID reader) implements BookCommand {

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.EXISTS;
    }
}
