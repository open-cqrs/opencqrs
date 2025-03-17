/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.domain.book.api;

import jakarta.validation.constraints.NotBlank;

public record ReturnBookCommand(@NotBlank String isbn) implements BookCommand {

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.EXISTS;
    }
}
