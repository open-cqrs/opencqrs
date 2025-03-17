/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.domain.reader.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegisterReaderCommand(@NotNull UUID id, @NotBlank String name) implements ReaderCommand {

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.PRISTINE;
    }
}
