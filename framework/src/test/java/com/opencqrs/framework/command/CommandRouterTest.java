/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.esdb.client.Client;
import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.Option;
import com.opencqrs.esdb.client.Precondition;
import com.opencqrs.framework.*;
import com.opencqrs.framework.command.cache.NoStateRebuildingCache;
import com.opencqrs.framework.metadata.PropagationMode;
import com.opencqrs.framework.persistence.CapturedEvent;
import com.opencqrs.framework.persistence.EventReader;
import com.opencqrs.framework.persistence.ImmediateEventPublisher;
import com.opencqrs.framework.serialization.EventData;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.serialization.JacksonEventDataMarshaller;
import com.opencqrs.framework.types.ClassNameEventTypeResolver;
import com.opencqrs.framework.types.EventTypeResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CommandRouterTest {

    @Mock
    private Client client;

    @Mock
    private ImmediateEventPublisher immediateEventPublisher;

    private final EventDataMarshaller eventDataMarshaller = new JacksonEventDataMarshaller(new ObjectMapper());

    private final EventTypeResolver eventTypeResolver =
            new ClassNameEventTypeResolver(CommandRouterTest.class.getClassLoader());

    private final EventReader eventReader = (clientRequestor, rawConsumer) -> {
        clientRequestor.request(
                client,
                raw -> rawConsumer.accept(
                        upcastedConsumer -> upcastedConsumer.accept(
                                new EventReader.UpcastedCallback() {

                                    @Override
                                    public Class<?> getEventJavaClass() {
                                        return eventTypeResolver.getJavaClass(raw.type());
                                    }

                                    @Override
                                    public void convert(BiConsumer<Map<String, ?>, Object> eventConsumer) {
                                        EventData<?> deserialized =
                                                eventDataMarshaller.deserialize(raw.data(), getEventJavaClass());
                                        eventConsumer.accept(deserialized.metaData(), deserialized.payload());
                                    }
                                },
                                // upcasting is simulated by copying the event using a different type
                                new Event(
                                        raw.source(),
                                        raw.subject(),
                                        "upcasted",
                                        raw.data(),
                                        raw.specVersion(),
                                        raw.id(),
                                        raw.time(),
                                        raw.dataContentType(),
                                        raw.hash(),
                                        raw.predecessorHash())),
                        raw));
    };

    @Test
    public void commandWithMetaDataPropagationSuccessfullyExecutedWithResultAndEventsApplied() {
        var sourcedEvent1 = new BookAddedEvent("4711");
        var sourcedEvent2 = new BookPageDamagedEvent(42);

        var command = new BorrowBookCommand("4711");
        var commandMetaData = Map.of(
                "user", "hugo",
                "propagated01", true,
                "not-propagated", false);
        var expectedResult = UUID.randomUUID();

        var publishedEvent1 = new BookBorrowedEvent();
        var publishedEvent2 = new BookPageDamagedEvent(205);
        var publishedEvent3 = new BookPageDamagedEvent(3456);

        doAnswer(invocation -> {
                    Consumer<Event> consumer = invocation.getArgument(2);
                    consumer.accept(new Event(
                            "test",
                            command.getSubject(),
                            eventTypeResolver.getEventType(sourcedEvent1.getClass()),
                            eventDataMarshaller.serialize(new EventData<>(Map.of("purpose", "testing"), sourcedEvent1)),
                            "1.0",
                            "2345",
                            Instant.now(),
                            "application/json",
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString()));
                    consumer.accept(new Event(
                            "test",
                            command.getSubject() + "/pages/42",
                            eventTypeResolver.getEventType(sourcedEvent2.getClass()),
                            eventDataMarshaller.serialize(new EventData<>(Map.of(), sourcedEvent2)),
                            "1.0",
                            "89437534",
                            Instant.now(),
                            "application/json",
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString()));
                    return null;
                })
                .when(client)
                .read(eq(command.getSubject()), eq(Set.of(new Option.Recursive())), any());

        List stateRebuildingHandlerDefinitions = List.of(
                new StateRebuildingHandlerDefinition<>(
                        Book.class,
                        BookAddedEvent.class,
                        (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<Book, BookAddedEvent>)
                                (book, event, metaData, subject, raw) -> {
                                    assertThat(metaData).hasEntrySatisfying("purpose", o -> assertThat(o)
                                            .isEqualTo("testing"));
                                    assertThat(subject).isEqualTo(command.getSubject());
                                    assertThat(raw.id()).isEqualTo("2345");
                                    return new Book(event.isbn(), false);
                                }),
                new StateRebuildingHandlerDefinition<>(
                        Book.class, BookBorrowedEvent.class, (StateRebuildingHandler.FromObject<
                                        Book, BookBorrowedEvent>)
                                (book, event) -> new Book(book.isbn(), true)));

        CommandHandlerDefinition<Book, BorrowBookCommand, UUID> chd = new CommandHandlerDefinition<>(
                Book.class, BorrowBookCommand.class, (CommandHandler.ForInstanceAndCommandAndMetaData<
                                Book, BorrowBookCommand, UUID>)
                        (book, cmd, metaData, eventPublisher) -> {
                            assertThat(book).isEqualTo(new Book("4711", false));
                            assertThat(cmd).isEqualTo(command);
                            assertThat(metaData).isEqualTo(commandMetaData);

                            eventPublisher.publish(publishedEvent1);
                            eventPublisher.publishRelative("pages/" + publishedEvent2.page(), publishedEvent2);
                            eventPublisher.publish(
                                    "/absolute",
                                    publishedEvent3,
                                    Map.of("id", 42),
                                    List.of(new Precondition.SubjectIsOnEventId("/absolute", "0815")));
                            return expectedResult;
                        });

        CommandRouter subject = new CommandRouter(
                eventReader,
                immediateEventPublisher,
                List.of(chd),
                stateRebuildingHandlerDefinitions,
                new NoStateRebuildingCache(),
                PropagationMode.KEEP_IF_PRESENT,
                Set.of("propagated01"));

        UUID result = subject.send(command, commandMetaData);

        assertThat(result).isEqualTo(expectedResult);

        verify(immediateEventPublisher)
                .publish(
                        List.of(
                                new CapturedEvent(
                                        command.getSubject(), publishedEvent1, Map.of("propagated01", true), List.of()),
                                new CapturedEvent(
                                        command.getSubject() + "/pages/" + publishedEvent2.page(),
                                        publishedEvent2,
                                        Map.of("propagated01", true),
                                        List.of()),
                                new CapturedEvent(
                                        "/absolute",
                                        publishedEvent3,
                                        Map.of("id", 42, "propagated01", true),
                                        List.of(new Precondition.SubjectIsOnEventId("/absolute", "0815")))),
                        List.of(
                                new Precondition.SubjectIsPristine(
                                        command.getSubject() + "/pages/" + publishedEvent2.page()),
                                new Precondition.SubjectIsOnEventId(command.getSubject(), "2345"),
                                new Precondition.SubjectIsOnEventId(command.getSubject() + "/pages/42", "89437534"),
                                new Precondition.SubjectIsOnEventId("/absolute", "0815")));
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                CommandHandler.ForCommand.class,
                CommandHandler.ForInstanceAndCommand.class,
                CommandHandler.ForInstanceAndCommandAndMetaData.class,
            })
    public void commandHandlerVariantExecuted(Class<? extends CommandHandler> clazz) {
        CommandHandler commandHandler = mock(clazz);
        CommandHandlerDefinition<Object, AddBookCommand, Void> chd =
                new CommandHandlerDefinition<>(Object.class, AddBookCommand.class, commandHandler);

        CommandRouter subject = new CommandRouter(eventReader, immediateEventPublisher, List.of(chd), List.of());

        var cmd = new AddBookCommand("isbn");
        var metaData = Map.of("key01", true);

        subject.send(cmd, metaData);

        switch (commandHandler) {
            case CommandHandler.ForCommand handler -> verify(handler).handle(eq(cmd), any());
            case CommandHandler.ForInstanceAndCommand handler -> verify(handler).handle(eq(null), eq(cmd), any());
            case CommandHandler.ForInstanceAndCommandAndMetaData handler -> verify(handler)
                    .handle(eq(null), eq(cmd), eq(metaData), any());
        }
    }

    @ParameterizedTest
    @EnumSource(value = Command.SubjectCondition.class, mode = EnumSource.Mode.EXCLUDE, names = "NONE")
    public void subjectConditionSuccessful(Command.SubjectCondition condition) {
        var sourcedEvent = new BookAddedEvent("4711");

        Command command = new Command() {
            @Override
            public String getSubject() {
                return "/subject";
            }

            @Override
            public SubjectCondition getSubjectCondition() {
                return condition;
            }
        };

        switch (condition) {
            case EXISTS -> doAnswer(invocation -> {
                        Consumer<Event> consumer = invocation.getArgument(2);
                        consumer.accept(new Event(
                                "test",
                                command.getSubject(),
                                eventTypeResolver.getEventType(sourcedEvent.getClass()),
                                eventDataMarshaller.serialize(
                                        new EventData<>(Map.of("purpose", "testing"), sourcedEvent)),
                                "1.0",
                                "2345",
                                Instant.now(),
                                "application/json",
                                UUID.randomUUID().toString(),
                                UUID.randomUUID().toString()));
                        return null;
                    })
                    .when(client)
                    .read(eq(command.getSubject()), eq(Set.of(new Option.Recursive())), any());

            case PRISTINE -> doAnswer(invocation -> {
                        Consumer<Event> consumer = invocation.getArgument(2);
                        consumer.accept(new Event(
                                "test",
                                command.getSubject() + "/child/42",
                                eventTypeResolver.getEventType(sourcedEvent.getClass()),
                                eventDataMarshaller.serialize(
                                        new EventData<>(Map.of("purpose", "testing"), sourcedEvent)),
                                "1.0",
                                "2345",
                                Instant.now(),
                                "application/json",
                                UUID.randomUUID().toString(),
                                UUID.randomUUID().toString()));
                        return null;
                    })
                    .when(client)
                    .read(eq(command.getSubject()), eq(Set.of(new Option.Recursive())), any());
        }

        CommandHandlerDefinition<Book, ? extends Command, Void> chd =
                new CommandHandlerDefinition<>(Book.class, command.getClass(), (CommandHandler.ForInstanceAndCommand)
                        (book, cmd, eventPublisher) -> null);

        CommandRouter subject = new CommandRouter(eventReader, immediateEventPublisher, List.of(chd), List.of());

        assertThatCode(() -> subject.send(command)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = Command.SubjectCondition.class, mode = EnumSource.Mode.EXCLUDE, names = "NONE")
    public void subjectConditionViolated(Command.SubjectCondition condition) {
        var sourcedEvent = new BookAddedEvent("4711");

        Command command = new Command() {
            @Override
            public String getSubject() {
                return "/subject";
            }

            @Override
            public SubjectCondition getSubjectCondition() {
                return condition;
            }
        };

        switch (condition) {
            case EXISTS -> doAnswer(invocation -> {
                        Consumer<Event> consumer = invocation.getArgument(2);
                        consumer.accept(new Event(
                                "test",
                                command.getSubject() + "/child/42",
                                eventTypeResolver.getEventType(sourcedEvent.getClass()),
                                eventDataMarshaller.serialize(
                                        new EventData<>(Map.of("purpose", "testing"), sourcedEvent)),
                                "1.0",
                                "2345",
                                Instant.now(),
                                "application/json",
                                UUID.randomUUID().toString(),
                                UUID.randomUUID().toString()));
                        return null;
                    })
                    .when(client)
                    .read(eq(command.getSubject()), eq(Set.of(new Option.Recursive())), any());

            case PRISTINE -> doAnswer(invocation -> {
                        Consumer<Event> consumer = invocation.getArgument(2);
                        consumer.accept(new Event(
                                "test",
                                command.getSubject(),
                                eventTypeResolver.getEventType(sourcedEvent.getClass()),
                                eventDataMarshaller.serialize(
                                        new EventData<>(Map.of("purpose", "testing"), sourcedEvent)),
                                "1.0",
                                "2345",
                                Instant.now(),
                                "application/json",
                                UUID.randomUUID().toString(),
                                UUID.randomUUID().toString()));
                        return null;
                    })
                    .when(client)
                    .read(eq(command.getSubject()), eq(Set.of(new Option.Recursive())), any());
        }

        CommandHandlerDefinition<Book, ? extends Command, Void> chd =
                new CommandHandlerDefinition<>(Book.class, command.getClass(), (CommandHandler.ForInstanceAndCommand)
                        (book, cmd, eventPublisher) -> null);

        CommandRouter subject = new CommandRouter(eventReader, immediateEventPublisher, List.of(chd), List.of());

        assertThatThrownBy(() -> subject.send(command)).isInstanceOfSatisfying(CqrsFrameworkException.class, e -> {
            switch (condition) {
                case PRISTINE -> assertThat(e)
                        .isInstanceOf(CommandSubjectAlreadyExistsException.class)
                        .hasFieldOrPropertyWithValue("command", command);
                case EXISTS -> assertThat(e)
                        .isInstanceOf(CommandSubjectDoesNotExistException.class)
                        .hasFieldOrPropertyWithValue("command", command);
                case NONE -> fail("no exception expected");
            }
        });
    }

    @ParameterizedTest
    @EnumSource(SourcingMode.class)
    public void eventsReadUsingSpecifiedSourcingMode(SourcingMode sourcingMode) {
        CommandHandlerDefinition<Book, BorrowBookCommand, Void> chd = new CommandHandlerDefinition<>(
                Book.class,
                BorrowBookCommand.class,
                (CommandHandler.ForInstanceAndCommand<Book, BorrowBookCommand, Void>)
                        (book, cmd, eventPublisher) -> null,
                sourcingMode);

        CommandRouter subject = new CommandRouter(eventReader, immediateEventPublisher, List.of(chd), List.of());

        var command = new BorrowBookCommand("isbn");
        subject.send(command);

        switch (sourcingMode) {
            case NONE -> verifyNoInteractions(client);
            case LOCAL -> verify(client).read(eq(command.getSubject()), eq(Set.of()), any());
            case RECURSIVE -> verify(client).read(eq(command.getSubject()), eq(Set.of(new Option.Recursive())), any());
        }
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                StateRebuildingHandler.FromObject.class,
                StateRebuildingHandler.FromObjectAndRawEvent.class,
                StateRebuildingHandler.FromObjectAndMetaData.class,
                StateRebuildingHandler.FromObjectAndMetaDataAndSubject.class,
                StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent.class
            })
    public void eventsSourcedToHandlerVariants(Class<StateRebuildingHandler> clazz) {
        var dummyState = new Book("4711", false);
        var sourcedEvent = new BookAddedEvent("4711");
        var metaData = Map.of("purpose", "testing");
        var command = new BorrowBookCommand("4711");
        var rawEvent = new Event(
                "test",
                command.getSubject(),
                eventTypeResolver.getEventType(sourcedEvent.getClass()),
                eventDataMarshaller.serialize(new EventData<>(metaData, sourcedEvent)),
                "1.0",
                "2345",
                Instant.now(),
                "application/json",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString());

        doAnswer(invocation -> {
                    Consumer<Event> consumer = invocation.getArgument(2);
                    consumer.accept(rawEvent);
                    return null;
                })
                .when(client)
                .read(eq(command.getSubject()), eq(Set.of(new Option.Recursive())), any());

        StateRebuildingHandler srh = mock(clazz, i -> dummyState);

        List stateRebuildingHandlerDefinitions =
                List.of(new StateRebuildingHandlerDefinition<>(Book.class, BookAddedEvent.class, srh));

        CommandHandlerDefinition<Book, BorrowBookCommand, Void> chd = new CommandHandlerDefinition<>(
                Book.class, BorrowBookCommand.class, (CommandHandler.ForInstanceAndCommand<
                                Book, BorrowBookCommand, Void>)
                        (book, cmd, eventPublisher) -> null);

        new CommandRouter(eventReader, immediateEventPublisher, List.of(chd), stateRebuildingHandlerDefinitions)
                .send(command);

        switch (srh) {
            case StateRebuildingHandler.FromObject handler -> verify(handler).on(null, sourcedEvent);
            case StateRebuildingHandler.FromObjectAndRawEvent handler -> verify(handler)
                    .on(null, sourcedEvent, rawEvent);
            case StateRebuildingHandler.FromObjectAndMetaData handler -> verify(handler)
                    .on(null, sourcedEvent, metaData);
            case StateRebuildingHandler.FromObjectAndMetaDataAndSubject handler -> verify(handler)
                    .on(null, sourcedEvent, metaData, command.getSubject());
            case StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent handler -> verify(handler)
                    .on(null, sourcedEvent, metaData, command.getSubject(), rawEvent);
        }
    }

    @Test
    public void eventsSourcedToHandlersForAssignableSuperClass() {
        var dummyState = new Book("4711", false);
        var sourcedEvent = new BookAddedEvent("4711");
        var command = new AddBookCommand("4711");

        doAnswer(invocation -> {
                    Consumer<Event> consumer = invocation.getArgument(2);
                    consumer.accept(new Event(
                            "test",
                            command.getSubject(),
                            eventTypeResolver.getEventType(sourcedEvent.getClass()),
                            eventDataMarshaller.serialize(new EventData<>(Map.of(), sourcedEvent)),
                            "1.0",
                            "2345",
                            Instant.now(),
                            "application/json",
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString()));
                    return null;
                })
                .when(client)
                .read(eq(command.getSubject()), eq(Set.of(new Option.Recursive())), any());

        StateRebuildingHandler.FromObject srh1 =
                mock(StateRebuildingHandler.FromObject.class, invocation -> dummyState);
        StateRebuildingHandler.FromObject srh2 =
                mock(StateRebuildingHandler.FromObject.class, invocation -> dummyState);
        StateRebuildingHandler.FromObject srh3 =
                mock(StateRebuildingHandler.FromObject.class, invocation -> dummyState);
        StateRebuildingHandler.FromObject srh4 =
                mock(StateRebuildingHandler.FromObject.class, invocation -> dummyState);
        StateRebuildingHandler.FromObject srh5 =
                mock(StateRebuildingHandler.FromObject.class, invocation -> dummyState);

        List stateRebuildingHandlerDefinitions = List.of(
                new StateRebuildingHandlerDefinition<>(Book.class, BookAddedEvent.class, srh1),
                new StateRebuildingHandlerDefinition<>(Book.class, BookBorrowedEvent.class, srh2),
                new StateRebuildingHandlerDefinition<>(Book.class, MyEvent.class, srh3),
                new StateRebuildingHandlerDefinition<>(Object.class, MyEvent.class, srh4),
                new StateRebuildingHandlerDefinition<>(Book.class, BookAddedEvent.class, srh5));

        CommandHandlerDefinition<Book, AddBookCommand, Void> chd = new CommandHandlerDefinition<>(
                Book.class, AddBookCommand.class, (CommandHandler.ForInstanceAndCommand<Book, AddBookCommand, Void>)
                        (book, cmd, eventPublisher) -> null);

        new CommandRouter(eventReader, immediateEventPublisher, List.of(chd), stateRebuildingHandlerDefinitions)
                .send(command);

        verify(srh1).on(any(), eq(sourcedEvent));
        verify(srh2, never()).on(any(), any());
        verify(srh3).on(any(), eq(sourcedEvent));
        verify(srh4, never()).on(any(), any());
        verify(srh5).on(any(), eq(sourcedEvent));
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                StateRebuildingHandler.FromObject.class,
                StateRebuildingHandler.FromObjectAndRawEvent.class,
                StateRebuildingHandler.FromObjectAndMetaData.class,
                StateRebuildingHandler.FromObjectAndMetaDataAndSubject.class,
                StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent.class
            })
    public void publishedEventsAppliedToHandlerVariants(Class<StateRebuildingHandler> clazz) {
        var dummyState = new Book("4711", false);
        var publishedEvent = new BookAddedEvent("4711");
        var metaData = Map.of("purpose", "testing");

        var command = new AddBookCommand("4711");

        StateRebuildingHandler srh = mock(clazz, i -> dummyState);

        List stateRebuildingHandlerDefinitions =
                List.of(new StateRebuildingHandlerDefinition<>(Book.class, BookAddedEvent.class, srh));

        CommandHandlerDefinition<Book, AddBookCommand, Void> chd = new CommandHandlerDefinition<>(
                Book.class, AddBookCommand.class, (CommandHandler.ForInstanceAndCommand<Book, AddBookCommand, Void>)
                        (book, cmd, eventPublisher) -> {
                            eventPublisher.publish(publishedEvent, metaData);
                            return null;
                        });

        new CommandRouter(eventReader, immediateEventPublisher, List.of(chd), stateRebuildingHandlerDefinitions)
                .send(command);

        switch (srh) {
            case StateRebuildingHandler.FromObject handler -> verify(handler).on(null, publishedEvent);
            case StateRebuildingHandler.FromObjectAndRawEvent handler -> verify(handler)
                    .on(null, publishedEvent, null);
            case StateRebuildingHandler.FromObjectAndMetaData handler -> verify(handler)
                    .on(null, publishedEvent, metaData);
            case StateRebuildingHandler.FromObjectAndMetaDataAndSubject handler -> verify(handler)
                    .on(null, publishedEvent, metaData, command.getSubject());
            case StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent handler -> verify(handler)
                    .on(null, publishedEvent, metaData, command.getSubject(), null);
        }
    }

    @Test
    public void publishedEventsAppliedToHandlersForAssignableSuperClass() {
        var dummyState = new Book("4711", false);

        StateRebuildingHandler.FromObject srh1 =
                mock(StateRebuildingHandler.FromObject.class, invocation -> dummyState);
        StateRebuildingHandler.FromObject srh2 =
                mock(StateRebuildingHandler.FromObject.class, invocation -> dummyState);
        StateRebuildingHandler.FromObject srh3 =
                mock(StateRebuildingHandler.FromObject.class, invocation -> dummyState);
        StateRebuildingHandler.FromObject srh4 =
                mock(StateRebuildingHandler.FromObject.class, invocation -> dummyState);
        StateRebuildingHandler.FromObject srh5 =
                mock(StateRebuildingHandler.FromObject.class, invocation -> dummyState);

        List stateRebuildingHandlerDefinitions = List.of(
                new StateRebuildingHandlerDefinition<>(Book.class, BookAddedEvent.class, srh1),
                new StateRebuildingHandlerDefinition<>(Book.class, BookBorrowedEvent.class, srh2),
                new StateRebuildingHandlerDefinition<>(Book.class, MyEvent.class, srh3),
                new StateRebuildingHandlerDefinition<>(Object.class, MyEvent.class, srh4),
                new StateRebuildingHandlerDefinition<>(Book.class, BookAddedEvent.class, srh5));

        var publishedEvent = new BookAddedEvent("4711");

        CommandHandlerDefinition<Book, AddBookCommand, Void> chd = new CommandHandlerDefinition<>(
                Book.class, AddBookCommand.class, (CommandHandler.ForInstanceAndCommand<Book, AddBookCommand, Void>)
                        (book, cmd, eventPublisher) -> {
                            eventPublisher.publish(publishedEvent);
                            return null;
                        });

        new CommandRouter(eventReader, immediateEventPublisher, List.of(chd), stateRebuildingHandlerDefinitions)
                .send(new AddBookCommand("4711"));

        verify(srh1).on(any(), eq(publishedEvent));
        verify(srh2, never()).on(any(), any());
        verify(srh3).on(any(), eq(publishedEvent));
        verify(srh4, never()).on(any(), any());
        verify(srh5).on(any(), eq(publishedEvent));
    }

    @Test
    public void stateRebuildingHandlerReturningNullDetectedOnSourcing() {
        var sourcedEvent = new BookAddedEvent("4711");
        var command = new BorrowBookCommand("4711");

        doAnswer(invocation -> {
                    Consumer<Event> consumer = invocation.getArgument(2);
                    consumer.accept(new Event(
                            "test",
                            command.getSubject(),
                            eventTypeResolver.getEventType(sourcedEvent.getClass()),
                            eventDataMarshaller.serialize(new EventData<>(Map.of(), sourcedEvent)),
                            "1.0",
                            "2345",
                            Instant.now(),
                            "application/json",
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString()));
                    return null;
                })
                .when(client)
                .read(eq(command.getSubject()), eq(Set.of(new Option.Recursive())), any());

        List stateRebuildingHandlerDefinitions = List.of(new StateRebuildingHandlerDefinition<>(
                Book.class, BookAddedEvent.class, (StateRebuildingHandler.FromObject<Book, BookAddedEvent>)
                        (book, event) -> null));

        CommandHandlerDefinition<Book, BorrowBookCommand, Void> chd = new CommandHandlerDefinition<>(
                Book.class, BorrowBookCommand.class, (CommandHandler.ForInstanceAndCommand<
                                Book, BorrowBookCommand, Void>)
                        (book, cmd, eventPublisher) -> null);

        assertThatThrownBy(() -> new CommandRouter(
                                eventReader, immediateEventPublisher, List.of(chd), stateRebuildingHandlerDefinitions)
                        .send(command))
                .isInstanceOf(CqrsFrameworkException.NonTransientException.class)
                .hasMessageContainingAll(
                        "state rebuilding handler returned 'null' instance", BookAddedEvent.class.getName());
    }

    @Test
    public void stateRebuildingHandlerReturningNullDetectedOnPublishing() {
        var command = new AddBookCommand("4711");

        List stateRebuildingHandlerDefinitions = List.of(new StateRebuildingHandlerDefinition<>(
                Book.class, BookAddedEvent.class, (StateRebuildingHandler.FromObject<Book, BookAddedEvent>)
                        (book, event) -> null));

        CommandHandlerDefinition<Book, AddBookCommand, Void> chd = new CommandHandlerDefinition<>(
                Book.class, AddBookCommand.class, (CommandHandler.ForInstanceAndCommand<Book, AddBookCommand, Void>)
                        (book, cmd, eventPublisher) -> {
                            eventPublisher.publish(new BookAddedEvent(cmd.isbn()));
                            return null;
                        });

        assertThatThrownBy(() -> new CommandRouter(
                                eventReader, immediateEventPublisher, List.of(chd), stateRebuildingHandlerDefinitions)
                        .send(command))
                .isInstanceOf(CqrsFrameworkException.NonTransientException.class)
                .hasMessageContainingAll(
                        "state rebuilding handler returned 'null' instance", BookAddedEvent.class.getName());
    }

    @Test
    public void noEventsWrittenOnException() {
        var exception = new RuntimeException("test");

        CommandHandlerDefinition<Book, BorrowBookCommand, Void> chd = new CommandHandlerDefinition<>(
                Book.class,
                BorrowBookCommand.class,
                (CommandHandler.ForInstanceAndCommand<Book, BorrowBookCommand, Void>) (book, cmd, eventPublisher) -> {
                    eventPublisher.publish(new BookBorrowedEvent());
                    throw exception;
                });

        CommandRouter subject = new CommandRouter(eventReader, immediateEventPublisher, List.of(chd), List.of());

        assertThatThrownBy(() -> subject.send(new BorrowBookCommand("4711"))).isSameAs(exception);

        verifyNoInteractions(immediateEventPublisher);
    }

    @Test
    public void ambiguousCommandHandlerDefinitionDetected() {
        CommandHandlerDefinition<Book, BorrowBookCommand, Void> chd = new CommandHandlerDefinition<>(
                Book.class, BorrowBookCommand.class, (CommandHandler.ForInstanceAndCommand<
                                Book, BorrowBookCommand, Void>)
                        (book, cmd, eventPublisher) -> null);

        assertThatThrownBy(() -> new CommandRouter(eventReader, immediateEventPublisher, List.of(chd, chd), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContainingAll("duplicate command handler definition", BorrowBookCommand.class.getName());
    }

    @Test
    public void missingCommandHandlerDetected() {
        var subject = new CommandRouter(eventReader, immediateEventPublisher, List.of(), List.of());

        assertThatThrownBy(() -> subject.send(new BorrowBookCommand("4711")))
                .isInstanceOf(CqrsFrameworkException.NonTransientException.class)
                .hasMessageContainingAll("no command handler definition", BorrowBookCommand.class.getName());
    }
}
