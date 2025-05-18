/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book.api;

import java.util.UUID;

public record MarkBookPageDamagedCommand(String isbn, Long page, UUID reader) implements BookPageCommand {

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.PRISTINE;
    }
}
