package com.opencqrs.framework.command.v2;

import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandHandlingTestFixture;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

public interface GivenDsl {
    
    interface Given<I, R> {
        Given<I, R> nothing();
        Given<I, R> time(Instant time);
        Given<I, R> timeDelta(Duration delta);
        Given<I, R> state(I state);
        Given<I, R> events(Object... events);
        Given<I, R> event(Consumer<EventSpecifier<I, R>> event);
        <C extends Command> Given<I, R> command(CommandHandlingTestFixture<I, C, ?> fixture, C command);
        <C extends Command> Given<I, R> command(CommandHandlingTestFixture<I, C, ?> fixture, C command, Map<String, ?> metaData);
        
        Given<I, R> usingSubject(String subject);
        Given<I, R> usingCommandSubject();
        
        ExpectDsl.Initializing<I, R> when(Object command);
        ExpectDsl.Initializing<I, R> when(Object command, Map<String, ?> metaData);
    }
    
    interface EventSpecifier<I, R> {
        EventSpecifier<I, R> payload(Object payload);
        EventSpecifier<I, R> time(Instant time);
        EventSpecifier<I, R> subject(String subject);
        EventSpecifier<I, R> id(String id);
        EventSpecifier<I, R> metaData(Map<String, ?> metaData);
    }
}
