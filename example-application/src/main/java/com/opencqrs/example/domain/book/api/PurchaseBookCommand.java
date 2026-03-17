/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book.api;

import org.jspecify.annotations.NonNull;

public record PurchaseBookCommand(String isbn, String author, String title, long numPages)
        implements BookCommand {

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.PRISTINE;
    }
}
