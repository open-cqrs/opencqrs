/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

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
import java.util.function.Consumer;
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

public class EventHandlingAnnotationProcessingAutoConfigurationTest {

    private static final ThreadLocal<Map<String, Object>> handlerResponse = new ThreadLocal<>();

    private static void putInResponse(String key, Object value) {
        handlerResponse.get().put(key, value);
    }

    static class ValidEventHandlerDefinitions {

        static class TypedEventOnly {

            @EventHandling("group-1")
            public void on(BookAddedEvent event) {
                putInResponse("event.id", event.id());
            }
        }

        static class JavaLangObjectEvent {

            @EventHandling("group-2")
            public void on(Object event) {
                putInResponse("object.class", event.getClass());
            }
        }

        static class RawEventOnly {

            @EventHandling("group-3")
            public void on(Event raw) {
                putInResponse("raw.id", raw.id());
            }
        }

        static class MetaDataOnly {

            @EventHandling("group-4")
            public void on(Map<String, ?> metaData) {
                putInResponse("meta.key01", metaData.get("meta01"));
            }
        }

        static class RawAndEventAndMetaData {

            @EventHandling("group-5")
            public void on(Event raw, BookAddedEvent event, Map<String, ?> metaData) {
                putInResponse("raw.id", raw.id());
                putInResponse("event.id", event.id());
                putInResponse("meta.key01", metaData.get("meta01"));
            }
        }

        static class ThrowingCheckedException {

            @EventHandling("group-6")
            public void on(Map<String, ?> metaData, Object event) throws IOException {
                throw new IOException();
            }
        }

        static class ResolvableAutowiredDependencies {

            @EventHandling("group-7")
            public void handle(
                    @Autowired Callable<String> dependency,
                    BookAddedEvent event,
                    @Autowired(required = false) Runnable noSuchDependency)
                    throws Exception {
                assertThat(noSuchDependency).isNull();
                putInResponse("event.id", event.id());
                putInResponse("callable", dependency.call());
            }
        }

        static class UnresolvableDependency {

            @EventHandling("group-8")
            public void handle(BookAddedEvent event, @Autowired Runnable requiredDependency) {}
        }

        static class ResolvableSpringDependency {

            @EventHandling("group-9")
            public void handle(@Autowired ApplicationContext context, Event rawEvent) {
                putInResponse("context.id", context.getId());
                putInResponse("raw.id", rawEvent.id());
            }
        }

        static class ResolvableListDependency {

            @EventHandling("group-10")
            public void handle(BookAddedEvent event, @Autowired List<Callable<String>> beans) throws Exception {
                putInResponse("event.id", event.id());
                putInResponse("callable", beans.getFirst().call());
            }
        }
    }

    static class InvalidEventHandlerDefinitions {

        @Scope("prototype")
        static class PrototypeBean {

            @EventHandling("irrelevant")
            public void on(Object event) {}
        }

        static class InvalidOrMissingGroupId {

            @EventHandling
            public void on(Object event) {}
        }

        static class NonVoidReturnType {

            @EventHandling("irrelevant")
            public String on(BookAddedEvent event) {
                return null;
            }
        }

        static class NoParameter {

            @EventHandling("irrelevant")
            public void on() {}
        }

        static class AmbiguousEventParameter {

            @EventHandling("irrelevant")
            public void on(BookAddedEvent e1, Event raw, Object e2) {}
        }

        static class DuplicateFrameworkParameter {

            @EventHandling("irrelevant")
            public void on(Event r1, BookAddedEvent event, Event r2) {}
        }

        static class AutowiredEvent {

            @EventHandling("irrelevant")
            public void handle(@Autowired BookAddedEvent event) {}
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventHandlingAnnotationProcessingAutoConfiguration.class));

    private Map<String, Object> call(EventHandler eventHandler) {
        Instant time = Instant.now();
        handlerResponse.set(new HashMap<>());
        ((EventHandler.ForObjectAndMetaDataAndRawEvent) eventHandler)
                .handle(
                        new BookAddedEvent("4711"),
                        Map.of("meta01", 42L),
                        new Event(
                                "source",
                                "/book/4711",
                                "type",
                                Map.of(),
                                "1.0",
                                "id001",
                                time,
                                "application/json",
                                "hash",
                                "predecessor",
                                time.toString()));
        return handlerResponse.get();
    }

    @FunctionalInterface
    public interface EventHandlerDefinitionAssertion<E> {
        void doAssert(EventHandlerDefinition<E> ehd, Execution execution);

        @FunctionalInterface
        interface Execution {
            void call(Consumer<Map<String, Object>> handlerResponse);
        }
    }

    interface MyCallable extends Callable<String> {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("validEventHandlerDefinitions")
    public <E> void validEventHandlerDefinitionsCreated(
            Class<?> container, EventHandlerDefinitionAssertion<E> assertion) {
        MyCallable callable = () -> "Hello World";
        runner.withUserConfiguration(container)
                .withBean(MyCallable.class, () -> callable)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(EventHandlerDefinition.class)
                        .getBean(EventHandlerDefinition.class)
                        .satisfies(eventHandlerDefinition -> {
                            assertThat(eventHandlerDefinition.group()).startsWith("group-");
                            assertion.doAssert(eventHandlerDefinition, execution -> {
                                call(eventHandlerDefinition.handler());
                                execution.accept(handlerResponse.get());
                            });
                        }));
    }

    public static Stream<Arguments> validEventHandlerDefinitions() {
        return Stream.of(
                Arguments.of(
                        ValidEventHandlerDefinitions.TypedEventOnly.class,
                        (EventHandlerDefinitionAssertion<BookAddedEvent>) (ehd, e) -> {
                            e.call(handlerResponse ->
                                    assertThat(handlerResponse).hasSize(1).containsEntry("event.id", "4711"));
                        }),
                Arguments.of(
                        ValidEventHandlerDefinitions.JavaLangObjectEvent.class,
                        (EventHandlerDefinitionAssertion<BookAddedEvent>) (ehd, e) -> {
                            e.call(handlerResponse -> assertThat(handlerResponse)
                                    .hasSize(1)
                                    .containsEntry("object.class", BookAddedEvent.class));
                        }),
                Arguments.of(
                        ValidEventHandlerDefinitions.RawEventOnly.class,
                        (EventHandlerDefinitionAssertion<BookAddedEvent>) (ehd, e) -> {
                            e.call(handlerResponse ->
                                    assertThat(handlerResponse).hasSize(1).containsEntry("raw.id", "id001"));
                        }),
                Arguments.of(
                        ValidEventHandlerDefinitions.MetaDataOnly.class,
                        (EventHandlerDefinitionAssertion<BookAddedEvent>) (ehd, e) -> {
                            e.call(handlerResponse ->
                                    assertThat(handlerResponse).hasSize(1).containsEntry("meta.key01", 42L));
                        }),
                Arguments.of(
                        ValidEventHandlerDefinitions.RawAndEventAndMetaData.class,
                        (EventHandlerDefinitionAssertion<BookAddedEvent>) (ehd, e) -> {
                            e.call(handlerResponse -> assertThat(handlerResponse)
                                    .hasSize(3)
                                    .containsEntry("event.id", "4711")
                                    .containsEntry("raw.id", "id001")
                                    .containsEntry("meta.key01", 42L));
                        }),
                Arguments.of(
                        ValidEventHandlerDefinitions.ThrowingCheckedException.class,
                        (EventHandlerDefinitionAssertion<BookAddedEvent>) (ehd, e) -> {
                            assertThatThrownBy(() -> e.call(ignored -> {}))
                                    .isInstanceOf(UndeclaredThrowableException.class)
                                    .hasCauseInstanceOf(IOException.class);
                        }),
                Arguments.of(
                        ValidEventHandlerDefinitions.ResolvableAutowiredDependencies.class,
                        (EventHandlerDefinitionAssertion<BookAddedEvent>) (ehd, e) -> {
                            e.call(handlerResponse -> assertThat(handlerResponse)
                                    .hasSize(2)
                                    .containsEntry("event.id", "4711")
                                    .containsEntry("callable", "Hello World"));
                        }),
                Arguments.of(
                        ValidEventHandlerDefinitions.UnresolvableDependency.class,
                        (EventHandlerDefinitionAssertion<BookAddedEvent>) (ehd, e) -> {
                            assertThatThrownBy(() -> e.call(ignored -> {}))
                                    .isInstanceOf(CqrsFrameworkException.NonTransientException.class)
                                    .hasCauseInstanceOf(NoSuchBeanDefinitionException.class);
                        }),
                Arguments.of(
                        ValidEventHandlerDefinitions.ResolvableSpringDependency.class,
                        (EventHandlerDefinitionAssertion<BookAddedEvent>) (ehd, e) -> {
                            e.call(handlerResponse -> assertThat(handlerResponse)
                                    .hasSize(2)
                                    .containsEntry("raw.id", "id001")
                                    .hasEntrySatisfying("context.id", id -> assertThat(id)
                                            .isInstanceOf(String.class)
                                            .isNotNull()));
                        }),
                Arguments.of(
                        ValidEventHandlerDefinitions.ResolvableListDependency.class,
                        (EventHandlerDefinitionAssertion<BookAddedEvent>) (ehd, e) -> {
                            e.call(handlerResponse -> assertThat(handlerResponse)
                                    .hasSize(2)
                                    .containsEntry("event.id", "4711")
                                    .containsEntry("callable", "Hello World"));
                        }));
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                InvalidEventHandlerDefinitions.PrototypeBean.class,
                InvalidEventHandlerDefinitions.InvalidOrMissingGroupId.class,
                InvalidEventHandlerDefinitions.NonVoidReturnType.class,
                InvalidEventHandlerDefinitions.NoParameter.class,
                InvalidEventHandlerDefinitions.AmbiguousEventParameter.class,
                InvalidEventHandlerDefinitions.DuplicateFrameworkParameter.class,
                InvalidEventHandlerDefinitions.AutowiredEvent.class,
            })
    public void invalidEventHandlerDefinitionsDetected(Class<?> container) {
        runner.withUserConfiguration(container)
                .run(context -> assertThat(context).hasFailed());
    }

    record BookAddedEvent(String id) {}
}
