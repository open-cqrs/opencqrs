/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.reader.api;

import com.opencqrs.framework.command.Command;
import java.util.UUID;

public interface ReaderCommand extends Command {

    UUID id();

    @Override
    default String getSubject() {
        return "/reader/" + id();
    }
}
