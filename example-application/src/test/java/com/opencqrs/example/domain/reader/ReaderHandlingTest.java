/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.domain.reader;

import com.opencqrs.example.domain.reader.api.ReaderRegisteredEvent;
import com.opencqrs.example.domain.reader.api.RegisterReaderCommand;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@CommandHandlingTest
public class ReaderHandlingTest {

    @Test
    public void canRegister(@Autowired CommandHandlingTestFixture<UUID, RegisterReaderCommand, UUID> fixture) {
        var readerId = UUID.randomUUID();
        fixture.givenNothing()
                .when(new RegisterReaderCommand(readerId, "Hugo Tester"))
                .expectSuccessfulExecution()
                .expectSingleEvent(new ReaderRegisteredEvent(readerId, "Hugo Tester"))
                .expectResult(readerId);
    }
}
