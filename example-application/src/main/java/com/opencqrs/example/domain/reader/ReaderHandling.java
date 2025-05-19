/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.reader;

import com.opencqrs.example.domain.reader.api.ReaderRegisteredEvent;
import com.opencqrs.example.domain.reader.api.RegisterReaderCommand;
import com.opencqrs.framework.command.CommandEventPublisher;
import com.opencqrs.framework.command.CommandHandlerConfiguration;
import com.opencqrs.framework.command.CommandHandling;
import com.opencqrs.framework.command.StateRebuilding;
import java.util.UUID;

@CommandHandlerConfiguration
public class ReaderHandling {

    @CommandHandling
    public UUID register(RegisterReaderCommand command, CommandEventPublisher<UUID> publisher) {
        publisher.publish(new ReaderRegisteredEvent(command.id(), command.name()));
        return command.id();
    }

    @StateRebuilding
    public UUID on(ReaderRegisteredEvent event) {
        return event.id();
    }
}
