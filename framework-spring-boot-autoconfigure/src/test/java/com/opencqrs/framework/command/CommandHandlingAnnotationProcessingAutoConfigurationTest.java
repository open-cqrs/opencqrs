/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.opencqrs.framework.CqrsFrameworkException;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CommandHandlingAnnotationProcessingAutoConfigurationTest {

    private static final ThreadLocal<Map<String, Object>> handlerResponse = new ThreadLocal<>();

    private static void putInResponse(String key, Object value) {
        handlerResponse.get().put(key, value);
    }

    static class ValidCommandHandlerDefinitions {

        static class AllRequiredArgumentsAndResult {
            @CommandHandling(sourcingMode = SourcingMode.LOCAL)
            public UUID handle(
                    Book book,
                    MyBookCommand command,
                    CommandEventPublisher<Book> eventPublisher,
                    Map<String, ?> metaData) {
                putInResponse("command.id", command.id());
                putInResponse("instance.oldId", book.id());
                putInResponse(
                        "instance.newId",
                        eventPublisher.publish(new BookIdIncreasedEvent()).id());
                putInResponse("meta.key01", metaData.get("key01"));
                return UUID.randomUUID();
            }
        }

        static class OnlyCommandAndInstance {

            @CommandHandling
            public void handle(MyBookCommand command, Book book) {
                putInResponse("command.id", command.id());
                putInResponse("instance.oldId", book.id());
            }
        }

        static class OnlyPublisherAndCommand {

            @CommandHandling
            public void handle(CommandEventPublisher<Book> eventPublisher, MyBookCommand command) {
                putInResponse("command.id", command.id());
                putInResponse(
                        "instance.newId",
                        eventPublisher.publish(new BookIdIncreasedEvent()).id());
            }
        }

        static class ThrowingCheckedException {

            @CommandHandling(sourcingMode = SourcingMode.NONE)
            public void handle(MyBookCommand command, CommandEventPublisher<Book> eventPublisher) throws IOException {
                throw new IOException();
            }
        }

        static class ResolvableAutowiredDependencies {

            @CommandHandling
            public String handle(
                    Book book,
                    MyBookCommand command,
                    @Autowired Callable<String> dependency,
                    CommandEventPublisher<Book> eventPublisher,
                    @Autowired(required = false) Runnable noSuchDependency)
                    throws Exception {
                assertThat(noSuchDependency).isNull();
                putInResponse("command.id", command.id());
                putInResponse("instance.id", book.id());
                return dependency.call();
            }
        }

        static class UnresolvableDependency {

            @CommandHandling
            public void handle(MyBookCommand command, Book book, @Autowired Runnable requiredDependency) {}
        }

        static class ResolvableSpringDependency {

            @CommandHandling
            public String handle(MyBookCommand command, Book book, @Autowired ApplicationContext context) {
                return context.getId();
            }
        }

        static class ResolvableListDependency {

            @CommandHandling
            public String handle(MyBookCommand command, Book book, @Autowired List<Callable<String>> beans)
                    throws Exception {
                return beans.getFirst().call();
            }
        }
    }

    static class InvalidCommandHandlerDefinitions {

        @Scope("prototype")
        static class PrototypeBean {

            @CommandHandling
            public void handle(Book book, MyBookCommand command, CommandEventPublisher<Book> eventPublisher) {}
        }

        static class MissingCommand {

            @CommandHandling
            public void handle(Map<String, ?> metaData, CommandEventPublisher<Book> eventPublisher) {}
        }

        static class MissingInstance {

            @CommandHandling
            public void handle(MyBookCommand command) {}
        }

        static class MissingInstanceGeneric {

            @CommandHandling
            public void handle(MyBookCommand command, CommandEventPublisher eventPublisher) {}
        }

        static class DuplicateCommand {

            @CommandHandling
            public void handle(MyBookCommand c1, Book book, MyBookCommand c2) {}
        }

        static class AmbiguousCommand {

            @CommandHandling
            public void handle(MyBookCommand c1, Book book, Command c2) {}
        }

        static class DuplicateInstance {

            @CommandHandling
            public void handle(Book b1, MyBookCommand command, Book b2) {}
        }

        static class AmbiguousInstance {

            @CommandHandling
            public void handle(MyBookCommand command, Book book, Object o) {}
        }

        static class AutowiredCommand {

            @CommandHandling
            public void handle(@Autowired MyBookCommand command, CommandEventPublisher<MyBookCommand> publisher) {}
        }
    }

    private final Book initialBook = new Book(42L);

    @Mock
    private CommandEventPublisher<Book> commandEventPublisher;

    @BeforeEach
    public void setup() {
        doAnswer(invocationOnMock -> new Book(initialBook.id() + 1))
                .when(commandEventPublisher)
                .publish(any(BookIdIncreasedEvent.class));
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommandHandlingAnnotationProcessingAutoConfiguration.class));

    private Object call(CommandHandler commandHandler) {
        handlerResponse.set(new HashMap<>());
        return ((CommandHandler.ForInstanceAndCommandAndMetaData) commandHandler)
                .handle(initialBook, new MyBookCommand("4711"), Map.of("key01", true), commandEventPublisher);
    }

    @FunctionalInterface
    public interface CommandHandlerDefinitionAssertion<R> {
        void doAssert(CommandHandlerDefinition<Book, MyBookCommand, R> chd, Execution<R> execution);

        @FunctionalInterface
        interface Execution<R> {
            R call(Consumer<Map<String, Object>> handlerResponse);
        }
    }

    interface MyCallable extends Callable<String> {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("validCommandHandlerDefinitions")
    public <R> void validCommandHandlerDefinitionsCreated(
            Class<?> container, CommandHandlerDefinitionAssertion<R> assertion) {
        MyCallable callable = () -> "Hello World";
        runner.withUserConfiguration(container)
                .withBean(MyCallable.class, () -> callable)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(CommandHandlerDefinition.class)
                        .getBean(CommandHandlerDefinition.class)
                        .satisfies(commandHandlerDefinition -> {
                            assertThat(commandHandlerDefinition.instanceClass()).isEqualTo(Book.class);
                            assertThat(commandHandlerDefinition.commandClass()).isEqualTo(MyBookCommand.class);

                            assertion.doAssert(commandHandlerDefinition, execution -> {
                                var result = (R) call(commandHandlerDefinition.handler());
                                execution.accept(handlerResponse.get());
                                return result;
                            });
                        }));
    }

    public static Stream<Arguments> validCommandHandlerDefinitions() {
        return Stream.of(
                Arguments.of(
                        ValidCommandHandlerDefinitions.AllRequiredArgumentsAndResult.class,
                        (CommandHandlerDefinitionAssertion<UUID>) (chd, e) -> {
                            assertThat(chd.sourcingMode()).isEqualTo(SourcingMode.LOCAL);
                            assertThat(e.call(handlerResponse -> {
                                        assertThat(handlerResponse)
                                                .hasSize(4)
                                                .containsEntry("command.id", "4711")
                                                .containsEntry("instance.oldId", 42L)
                                                .containsEntry("instance.newId", 43L)
                                                .containsEntry("meta.key01", true);
                                    }))
                                    .isInstanceOf(UUID.class);
                        }),
                Arguments.of(
                        ValidCommandHandlerDefinitions.OnlyCommandAndInstance.class,
                        (CommandHandlerDefinitionAssertion<Void>) (chd, e) -> {
                            assertThat(chd.sourcingMode()).isEqualTo(SourcingMode.RECURSIVE);
                            assertThat(e.call(handlerResponse -> {
                                        assertThat(handlerResponse)
                                                .hasSize(2)
                                                .containsEntry("command.id", "4711")
                                                .containsEntry("instance.oldId", 42L);
                                    }))
                                    .isNull();
                        }),
                Arguments.of(
                        ValidCommandHandlerDefinitions.OnlyPublisherAndCommand.class,
                        (CommandHandlerDefinitionAssertion<Void>) (chd, e) -> {
                            assertThat(chd.sourcingMode()).isEqualTo(SourcingMode.RECURSIVE);
                            assertThat(e.call(handlerResponse -> {
                                        assertThat(handlerResponse)
                                                .hasSize(2)
                                                .containsEntry("command.id", "4711")
                                                .containsEntry("instance.newId", 43L);
                                    }))
                                    .isNull();
                        }),
                Arguments.of(
                        ValidCommandHandlerDefinitions.ThrowingCheckedException.class,
                        (CommandHandlerDefinitionAssertion<Void>) (chd, e) -> {
                            assertThat(chd.sourcingMode()).isEqualTo(SourcingMode.NONE);
                            assertThatThrownBy(() -> e.call(unused -> {}))
                                    .isInstanceOf(UndeclaredThrowableException.class)
                                    .hasCauseInstanceOf(IOException.class);
                        }),
                Arguments.of(
                        ValidCommandHandlerDefinitions.ResolvableAutowiredDependencies.class,
                        (CommandHandlerDefinitionAssertion<String>) (chd, e) -> {
                            assertThat(chd.sourcingMode()).isEqualTo(SourcingMode.RECURSIVE);
                            assertThat(e.call(handlerResponse -> {
                                        assertThat(handlerResponse)
                                                .hasSize(2)
                                                .containsEntry("command.id", "4711")
                                                .containsEntry("instance.id", 42L);
                                    }))
                                    .isEqualTo("Hello World");
                        }),
                Arguments.of(
                        ValidCommandHandlerDefinitions.UnresolvableDependency.class,
                        (CommandHandlerDefinitionAssertion<Void>) (chd, e) -> {
                            assertThat(chd.sourcingMode()).isEqualTo(SourcingMode.RECURSIVE);
                            assertThatThrownBy(() -> e.call(unused -> {}))
                                    .isInstanceOf(CqrsFrameworkException.NonTransientException.class)
                                    .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
                        }),
                Arguments.of(
                        ValidCommandHandlerDefinitions.ResolvableSpringDependency.class,
                        (CommandHandlerDefinitionAssertion<String>) (chd, e) -> {
                            assertThat(chd.sourcingMode()).isEqualTo(SourcingMode.RECURSIVE);
                            assertThat(e.call(handlerResponse -> {})).isNotBlank();
                        }),
                Arguments.of(
                        ValidCommandHandlerDefinitions.ResolvableListDependency.class,
                        (CommandHandlerDefinitionAssertion<String>) (chd, e) -> {
                            assertThat(chd.sourcingMode()).isEqualTo(SourcingMode.RECURSIVE);
                            assertThat(e.call(handlerResponse -> {})).isEqualTo("Hello World");
                        }));
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                InvalidCommandHandlerDefinitions.PrototypeBean.class,
                InvalidCommandHandlerDefinitions.MissingCommand.class,
                InvalidCommandHandlerDefinitions.MissingInstance.class,
                InvalidCommandHandlerDefinitions.MissingInstanceGeneric.class,
                InvalidCommandHandlerDefinitions.DuplicateCommand.class,
                InvalidCommandHandlerDefinitions.AmbiguousCommand.class,
                InvalidCommandHandlerDefinitions.DuplicateInstance.class,
                InvalidCommandHandlerDefinitions.AmbiguousInstance.class,
                InvalidCommandHandlerDefinitions.AutowiredCommand.class,
            })
    public void invalidCommandHandlerDefinitionsDetected(Class<?> container) {
        runner.withUserConfiguration(container)
                .run(context -> assertThat(context).hasFailed());
    }

    record Book(Long id) {}

    record MyBookCommand(String id) implements Command {
        @Override
        public String getSubject() {
            return "/books/" + id;
        }
    }

    record BookIdIncreasedEvent() {}
}
