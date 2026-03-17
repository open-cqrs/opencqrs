/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.reader.api;

import java.util.UUID;

public record RegisterReaderCommand(UUID id, String name) implements ReaderCommand {

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.PRISTINE;
    }
}
