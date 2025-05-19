/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.projection.book.verifier;

import com.opencqrs.example.domain.book.api.BookPurchasedEvent;
import com.opencqrs.example.domain.book.api.BookReturnedEvent;
import com.opencqrs.example.domain.book.api.MarkBookPageDamagedCommand;
import com.opencqrs.framework.command.CommandRouter;
import com.opencqrs.framework.command.CommandSubjectAlreadyExistsException;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BookVerifier {

    private static final Random random = new Random();

    @BookVerifying
    public void on(BookPurchasedEvent event, @Autowired BookRepository repository) {
        repository.save(new BookEntity(event.isbn(), event.numPages()));
    }

    @BookVerifying
    public void on(
            BookReturnedEvent event, @Autowired BookRepository repository, @Autowired CommandRouter commandRouter) {
        if (random.nextBoolean()) {
            var book = repository.findById(event.isbn()).get();
            try {
                commandRouter.send(
                        new MarkBookPageDamagedCommand(book.isbn, random.nextLong(book.pages), event.reader()));
            } catch (CommandSubjectAlreadyExistsException e) {
                // already marked as damaged
            }
        }
    }
}
