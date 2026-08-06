/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.command.interceptor.*;
import com.opencqrs.framework.interceptor.InterceptorExecutionException;
import com.opencqrs.framework.persistence.CapturedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * Test support for {@link CommandHandler} or {@link CommandHandlerDefinition}. This class can be used in favor of the
 * {@link CommandRouter} to test command handling logic without interacting with the event store, solely relying on a
 * set of {@link StateRebuildingHandlerDefinition}s and optionally a set of {@link CommandInterceptor}s. No event
 * upcasting, event type resolution, meta-data propagation, or store-based event sourcing is involved during test
 * execution &mdash; {@linkplain Given given} events are replayed directly.
 *
 * <p>Command execution runs through the configured {@link CommandInterceptor}s exactly as in production &mdash; they
 * may transform, short-circuit, or veto the command. Layer or clear them via {@link #withAdditionalInterceptors} /
 * {@link #withoutInterceptors}; a {@link CommandHandlingTest} slice contributes its interceptors as the base set.
 * Because the fixture performs no store-based sourcing, the {@link SourcingMode} surfaced to interceptors (via the
 * {@code Sourcing} and {@code CommandHandlerInvocation} join points) is always {@link SourcingMode#NONE} and does
 * <em>not</em> reflect the handler's configured mode. Event assertions reflect the events <em>as finally appended</em>
 * &mdash; that is, after any {@code publish}-stage transformation (e.g. meta-data propagation).
 *
 * <p>This class follows the <a href="https://martinfowler.com/bliki/GivenWhenThen.html">Given When Then</a> style of
 * representing tests with a fluent API supporting:
 *
 * <ol>
 *   <li>{@linkplain Given given} state initialization based on in-memory events and meta-data
 *   <li>{@link Command} {@linkplain GivenDsl#when(Command) execution} to execute the {@link CommandHandler} under test
 *   <li>{@linkplain Expect assertions} to verify the command executed as expected, including verification of the events
 *       published by the command handler under test
 * </ol>
 *
 * A typical test case using {@code this} may look as follows. The {@link StateRebuildingHandlerDefinition}s needed to
 * mimic event sourcing as well as the {@link CommandHandler} definition under test have been omitted for brevity.
 *
 * <pre>
 *     {@literal @Test}
 *     public void bookAdded() {
 *          UUID bookId = UUID.randomUUID();
 *          CommandHandlingTestFixture
 *              // specify state rebuilding handler definitions to use
 *              .withStateRebuildingHandlerDefinitions(...)
 *              // specify command handler (definition) to test
 *              .using(...)
 *              .given()
 *              .nothing()
 *              .when(new AddBookCommand(bookId, "Tolkien", "LOTR", "DE234723432"))
 *              .succeeds()
 *              .allEvents()
 *              .exactly(new BookAddedEvent(bookId, "Tolkien", "LOTR", "DE234723432"));
 *     }
 * </pre>
 *
 * In lack of the event store, for {@link StateRebuildingHandler.FromObjectAndRawEvent#on(Object, Object, Event)} and
 * {@link StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent#on(Object, Object, Map, String, Event)} the
 * {@linkplain Given given} state initialization uses <strong>stubbed</strong> raw {@link Event}s, instead, based on the
 * following contents:
 *
 * <table>
 *     <caption>Raw event stubbing</caption>
 *     <thead>
 *     <tr>
 *         <th>{@linkplain Event event} attribute</th>
 *         <th>value derivation</th>
 *     </tr>
 *     </thead>
 *     <tbody>
 *     <tr>
 *         <td>{@link Event#source()}</td>
 *         <td>is set to a fixed value and cannot be overridden</td>
 *     </tr>
 *     <tr>
 *         <td>{@link Event#subject()}</td>
 *         <td>is set to {@link Command#getSubject()}, but can be overridden using {@link GivenDsl#usingSubject(String)} or per event using {@link EventSpecifierDsl#subject(String)}</td>
 *     </tr>
 *     <tr>
 *         <td>{@link Event#type()}</td>
 *         <td>is set to a fixed value and cannot be overridden</td>
 *     </tr>
 *     <tr>
 *         <td>{@link Event#data()}</td>
 *         <td>is set to an empty map and cannot be overridden</td>
 *     </tr>
 *     <tr>
 *         <td>{@link Event#specVersion()}</td>
 *         <td>is set to a fixed value and cannot be overridden</td>
 *     </tr>
 *     <tr>
 *         <td>{@link Event#id()}</td>
 *         <td>is set randomly, but can be overridden per event using {@link EventSpecifierDsl#id(String)}</td>
 *     </tr>
 *     <tr>
 *         <td>{@link Event#time()}</td>
 *         <td>is set to the value from {@link GivenDsl#time(Instant)}, or {@link Instant#now()} by default, but can be overridden per event using {@link EventSpecifierDsl#time(Instant)}</td>
 *     </tr>
 *     <tr>
 *         <td>{@link Event#dataContentType()}</td>
 *         <td>is set to a fixed value and cannot be overridden</td>
 *     </tr>
 *     <tr>
 *         <td>{@link Event#hash()}</td>
 *         <td>is set to a random value and cannot be overridden</td>
 *     </tr>
 *     <tr>
 *         <td>{@link Event#predecessorHash()}</td>
 *         <td>is set to a random value and cannot be overridden</td>
 *     </tr>
 *     </tbody>
 * </table>
 *
 * @param <C> the command type
 */
public class CommandHandlingTestFixture<C extends Command> {

    private final Class<?> instanceClass;
    private final List<StateRebuildingHandlerDefinition<Object, Object>> stateRebuildingHandlerDefinitions;
    final CommandHandler<?, C, ?> commandHandler;
    private final List<CommandInterceptor> interceptors;

    private CommandHandlingTestFixture(
            Class<?> instanceClass,
            List<StateRebuildingHandlerDefinition<Object, Object>> stateRebuildingHandlerDefinitions,
            CommandHandler<?, C, ?> commandHandler,
            List<CommandInterceptor> interceptors) {
        this.instanceClass = instanceClass;
        this.stateRebuildingHandlerDefinitions = stateRebuildingHandlerDefinitions;
        this.commandHandler = commandHandler;
        this.interceptors = interceptors;
    }

    /**
     * Creates a {@link Builder} instance for the given {@link StateRebuildingHandlerDefinition}s.
     *
     * @param definitions the {@link StateRebuildingHandlerDefinition}s to be used to mimic event sourcing for the
     *     {@link CommandHandler} under test
     * @return a {@link Builder} instance
     * @param <I> the generic type of the instance to be event sourced before handling the command
     */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <I> Builder<I> withStateRebuildingHandlerDefinitions(
            StateRebuildingHandlerDefinition<I, ?>... definitions) {
        return new Builder<>((List<StateRebuildingHandlerDefinition<Object, Object>>)
                (List<?>) Arrays.stream(definitions).toList());
    }

    /**
     * Builder for {@link CommandHandlingTestFixture}.
     *
     * @param <I> the generic type of the instance to be event sourced before handling the command
     */
    public static class Builder<I> {
        final List<StateRebuildingHandlerDefinition<Object, Object>> stateRebuildingHandlerDefinitions;

        List<CommandInterceptor> interceptors = List.of();

        private Builder(List<StateRebuildingHandlerDefinition<Object, Object>> stateRebuildingHandlerDefinitions) {
            this.stateRebuildingHandlerDefinitions = stateRebuildingHandlerDefinitions;
        }

        /**
         * Configures the base {@link CommandInterceptor}s applied to every fixture created by this builder (index 0 =
         * outermost). Used by {@link CommandHandlingTestAutoConfiguration} to wire the interceptors found in a
         * {@link CommandHandlingTest} slice.
         *
         * @param interceptors the ordered base interceptors
         * @return {@code this}
         */
        Builder<I> withInterceptors(List<CommandInterceptor> interceptors) {
            this.interceptors = interceptors;
            return this;
        }

        /**
         * Initializes the {@link CommandHandlingTestFixture} using the given {@link CommandHandlerDefinition}.
         *
         * @param definition the {@link CommandHandlerDefinition} under test
         * @return a fully initialized test fixture
         * @param <C> the command type
         */
        public <C extends Command> CommandHandlingTestFixture<C> using(CommandHandlerDefinition<I, C, ?> definition) {
            return new CommandHandlingTestFixture<>(
                    definition.instanceClass(), stateRebuildingHandlerDefinitions, definition.handler(), interceptors);
        }

        /**
         * Initializes the {@link CommandHandlingTestFixture} using the given {@link CommandHandler}.
         *
         * @param instanceClass the state instance class
         * @param handler the {@link CommandHandler} under test
         * @return a fully initialized test fixture
         * @param <C> the command type
         */
        public <C extends Command> CommandHandlingTestFixture<C> using(
                Class<I> instanceClass, CommandHandler<I, C, ?> handler) {
            return new CommandHandlingTestFixture<>(
                    instanceClass, stateRebuildingHandlerDefinitions, handler, interceptors);
        }
    }

    /**
     * Derives a new fixture with the given {@link CommandInterceptor}s appended (inner-most, in argument order) to the
     * current ones. The fixture is immutable, so the current instance is unaffected; the derived fixture runs the
     * command execution through the applicable interceptors.
     *
     * @param additionalInterceptors the interceptors to add
     * @return a new fixture including the additional interceptors
     */
    public CommandHandlingTestFixture<C> withAdditionalInterceptors(
            CommandInterceptor<? super C>... additionalInterceptors) {
        List<CommandInterceptor> combined = new ArrayList<>(interceptors);
        combined.addAll(Arrays.asList(additionalInterceptors));
        return new CommandHandlingTestFixture<>(
                instanceClass, stateRebuildingHandlerDefinitions, commandHandler, List.copyOf(combined));
    }

    /**
     * Derives a new fixture with no {@link CommandInterceptor}s.
     *
     * @return a new fixture without any interceptors
     */
    public CommandHandlingTestFixture<C> withoutInterceptors() {
        return new CommandHandlingTestFixture<>(
                instanceClass, stateRebuildingHandlerDefinitions, commandHandler, List.of());
    }

    /**
     * Fluent API helper class encapsulating the current state stubbing prior to {@linkplain #when(Command) executing}
     * the {@link CommandHandler} under test.
     */
    class Given implements GivenDsl.Initial<C>, GivenDsl.Terminated<C> {

        sealed interface Stub {

            record State(@Nullable Object state) implements Stub {}

            record Time(Instant time) implements Stub {}

            record TimeDelta(Duration duration) implements Stub {}

            @NullUnmarked
            record Event(String id, Instant time, String subject, Object payload, Map<String, ?> metaData)
                    implements Stub {
                public Event() {
                    this(null, null, null, null, null);
                }

                public Event withId(String id) {
                    return new Event(id, time(), subject(), payload(), metaData());
                }

                public Event withTime(Instant time) {
                    return new Event(id(), time, subject(), payload(), metaData());
                }

                public Event withSubject(String subject) {
                    return new Event(id(), time(), subject, payload(), metaData());
                }

                public Event withPayload(Object payload) {
                    return new Event(id(), time(), subject(), payload, metaData());
                }

                public Event withMetaData(Map<String, ?> metaData) {
                    return new Event(id(), time(), subject(), payload(), metaData);
                }
            }
        }

        record StubResult(
                Class<?> instanceClass,
                @Nullable Object state,
                Instant time,
                Command command,
                List<StateRebuildingHandlerDefinition<Object, Object>> stateRebuildingHandlerDefinitions,
                Set<String> subjects,
                @Nullable String latestSourcedEventId) {

            public StubResult withState(@Nullable Object newState) {
                return new StubResult(
                        instanceClass(),
                        newState,
                        time(),
                        command(),
                        stateRebuildingHandlerDefinitions(),
                        subjects(),
                        latestSourcedEventId());
            }

            public StubResult withTime(Instant newTime) {
                return new StubResult(
                        instanceClass(),
                        state(),
                        newTime,
                        command(),
                        stateRebuildingHandlerDefinitions(),
                        subjects(),
                        latestSourcedEventId());
            }

            public StubResult withSubject(String newSubject) {
                var newSubjects = new HashSet<>(subjects());
                newSubjects.add(newSubject);
                return new StubResult(
                        instanceClass(),
                        state(),
                        time(),
                        command(),
                        stateRebuildingHandlerDefinitions(),
                        newSubjects,
                        latestSourcedEventId());
            }

            public StubResult withLatestSourcedEventId(@Nullable String newLatestSourcedEventId) {
                return new StubResult(
                        instanceClass(),
                        state(),
                        time(),
                        command(),
                        stateRebuildingHandlerDefinitions(),
                        subjects(),
                        newLatestSourcedEventId);
            }

            public StubResult merge(Stub stub, CommandInterceptorChain<?> chain) {
                return switch (stub) {
                    case Stub.State state -> {
                        if (state() != null) throw new IllegalArgumentException("givenState() must only be used once");
                        yield withState(state.state());
                    }
                    case Stub.Time time -> withTime(time.time());
                    case Stub.TimeDelta timeDelta -> withTime(time().plus(timeDelta.duration()));
                    case Stub.Event event -> {
                        var rawEvent = new Event(
                                CommandHandlingTestFixture.class.getSimpleName(),
                                event.subject() != null
                                        ? event.subject()
                                        : command().getSubject(),
                                "test",
                                Map.of(),
                                "1.0",
                                event.id() != null
                                        ? event.id()
                                        : UUID.randomUUID().toString(),
                                event.time() != null ? event.time() : time(),
                                "application/test",
                                UUID.randomUUID().toString(),
                                UUID.randomUUID().toString());

                        AtomicReference reference = new AtomicReference<@Nullable Object>(state());
                        boolean applied;
                        try {
                            applied = Util.applyUsingHandlers(
                                    stateRebuildingHandlerDefinitions.stream()
                                            .filter(srhd -> srhd.instanceClass().equals(instanceClass()))
                                            .toList(),
                                    reference,
                                    rawEvent.subject(),
                                    event.payload(),
                                    event.metaData() != null ? event.metaData() : Map.of(),
                                    rawEvent,
                                    chain);
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new InterceptorExecutionException(
                                    "command interceptor raised a checked exception while applying a given event", e);
                        }
                        if (!applied) {
                            throw new IllegalArgumentException(
                                    "No suitable state rebuilding handler definition found for event type: "
                                            + event.payload().getClass().getSimpleName());
                        }
                        yield withSubject(rawEvent.subject())
                                .withState(reference.get())
                                .withLatestSourcedEventId(rawEvent.id());
                    }
                };
            }
        }

        public static class GivenEvent implements EventSpecifierDsl {
            Stub.Event stub;

            GivenEvent(Stub.Event stub) {
                this.stub = stub;
            }

            @Override
            public EventSpecifierDsl payload(Object payload) {
                stub = stub.withPayload(payload);
                return this;
            }

            @Override
            public EventSpecifierDsl time(Instant time) {
                stub = stub.withTime(time);
                return this;
            }

            @Override
            public EventSpecifierDsl subject(String subject) {
                stub = stub.withSubject(subject);
                return this;
            }

            @Override
            public EventSpecifierDsl id(String id) {
                stub = stub.withId(id);
                return this;
            }

            @Override
            public EventSpecifierDsl metaData(Map<String, ?> metaData) {
                stub = stub.withMetaData(metaData);
                return this;
            }
        }

        private final CommandHandler<?, C, ?> commandHandler;
        private final List<Stub> stubs = new ArrayList<>();

        @Nullable
        private final String subject;

        private Given(CommandHandler<?, C, ?> commandHandler, Instant time) {
            this.subject = null;
            this.commandHandler = commandHandler;
            stubs.add(new Stub.Time(time));
        }

        private Given(CommandHandler<?, C, ?> commandHandler) {
            this(commandHandler, Instant.now());
        }

        private Given(@Nullable String subject, CommandHandler<?, C, ?> commandHandler, List<Stub> stubs) {
            this.subject = subject;
            this.commandHandler = commandHandler;
            this.stubs.addAll(stubs);
        }

        private void addToStubs(Consumer<? super GivenEvent> givenEvent) {
            GivenEvent capture = new GivenEvent(new Stub.Event());
            givenEvent.accept(capture);

            Stub.Event stub = capture.stub;
            if (stub.payload() == null) {
                throw new IllegalArgumentException("Event payload must be specified using payload()");
            }
            if (stub.subject() == null) {
                stubs.add(stub.withSubject(subject));
            } else {
                stubs.add(stub);
            }
        }

        @Override
        public GivenDsl.Terminated<C> nothing() {
            return this;
        }

        @Override
        public GivenDsl<C> time(Instant time) {
            stubs.add(new Stub.Time(time));
            return this;
        }

        @Override
        public GivenDsl<C> timeDelta(Duration delta) {
            stubs.add(new Stub.TimeDelta(delta));
            return this;
        }

        @Override
        public GivenDsl<C> state(@Nullable Object state) {
            stubs.add(new Stub.State(state));
            return this;
        }

        @Override
        public GivenDsl<C> events(Object... events) {
            for (Object event : events) {
                addToStubs(e -> e.payload(event));
            }
            return this;
        }

        @Override
        public GivenDsl<C> event(Consumer<EventSpecifierDsl> event) {
            addToStubs(event);
            return this;
        }

        @Override
        public <CMD extends Command> GivenDsl<C> command(CommandHandlingTestFixture<CMD> fixture, CMD command) {
            return command(fixture, command, Map.of());
        }

        @Override
        public <CMD extends Command> GivenDsl<C> command(
                CommandHandlingTestFixture<CMD> fixture, CMD command, Map<String, ?> metaData) {
            Expect otherExpect = (Expect) fixture.givenStubs(stubs).when(command, metaData);

            otherExpect.succeeds();

            otherExpect.capturedEvents.forEach(
                    capturedEvent -> addToStubs(event -> event.subject(capturedEvent.subject())
                            .payload(capturedEvent.event())
                            .metaData(capturedEvent.metaData())));

            return this;
        }

        @Override
        public GivenDsl<C> usingSubject(String subject) {
            return new Given(subject, commandHandler, stubs);
        }

        @Override
        public GivenDsl<C> usingCommandSubject() {
            return new Given(null, commandHandler, stubs);
        }

        @Override
        public ExpectDsl.Outcome when(C command) {
            return when(command, Map.of());
        }

        @Override
        @SuppressWarnings("unchecked")
        public ExpectDsl.Outcome when(C command, Map<String, ?> metaData) {
            var applicableInterceptors = interceptors.stream()
                    .filter(interceptor -> interceptor.commandClass().isAssignableFrom(command.getClass()))
                    .toList();
            CommandInterceptorChain<Object> chain = new CommandInterceptorChain<>(applicableInterceptors);

            CommandHandler<Object, C, Object> typedHandler = (CommandHandler<Object, C, Object>) commandHandler;
            CommandHandlerDefinition<Object, C, Object> definition = new CommandHandlerDefinition<>(
                    (Class<Object>) instanceClass, (Class<C>) command.getClass(), typedHandler, SourcingMode.NONE);
            boolean stateStubbed = stubs.stream().anyMatch(s -> s instanceof Stub.State);

            /*
             * premature check that all given events have their corresponding state-rebuilding handler, since
             * postponing that check into the interceptor chain would manifest as a failing command handler execution
             * only.
             */
            stubs.stream()
                    .filter(Stub.Event.class::isInstance)
                    .map(Stub.Event.class::cast)
                    .forEach(event -> {
                        if (stateRebuildingHandlerDefinitions.stream()
                                .filter(srhd -> srhd.instanceClass().equals(instanceClass))
                                .noneMatch(srhd -> srhd.eventClass()
                                        .isAssignableFrom(event.payload().getClass()))) {
                            throw new IllegalArgumentException(
                                    "No suitable state rebuilding handler definition found for event type: "
                                            + event.payload().getClass().getSimpleName());
                        }
                    });

            AtomicReference<@Nullable Object> currentStateHolder = new AtomicReference<>();
            AtomicReference<@Nullable String> sourcedEventIdHolder = new AtomicReference<>();
            AtomicReference<@Nullable CommandEventCapturer<Object>> capturerHolder = new AtomicReference<>();
            AtomicReference<List<CapturedEvent>> appendedEventsHolder = new AtomicReference<>(List.of());

            try {
                Object result = chain.execute(new CommandInvocation<>(command, metaData), c -> {
                    c.sourcing(new Sourcing(instanceClass, SourcingMode.NONE), () -> {
                        AtomicReference<StubResult> stubResult = new AtomicReference<>(new StubResult(
                                instanceClass,
                                null,
                                Instant.now(),
                                command,
                                stateRebuildingHandlerDefinitions,
                                Set.of(),
                                null));
                        stubs.forEach(stub -> stubResult.updateAndGet(acc -> acc.merge(stub, c)));
                        currentStateHolder.set(stubResult.get().state());
                        sourcedEventIdHolder.set(stubResult.get().latestSourcedEventId());

                        if (!stateStubbed) {
                            switch (command.getSubjectCondition()) {
                                case PRISTINE -> {
                                    if (stubResult.get().subjects().contains(command.getSubject())) {
                                        throw new CommandSubjectAlreadyExistsException(
                                                "subject already exists", command);
                                    }
                                }
                                case EXISTS -> {
                                    if (!stubResult.get().subjects().contains(command.getSubject())) {
                                        throw new CommandSubjectDoesNotExistException(
                                                "subject does not exist", command);
                                    }
                                }
                                case NONE -> {}
                            }
                        }
                    });

                    Object currentState = currentStateHolder.get();
                    CommandEventCapturer<Object> eventCapturer = new CommandEventCapturer<>(
                            currentState,
                            command.getSubject(),
                            stateRebuildingHandlerDefinitions.stream()
                                    .filter(it -> it.instanceClass().equals(instanceClass))
                                    .toList(),
                            c);
                    capturerHolder.set(eventCapturer);

                    Object handlerResult = c.handler(
                            new CommandHandlerInvocation(definition, currentState, sourcedEventIdHolder.get()),
                            () -> switch (typedHandler) {
                                case CommandHandler.ForCommand<Object, C, Object> handler ->
                                    handler.handle(command, eventCapturer);
                                case CommandHandler.ForInstanceAndCommand<Object, C, Object> handler ->
                                    handler.handle(currentState, command, eventCapturer);
                                case CommandHandler.ForInstanceAndCommandAndMetaData<Object, C, Object> handler ->
                                    handler.handle(currentState, command, metaData, eventCapturer);
                            });

                    if (!eventCapturer.getEvents().isEmpty()) {
                        Publish publishRequest = new Publish(List.copyOf(eventCapturer.getEvents()), List.of());
                        // reflect what actually gets appended: interceptors may rewrite the events at the publish stage
                        // (e.g. meta-data propagation), so expose the transformed events, not the raw captured ones
                        appendedEventsHolder.set(
                                c.publish(publishRequest, () -> publishRequest).events());
                    }
                    return handlerResult;
                });

                CommandEventCapturer<Object> capturer = Objects.requireNonNull(capturerHolder.get());
                return new Expect(command, capturer.previousInstance.get(), appendedEventsHolder.get(), result, null);
            } catch (Throwable t) {
                return new Expect(command, currentStateHolder.get(), List.of(), null, t);
            }
        }
    }

    /**
     * Initializes the {@link CommandHandlingTestFixture} and returns a {@link GivenDsl.Initial} instance for fluent
     * state stubbing prior to {@linkplain GivenDsl#when(Command) command execution}.
     *
     * @return a {@link GivenDsl.Initial} instance for further fluent API calls
     */
    public GivenDsl.Initial<C> given() {
        return new Given(commandHandler);
    }

    @SuppressWarnings("unchecked")
    private Given givenStubs(List stubs) {
        Given given = new Given(commandHandler);
        given.stubs.addAll(stubs);
        return given;
    }

    /**
     * Fluent API helper class encapsulating the results of a {@link CommandHandler} {@linkplain GivenDsl#when(Command)
     * execution} for assertion.
     *
     * <p>Assertions are organized into inner classes implementing the {@link ExpectDsl} sub-interfaces:
     *
     * <ul>
     *   <li>{@link Succeeding} for assertions after successful command execution
     *   <li>{@link Failing} for assertions after failed command execution
     *   <li>{@link AllEvents} for non-sequential assertions on all captured events
     *   <li>{@link NextEvents} for cursor-based sequential assertions on captured events
     * </ul>
     */
    public static class Expect implements ExpectDsl.Outcome {
        private final Command command;

        @Nullable
        private final Object state;

        final List<CapturedEvent> capturedEvents;

        @Nullable
        private final Object result;

        @Nullable
        private final Throwable throwable;

        private Expect(
                Command command,
                @Nullable Object state,
                List<CapturedEvent> capturedEvents,
                @Nullable Object result,
                @Nullable Throwable throwable) {
            this.command = command;
            this.state = state;
            this.capturedEvents = capturedEvents;
            this.result = result;
            this.throwable = throwable;
        }

        class Succeeding implements ExpectDsl.Succeeding {
            @Override
            public ExpectDsl.Succeeding withoutEvents() {
                if (!capturedEvents.isEmpty()) {
                    throw new AssertionError(
                            "Expected no events, but found " + capturedEvents.size() + ": " + capturedEvents);
                }
                return this;
            }

            @Override
            public ExpectDsl.Succeeding havingResult(@Nullable Object expected) {
                if (expected == null && result == null) return this;
                if (expected == null || !expected.equals(result)) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("Command handler result expected to be equal, but captured result:\n");
                    builder.append(result);
                    builder.append("\n");
                    builder.append("differs from:\n");
                    builder.append(expected);
                    throw new AssertionError(builder.toString());
                }
                return this;
            }

            @Override
            public ExpectDsl.Succeeding resultSatisfying(Consumer<@Nullable Object> assertion) {
                assertion.accept(result);
                return this;
            }

            @Override
            public ExpectDsl.Succeeding havingState(@Nullable Object expectedState) {
                if (state == null) throw new AssertionError("No state captured");
                if (!state.equals(expectedState)) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("State expected to be equal, but captured state:\n");
                    builder.append(state);
                    builder.append("\n");
                    builder.append("differs from:\n");
                    builder.append(expectedState);
                    throw new AssertionError(builder.toString());
                }
                return this;
            }

            @Override
            public ExpectDsl.Succeeding stateSatisfying(Consumer<@Nullable Object> assertion) {
                assertion.accept(state);
                return this;
            }

            @Override
            public <T> ExpectDsl.Succeeding stateExtracting(Function<@Nullable Object, T> extractor, T expected) {
                if (state == null) throw new AssertionError("No state captured");
                T extracted = extractor.apply(state);
                if (expected == null && extracted == null) return this;
                if (expected == null || (extracted == null) || !expected.equals(extracted)) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("Extracted state expected to be equal, but captured extracted state:\n");
                    builder.append(extracted);
                    builder.append("\n");
                    builder.append("differs from:\n");
                    builder.append(expected);
                    throw new AssertionError(builder.toString());
                }
                return this;
            }

            @Override
            public ExpectDsl.All allEvents() {
                return new AllEvents();
            }

            @Override
            public ExpectDsl.Next nextEvents() {
                return new NextEvents();
            }
        }

        class Failing implements ExpectDsl.Failing {
            @Override
            public <T extends Throwable> ExpectDsl.Failing throwing(Class<T> t) {
                if (throwable == null) throw new AssertionError("No exception occurred, as expected");
                if (!t.isAssignableFrom(throwable.getClass())) {
                    throw new AssertionError(
                            "Captured exception has wrong type: "
                                    + throwable.getClass().getSimpleName(),
                            throwable);
                }
                return this;
            }

            @Override
            public <T extends Throwable> ExpectDsl.Failing throwing(T t) {
                if (throwable == null) throw new AssertionError("No exception occurred, as expected");
                if (!t.getClass().isAssignableFrom(throwable.getClass())) {
                    throw new AssertionError(
                            "Captured exception has wrong type: expected "
                                    + t.getClass().getSimpleName() + " but got "
                                    + throwable.getClass().getSimpleName(),
                            throwable);
                }
                if (!Objects.equals(t.getMessage(), throwable.getMessage())) {
                    throw new AssertionError(
                            "Captured exception has wrong message: expected \""
                                    + t.getMessage() + "\" but got \""
                                    + throwable.getMessage() + "\"",
                            throwable);
                }
                return this;
            }

            @Override
            public <T extends Throwable> ExpectDsl.Failing throwsSatisfying(Consumer<T> assertion) {
                if (throwable == null) throw new AssertionError("No exception occurred, as expected");
                assertion.accept((T) throwable);
                return this;
            }

            @Override
            public ExpectDsl.Failing violatingAnyCondition() {
                if (!(throwable instanceof CommandSubjectAlreadyExistsException
                        || throwable instanceof CommandSubjectDoesNotExistException)) {
                    throw new AssertionError("Expected command subject condition not violated");
                }
                return this;
            }

            @Override
            public ExpectDsl.Failing violatingExactly(Command.SubjectCondition condition) {
                if (condition == Command.SubjectCondition.NONE) {
                    throw new IllegalArgumentException("Command subject condition NONE cannot be violated");
                }
                if (condition == Command.SubjectCondition.PRISTINE) {
                    if (!(throwable instanceof CommandSubjectAlreadyExistsException)) {
                        throw new AssertionError("Expected command subject condition not violated: PRISTINE");
                    }
                } else if (condition == Command.SubjectCondition.EXISTS) {
                    if (!(throwable instanceof CommandSubjectDoesNotExistException)) {
                        throw new AssertionError("Expected command subject condition not violated: EXISTS");
                    }
                }
                return this;
            }
        }

        class AllEvents implements ExpectDsl.All {
            @Override
            public ExpectDsl.All count(int count) {
                if (count < 0) throw new IllegalArgumentException("Count must be zero or positive");

                if (capturedEvents.size() != count) {
                    throw new AssertionError("Number of expected events differs, expected " + count + " but captured: "
                            + capturedEvents.size() + ":\n"
                            + capturedEvents.stream().map(CapturedEvent::event));
                }

                return this;
            }

            @Override
            public ExpectDsl.All single(Consumer<ExpectDsl.EventValidator> consumer) {
                if (capturedEvents.size() != 1) {
                    throw new AssertionError("Expected exactly one event, but captured " + capturedEvents.size());
                }
                EventValidatorImpl validator = new EventValidatorImpl(capturedEvents.get(0));
                consumer.accept(validator);
                return this;
            }

            @Override
            public ExpectDsl.All once(Consumer<ExpectDsl.EventValidator> consumer) {
                if (capturedEvents.isEmpty()) {
                    throw new AssertionError(
                            "Expected exactly one event matching validator, but no events were captured");
                }

                int matches = 0;

                for (CapturedEvent event : capturedEvents) {
                    try {
                        EventValidatorImpl validator = new EventValidatorImpl(event);
                        consumer.accept(validator);
                        matches++;
                    } catch (AssertionError e) {
                    }
                }

                if (matches == 0) {
                    throw new AssertionError("Expected exactly one event matching validator, but found none");
                } else if (matches > 1) {
                    throw new AssertionError("Expected exactly one event matching validator, but found " + matches);
                }

                return this;
            }

            @Override
            public ExpectDsl.All any(Consumer<ExpectDsl.EventValidator> consumer) {
                if (capturedEvents.isEmpty()) {
                    throw new AssertionError(
                            "Expected at least one event matching validator, but no events were captured");
                }

                boolean found = false;

                for (CapturedEvent event : capturedEvents) {
                    try {
                        EventValidatorImpl validator = new EventValidatorImpl(event);
                        consumer.accept(validator);
                        found = true;
                        break;
                    } catch (AssertionError e) {
                    }
                }

                if (!found) {
                    throw new AssertionError("Expected at least one event matching validator, but found none");
                }

                return this;
            }

            @Override
            public ExpectDsl.All every(Consumer<ExpectDsl.EventValidator> consumer) {
                if (capturedEvents.isEmpty()) {
                    throw new AssertionError("Expected every event to match validator, but no events were captured");
                }

                for (CapturedEvent event : capturedEvents) {
                    EventValidatorImpl validator = new EventValidatorImpl(event);
                    consumer.accept(validator);
                }

                return this;
            }

            @Override
            public ExpectDsl.All none(Consumer<ExpectDsl.EventValidator> consumer) {
                int matches = 0;
                for (CapturedEvent event : capturedEvents) {
                    try {
                        EventValidatorImpl validator = new EventValidatorImpl(event);
                        consumer.accept(validator);
                        matches++;
                    } catch (AssertionError e) {
                    }
                }
                if (matches > 0) {
                    throw new AssertionError("Expected no events matching validator, but found " + matches);
                }
                return this;
            }

            @Override
            public ExpectDsl.All exactly(Object... events) {
                if (capturedEvents.size() != events.length) {
                    throw new AssertionError(
                            "Expected exactly " + events.length + " events, but captured " + capturedEvents.size());
                }

                for (int i = 0; i < events.length; i++) {
                    Object captured = capturedEvents.get(i).event();
                    Object expected = events[i];
                    if (!captured.equals(expected)) {
                        StringBuilder builder = new StringBuilder();
                        builder.append("Event at position ")
                                .append(i)
                                .append(" expected to be equal, but captured event payload:\n");
                        builder.append(captured);
                        builder.append("\n");
                        builder.append("differs from:\n");
                        builder.append(expected);
                        throw new AssertionError(builder.toString());
                    }
                }

                return this;
            }

            public ExpectDsl.Succeeding and() {
                return new Succeeding();
            }
        }

        class NextEvents implements ExpectDsl.Next {
            private final ListIterator<CapturedEvent> nextEvent;

            NextEvents() {
                this.nextEvent = capturedEvents.listIterator();
            }

            @Override
            public ExpectDsl.Next skipping(int num) {
                for (int i = 0; i < num; i++) {
                    if (!nextEvent.hasNext()) {
                        throw new AssertionError("Cannot skip " + num + " events, only " + i + " remaining");
                    }
                    nextEvent.next();
                }
                return this;
            }

            @Override
            public ExpectDsl.Succeeding noMore() {
                if (nextEvent.hasNext()) {
                    throw new AssertionError("Expected no more events, but found more");
                }
                return new Succeeding();
            }

            @Override
            public ExpectDsl.Succeeding remaining(int count) {
                return skipping(count).noMore();
            }

            @Override
            public ExpectDsl.Succeeding single(Consumer<ExpectDsl.EventValidator> consumer) {
                if (!nextEvent.hasNext()) {
                    throw new AssertionError("Expected exactly one remaining event, but none remain");
                }
                CapturedEvent event = nextEvent.next();
                if (nextEvent.hasNext()) {
                    int extra = 1;
                    while (nextEvent.hasNext()) {
                        nextEvent.next();
                        extra++;
                    }
                    throw new AssertionError("Expected exactly one remaining event, but " + extra + " remain");
                }
                EventValidatorImpl validator = new EventValidatorImpl(event);
                consumer.accept(validator);
                return new Succeeding();
            }

            @Override
            public ExpectDsl.Succeeding once(Consumer<ExpectDsl.EventValidator> consumer) {
                int matches = 0;
                int seen = 0;
                while (nextEvent.hasNext()) {
                    CapturedEvent event = nextEvent.next();
                    seen++;
                    try {
                        EventValidatorImpl validator = new EventValidatorImpl(event);
                        consumer.accept(validator);
                        matches++;
                    } catch (AssertionError e) {
                    }
                }
                if (seen == 0) {
                    throw new AssertionError(
                            "Expected exactly one event matching validator in remaining events, but no events remain");
                }
                if (matches == 0) {
                    throw new AssertionError(
                            "Expected exactly one event matching validator in remaining events, but found none");
                } else if (matches > 1) {
                    throw new AssertionError(
                            "Expected exactly one event matching validator in remaining events, but found " + matches);
                }
                return new Succeeding();
            }

            @Override
            public ExpectDsl.Succeeding any(Consumer<ExpectDsl.EventValidator> consumer) {
                if (!nextEvent.hasNext()) {
                    throw new AssertionError(
                            "Expected at least one remaining event matching validator, but no events remain");
                }
                boolean found = false;
                while (nextEvent.hasNext()) {
                    CapturedEvent event = nextEvent.next();
                    try {
                        EventValidatorImpl validator = new EventValidatorImpl(event);
                        consumer.accept(validator);
                        found = true;
                    } catch (AssertionError e) {
                    }
                }
                if (!found) {
                    throw new AssertionError(
                            "Expected at least one remaining event matching validator, but found none");
                }
                return new Succeeding();
            }

            @Override
            public ExpectDsl.Succeeding every(Consumer<ExpectDsl.EventValidator> consumer) {
                if (!nextEvent.hasNext()) {
                    throw new AssertionError("Expected every remaining event to match validator, but no events remain");
                }
                while (nextEvent.hasNext()) {
                    CapturedEvent event = nextEvent.next();
                    EventValidatorImpl validator = new EventValidatorImpl(event);
                    consumer.accept(validator);
                }
                return new Succeeding();
            }

            @Override
            public ExpectDsl.Succeeding exactly(Object... events) {
                for (int i = 0; i < events.length; i++) {
                    if (!nextEvent.hasNext()) {
                        throw new AssertionError(
                                "Expected " + events.length + " more events, but only " + i + " remaining");
                    }
                    Object captured = nextEvent.next().event();
                    Object expected = events[i];
                    if (!captured.equals(expected)) {
                        StringBuilder builder = new StringBuilder();
                        builder.append("Event at position ")
                                .append(i)
                                .append(" expected to be equal, but captured event payload:\n");
                        builder.append(captured);
                        builder.append("\n");
                        builder.append("differs from:\n");
                        builder.append(expected);
                        throw new AssertionError(builder.toString());
                    }
                }

                if (nextEvent.hasNext()) {
                    int remaining = 0;
                    while (nextEvent.hasNext()) {
                        nextEvent.next();
                        remaining++;
                    }
                    throw new AssertionError(
                            "Expected exactly " + events.length + " events, but " + remaining + " more remaining");
                }

                return new Succeeding();
            }

            @Override
            public ExpectDsl.Succeeding none(Consumer<ExpectDsl.EventValidator> consumer) {
                int matches = 0;
                while (nextEvent.hasNext()) {
                    CapturedEvent event = nextEvent.next();
                    try {
                        EventValidatorImpl validator = new EventValidatorImpl(event);
                        consumer.accept(validator);
                        matches++;
                    } catch (AssertionError e) {
                    }
                }
                if (matches > 0) {
                    throw new AssertionError("Expected no remaining events matching validator, but found " + matches);
                }
                return new Succeeding();
            }

            @Override
            public ExpectDsl.Next matches(Consumer<ExpectDsl.EventValidator> consumer) {
                if (!nextEvent.hasNext()) {
                    throw new AssertionError("Expected an event at cursor position, but no more events remain");
                }
                CapturedEvent event = nextEvent.next();
                EventValidatorImpl validator = new EventValidatorImpl(event);
                consumer.accept(validator);
                return this;
            }

            public ExpectDsl.Succeeding and() {
                return new Succeeding();
            }
        }

        @Override
        public ExpectDsl.Succeeding succeeds() {
            if (throwable != null) {
                throw new AssertionError("Expected successful execution but got exception", throwable);
            }
            return new Succeeding();
        }

        @Override
        public ExpectDsl.Failing fails() {
            if (throwable == null) {
                throw new AssertionError("Expected failed execution but command succeeded");
            }
            return new Failing();
        }

        class EventValidatorImpl implements ExpectDsl.EventValidator {
            private final CapturedEvent event;

            public EventValidatorImpl(CapturedEvent event) {
                this.event = event;
            }

            @Override
            public ExpectDsl.EventValidator comparing(Object expectedEvent) {
                return asserting(eventAsserter -> eventAsserter.payload(expectedEvent));
            }

            @Override
            public <E> ExpectDsl.EventValidator satisfying(Consumer<E> assertion) {
                return asserting(eventAsserter -> eventAsserter.payloadSatisfying(assertion));
            }

            @Override
            public ExpectDsl.EventValidator asserting(Consumer<EventAsserting> asserting) {
                asserting.accept(new EventAsserter(command, event));
                return this;
            }

            @Override
            public ExpectDsl.EventValidator ofType(Class<?> type) {
                if (!type.isAssignableFrom(event.event().getClass())) {
                    throw new AssertionError("Event type not as expected: "
                            + event.event().getClass().getSimpleName() + ", expected: " + type.getSimpleName());
                }
                return this;
            }
        }
    }

    /**
     * Fluent API helper class for asserting captured events.
     *
     * @see ExpectDsl.EventValidator#asserting(Consumer)
     */
    public static class EventAsserter implements EventAsserting {

        private final Command command;
        private final CapturedEvent captured;

        EventAsserter(Command command, CapturedEvent captured) {
            this.command = command;
            this.captured = captured;
        }

        @Override
        public EventAsserter payloadType(Class<?> type) {
            return payloadSatisfying(payload -> {
                if (!type.isAssignableFrom(payload.getClass()))
                    throw new AssertionError(
                            "Event type not as expected: " + payload.getClass().getSimpleName());
            });
        }

        @Override
        public <E> EventAsserter payload(E expected) throws AssertionError {
            return payloadSatisfying(payload -> {
                if (!payload.equals(expected)) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("Event payloads expected to be equal, but captured event payload:\n");
                    builder.append(payload);
                    builder.append("\n");
                    builder.append("differs from:\n");
                    builder.append(expected);

                    throw new AssertionError(builder.toString());
                }
            });
        }

        @Override
        public <E, R> EventAsserter payloadExtracting(Function<E, R> extractor, R expected) throws AssertionError {
            return payloadSatisfying((E payload) -> {
                R extracted = extractor.apply(payload);

                if (expected == null && extracted == null) return;

                if (expected == null || !expected.equals(extracted)) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("Extracted payload expected to be equal, but captured extracted payload:\n");
                    builder.append(extracted);
                    builder.append("\n");
                    builder.append("differs from:\n");
                    builder.append(expected);

                    throw new AssertionError(builder.toString());
                }
            });
        }

        @Override
        public <E> EventAsserter payloadSatisfying(Consumer<E> assertion) throws AssertionError {
            assertion.accept((E) captured.event());
            return this;
        }

        @Override
        public EventAsserter metaData(Map<String, ?> expected) throws AssertionError {
            return metaDataSatisfying(metaData -> {
                if (!metaData.equals(expected)) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("Event meta-data expected to be equal, but captured event meta-data:\n");
                    builder.append(metaData);
                    builder.append("\n");
                    builder.append("differs from:\n");
                    builder.append(expected);

                    throw new AssertionError(builder.toString());
                }
            });
        }

        @Override
        public EventAsserter metaDataSatisfying(Consumer<Map<String, ?>> assertion) throws AssertionError {
            assertion.accept(captured.metaData());
            return this;
        }

        @Override
        public EventAsserter noMetaData() throws AssertionError {
            metaDataSatisfying(metaData -> {
                if (!metaData.isEmpty()) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("Empty event meta-data expected, but found:\n");
                    builder.append(metaData);

                    throw new AssertionError(builder.toString());
                }
            });
            return this;
        }

        @Override
        public EventAsserter subject(String expected) throws AssertionError {
            return subjectSatisfying(subject -> {
                if (!subject.equals(expected)) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("Event subject expected to be equal, but captured event subject:\n");
                    builder.append(subject);
                    builder.append("\n");
                    builder.append("differs from:\n");
                    builder.append(expected);

                    throw new AssertionError(builder.toString());
                }
            });
        }

        @Override
        public EventAsserter subjectSatisfying(Consumer<String> assertion) throws AssertionError {
            assertion.accept(captured.subject());
            return this;
        }

        @Override
        public EventAsserter commandSubject() throws AssertionError {
            return subject(command.getSubject());
        }
    }
}
