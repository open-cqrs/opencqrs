/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book;

import com.opencqrs.example.domain.book.api.BookPageDamagedEvent;
import com.opencqrs.example.domain.book.api.MarkBookPageDamagedCommand;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@CommandHandlingTest
public class PageHandlingTest {

    @Test
    public void pageMarkedAsDamaged(
            @Autowired CommandHandlingTestFixture<Page, MarkBookPageDamagedCommand, Void> fixture) {
        fixture.givenNothing()
                .when(new MarkBookPageDamagedCommand("4711", 42L, UUID.randomUUID()))
                .expectSuccessfulExecution()
                .expectSingleEvent(event -> event.commandSubject().payloadType(BookPageDamagedEvent.ByReader.class));
    }
}
