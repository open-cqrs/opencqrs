/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book;

import com.opencqrs.example.domain.book.api.*;
import com.opencqrs.example.projection.reader.ReaderRepository;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.doReturn;

@CommandHandlingTest
public class BookHandlingTest {

    @MockitoBean
    private ReaderRepository readerRepository;

    @Test
    public void canBePurchased(@Autowired CommandHandlingTestFixture<Book, PurchaseBookCommand, String> fixture) {
        fixture.givenNothing()
                .when(new PurchaseBookCommand("4711", "JRR Tolkien", "LOTR", 435))
                .expectSuccessfulExecution()
                .expectSingleEvent(event -> event.commandSubject().noMetaData().payloadType(BookPurchasedEvent.class));
    }

    @Test
    public void canBeBorrowedIfReaderExists(
            @Autowired CommandHandlingTestFixture<Book, BorrowBookCommand, Void> fixture) {
        var reader = UUID.randomUUID();
        doReturn(true).when(readerRepository).existsById(reader);

        fixture.given(new BookPurchasedEvent("4711", "JRR Tolkien", "LOTR", 435))
                .when(new BorrowBookCommand("4711", reader))
                .expectSuccessfulExecution()
                .expectSingleEvent(new BookLentEvent("4711", reader));
    }

    @Test
    public void canBeReturnedIfLent(@Autowired CommandHandlingTestFixture<Book, ReturnBookCommand, Void> fixture) {
        fixture.givenState(new Book("4711", 435, Set.of(), new Book.Lending.Lent(UUID.randomUUID())))
                .when(new ReturnBookCommand("4711"))
                .expectSuccessfulExecution()
                .expectSingleEventType(BookReturnedEvent.class);
    }

    @Test
    public void cannotBeBorrowedIfTooManyPagesDamaged(
            @Autowired CommandHandlingTestFixture<Book, BorrowBookCommand, Void> fixture) {
        var reader = UUID.randomUUID();
        doReturn(true).when(readerRepository).existsById(reader);

        fixture.given(new BookPurchasedEvent("4711", "JRR Tolkien", "LOTR", 435))
                .andGiven(new BookPageDamagedEvent.ByReader("4711", 1L, reader))
                .andGiven(new BookPageDamagedEvent.ByReader("4711", 2L, reader))
                .andGiven(new BookPageDamagedEvent.ByReader("4711", 3L, reader))
                .andGiven(new BookPageDamagedEvent.ByReader("4711", 4L, reader))
                .andGiven(new BookPageDamagedEvent.ByReader("4711", 5L, reader))
                .when(new BorrowBookCommand("4711", reader))
                .expectException(BookNeedsReplacementException.class);
    }
}
