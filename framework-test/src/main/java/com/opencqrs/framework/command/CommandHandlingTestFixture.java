/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.metadata.PropagationMode;
import com.opencqrs.framework.metadata.PropagationUtil;
import com.opencqrs.framework.persistence.CapturedEvent;
import com.opencqrs.framework.types.EventTypeResolver;
import com.opencqrs.framework.upcaster.EventUpcaster;
import com.opencqrs.framework.command.ExpectDsl.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Test support for {@link CommandHandler} or {@link CommandHandlerDefinition}. This class can be used in favor of the
 * {@link CommandRouter} to test command handling logic without interacting with the
 * {@linkplain com.opencqrs.esdb.client.EsdbClient event store}, solely relying on a set of
 * {@link StateRebuildingHandlerDefinition}s. No {@linkplain EventUpcaster event upcasting},
 * {@linkplain EventTypeResolver event type resolution}, or {@linkplain PropagationUtil#propagateMetaData(Map, Map,
 * PropagationMode) meta-data propagation} is involved during test execution. <strong>Be aware that no
 * {@link Command.SubjectCondition}s will be checked either.</strong>
 *
 * <p>This class follows the <a href="https://martinfowler.com/bliki/GivenWhenThen.html">Given When Then</a> style of
 * representing tests with a fluent API supporting:
 *
 * <ol>
 *   <li>{@linkplain Given given} state initialization based on in-memory events and meta-data
 *   <li>{@link Command} {@linkplain Given#when(Command) execution} to execute the {@link CommandHandler} under test
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
 *          CommandHandlingTestFixtureNew
 *              // specify state rebuilding handler definitions to use
 *              .withStateRebuildingHandlerDefinitions(...)
 *              // specify command handler (definition) to test
 *              .using(...)
 *              .givenNothing()
 *              .when(
 *                  new AddBookCommand(
 *                      bookId,
 *                      "Tolkien",
 *                      "LOTR",
 *                      "DE234723432"
 *                  )
 *              )
 *              .expectSuccessfulExecution()
 *              .expectSingleEvent(
 *                  new BookAddedEvent(
 *                      bookId,
 *                      "Tolkien",
 *                      "LOTR",
 *                      "DE234723432"
 *                  )
 *              );
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
 *         <td>is set to {@link Command#getSubject()}, but can be overridden per event using {@link Given.GivenEvent#subject(String)}</td>
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
 *         <td>is set randomly, but can be overridden per event using {@link Given.GivenEvent#id(String)}</td>
 *     </tr>
 *     <tr>
 *         <td>{@link Event#time()}</td>
 *         <td>is set to the value from {@link CommandHandlingTestFixture#givenTime(Instant)}, or {@link Given#andGivenTime(Instant)}, or {@link Instant#now()} by default, but can be overridden per event using {@link Given.GivenEvent#time(Instant)}</td>
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
 * @param <I> the generic type of the instance to be event sourced before handling the command
 * @param <C> the command type
 * @param <R> the command execution result type
 */
public class CommandHandlingTestFixture<I, C extends Command, R> {

    private final Class<I> instanceClass;
    private final List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions;
    final CommandHandler<I, C, R> commandHandler;

    private CommandHandlingTestFixture(
            Class<I> instanceClass,
            List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions,
            CommandHandler<I, C, R> commandHandler) {
        this.instanceClass = instanceClass;
        this.stateRebuildingHandlerDefinitions = stateRebuildingHandlerDefinitions;
        this.commandHandler = commandHandler;
    }

    /**
     * Creates a {@link Builder} instance for the given {@link StateRebuildingHandlerDefinition}s.
     *
     * @param definitions the {@link StateRebuildingHandlerDefinition}s to be used to mimic event sourcing for the
     *     {@link CommandHandler} under test
     * @return a {@link Builder} instance
     * @param <I> the generic type of the instance to be event sourced before handling the command
     */
    public static <I> Builder<I> withStateRebuildingHandlerDefinitions(
            StateRebuildingHandlerDefinition<I, ?>... definitions) {
        return new Builder(Arrays.stream(definitions).toList());
    }

    /**
     * Builder for {@link CommandHandlingTestFixture}.
     *
     * @param <I> the generic type of the instance to be event sourced before handling the command
     */
    public static class Builder<I> {
        final List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions;

        private Builder(List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions) {
            this.stateRebuildingHandlerDefinitions = stateRebuildingHandlerDefinitions;
        }

        /**
         * Initializes the {@link CommandHandlingTestFixture} using the given {@link CommandHandlerDefinition}.
         *
         * @param definition the {@link CommandHandlerDefinition} under test
         * @return a fully initialized test fixture
         * @param <C> the command type
         * @param <R> the command execution result type
         */
        public <C extends Command, R> CommandHandlingTestFixture<I, C, R> using(
                CommandHandlerDefinition<I, C, R> definition) {
            return using(definition.instanceClass(), definition.handler());
        }

        /**
         * Initializes the {@link CommandHandlingTestFixture} using the given {@link CommandHandler}.
         *
         * @param instanceClass the state instance class
         * @param handler the {@link CommandHandler} under test
         * @return a fully initialized test fixture
         * @param <C> the command type
         * @param <R> the command execution result type
         */
        public <C extends Command, R> CommandHandlingTestFixture<I, C, R> using(
                Class<I> instanceClass, CommandHandler<I, C, R> handler) {
            return new CommandHandlingTestFixture<>(instanceClass, stateRebuildingHandlerDefinitions, handler);
        }
    }

    /**
     * Initializes the {@link CommandHandlingTestFixture} with no prior state. This should be used for testing
     * {@link CommandHandler}s using a pristine subject.
     *
     * @return a {@link Given} instance for further fluent API calls
     */
    public Given<C> givenNothing() {
        return new Given<>(commandHandler);
    }

    /**
     * Initializes the {@link CommandHandlingTestFixture} with no prior state, but a specific time-stamp. This should be
     * used, if {@linkplain Given#andGiven(Object...) further events} shall be applied with that {@link Event#time()}.
     *
     * @param time the initialization time-stamp
     * @return a {@link Given} instance for further fluent API calls
     */
    public Given<C> givenTime(Instant time) {
        return new Given<>(commandHandler, time);
    }

    /**
     * Initializes the {@link CommandHandlingTestFixture} with the given instance state. This can be used in favor of
     * the preferred {@linkplain #given(Object...) event based initialization}, if the latter one is too complex, for
     * instance requiring too many events.
     *
     * @param state the initialization state
     * @return a {@link Given} instance for further fluent API calls
     */
    public Given<C> givenState(I state) {
        return new Given<>(commandHandler, state);
    }

    /**
     * Initializes the {@link CommandHandlingTestFixture} with the given event payloads applied in order to the
     * {@linkplain CommandHandlingTestFixture#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
     * configured} {@link StateRebuildingHandlerDefinition}s to reconstruct the instance state.
     *
     * @param events the events to be applied
     * @return a {@link Given} instance for further fluent API calls
     */
    public Given<C> given(Object... events) {
        return new Given<>(commandHandler, events);
    }

    /**
     * Initializes the {@link CommandHandlingTestFixture} using the {@link Given.GivenEvent} consumer for more
     * fine-grained event specification of the events and their meta-data, which will be applied in order to the
     * {@linkplain CommandHandlingTestFixture#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
     * configured} {@link StateRebuildingHandlerDefinition}s to reconstruct the instance state.
     *
     * @param event event specification consumer, at least {@link Given.GivenEvent#payload(Object)} must be called
     * @return a {@link Given} instance for further fluent API calls
     */
    public Given<C> given(Consumer<Given.GivenEvent<I>> event) {
        return new Given<>(commandHandler, event);
    }

    /**
     * Execute the given {@link Command} without meta-data using the {@link CommandHandlerDefinition} encapsulated
     * within the given fixture to capture any new events published, which in turn will be applied to {@code this}. This
     * is mostly used for {@link CommandHandler}s publishing a lot or more complex events, in favor of stubbing the
     * events directly.
     *
     * <p><strong>Be aware that stubbed events can be specified more precisely than captured ones, since the
     * encapsulated {@link CommandHandler} is responsible for event publication using the {@link CommandEventPublisher}.
     * Hence, {@link Given.GivenEvent#time(Instant)} and {@link Given.GivenEvent#id(String)} cannot be specified using
     * this approach.</strong>
     *
     * @param fixture the fixture holding the command to execute
     * @param command the command to execute for event capturing
     * @return a {@code this} for further fluent API calls
     * @throws AssertionError in case the given command did not {@linkplain Expect#expectSuccessfulExecution() execute
     *     successfully}
     * @param <AnotherCommand> generic command type to execute
     */
    public <AnotherCommand extends Command> Given<C> givenCommand(
            CommandHandlingTestFixture<I, AnotherCommand, ?> fixture, AnotherCommand command) {
        return givenNothing().andGivenCommand(fixture, command);
    }

    /**
     * Execute the given {@link Command} with meta-data using the {@link CommandHandlerDefinition} encapsulated within
     * the given fixture to capture any new events published, which in turn will be applied to {@code this}. This is
     * mostly used for {@link CommandHandler}s publishing a lot or more complex events, in favor of stubbing the events
     * directly.
     *
     * <p><strong>Be aware that stubbed events can be specified more precisely than captured ones, since the
     * encapsulated {@link CommandHandler} is responsible for event publication using the {@link CommandEventPublisher}.
     * Hence, {@link Given.GivenEvent#time(Instant)} and {@link Given.GivenEvent#id(String)} cannot be specified using
     * this approach.</strong>
     *
     * @param fixture the fixture holding the command to execute
     * @param command the command to execute for event capturing
     * @param metaData the command meta-data
     * @return a {@code this} for further fluent API calls
     * @throws AssertionError in case the given command did not {@linkplain Expect#expectSuccessfulExecution() execute
     *     successfully}
     * @param <AnotherCommand> generic command type to execute
     */
    public <AnotherCommand extends Command> Given<C> givenCommand(
            CommandHandlingTestFixture<I, AnotherCommand, ?> fixture, AnotherCommand command, Map<String, ?> metaData) {
        return givenNothing().andGivenCommand(fixture, command, metaData);
    }

    private Given<C> givenStubs(List<Given.Stub<I>> stubs) {
        return new Given<>(commandHandler, stubs);
    }

    /**
     * Fluent API helper class encapsulating the current state stubbing prior to {@linkplain #when(Command) executing}
     * the {@link CommandHandler} under test.
     *
     * @param <C> the command type
     */
    public class Given<C extends Command> {

        sealed interface Stub<I> {

            record State<I>(I state) implements Stub<I> {}

            record Time<I>(Instant time) implements Stub<I> {}

            record TimeDelta<I>(Duration duration) implements Stub<I> {}

            record Event<I>(String id, Instant time, String subject, Object payload, Map<String, ?> metaData)
                    implements Stub<I> {

                public Event() {
                    this(null, null, null, null, null);
                }

                public Stub.Event<I> withId(String id) {
                    return new Stub.Event<>(id, time(), subject(), payload(), metaData());
                }

                public Stub.Event<I> withTime(Instant time) {
                    return new Stub.Event<>(id(), time, subject(), payload(), metaData());
                }

                public Stub.Event<I> withSubject(String subject) {
                    return new Stub.Event<>(id(), time(), subject, payload(), metaData());
                }

                public Stub.Event<I> withPayload(Object payload) {
                    return new Stub.Event<>(id(), time(), subject(), payload, metaData());
                }

                public Stub.Event<I> withMetaData(Map<String, ?> metaData) {
                    return new Stub.Event<>(id(), time(), subject(), payload(), metaData);
                }
            }
        }

        record Result<I>(
                Class<I> instanceClass,
                I state,
                Instant time,
                Command command,
                List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions) {

            public Result<I> withState(I newState) {
                return new Result<>(instanceClass(), newState, time(), command(), stateRebuildingHandlerDefinitions());
            }

            public Result<I> withTime(Instant newTime) {
                return new Result<>(instanceClass(), state(), newTime, command(), stateRebuildingHandlerDefinitions());
            }

            public Result<I> merge(Stub<I> stub) {
                return switch (stub) {
                    case Stub.State<I> state -> {
                        if (state() != null) throw new IllegalArgumentException("givenState() must only be used once");
                        yield withState(state.state());
                    }
                    case Stub.Time<I> time -> withTime(time.time());
                    case Stub.TimeDelta<I> timeDelta -> withTime(time().plus(timeDelta.duration()));
                    case Stub.Event<I> event -> {
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
                        AtomicReference<I> reference = new AtomicReference<>(state());
                        if (!Util.applyUsingHandlers(
                                stateRebuildingHandlerDefinitions.stream()
                                        .filter(srhd -> srhd.instanceClass().equals(instanceClass()))
                                        .toList(),
                                reference,
                                rawEvent.subject(),
                                event.payload(),
                                event.metaData() != null ? event.metaData() : Map.of(),
                                rawEvent)) {
                            throw new IllegalArgumentException(
                                    "No suitable state rebuilding handler definition found for event type: "
                                            + event.payload().getClass().getSimpleName());
                        }
                        yield withState(reference.get());
                    }
                };
            }
        }

        /**
         * Fluent API helper class for fine granular specification of a single <strong>given</strong> event.
         *
         * @param <I> the generic type of the instance to be event sourced before handling the command
         */
        public static class GivenEvent<I> {

            Stub.Event<I> stub;

            GivenEvent(Stub.Event<I> stub) {
                this.stub = stub;
            }

            /**
             * Specifies the {@link Event#id()} to be used.
             *
             * @param id the event id
             * @return {@code this} for further specification calls
             */
            public GivenEvent<I> id(String id) {
                stub = stub.withId(id);
                return this;
            }

            /**
             * Specifies the {@link Event#time()} to be used.
             *
             * @param time the event time-stamp
             * @return {@code this} for further specification calls
             */
            public GivenEvent<I> time(Instant time) {
                stub = stub.withTime(time);
                return this;
            }

            /**
             * Specifies the {@link Event#subject()} to be used.
             *
             * @param subject the event subject
             * @return {@code this} for further specification calls
             */
            public GivenEvent<I> subject(String subject) {
                stub = stub.withSubject(subject);
                return this;
            }

            /**
             * Specifies the event payload passed any of the {@link StateRebuildingHandler} variants.
             *
             * @param payload the event payload
             * @return {@code this} for further specification calls
             * @param <E> the generic payload type
             */
            public <E> GivenEvent<I> payload(E payload) {
                stub = stub.withPayload(payload);
                return this;
            }

            /**
             * Specifies the event meta-data passed to appropriate {@link StateRebuildingHandler} variants.
             *
             * @param metaData the event meta-data
             * @return {@code this} for further specification calls
             */
            public GivenEvent<I> metaData(Map<String, ?> metaData) {
                stub = stub.withMetaData(metaData);
                return this;
            }
        }

        private final List<Stub<I>> stubs = new ArrayList<>();
        private final CommandHandler<I, C, R> commandHandler;

        private void addToStubs(Consumer<GivenEvent<I>> givenEvent) {
            GivenEvent<I> capture = new GivenEvent<>(new Stub.Event<>());
            givenEvent.accept(capture);

            Stub.Event<I> stub = capture.stub;
            if (stub.payload() == null) {
                throw new IllegalArgumentException("Event payload must be specified using payload()");
            }
            stubs.add(stub);
        }

        private Given(CommandHandler<I, C, R> commandHandler, Instant time) {
            this.commandHandler = commandHandler;
            stubs.add(new Stub.Time<>(time));
        }

        private Given(CommandHandler<I, C, R> commandHandler) {
            this(commandHandler, Instant.now());
        }

        private Given(CommandHandler<I, C, R> commandHandler, I state) {
            this(commandHandler);
            stubs.add(new Stub.State<>(state));
        }

        private Given(CommandHandler<I, C, R> commandHandler, Consumer<GivenEvent<I>> givenEvent) {
            this(commandHandler);

            addToStubs(givenEvent);
        }

        private Given(CommandHandler<I, C, R> commandHandler, Object... events) {
            this(commandHandler);

            for (Object e : events) {
                addToStubs(given -> given.payload(e));
            }
        }

        private Given(CommandHandler<I, C, R> commandHandler, List<Stub<I>> stubs) {
            this(commandHandler);
            this.stubs.addAll(stubs);
        }

        /**
         * Uses any previously configured {@linkplain Given stubbings} to execute the given {@link Command} without
         * meta-data using the {@link CommandHandlerDefinition} encapsulated within the given fixture to capture any new
         * events published, which in turn will be applied to {@code this}. This is mostly used for
         * {@link CommandHandler}s publishing a lot or more complex events, in favor of stubbing the events directly.
         *
         * <p><strong>Be aware that stubbed events can be specified more precisely than captured ones, since the
         * encapsulated {@link CommandHandler} is responsible for event publication using the
         * {@link CommandEventPublisher}. Hence, {@link GivenEvent#time(Instant)} and {@link GivenEvent#id(String)}
         * cannot be specified using this approach.</strong>
         *
         * @param fixture the fixture holding the command to execute
         * @param command the command to execute for event capturing
         * @return a {@code this} for further fluent API calls
         * @throws AssertionError in case the given command did not {@linkplain Expect#expectSuccessfulExecution()
         *     execute successfully}
         * @param <AnotherCommand> generic command type to execute
         */
        public <AnotherCommand extends Command> Given<C> andGivenCommand(
                CommandHandlingTestFixture<I, AnotherCommand, ?> fixture, AnotherCommand command)
                throws AssertionError {
            return andGivenCommand(fixture, command, Map.of());
        }

        /**
         * Uses any previously configured {@linkplain Given stubbings} to execute the given {@link Command} with
         * meta-data using the {@link CommandHandlerDefinition} encapsulated within the given fixture to capture any new
         * events published, which in turn will be applied to {@code this}. This is mostly used for
         * {@link CommandHandler}s publishing a lot or more complex events, in favor of stubbing the events directly.
         *
         * <p><strong>Be aware that stubbed events can be specified more precisely than captured ones, since the
         * encapsulated {@link CommandHandler} is responsible for event publication using the
         * {@link CommandEventPublisher}. Hence, {@link GivenEvent#time(Instant)} and {@link GivenEvent#id(String)}
         * cannot be specified using this approach.</strong>
         *
         * @param fixture the fixture holding the command to execute
         * @param command the command to execute for event capturing
         * @param metaData the command meta-data
         * @return a {@code this} for further fluent API calls
         * @throws AssertionError in case the given command did not {@linkplain Expect#expectSuccessfulExecution()
         *     execute successfully}
         * @param <AnotherCommand> generic command type to execute
         */
        public <AnotherCommand extends Command> Given<C> andGivenCommand(
                CommandHandlingTestFixture<I, AnotherCommand, ?> fixture,
                AnotherCommand command,
                Map<String, ?> metaData)
                throws AssertionError {
            fixture.givenStubs(stubs)
                    .when(command, metaData)
                    .expectSuccessfulExecution()
                    .capturedEvents
                    .forEach(capturedEvent -> andGiven(event -> event.subject(capturedEvent.subject())
                            .payload(capturedEvent.event())
                            .metaData(capturedEvent.metaData())));

            return this;
        }

        /**
         * Configures a (new) time-stamp to be used, if {@linkplain Given#andGiven(Object...) further events} shall be
         * applied with that {@link Event#time()}.
         *
         * @param time the new time-stamp to be used
         * @return a {@code this} for further fluent API calls
         */
        public Given<C> andGivenTime(Instant time) {
            stubs.add(new Stub.Time<>(time));
            return this;
        }

        /**
         * Shifts the previously configured time-stamp by given duration, if {@linkplain Given#andGiven(Object...)
         * further events} shall be applied with that {@link Event#time()}.
         *
         * @param duration the time-stamp delta to be applied
         * @return a {@code this} for further fluent API calls
         */
        public Given<C> andGivenTimeDelta(Duration duration) {
            stubs.add(new Stub.TimeDelta<>(duration));
            return this;
        }

        /**
         * Applies further events in order to the
         * {@linkplain CommandHandlingTestFixture#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
         * configured} {@link StateRebuildingHandlerDefinition}s to update the instance state.
         *
         * @param events the events to be applied
         * @return a {@code this} for further fluent API calls
         */
        public Given<C> andGiven(Object... events) {
            for (Object e : events) {
                addToStubs(given -> given.payload(e));
            }
            return this;
        }

        /**
         * Applies a further event using the {@link Given.GivenEvent} consumer for more fine-grained event specification
         * of the event and its meta-data to update the instance state.
         *
         * @param event event specification consumer, at least {@link Given.GivenEvent#payload(Object)} must be called
         * @return a {@code this} for further fluent API calls
         */
        public Given<C> andGiven(Consumer<GivenEvent<I>> event) {
            addToStubs(event);
            return this;
        }

        /**
         * Executes the {@linkplain Builder#using(Class, CommandHandler)} configured} {@link CommandHandler} using the
         * previously initialized instance state. All events {@linkplain CommandEventPublisher published} as part of the
         * execution as well as any exceptions thrown will be captured in-memory for further {@linkplain Expect
         * assertion}.
         *
         * <p>Events will get applied to the current instance state using the
         * {@linkplain CommandHandlingTestFixture#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
         * configured} {@link StateRebuildingHandlerDefinition}s, if and only if the {@link CommandHandler} terminates
         * non exceptionally. This mimics the {@link CommandRouter} behaviour, as events will only be published to the
         * event store for successful executions.<b>Be aware, however, that instance mutability cannot be enforced.
         * Hence, {@link CommandHandler}s publishing events, which result in mutated instance state, cannot be rolled
         * back.</b>
         *
         * @param command the {@link Command} to execute
         * @return an {@link Expect} instance for further fluent API calls
         */
        public Expect when(C command) {
            return when(command, Map.of());
        }

        /**
         * Executes the {@linkplain Builder#using(Class, CommandHandler)} configured} {@link CommandHandler} using the
         * previously initialized instance state. All events {@linkplain CommandEventPublisher published} as part of the
         * execution as well as any exceptions thrown will be captured in-memory for further {@linkplain Expect
         * assertion}.
         *
         * <p>Events will get applied to the current instance state using the
         * {@linkplain CommandHandlingTestFixture#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
         * configured} {@link StateRebuildingHandlerDefinition}s, if and only if the {@link CommandHandler} terminates
         * non exceptionally. This mimics the {@link CommandRouter} behaviour, as events will only be published to the
         * event store for successful executions.<b>Be aware, however, that instance mutability cannot be enforced.
         * Hence, {@link CommandHandler}s publishing events, which result in mutated instance state, cannot be rolled
         * back.</b>
         *
         * @param command the {@link Command} to execute
         * @param metaData additional command meta-data supplied to the {@link CommandHandler}
         * @return an {@link Expect} instance for further fluent API calls
         */
        public Expect when(C command, Map<String, ?> metaData) {
            AtomicReference<Result<I>> stubResult = new AtomicReference<>(
                    new Result<>(instanceClass, null, null, command, stateRebuildingHandlerDefinitions));
            stubs.forEach(stub -> stubResult.updateAndGet(result -> result.merge(stub)));

            I currentState = stubResult.get().state();
            CommandEventCapturer<I> eventCapturer = new CommandEventCapturer<>(
                    currentState,
                    command.getSubject(),
                    stateRebuildingHandlerDefinitions.stream()
                            .filter(it -> it.instanceClass().equals(instanceClass))
                            .toList());
            try {
                R result =
                        switch (commandHandler) {
                            case CommandHandler.ForCommand<I, C, R> handler -> handler.handle(command, eventCapturer);
                            case CommandHandler.ForInstanceAndCommand<I, C, R> handler ->
                                    handler.handle(currentState, command, eventCapturer);
                            case CommandHandler.ForInstanceAndCommandAndMetaData<I, C, R> handler ->
                                    handler.handle(currentState, command, metaData, eventCapturer);
                        };
                return new Expect(
                        command, eventCapturer.previousInstance.get(), eventCapturer.getEvents(), result, null);
            } catch (Throwable t) {
                return new Expect(command, currentState, List.of(), null, t);
            }
        }
    }

    /**
     * Fluent API helper class encapsulating the results of a {@link CommandHandler} {@linkplain Given#when(Command)
     * execution} for assertion.
     *
     * <p>This class provides stateful event assertions, effectively iterating through the events published during a
     * {@linkplain Given#when(Command) command handler execution}. Methods within {@code this} annotated using
     */
    public class Expect implements Initializing {
        private final Command command;
        private final I state;
        private final List<CapturedEvent> capturedEvents;
        private final ListIterator<CapturedEvent> nextEvent;
        private final R result;
        private final Throwable throwable;

        private Expect(Command command, I state, List<CapturedEvent> capturedEvents, R result, Throwable throwable) {
            this.command = command;
            this.state = state;
            this.capturedEvents = capturedEvents;
            this.nextEvent = capturedEvents.listIterator();
            this.result = result;
            this.throwable = throwable;
        }

        @Override
        public Succeeds succeeds() {
            if(throwable != null) {
                throw new AssertionError("Expected successful execution but got exception", throwable);
            }

            return new Succeeds(this);
        }

        @Override
        public Failing fails() {
            if (throwable == null) {
                throw new AssertionError("Expected failed execution but command succeeded");
            }
            return new Fails();
        }
    }

    public class Common implements ExpectDsl.Common<I, R> {

        @Override
        public All allEvents() {
            return null;
        }

        @Override
        public Next nextEvents() {
            return null;
        }
    }

    public class Succeeds implements Succeeding<I, R> {

        private final Expect expect;

        Succeeds(Expect expect) {
            this.expect = expect;
        }

        @Override
        public Succeeding<I, R> withoutEvents() {
            if (!this.expect.capturedEvents.isEmpty()) {
                throw new AssertionError("Expected no events, but found " + this.expect.capturedEvents.size());
            }
            return this;
        }

        @Override
        public Common and() {
            return null;
        }

        @Override
        public Succeeds havingResult(R expected) {
            return null;
        }

        @Override
        public <T> Succeeding<I, R> stateExtracting(Function<I, T> extractor, T expected) {
            return null;
        }

        @Override
        public Succeeds stateSatisfying(Consumer<I> assertion) {
            return null;
        }

        @Override
        public Succeeds havingState(I state) {
            return null;
        }

        @Override
        public Succeeds resultSatisfying(Consumer<R> assertion) {
            return null;
        }
    }

    public class Fails implements Failing<I, R> {

        @Override
        public <T> Failing<I, R> throwing(Class<T> t) {
            return null;
        }

        @Override
        public <T> Failing<I, R> throwsSatisfying(Consumer<T> assertion) {
            return null;
        }

        @Override
        public Failing<I, R> violatingAnyCondition() {
            return null;
        }

        @Override
        public Failing<I, R> violatingExactly(Command.SubjectCondition condition) {
            return null;
        }
    }

    public class All implements ExpectDsl.All<I, R> {

        @Override
        public All count(int count) {
            return null;
        }

        @Override
        public <E> All single(E payload) {
            return null;
        }

        @Override
        public All singleAsserting(Consumer<EventAsserting> assertion) {
            return null;
        }

        @Override
        public All singleType(Class<?> type) {
            return null;
        }

        @Override
        public <E> All singleSatisfying(Consumer<E> assertion) {
            return null;
        }

        @Override
        public All exactly(Object event, Object... events) {
            return null;
        }

        @Override
        public All inAnyOrder(Object... events) {
            return null;
        }

        @Override
        public All expectAnyEvent(Consumer<EventAsserting> assertion) {
            return null;
        }

        @Override
        public <E> All any(E payload) {
            return null;
        }

        @Override
        public All anySatisfying(Consumer<EventAsserting> assertion) {
            return null;
        }

        @Override
        public All anyType(Class<?> type) {
            return null;
        }

        @Override
        public All none() {
            return null;
        }

        @Override
        public All notContaining(Consumer<EventAsserting> assertion) {
            return null;
        }

        @Override
        public All notContainingType(Class<?> type) {
            return null;
        }

        @Override
        public All allSatisfying(Consumer<List<Object>> assertion, Consumer<List<Object>>... assertions) {
            return null;
        }

        @Override
        public Common and() {
            return null;
        }
    }

    public class Next implements ExpectDsl.Next {

        @Override
        public Next skip(int num) {
            return null;
        }

        @Override
        public Next andNoMore() {
            return null;
        }

        @Override
        public Next exactly(Object event, Object... events) {
            return null;
        }

        @Override
        public Next inAnyOrder(Object event, Object... events) {
            return null;
        }

        @Override
        public Next any(Object e) {
            return null;
        }

        @Override
        public Next satisfying(Consumer assertion) {
            return null;
        }

        @Override
        public Next comparingType(Class type) {
            return null;
        }

        @Override
        public Next comparing(Consumer assertion) {
            return null;
        }

        @Override
        public Next comparing(Object payload) {
            return null;
        }

        @Override
        public Next matchingTypesInAnyOrder(Class type, Class[] types) {
            return null;
        }

        @Override
        public Next matchingTypes(Class type, Class[] types) {
            return null;
        }

        @Override
        public Common and() {
            return null;
        }
    }

    /**
     * Fluent API helper class for asserting a captured event.
     *
     * @see CommandHandlingTestFixture.Expect#expectNextEvent(Consumer)
     * @see CommandHandlingTestFixture.Expect#expectSingleEvent(Consumer)
     * @see CommandHandlingTestFixture.Expect#expectAnyEvent(Consumer)
     */
    public static class EventAsserter implements EventAsserting {

        private final Command command;
        private final CapturedEvent captured;

        EventAsserter(Command command, CapturedEvent captured) {
            this.command = command;
            this.captured = captured;
        }

        /**
         * Asserts that the captured event payload {@linkplain Class#isAssignableFrom(Class)} is assignable to} the
         * expected type
         *
         * @param type the assignable type
         * @return {@code this} for further assertions
         */
        @Override
        public EventAsserter payloadType(Class<?> type) {
            return payloadSatisfying(payload -> {
                if (!type.isAssignableFrom(payload.getClass()))
                    throw new AssertionError(
                            "Event type not as expected: " + payload.getClass().getSimpleName());
            });
        }

        /**
         * Asserts that the captured event payload {@linkplain Object#equals(Object) is equal} to the expected one.
         *
         * @param expected the expected event payload
         * @return {@code this} for further assertions
         * @throws AssertionError if the captured event payload is not equal to the expected one
         * @param <E> the generic payload type
         */
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

        /**
         * Asserts that the captured event's payload property from the given extractor function
         * {@linkplain Object#equals(Object) is equal} to the expected one.
         *
         * @param extractor the extractor function
         * @param expected the expected event payload property, may be {@code null}
         * @return {@code this} for further assertions
         * @throws AssertionError if the extracted payload property is not equal to the expected value
         * @param <E> the generic payload type
         * @param <R> the generic extraction result type
         */
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

        /**
         * Verifies that the captured event payload asserts successfully using the given custom assertion.
         *
         * @param assertion custom assertion
         * @return {@code this} for further assertions
         * @throws AssertionError if thrown by the custom assertion
         * @param <E> the generic payload type
         */
        @Override
        public <E> EventAsserter payloadSatisfying(Consumer<E> assertion) throws AssertionError {
            assertion.accept((E) captured.event());
            return this;
        }

        /**
         * Asserts that the captured event meta-data {@linkplain Object#equals(Object) is equal} to the expected one.
         *
         * @param expected the expected event meta-data
         * @return {@code this} for further assertions
         * @throws AssertionError if the meta-data of the event is not as expected
         */
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

        /**
         * Verifies that the captured event meta-data asserts successfully using the given custom assertion.
         *
         * @param assertion custom assertion
         * @return {@code this} for further assertions
         * @throws AssertionError if thrown by the custom assertion
         */
        @Override
        public EventAsserter metaDataSatisfying(Consumer<Map<String, ?>> assertion) throws AssertionError {
            assertion.accept(captured.metaData());
            return this;
        }

        /**
         * Verifies that no (aka empty) meta-data was published for the captured event.
         *
         * @return {@code this} for further assertions
         * @throws AssertionError if the meta-data is not empty
         */
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

        /**
         * Asserts that the captured event subject {@linkplain Object#equals(Object) is equal} to the expected one.
         *
         * @param expected the expected event subject
         * @return {@code this} for further assertions
         * @throws AssertionError if the event subject is not as expected
         */
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

        /**
         * Verifies that the captured event subject asserts successfully using the given custom assertion.
         *
         * @param assertion custom assertion
         * @return {@code this} for further assertions
         * @throws AssertionError if thrown by the custom assertion
         */
        @Override
        public EventAsserter subjectSatisfying(Consumer<String> assertion) throws AssertionError {
            assertion.accept(captured.subject());
            return this;
        }

        /**
         * Verifies that the captured event was published for the {@link Command#getSubject()}.
         *
         * @return {@code this} for further assertions
         * @throws AssertionError if the published event subject differs from the command's subject
         */
        @Override
        public EventAsserter commandSubject() throws AssertionError {
            return subject(command.getSubject());
        }
    }
}
