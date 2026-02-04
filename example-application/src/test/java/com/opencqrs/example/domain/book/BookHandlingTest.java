/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book;

import static org.mockito.Mockito.doReturn;

import com.opencqrs.example.domain.book.api.*;
import com.opencqrs.example.projection.reader.ReaderRepository;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@CommandHandlingTest
public class BookHandlingTest {

    @MockitoBean
    private ReaderRepository readerRepository;

    @Test
    public void canBePurchased(@Autowired CommandHandlingTestFixture<PurchaseBookCommand> fixture) {
        fixture.given()
                .nothing()
                .when(new PurchaseBookCommand("4711", "JRR Tolkien", "LOTR", 435))
                .succeeds()
                .then()
                .allEvents()
                .single(event ->
                        event.asserting(a -> a.commandSubject().noMetaData().payloadType(BookPurchasedEvent.class)));
    }

    @Test
    public void canBeBorrowedIfReaderExists(@Autowired CommandHandlingTestFixture<BorrowBookCommand> fixture) {
        var reader = UUID.randomUUID();
        doReturn(true).when(readerRepository).existsById(reader);

        fixture.given()
                .events(new BookPurchasedEvent("4711", "JRR Tolkien", "LOTR", 435))
                .when(new BorrowBookCommand("4711", reader))
                .succeeds()
                .then()
                .allEvents()
                .exactly(e -> e.comparing(new BookLentEvent("4711", reader)));
    }

    @Test
    public void canBeReturnedIfLent(@Autowired CommandHandlingTestFixture<ReturnBookCommand> fixture) {
        fixture.given()
                .state(new Book("4711", 435, Set.of(), new Book.Lending.Lent(UUID.randomUUID())))
                .when(new ReturnBookCommand("4711"))
                .succeeds()
                .then()
                .allEvents()
                .single(e -> e.ofType(BookReturnedEvent.class));
    }

    @Test
    public void cannotBeBorrowedIfTooManyPagesDamaged(
            @Autowired CommandHandlingTestFixture<BorrowBookCommand> fixture) {
        var reader = UUID.randomUUID();
        doReturn(true).when(readerRepository).existsById(reader);

        fixture.given()
                .events(
                        new BookPurchasedEvent("4711", "JRR Tolkien", "LOTR", 435),
                        new BookPageDamagedEvent.ByReader("4711", 1L, reader),
                        new BookPageDamagedEvent.ByReader("4711", 2L, reader),
                        new BookPageDamagedEvent.ByReader("4711", 3L, reader),
                        new BookPageDamagedEvent.ByReader("4711", 4L, reader),
                        new BookPageDamagedEvent.ByReader("4711", 5L, reader))
                .when(new BorrowBookCommand("4711", reader))
                .fails()
                .throwing(BookNeedsReplacementException.class);
    }
}
