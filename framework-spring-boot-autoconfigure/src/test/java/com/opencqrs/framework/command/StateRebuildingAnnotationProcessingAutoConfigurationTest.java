/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.CqrsFrameworkException;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;

public class StateRebuildingAnnotationProcessingAutoConfigurationTest {

    static class ValidStateRebuildingHandlerDefinitions {

        static class OnlyEvent {

            @StateRebuilding
            public Book on(BookAddedEvent event) {
                return new Book(Map.of("event.id", event.id()));
            }
        }

        static class InstanceAndEvent {

            @StateRebuilding
            public Book on(Book book, BookAddedEvent event) {
                return book.with("event.id", event.id());
            }
        }

        static class EventAndSubjectAndInstance {

            @StateRebuilding
            public Book on(BookAddedEvent event, String subject, Book book) {
                return book.with("event.id", event.id()).with("subject", subject);
            }
        }

        static class MetaDataAndInstanceAndEvent {

            @StateRebuilding
            public Book on(Map<String, ?> metaData, Book book, BookAddedEvent event) {
                return book.with("event.id", event.id()).with("meta.key01", metaData.get("meta01"));
            }
        }

        static class InstanceAndEventAndRawEvent {

            @StateRebuilding
            public Book on(Book book, BookAddedEvent event, Event raw) {
                return book.with("event.id", event.id()).with("raw.id", raw.id());
            }
        }

        static class AllFrameworkParameters {

            @StateRebuilding
            public Book on(Book book, BookAddedEvent event, Event raw, String subject, Map<String, ?> metaData) {
                return book.with("event.id", event.id())
                        .with("raw.id", raw.id())
                        .with("subject", subject)
                        .with("meta.key01", metaData.get("meta01"));
            }
        }

        static class ThrowingCheckedException {

            @StateRebuilding
            public Book on(BookAddedEvent event, Event raw) throws IOException {
                throw new IOException();
            }
        }

        static class ResolvableAutowiredDependencies {
            @StateRebuilding
            public Book on(
                    @Autowired Callable<String> dependency,
                    BookAddedEvent event,
                    Book book,
                    @Autowired(required = false) Runnable noSuchDependency)
                    throws Exception {
                assertThat(noSuchDependency).isNull();
                return book.with("event.id", event.id()).with("callable", dependency.call());
            }
        }

        static class UnresolvableDependency {

            @StateRebuilding
            public Book on(@Autowired Runnable requiredDependency, BookAddedEvent event) {
                throw new AssertionError("must not be reached");
            }
        }

        static class ResolvableSpringDependency {

            @StateRebuilding
            public Book on(@Autowired ApplicationContext context, BookAddedEvent event, Book book) {
                return book.with("event.id", event.id()).with("context.id", context.getId());
            }
        }

        static class ResolvableListDependency {

            @StateRebuilding
            public Book on(@Autowired List<Callable<String>> beans, BookAddedEvent event, Book book) throws Exception {
                return book.with("event.id", event.id())
                        .with("callable", beans.getFirst().call());
            }
        }
    }

    static class InvalidStateRebuildingHandlerDefinitions {

        @Scope("prototype")
        static class PrototypeBean {

            @StateRebuilding
            public Book on(BookAddedEvent event) {
                return null;
            }
        }

        static class MissingReturnType {

            @StateRebuilding
            public void on(BookAddedEvent event) {}
        }

        static class PrimitiveReturnType {

            @StateRebuilding
            public int on(BookAddedEvent event) {
                return 42;
            }
        }

        static class MissingEventClass {

            @StateRebuilding
            public Book on(Book book, Event event, Map<String, ?> metaData, String subject) {
                return null;
            }
        }

        static class UnknownParameter {

            @StateRebuilding
            public Book on(BookAddedEvent event, Object o) {
                return null;
            }
        }

        static class DuplicateEventParameter {

            @StateRebuilding
            public Book on(BookAddedEvent e1, Book book, BookAddedEvent e2) {
                return null;
            }
        }

        static class DuplicateInstanceParameter {

            @StateRebuilding
            public Book on(Book b1, BookAddedEvent event, Book b2) {
                return null;
            }
        }

        static class AutowireEvent {

            @StateRebuilding
            public Book on(@Autowired BookAddedEvent event) {
                return null;
            }
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StateRebuildingAnnotationProcessingAutoConfiguration.class));

    private Book call(StateRebuildingHandler stateRebuildingHandler) {
        return (Book) ((StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent) stateRebuildingHandler)
                .on(
                        new Book(Map.of("empty", true)),
                        new BookAddedEvent("4711"),
                        Map.of("meta01", 42L),
                        "/book/4711",
                        new Event(
                                "source",
                                "/book/4711",
                                "type",
                                Map.of(),
                                "1.0",
                                "id001",
                                Instant.now(),
                                "application/json",
                                "hash",
                                "predecessor"));
    }

    @FunctionalInterface
    public interface StateRebuildingHandlerDefinitionAssertion<E> {

        void doAssert(StateRebuildingHandlerDefinition<Book, E> srhd, Execution execution);

        @FunctionalInterface
        interface Execution {
            Book call();
        }
    }

    interface MyCallable extends Callable<String> {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("validStateRebuildingHandlerDefinitions")
    public <E> void validStateRebuildingHandlerDefinitionsCreated(
            Class<?> container, StateRebuildingHandlerDefinitionAssertion<E> assertion) {
        MyCallable callable = () -> "Hello World";
        runner.withUserConfiguration(container)
                .withBean(MyCallable.class, () -> callable)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(StateRebuildingHandlerDefinition.class)
                        .getBean(StateRebuildingHandlerDefinition.class)
                        .satisfies(stateRebuildingHandlerDefinition -> {
                            assertThat(stateRebuildingHandlerDefinition.instanceClass())
                                    .isEqualTo(Book.class);
                            assertThat(stateRebuildingHandlerDefinition.eventClass())
                                    .isEqualTo(BookAddedEvent.class);

                            assertion.doAssert(
                                    stateRebuildingHandlerDefinition,
                                    () -> call(stateRebuildingHandlerDefinition.handler()));
                        }));
    }

    public static Stream<Arguments> validStateRebuildingHandlerDefinitions() {
        return Stream.of(
                Arguments.of(
                        ValidStateRebuildingHandlerDefinitions.OnlyEvent.class,
                        (StateRebuildingHandlerDefinitionAssertion<BookAddedEvent>) (srhd, e) -> {
                            assertThat(e.call().contents()).hasSize(1).containsEntry("event.id", "4711");
                        }),
                Arguments.of(
                        ValidStateRebuildingHandlerDefinitions.InstanceAndEvent.class,
                        (StateRebuildingHandlerDefinitionAssertion<BookAddedEvent>) (srhd, e) -> {
                            assertThat(e.call().contents())
                                    .hasSize(2)
                                    .containsEntry("empty", true)
                                    .containsEntry("event.id", "4711");
                        }),
                Arguments.of(
                        ValidStateRebuildingHandlerDefinitions.EventAndSubjectAndInstance.class,
                        (StateRebuildingHandlerDefinitionAssertion<BookAddedEvent>) (srhd, e) -> {
                            assertThat(e.call().contents())
                                    .hasSize(3)
                                    .containsEntry("empty", true)
                                    .containsEntry("event.id", "4711")
                                    .containsEntry("subject", "/book/4711");
                        }),
                Arguments.of(
                        ValidStateRebuildingHandlerDefinitions.MetaDataAndInstanceAndEvent.class,
                        (StateRebuildingHandlerDefinitionAssertion<BookAddedEvent>) (srhd, e) -> {
                            assertThat(e.call().contents())
                                    .hasSize(3)
                                    .containsEntry("empty", true)
                                    .containsEntry("event.id", "4711")
                                    .containsEntry("meta.key01", 42L);
                        }),
                Arguments.of(
                        ValidStateRebuildingHandlerDefinitions.InstanceAndEventAndRawEvent.class,
                        (StateRebuildingHandlerDefinitionAssertion<BookAddedEvent>) (srhd, e) -> {
                            assertThat(e.call().contents())
                                    .hasSize(3)
                                    .containsEntry("empty", true)
                                    .containsEntry("event.id", "4711")
                                    .containsEntry("raw.id", "id001");
                        }),
                Arguments.of(
                        ValidStateRebuildingHandlerDefinitions.AllFrameworkParameters.class,
                        (StateRebuildingHandlerDefinitionAssertion<BookAddedEvent>) (srhd, e) -> {
                            assertThat(e.call().contents())
                                    .hasSize(5)
                                    .containsEntry("empty", true)
                                    .containsEntry("event.id", "4711")
                                    .containsEntry("meta.key01", 42L)
                                    .containsEntry("subject", "/book/4711")
                                    .containsEntry("meta.key01", 42L);
                        }),
                Arguments.of(
                        ValidStateRebuildingHandlerDefinitions.ThrowingCheckedException.class,
                        (StateRebuildingHandlerDefinitionAssertion<BookAddedEvent>) (srhd, e) -> {
                            assertThatThrownBy(e::call)
                                    .isInstanceOf(UndeclaredThrowableException.class)
                                    .hasCauseInstanceOf(IOException.class);
                        }),
                Arguments.of(
                        ValidStateRebuildingHandlerDefinitions.ResolvableAutowiredDependencies.class,
                        (StateRebuildingHandlerDefinitionAssertion<BookAddedEvent>) (srhd, e) -> {
                            assertThat(e.call().contents())
                                    .hasSize(3)
                                    .containsEntry("empty", true)
                                    .containsEntry("event.id", "4711")
                                    .containsEntry("callable", "Hello World");
                        }),
                Arguments.of(
                        ValidStateRebuildingHandlerDefinitions.UnresolvableDependency.class,
                        (StateRebuildingHandlerDefinitionAssertion<BookAddedEvent>) (srhd, e) -> {
                            assertThatThrownBy(e::call)
                                    .isInstanceOf(CqrsFrameworkException.NonTransientException.class)
                                    .hasCauseInstanceOf(NoSuchBeanDefinitionException.class);
                        }),
                Arguments.of(
                        ValidStateRebuildingHandlerDefinitions.ResolvableSpringDependency.class,
                        (StateRebuildingHandlerDefinitionAssertion<BookAddedEvent>) (srhd, e) -> {
                            assertThat(e.call().contents())
                                    .hasSize(3)
                                    .containsEntry("empty", true)
                                    .containsEntry("event.id", "4711")
                                    .hasEntrySatisfying("context.id", id -> assertThat(id)
                                            .isInstanceOf(String.class)
                                            .isNotNull());
                        }),
                Arguments.of(
                        ValidStateRebuildingHandlerDefinitions.ResolvableListDependency.class,
                        (StateRebuildingHandlerDefinitionAssertion<BookAddedEvent>) (srhd, e) -> {
                            assertThat(e.call().contents())
                                    .hasSize(3)
                                    .containsEntry("empty", true)
                                    .containsEntry("event.id", "4711")
                                    .containsEntry("callable", "Hello World");
                        }));
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                InvalidStateRebuildingHandlerDefinitions.PrototypeBean.class,
                InvalidStateRebuildingHandlerDefinitions.MissingReturnType.class,
                InvalidStateRebuildingHandlerDefinitions.PrimitiveReturnType.class,
                InvalidStateRebuildingHandlerDefinitions.MissingEventClass.class,
                InvalidStateRebuildingHandlerDefinitions.UnknownParameter.class,
                InvalidStateRebuildingHandlerDefinitions.DuplicateEventParameter.class,
                InvalidStateRebuildingHandlerDefinitions.DuplicateInstanceParameter.class,
                InvalidStateRebuildingHandlerDefinitions.AutowireEvent.class,
            })
    public void invalidStateRebuildingHandlerDefinitionsDetected(Class<?> container) {
        runner.withUserConfiguration(container)
                .run(context -> assertThat(context).hasFailed());
    }

    record Book(Map<String, Object> contents) {
        public Book with(String key, Object value) {
            Map<String, Object> map = new HashMap<>(contents);
            map.put(key, value);
            return new Book(map);
        }
    }

    record BookAddedEvent(String id) {}
}
