/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.metadata;

import com.opencqrs.framework.Book;
import com.opencqrs.framework.BookAddedEvent;
import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandEventPublisher;
import com.opencqrs.framework.command.CommandHandling;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;

@CommandHandlingTest
public class MetaDataPropagatingCommandInterceptorTest {

    record AddBookCommand(String isbn, Map<String, ?> eventMetaData) implements Command {
        @Override
        public String getSubject() {
            return "/books/" + isbn;
        }
    }

    @TestConfiguration
    static class Config {

        @CommandHandling
        public void add(AddBookCommand command, CommandEventPublisher<Book> publisher) {
            publisher.publish(new BookAddedEvent(command.isbn()), command.eventMetaData());
        }
    }

    @ParameterizedTest
    @EnumSource(value = PropagationMode.class, mode = EnumSource.Mode.EXCLUDE, names = "NONE")
    public void propagatesConfiguredKeysToPublishedEventsIfPresent(
            PropagationMode propagationMode, @Autowired CommandHandlingTestFixture<AddBookCommand> fixture) {
        fixture.withAdditionalInterceptors(
                        new MetaDataPropagatingCommandInterceptor(propagationMode, Set.of("correlation-id")))
                .given()
                .nothing()
                .when(new AddBookCommand("4711", Map.of()), Map.of("correlation-id", "abc", "ignored", "x"))
                .succeeds()
                .allEvents()
                .single(e -> e.asserting(a -> a.metaData(Map.of("correlation-id", "abc"))));
    }

    @Test
    public void propagatesNothingIfModeNone(@Autowired CommandHandlingTestFixture<AddBookCommand> fixture) {
        fixture.withAdditionalInterceptors(
                        new MetaDataPropagatingCommandInterceptor(PropagationMode.NONE, Set.of("correlation-id")))
                .given()
                .nothing()
                .when(new AddBookCommand("4711", Map.of()), Map.of("correlation-id", "abc", "ignored", "x"))
                .succeeds()
                .allEvents()
                .single(e -> e.asserting(a -> a.metaData(Map.of())));
    }

    @Test
    public void propagatesWithoutOverridingIfModeKeepIfPresent(
            @Autowired CommandHandlingTestFixture<AddBookCommand> fixture) {
        fixture.withAdditionalInterceptors(new MetaDataPropagatingCommandInterceptor(
                        PropagationMode.KEEP_IF_PRESENT, Set.of("correlation-id")))
                .given()
                .nothing()
                .when(new AddBookCommand("4711", Map.of("correlation-id", "existing")), Map.of("correlation-id", "abc"))
                .succeeds()
                .allEvents()
                .single(e -> e.asserting(a -> a.metaData(Map.of("correlation-id", "existing"))));
    }

    @Test
    public void propagatesOverridingIfModeOverrideIfPresent(
            @Autowired CommandHandlingTestFixture<AddBookCommand> fixture) {
        fixture.withAdditionalInterceptors(new MetaDataPropagatingCommandInterceptor(
                        PropagationMode.OVERRIDE_IF_PRESENT, Set.of("correlation-id")))
                .given()
                .nothing()
                .when(
                        new AddBookCommand("4711", Map.of("correlation-id", "existing")),
                        Map.of("correlation-id", "overridden"))
                .succeeds()
                .allEvents()
                .single(e -> e.asserting(a -> a.metaData(Map.of("correlation-id", "overridden"))));
    }
}
