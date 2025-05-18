/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.book;

import com.opencqrs.example.domain.book.api.*;
import com.opencqrs.example.domain.reader.api.NoSuchReaderException;
import com.opencqrs.example.projection.reader.ReaderRepository;
import com.opencqrs.framework.command.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

@CommandHandlerConfiguration
public class BookHandling {

    @CommandHandling(sourcingMode = SourcingMode.LOCAL)
    public String purchase(PurchaseBookCommand command, CommandEventPublisher<Book> publisher) {
        publisher.publish(
                new BookPurchasedEvent(command.isbn(), command.author(), command.title(), command.numPages()));

        return command.isbn();
    }

    @StateRebuilding
    public Book on(BookPurchasedEvent event) {
        return new Book(event.isbn(), event.numPages(), Set.of(), new Book.Lending.Available());
    }

    @CommandHandling
    public void borrow(
            Book book,
            BorrowBookCommand command,
            CommandEventPublisher<Book> publisher,
            @Autowired ReaderRepository readerRepository) {
        if (book.lending() instanceof Book.Lending.Lent) {
            throw new BookAlreadyLentException();
        }

        if (!readerRepository.existsById(command.reader())) {
            throw new NoSuchReaderException();
        }

        if (book.damagedPages().size() > 4) {
            throw new BookNeedsReplacementException();
        }

        publisher.publish(new BookLentEvent(command.isbn(), command.reader()));
    }

    @StateRebuilding
    public Book on(Book book, BookLentEvent event) {
        return book.with(new Book.Lending.Lent(event.reader()));
    }

    @CommandHandling(sourcingMode = SourcingMode.LOCAL)
    public void returnBook(Book book, ReturnBookCommand command, CommandEventPublisher<Book> publisher) {
        switch (book.lending()) {
            case Book.Lending.Available ignored -> throw new BookNotLentException();
            case Book.Lending.Lent lent -> publisher.publish(new BookReturnedEvent(command.isbn(), lent.reader()));
        }
    }

    @StateRebuilding
    public Book on(Book book, BookReturnedEvent event) {
        return book.with(new Book.Lending.Available());
    }

    @StateRebuilding
    public Book on(Book book, BookPageDamagedEvent event) {
        return book.withDamagedPage(event.page());
    }
}
