/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book.api;

import com.opencqrs.framework.command.Command;

public interface BookPageCommand extends Command {

    String isbn();

    Long page();

    @Override
    default String getSubject() {
        return "/book/" + isbn() + "/page/" + page();
    }
}
