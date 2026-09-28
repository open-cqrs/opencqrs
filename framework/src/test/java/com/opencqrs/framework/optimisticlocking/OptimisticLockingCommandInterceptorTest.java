/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.optimisticlocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.opencqrs.framework.Book;
import com.opencqrs.framework.BookAddedEvent;
import com.opencqrs.framework.command.*;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@CommandHandlingTest
@Import(OptimisticLockingCommandInterceptor.class)
public class OptimisticLockingCommandInterceptorTest {

    record TouchBookCommand(String isbn, EventIdExpectation expectedEventId) implements EventIdExpectingCommand {
        @Override
        public String getSubject() {
            return "/books/" + isbn;
        }
    }

    @TestConfiguration
    static class Handlers {

        @StateRebuilding
        public Book on(BookAddedEvent event) {
            return null;
        }

        @CommandHandling
        public void touch(Book state, TouchBookCommand command) {}
    }

    private static ExpectDsl.Outcome outcome(
            @Nullable String sourcedId,
            EventIdExpectation expectation,
            CommandHandlingTestFixture<TouchBookCommand> fixture) {
        TouchBookCommand command = new TouchBookCommand("4711", expectation);
        if (sourcedId != null) {
            return fixture.given()
                    .event(e -> e.id(sourcedId).payload(new BookAddedEvent("4711")))
                    .when(command);
        }
        return fixture.given().nothing().when(command);
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("proceeding")
    public void proceeds(
            @Nullable String sourcedId,
            EventIdExpectation expectation,
            String label,
            @Autowired CommandHandlingTestFixture<TouchBookCommand> fixture) {
        outcome(sourcedId, expectation, fixture).succeeds();
    }

    static Stream<Arguments> proceeding() {
        return Stream.of(
                arguments(null, new EventIdExpectation.None(), "none: pristine"),
                arguments("5", new EventIdExpectation.None(), "none: ignores head"),
                arguments("5", new EventIdExpectation.Exactly("5"), "exactly: equal head"),
                arguments("5", new EventIdExpectation.AtMost("5"), "at-most: equal head"),
                arguments("2", new EventIdExpectation.AtMost("5"), "at-most: older head (narrower scope)"),
                arguments(null, new EventIdExpectation.AtMost("5"), "at-most: pristine head"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("conflicting")
    public void conflicts(
            @Nullable String sourcedId,
            EventIdExpectation expectation,
            String label,
            @Autowired CommandHandlingTestFixture<TouchBookCommand> fixture) {
        outcome(sourcedId, expectation, fixture).fails().throwsSatisfying((OptimisticLockingException e) -> {
            assertThat(e.getSubject()).isEqualTo("/books/4711");
            assertThat(e.getExpectation()).isEqualTo(expectation);
            assertThat(e.getActualEventId()).isEqualTo(sourcedId);
        });
    }

    static Stream<Arguments> conflicting() {
        return Stream.of(
                arguments("5", new EventIdExpectation.Exactly("2"), "exactly: different head"),
                arguments(null, new EventIdExpectation.Exactly("5"), "exactly: pristine head"),
                arguments("6", new EventIdExpectation.AtMost("5"), "at-most: newer head"));
    }
}
