/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.domain.book;

import com.opencqrs.framework.command.CommandEventPublisher;
import com.opencqrs.framework.command.CommandHandlerConfiguration;
import com.opencqrs.framework.command.CommandHandling;
import com.opencqrs.framework.command.StateRebuilding;
import de.dxfrontiers.cqrs.example.domain.book.api.BookPageDamagedEvent;
import de.dxfrontiers.cqrs.example.domain.book.api.MarkBookPageDamagedCommand;

@CommandHandlerConfiguration
public class PageHandling {

    @CommandHandling
    public void handle(MarkBookPageDamagedCommand command, CommandEventPublisher<Page> publisher) {
        publisher.publish(new BookPageDamagedEvent.ByReader(command.isbn(), command.page(), command.reader()));
    }

    @StateRebuilding
    public Page on(BookPageDamagedEvent event) {
        return new Page(event.page(), true);
    }
}
