/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.opencqrs.esdb.client.EsdbClient;
import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.Book;
import com.opencqrs.framework.BookAddedEvent;
import com.opencqrs.framework.BookBorrowedEvent;
import com.opencqrs.framework.BorrowBookCommand;
import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandHandler;
import com.opencqrs.framework.command.CommandHandlerDefinition;
import com.opencqrs.framework.command.CommandRouter;
import com.opencqrs.framework.command.StateRebuildingHandler;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import com.opencqrs.framework.interceptor.InterceptorExecutionException;
import com.opencqrs.framework.interceptor.Proceeded;
import com.opencqrs.framework.interceptor.ValueContinuation;
import com.opencqrs.framework.persistence.EventReader;
import com.opencqrs.framework.persistence.ImmediateEventPublisher;
import com.opencqrs.framework.serialization.EventData;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.serialization.JacksonEventDataMarshaller;
import com.opencqrs.framework.types.ClassNameEventTypeResolver;
import com.opencqrs.framework.types.EventTypeResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/** Command-side interceptor wiring, exercised against the real {@link CommandRouter} pipeline. */
@ExtendWith(MockitoExtension.class)
class CommandRouterInterceptorTest {

    @Mock
    private EsdbClient client;

    @Mock
    private ImmediateEventPublisher immediateEventPublisher;

    private final EventDataMarshaller eventDataMarshaller = new JacksonEventDataMarshaller(new ObjectMapper());
    private final EventTypeResolver eventTypeResolver =
            new ClassNameEventTypeResolver(CommandRouterInterceptorTest.class.getClassLoader());

    private final EventReader eventReader = (clientRequestor, rawConsumer) -> clientRequestor.request(
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
                            raw),
                    raw));

    private final BorrowBookCommand command = new BorrowBookCommand("4711");

    private <E> Event rawEvent(String id, Class<E> type, E payload) {
        return new Event(
                "test",
                command.getSubject(),
                eventTypeResolver.getEventType(type),
                eventDataMarshaller.serialize(new EventData<>(Map.of(), payload)),
                "1.0",
                id,
                Instant.now(),
                "application/json",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
    }

    private void sourceBookAdded() {
        doAnswer(invocation -> {
                    Consumer<Event> consumer = invocation.getArgument(2);
                    consumer.accept(rawEvent("event-id-1", BookAddedEvent.class, new BookAddedEvent("4711")));
                    return null;
                })
                .when(client)
                .read(eq(command.getSubject()), any(), any());
    }

    /** An event type with <strong>no</strong> registered state-rebuilding handler. */
    record UnhandledEvent(String note) {}

    private void sourceBookAddedThenUnhandled() {
        doAnswer(invocation -> {
                    Consumer<Event> consumer = invocation.getArgument(2);
                    consumer.accept(rawEvent("event-id-1", BookAddedEvent.class, new BookAddedEvent("4711")));
                    consumer.accept(rawEvent("event-id-2", UnhandledEvent.class, new UnhandledEvent("no handler")));
                    return null;
                })
                .when(client)
                .read(eq(command.getSubject()), any(), any());
    }

    @SuppressWarnings("rawtypes")
    private CommandRouter router(
            CommandHandlerDefinition<Book, BorrowBookCommand, ?> chd, List<CommandInterceptor> interceptors) {
        List<StateRebuildingHandlerDefinition> srhds = List.of(
                new StateRebuildingHandlerDefinition<>(
                        Book.class, BookAddedEvent.class, (StateRebuildingHandler.FromObject<Book, BookAddedEvent>)
                                (book, event) -> new Book(event.isbn(), false)),
                new StateRebuildingHandlerDefinition<>(
                        Book.class, BookBorrowedEvent.class, (StateRebuildingHandler.FromObject<
                                        Book, BookBorrowedEvent>)
                                (book, event) -> new Book(book.isbn(), true)));
        return new CommandRouter(
                eventReader,
                immediateEventPublisher,
                List.of(chd),
                srhds,
                interceptors,
                new com.opencqrs.framework.command.cache.NoStateRebuildingCache(),
                com.opencqrs.framework.metadata.PropagationMode.NONE,
                Set.of());
    }

    private CommandHandlerDefinition<Book, BorrowBookCommand, UUID> borrowingHandler(UUID result) {
        return new CommandHandlerDefinition<>(
                Book.class,
                BorrowBookCommand.class,
                (CommandHandler.ForInstanceAndCommand<Book, BorrowBookCommand, UUID>) (book, cmd, publisher) -> {
                    publisher.publish(new BookBorrowedEvent());
                    return result;
                });
    }

    /** Records the firing order and join-point highlights of every command lifecycle hook. */
    private static final class Recording implements CommandInterceptor<Command> {
        private final String name;
        private final List<String> trace;

        Recording(String name, List<String> trace) {
            this.name = name;
            this.trace = trace;
        }

        @Override
        public Class<Command> commandClass() {
            return Command.class;
        }

        @Override
        public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                throws Exception {
            trace.add(name + ":root:before:" + inv.command().getClass().getSimpleName());
            lc.sourcing((jp, c) -> {
                trace.add(name + ":sourcing:before:" + jp.instanceClass().getSimpleName());
                Proceeded p = c.proceed();
                trace.add(name + ":sourcing:after");
                return p;
            });
            lc.sourcedEvent((jp, c) -> {
                trace.add(name + ":sourcedEvent:before:" + jp.event().getClass().getSimpleName() + ":"
                        + jp.rawEvent().id());
                Proceeded p = c.proceed();
                trace.add(name + ":sourcedEvent:after");
                return p;
            });
            lc.publishedEvent((jp, c) -> {
                trace.add(
                        name + ":publishedEvent:before:" + jp.event().getClass().getSimpleName());
                Proceeded p = c.proceed();
                trace.add(name + ":publishedEvent:after");
                return p;
            });
            lc.handler((jp, c) -> {
                trace.add(name + ":handler:before:" + jp.instance() + ":" + jp.latestSourcedEventId());
                R r = c.proceed();
                trace.add(name + ":handler:after");
                return r;
            });
            lc.publish((jp, c) -> {
                trace.add(name + ":publish:before:" + jp.events().size());
                Publish p = c.proceed();
                trace.add(name + ":publish:after");
                return p;
            });
            R result = cont.proceed();
            trace.add(name + ":root:after");
            return result;
        }
    }

    @Test
    void allHooksFireAtCorrectJoinPointsOverRealPipeline() {
        sourceBookAdded();
        var trace = new ArrayList<String>();
        var result = UUID.randomUUID();

        UUID actual = router(borrowingHandler(result), List.of(new Recording("I", trace)))
                .send(command);

        assertThat(actual).isEqualTo(result);
        assertThat(trace)
                .containsExactly(
                        "I:root:before:BorrowBookCommand",
                        "I:sourcing:before:Book",
                        "I:sourcedEvent:before:BookAddedEvent:event-id-1",
                        "I:sourcedEvent:after",
                        "I:sourcing:after",
                        "I:handler:before:Book[isbn=4711, lent=false]:event-id-1",
                        "I:publishedEvent:before:BookBorrowedEvent",
                        "I:publishedEvent:after",
                        "I:handler:after",
                        "I:publish:before:1",
                        "I:publish:after",
                        "I:root:after");
        verify(immediateEventPublisher).publish(anyList(), anyList());
    }

    @Test
    void handlerJoinPointExposesSourcedHeadEventIdToInterceptors() {
        sourceBookAdded();
        var head = new AtomicReference<>("unset");

        router(borrowingHandler(UUID.randomUUID()), List.<CommandInterceptor>of(new CommandInterceptor<>() {
                    @Override
                    public Class<Command> commandClass() {
                        return Command.class;
                    }

                    @Override
                    public <R> R intercept(
                            CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                            throws Exception {
                        lc.handler((jp, c) -> {
                            head.set(jp.latestSourcedEventId());
                            return c.proceed();
                        });
                        return cont.proceed();
                    }
                }))
                .send(command);

        assertThat(head.get()).isEqualTo("event-id-1");
    }

    @Test
    void handlerJoinPointExposesNullSourcedEventIdWhenNothingWasSourced() {
        var trace = new ArrayList<String>();
        CommandHandlerDefinition<Book, BorrowBookCommand, UUID> noEmit = new CommandHandlerDefinition<>(
                Book.class, BorrowBookCommand.class, (CommandHandler.ForInstanceAndCommand<
                                Book, BorrowBookCommand, UUID>)
                        (book, cmd, publisher) -> UUID.randomUUID());

        router(noEmit, List.of(new Recording("I", trace))).send(command);

        assertThat(trace).contains("I:handler:before:null:null");
    }

    @Test
    void handlerJoinPointExposesSourcedHeadEventIdWhenHeadEventHasNoStateRebuildingHandler() {
        sourceBookAddedThenUnhandled();
        var head = new AtomicReference<>("unset");

        router(borrowingHandler(UUID.randomUUID()), List.<CommandInterceptor>of(new CommandInterceptor<>() {
                    @Override
                    public Class<Command> commandClass() {
                        return Command.class;
                    }

                    @Override
                    public <R> R intercept(
                            CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                            throws Exception {
                        lc.handler((jp, c) -> {
                            head.set(jp.latestSourcedEventId());
                            return c.proceed();
                        });
                        return cont.proceed();
                    }
                }))
                .send(command);

        assertThat(head.get()).isEqualTo("event-id-2");
    }

    @Test
    void interceptorsNestInListOrderOutermostFirst() {
        sourceBookAdded();
        var trace = new ArrayList<String>();

        router(borrowingHandler(UUID.randomUUID()), List.of(new Recording("A", trace), new Recording("B", trace)))
                .send(command);

        // every stage nests A-outer-B-inner: inbound A→B, outbound (inside-out) B→A — including the post-handler
        // publishedEvent / publish stages
        assertThat(trace)
                .containsExactly(
                        "A:root:before:BorrowBookCommand",
                        "B:root:before:BorrowBookCommand",
                        "A:sourcing:before:Book",
                        "B:sourcing:before:Book",
                        "A:sourcedEvent:before:BookAddedEvent:event-id-1",
                        "B:sourcedEvent:before:BookAddedEvent:event-id-1",
                        "B:sourcedEvent:after",
                        "A:sourcedEvent:after",
                        "B:sourcing:after",
                        "A:sourcing:after",
                        "A:handler:before:Book[isbn=4711, lent=false]:event-id-1",
                        "B:handler:before:Book[isbn=4711, lent=false]:event-id-1",
                        "A:publishedEvent:before:BookBorrowedEvent",
                        "B:publishedEvent:before:BookBorrowedEvent",
                        "B:publishedEvent:after",
                        "A:publishedEvent:after",
                        "B:handler:after",
                        "A:handler:after",
                        "A:publish:before:1",
                        "B:publish:before:1",
                        "B:publish:after",
                        "A:publish:after",
                        "B:root:after",
                        "A:root:after");
    }

    @Test
    void interceptorNotMatchingCommandClassIsNotApplied() {
        sourceBookAdded();
        var trace = new ArrayList<String>();
        CommandInterceptor<com.opencqrs.framework.AddBookCommand> forOtherCommand = new CommandInterceptor<>() {
            @Override
            public Class<com.opencqrs.framework.AddBookCommand> commandClass() {
                return com.opencqrs.framework.AddBookCommand.class;
            }

            @Override
            public <R> R intercept(
                    CommandInvocation<com.opencqrs.framework.AddBookCommand> inv,
                    CommandLifecycle<R> lc,
                    ValueContinuation<R> cont)
                    throws Exception {
                trace.add("should-not-fire");
                return cont.proceed();
            }
        };

        router(borrowingHandler(UUID.randomUUID()), List.of(forOtherCommand)).send(command);

        assertThat(trace).isEmpty();
        verify(immediateEventPublisher).publish(anyList(), anyList());
    }

    @Test
    void throwFromHandlerAdviceVetoesCommandAndSkipsPublish() {
        sourceBookAdded();
        var boom = new RuntimeException("denied");
        CommandInterceptor<Command> denying = new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                lc.handler((jp, c) -> {
                    throw boom;
                });
                return cont.proceed();
            }
        };

        assertThatThrownBy(() -> router(borrowingHandler(UUID.randomUUID()), List.of(denying))
                        .send(command))
                .isSameAs(boom);
        verifyNoInteractions(immediateEventPublisher);
    }

    @Test
    void checkedExceptionFromHandlerAdviceIsWrappedAsInterceptorExecutionException() {
        sourceBookAdded();
        var checked = new Exception("checked failure");
        CommandInterceptor<Command> throwingChecked = new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                lc.handler((jp, c) -> {
                    throw checked;
                });
                return cont.proceed();
            }
        };

        assertThatThrownBy(() -> router(borrowingHandler(UUID.randomUUID()), List.of(throwingChecked))
                        .send(command))
                .isInstanceOf(InterceptorExecutionException.class)
                .hasCause(checked);
        verifyNoInteractions(immediateEventPublisher);
    }

    @Test
    void checkedExceptionFromPublishedEventAdviceIsWrappedAsInterceptorExecutionException() {
        sourceBookAdded();
        var checked = new Exception("checked failure");
        CommandInterceptor<Command> throwingChecked = new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                lc.publishedEvent((jp, c) -> {
                    throw checked;
                });
                return cont.proceed();
            }
        };

        assertThatThrownBy(() -> router(borrowingHandler(UUID.randomUUID()), List.of(throwingChecked))
                        .send(command))
                .isInstanceOf(InterceptorExecutionException.class)
                .hasCause(checked);
        verifyNoInteractions(immediateEventPublisher);
    }

    @Test
    void handlerShortCircuitSubstitutesResultAndSkipsHandlerAndPublish() {
        sourceBookAdded();
        var cached = UUID.randomUUID();
        var handlerRan = new java.util.concurrent.atomic.AtomicBoolean(false);
        CommandInterceptor<Command> idempotent = new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                lc.handler((jp, c) -> {
                    @SuppressWarnings("unchecked")
                    R substitute = (R) cached;
                    return substitute;
                });
                return cont.proceed();
            }
        };
        CommandHandlerDefinition<Book, BorrowBookCommand, UUID> chd = new CommandHandlerDefinition<>(
                Book.class,
                BorrowBookCommand.class,
                (CommandHandler.ForInstanceAndCommand<Book, BorrowBookCommand, UUID>) (book, cmd, publisher) -> {
                    handlerRan.set(true);
                    publisher.publish(new BookBorrowedEvent());
                    return UUID.randomUUID();
                });

        UUID actual = router(chd, List.of(idempotent)).send(command);

        assertThat(actual).isEqualTo(cached);
        assertThat(handlerRan).as("real handler must be short-circuited").isFalse();
        verifyNoInteractions(immediateEventPublisher);
    }

    @Test
    void publishHookFiresOnlyWhenEventsAreAppended() {
        sourceBookAdded();
        var trace = new ArrayList<String>();
        CommandHandlerDefinition<Book, BorrowBookCommand, UUID> noEmit = new CommandHandlerDefinition<>(
                Book.class, BorrowBookCommand.class, (CommandHandler.ForInstanceAndCommand<
                                Book, BorrowBookCommand, UUID>)
                        (book, cmd, publisher) -> UUID.randomUUID());

        router(noEmit, List.of(new Recording("I", trace))).send(command);

        // no events emitted → neither publishedEvent nor the publish stage fire
        assertThat(trace)
                .containsExactly(
                        "I:root:before:BorrowBookCommand",
                        "I:sourcing:before:Book",
                        "I:sourcedEvent:before:BookAddedEvent:event-id-1",
                        "I:sourcedEvent:after",
                        "I:sourcing:after",
                        "I:handler:before:Book[isbn=4711, lent=false]:event-id-1",
                        "I:handler:after",
                        "I:root:after");
        verifyNoInteractions(immediateEventPublisher);
    }
}
