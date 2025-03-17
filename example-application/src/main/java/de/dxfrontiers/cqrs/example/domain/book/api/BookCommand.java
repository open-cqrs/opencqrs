/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.domain.book.api;

import com.opencqrs.framework.command.Command;

public interface BookCommand extends Command {

    String isbn();

    @Override
    default String getSubject() {
        return "/book/" + isbn();
    }
}
