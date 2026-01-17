/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.v2;

import com.opencqrs.framework.command.Command;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

public interface GivenDsl {

    interface Given {
        Given nothing();

        Given time(Instant time);

        Given timeDelta(Duration delta);

        Given state(Object state);

        Given events(Object... events);

        Given event(Consumer<EventSpecifier> event);

        <C extends Command> Given command(CommandHandlingTestFixture<C> fixture, C command);

        <C extends Command> Given command(CommandHandlingTestFixture<C> fixture, C command, Map<String, ?> metaData);

        Given usingSubject(String subject);

        Given usingCommandSubject();

        ExpectDsl.Initializing when(Object command);

        ExpectDsl.Initializing when(Object command, Map<String, ?> metaData);
    }

    interface EventSpecifier {
        EventSpecifier payload(Object payload);

        EventSpecifier time(Instant time);

        EventSpecifier subject(String subject);

        EventSpecifier id(String id);

        EventSpecifier metaData(Map<String, ?> metaData);
    }
}
