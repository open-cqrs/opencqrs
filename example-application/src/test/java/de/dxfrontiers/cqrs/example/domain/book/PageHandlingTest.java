/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.domain.book;

import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import de.dxfrontiers.cqrs.example.domain.book.api.BookPageDamagedEvent;
import de.dxfrontiers.cqrs.example.domain.book.api.MarkBookPageDamagedCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
