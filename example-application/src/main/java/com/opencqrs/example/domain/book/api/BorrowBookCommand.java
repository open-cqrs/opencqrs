/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book.api;

import java.util.UUID;
import org.jspecify.annotations.NonNull;

public record BorrowBookCommand(String isbn, UUID reader) implements BookCommand {

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.EXISTS;
    }
}
