/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book;

import com.opencqrs.example.domain.book.api.BookPageDamagedEvent;
import com.opencqrs.example.domain.book.api.MarkBookPageDamagedCommand;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommandHandlingTest
public class PageHandlingTest {

    @Test
    public void pageMarkedAsDamaged(@Autowired CommandHandlingTestFixture<MarkBookPageDamagedCommand> fixture) {
        fixture.given()
                .nothing()
                .when(new MarkBookPageDamagedCommand("4711", 42L, UUID.randomUUID()))
                .succeeds()
                .then()
                .allEvents()
                .single(event ->
                        event.asserting(a -> a.commandSubject().payloadType(BookPageDamagedEvent.ByReader.class)));
    }
}
