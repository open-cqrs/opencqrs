/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.domain.book.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PurchaseBookCommand(
        @NotBlank String isbn, @NotBlank String author, @NotBlank String title, @NotNull long numPages)
        implements BookCommand {

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.PRISTINE;
    }
}
