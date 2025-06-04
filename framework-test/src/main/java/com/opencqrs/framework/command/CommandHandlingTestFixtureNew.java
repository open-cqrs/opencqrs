/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.metadata.PropagationMode;
import com.opencqrs.framework.metadata.PropagationUtil;
import com.opencqrs.framework.persistence.CapturedEvent;
import com.opencqrs.framework.types.EventTypeResolver;
import com.opencqrs.framework.upcaster.EventUpcaster;
import java.lang.annotation.*;
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
 *         <td>is set to the value from {@link CommandHandlingTestFixtureNew#givenTime(Instant)}, or {@link Given#andGivenTime(Instant)}, or {@link Instant#now()} by default, but can be overridden per event using {@link Given.GivenEvent#time(Instant)}</td>
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
public class CommandHandlingTestFixtureNew<I, C extends Command, R> {

    private final Class<I> instanceClass;
    private final List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions;
    final CommandHandler<I, C, R> commandHandler;

    private CommandHandlingTestFixtureNew(
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
     * Builder for {@link CommandHandlingTestFixtureNew}.
     *
     * @param <I> the generic type of the instance to be event sourced before handling the command
     */
    public static class Builder<I> {
        final List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions;

        private Builder(List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions) {
            this.stateRebuildingHandlerDefinitions = stateRebuildingHandlerDefinitions;
        }

        /**
         * Initializes the {@link CommandHandlingTestFixtureNew} using the given {@link CommandHandlerDefinition}.
         *
         * @param definition the {@link CommandHandlerDefinition} under test
         * @return a fully initialized test fixture
         * @param <C> the command type
         * @param <R> the command execution result type
         */
        public <C extends Command, R> CommandHandlingTestFixtureNew<I, C, R> using(
                CommandHandlerDefinition<I, C, R> definition) {
            return using(definition.instanceClass(), definition.handler());
        }

        /**
         * Initializes the {@link CommandHandlingTestFixtureNew} using the given {@link CommandHandler}.
         *
         * @param instanceClass the state instance class
         * @param handler the {@link CommandHandler} under test
         * @return a fully initialized test fixture
         * @param <C> the command type
         * @param <R> the command execution result type
         */
        public <C extends Command, R> CommandHandlingTestFixtureNew<I, C, R> using(
                Class<I> instanceClass, CommandHandler<I, C, R> handler) {
            return new CommandHandlingTestFixtureNew<>(instanceClass, stateRebuildingHandlerDefinitions, handler);
        }
    }

    /**
     * Initializes the {@link CommandHandlingTestFixtureNew} with no prior state. This should be used for testing
     * {@link CommandHandler}s using a pristine subject.
     *
     * @return a {@link Given} instance for further fluent API calls
     */
    public Given<C> givenNothing() {
        return new Given<>(commandHandler);
    }

    /**
     * Initializes the {@link CommandHandlingTestFixtureNew} with no prior state, but a specific time-stamp. This should be
     * used, if {@linkplain Given#andGiven(Object...) further events} shall be applied with that {@link Event#time()}.
     *
     * @param time the initialization time-stamp
     * @return a {@link Given} instance for further fluent API calls
     */
    public Given<C> givenTime(Instant time) {
        return new Given<>(commandHandler, time);
    }

    /**
     * Initializes the {@link CommandHandlingTestFixtureNew} with the given instance state. This can be used in favor of
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
     * Initializes the {@link CommandHandlingTestFixtureNew} with the given event payloads applied in order to the
     * {@linkplain CommandHandlingTestFixtureNew#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
     * configured} {@link StateRebuildingHandlerDefinition}s to reconstruct the instance state.
     *
     * @param events the events to be applied
     * @return a {@link Given} instance for further fluent API calls
     */
    public Given<C> given(Object... events) {
        return new Given<>(commandHandler, events);
    }

    /**
     * Initializes the {@link CommandHandlingTestFixtureNew} using the {@link Given.GivenEvent} consumer for more
     * fine-grained event specification of the events and their meta-data, which will be applied in order to the
     * {@linkplain CommandHandlingTestFixtureNew#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
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
            CommandHandlingTestFixtureNew<I, AnotherCommand, ?> fixture, AnotherCommand command) {
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
            CommandHandlingTestFixtureNew<I, AnotherCommand, ?> fixture, AnotherCommand command, Map<String, ?> metaData) {
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
                                CommandHandlingTestFixtureNew.class.getSimpleName(),
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
                CommandHandlingTestFixtureNew<I, AnotherCommand, ?> fixture, AnotherCommand command)
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
                CommandHandlingTestFixtureNew<I, AnotherCommand, ?> fixture,
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
         * {@linkplain CommandHandlingTestFixtureNew#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
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
         * {@linkplain CommandHandlingTestFixtureNew#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
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
         * {@linkplain CommandHandlingTestFixtureNew#withStateRebuildingHandlerDefinitions(StateRebuildingHandlerDefinition[])
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
     * {@link StatefulAssertion} represent stateful assertions and thus both rely on previous stateful assertions and
     * proceed through the captured event stream, if invoked.
     */
    public class Expect {

        /**
         * Marker annotation for assertion methods within {@link CommandHandlingTestFixtureNew.Expect} which proceed
         * through the captured event stream, if invoked.
         */
        @Documented
        @Target(ElementType.METHOD)
        @Retention(RetentionPolicy.SOURCE)
        public @interface StatefulAssertion {}

        private boolean eventVerified = false;
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

        /**
         * Asserts that the {@link CommandHandler} {@linkplain Given#when(Command) executed} non exceptionally.
         *
         * @return {@code this} for further assertions
         * @throws AssertionError if the {@link CommandHandler} terminated exceptionally
         */
        public Expect expectSuccessfulExecution() throws AssertionError {
            if (throwable != null) throw new AssertionError("Failed execution, due to", throwable);

            return this;
        }

        /**
         * Asserts that the {@link CommandHandler} {@linkplain Given#when(Command) executed} exceptionally, irrespective
         * of the exception type.
         *
         * @return {@code this} for further assertions
         * @throws AssertionError if the {@link CommandHandler} terminated non exceptionally
         */
        public Expect expectUnsuccessfulExecution() throws AssertionError {
            return expectException(Throwable.class);
        }

        public Expect expectResult(R expected) throws AssertionError {
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

        public Expect expectResultSatisfying(Consumer<R> assertion) throws AssertionError {
            assertion.accept(result);
            return this;
        }

        /**
         * Asserts that the {@link CommandHandler} {@linkplain Given#when(Command) executed} exceptionally by throwing
         * an exception of the given class.
         *
         * @param t the expected exception class
         * @return {@code this} for further assertions
         * @throws AssertionError if the {@link CommandHandler} executed non exceptionally or an exception was thrown
         *     that is not assignable to the requested type
         * @param <T> the generic exception type
         */
        public <T extends Throwable> Expect expectException(Class<T> t) throws AssertionError {
            if (throwable == null) throw new AssertionError("No exception occurred, as expected");
            if (!t.isAssignableFrom(throwable.getClass()))
                throw new AssertionError(
                        "Captured exception has wrong type: "
                                + throwable.getClass().getSimpleName(),
                        throwable);

            return this;
        }

        /**
         * Asserts that the {@link CommandHandler} {@linkplain Given#when(Command) executed} exceptionally and allows
         * for further custom assertions using the provided {@link Consumer}.
         *
         * @param assertion a consumer for custom assertions of the captured exception
         * @return {@code this} for further assertions
         * @throws AssertionError if the {@link CommandHandler} executed non exceptionally or if thrown by the given
         *     consumer
         * @param <T> the generic exception type
         */
        public <T extends Throwable> Expect expectExceptionSatisfying(Consumer<T> assertion) throws AssertionError {
            if (throwable == null) throw new AssertionError("No exception occurred, as expected");

            assertion.accept((T) throwable);

            return this;
        }

        /**
         * Asserts that the (potentially altered) state resulting from the @link CommandHandler}
         * {@linkplain Given#when(Command) execution} {@linkplain Object#equals(Object) is equal} to the given state.
         *
         * @param state the expected state
         * @return {@code this} for further assertions
         * @throws AssertionError if the captured state is {@code null} or not equal to the expected state
         */
        public Expect expectState(I state) throws AssertionError {
            if (this.state == null) throw new AssertionError("No state captured");
            if (!state.equals(this.state)) {
                StringBuilder builder = new StringBuilder();
                builder.append("State expected to be equal, but captured state:\n");
                builder.append(this.state.toString());
                builder.append("\n");
                builder.append("differs from:\n");
                builder.append(state);

                throw new AssertionError(builder.toString());
            }

            return this;
        }

        /**
         * Asserts that the (potentially altered) state resulting from the @link CommandHandler}
         * {@linkplain Given#when(Command) execution} {@linkplain Object#equals(Object) is equal} to the given state
         * using the extractor function.
         *
         * @param extractor extractor function applied to the state before comparison
         * @param expected the extracted state expected, may be {@code null} if needed
         * @return {@code this} for further assertions
         * @throws AssertionError if the captured state is {@code null} or the extracted state is not as expected
         * @param <R> the result type of the extractor function
         */
        public <R> Expect expectStateExtracting(Function<I, R> extractor, R expected) throws AssertionError {
            if (state == null) throw new AssertionError("No state captured");
            R extracted = extractor.apply(state);

            if (expected == null && extracted == null) return this;

            if (expected == null || !expected.equals(extracted)) {
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

        /**
         * Asserts the (potentially altered) state resulting from the @link CommandHandler}
         * {@linkplain Given#when(Command) execution} using the given {@link Consumer}.
         *
         * @param assertion the consumer used for custom state assertions
         * @return {@code this} for further assertions
         * @throws AssertionError if thrown by the given consumer
         */
        public Expect expectStateSatisfying(Consumer<I> assertion) throws AssertionError {
            assertion.accept(this.state);
            return this;
        }

        /**
         * Asserts that the {@linkplain StatefulAssertion next event payloads} within the captured event stream
         * {@linkplain Object#equals(Object) are equal} to the given events in order.
         *
         * @param events the expected events
         * @return {@code this} for further assertions
         * @throws AssertionError if less captured events remain than expected or any of the expected events is not
         *     equal
         */
        @StatefulAssertion
        public Expect expectEvents(Object... events) throws AssertionError {
            Arrays.stream(events).forEach(this::expectNextEvent);

            return this;
        }

        /**
         * Asserts that the {@linkplain StatefulAssertion next event payloads} within the captured event stream
         * {@linkplain Class#isAssignableFrom(Class)} are assignable} to the given event types in order.
         *
         * @param types the expected event types
         * @return {@code this} for further assertions
         * @throws AssertionError if less captured events remain than expected or any of the expected event types is not
         *     assignable
         */
        @StatefulAssertion
        public Expect expectEventTypes(Class<?>... types) throws AssertionError {
            Arrays.stream(types).forEach(this::expectNextEventType);

            return this;
        }

        /**
         * Asserts that the {@linkplain StatefulAssertion next event payloads} within the captured event stream are
         * successfully asserted by the given consumer.
         *
         * @param assertion consumer asserting the remaining events
         * @return {@code this} for further assertions
         * @throws AssertionError if thrown by the consumer
         */
        @StatefulAssertion
        public Expect expectEventsSatisfying(Consumer<List<Object>> assertion) throws AssertionError {
            eventVerified = true;

            ArrayList<Object> events = new ArrayList<>();
            nextEvent.forEachRemaining(next -> events.add(next.event()));
            assertion.accept(events);

            return this;
        }

        /**
         * Asserts that the {@linkplain StatefulAssertion next event payloads} within the captured event stream
         * {@linkplain Object#equals(Object) are equal} to the given events in <b>any</b> order.
         *
         * @param events the expected events (in any order)
         * @return {@code this} for further assertions
         * @throws AssertionError if less captured events remain than expected or any of the expected events is not
         *     equal
         */
        @StatefulAssertion
        public Expect expectEventsInAnyOrder(Object... events) throws AssertionError {
            eventVerified = true;

            ArrayList<Object> capturedEvents = new ArrayList<>();
            for (int i = 0; i < events.length; i++) {
                if (!nextEvent.hasNext())
                    throw new AssertionError(
                            "No more events captured, expected " + (events.length - i) + " more events");
                capturedEvents.add(nextEvent.next().event());
            }
            capturedEvents.removeAll(Arrays.stream(events).toList());

            if (!capturedEvents.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                builder.append("The following events were captured, but did not match the expected events:\n");
                capturedEvents.forEach(e -> {
                    builder.append(e.toString());
                    builder.append("\n");
                });

                throw new AssertionError(builder.toString());
            }

            return this;
        }

        /**
         * Asserts that the {@linkplain StatefulAssertion next event payloads} within the captured event stream
         * {@linkplain Class#isAssignableFrom(Class)} are assignable} to the given event types in <b>any</b> order.
         *
         * @param types the expected event types (in any order)
         * @return {@code this} for further assertions
         * @throws AssertionError if less captured events remain than expected or any of the expected event type is not
         *     assignable
         */
        @StatefulAssertion
        public Expect expectEventTypesInAnyOrder(Class<?>... types) throws AssertionError {
            eventVerified = true;

            ArrayList<Class<?>> capturedEventTypes = new ArrayList<>();
            for (int i = 0; i < types.length; i++) {
                if (!nextEvent.hasNext())
                    throw new AssertionError(
                            "No more events captured, expected " + (types.length - i) + " more events");
                capturedEventTypes.add(nextEvent.next().event().getClass());
            }
            capturedEventTypes.removeAll(Arrays.stream(types).toList());

            if (!capturedEventTypes.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                builder.append("The following events were captured, but did not match the expected event types:\n");
                capturedEventTypes.forEach(e -> {
                    builder.append(e.getSimpleName());
                    builder.append("\n");
                });

                throw new AssertionError(builder.toString());
            }

            return this;
        }

        /**
         * Asserts that the {@linkplain StatefulAssertion next payload payload} within the captured payload stream
         * {@linkplain Object#equals(Object) is equal} to the given payload.
         *
         * @param payload the expected payload
         * @return {@code this} for further assertions
         * @throws AssertionError if no captured events remain or the next payload does not equal the expected one
         * @param <E> the generic payload type
         */
        @StatefulAssertion
        public <E> Expect expectNextEvent(E payload) throws AssertionError {
            if (!nextEvent.hasNext())
                throw new AssertionError("No more events captured, but expected payload of type: "
                        + payload.getClass().getSimpleName());

            return expectNextEvent(it -> it.payload(payload));
        }

        /**
         * Asserts the {@linkplain StatefulAssertion next event} within the captured event stream using the given
         * {@linkplain EventAsserter event asserting consumer}.
         *
         * @param assertion consumer for further event assertion
         * @return {@code this} for further assertions
         * @throws AssertionError if no captured events remain or if thrown by the event assertion consumer
         */
        @StatefulAssertion
        public Expect expectNextEvent(Consumer<EventAsserter> assertion) {
            eventVerified = true;

            if (!nextEvent.hasNext()) throw new AssertionError("No more events captured.");

            CapturedEvent next = nextEvent.next();
            assertion.accept(new EventAsserter(command, next));
            return this;
        }

        /**
         * Asserts that {@linkplain StatefulAssertion next event} within the captured event stream
         * {@linkplain Class#isAssignableFrom(Class)} is assignable} to the given event type.
         *
         * @param type the expected event type
         * @return {@code this} for further assertions
         * @throws AssertionError if no captured events remain or the next event is not assignable to the expected type
         */
        @StatefulAssertion
        public Expect expectNextEventType(Class<?> type) throws AssertionError {
            if (!nextEvent.hasNext())
                throw new AssertionError(
                        "No more events captured, but expected event of type: " + type.getSimpleName());

            return expectNextEvent(it -> it.payloadType(type));
        }

        /**
         * Asserts that {@linkplain StatefulAssertion next event} within the captured event stream is successfully
         * asserted by the given {@link Consumer}.
         *
         * @param assertion custom assertion applied to the next event
         * @return {@code this} for further assertions
         * @throws AssertionError if no captured events remain or if thrown by the custom assertion
         * @param <E> the generic event type
         */
        @StatefulAssertion
        public <E> Expect expectNextEventSatisfying(Consumer<E> assertion) throws AssertionError {
            return expectNextEvent(it -> it.payloadSatisfying(assertion));
        }

        /**
         * Asserts that no more events have been captured.
         *
         * @return {@code this} for further assertions
         * @throws AssertionError if there are any remaining events
         */
        public Expect expectNoMoreEvents() throws AssertionError {
            if (nextEvent.hasNext()) {
                StringBuilder builder = new StringBuilder();
                builder.append("No more events expected, but got:\n");
                nextEvent.forEachRemaining(e -> {
                    builder.append(e.event().toString());
                    builder.append("\n");
                });
                throw new AssertionError(builder.toString());
            }

            return this;
        }

        /**
         * Asserts that a single payload was captured as part of the published payload stream whose payload
         * {@linkplain Object#equals(Object) is equal} to the expected one.
         *
         * @param payload the expected payload type
         * @return {@code this} for further assertions
         * @throws AssertionError if no or more events were captured or the captured payload does not equal the expected
         *     one
         * @param <E> the generic payload type
         */
        public <E> Expect expectSingleEvent(E payload) throws AssertionError {
            if (eventVerified)
                throw new Error("Cannot expect single payload, if previous events have already been verified.");
            return expectNextEvent(payload).expectNoMoreEvents();
        }

        /**
         * Asserts that a single event was captured as part of the published event stream which using the given
         * {@linkplain EventAsserter event asserting consumer}.
         *
         * @param assertion consumer for further event assertion
         * @return {@code this} for further assertions
         * @throws AssertionError if no or more events were captured or if thrown by the event assertion consumer
         */
        public Expect expectSingleEvent(Consumer<EventAsserter> assertion) {
            if (eventVerified)
                throw new Error("Cannot expect single event, if previous events have already been verified.");
            return expectNextEvent(assertion).expectNoMoreEvents();
        }

        /**
         * Asserts that a single event was captured as part of the published event stream which
         * {@linkplain Class#isAssignableFrom(Class)} is assignable} to the given event type.
         *
         * @param type the expected event type
         * @return {@code this} for further assertions
         * @throws AssertionError if no or more events were captured or the captured event is not assignable to the
         *     expected type
         */
        public Expect expectSingleEventType(Class<?> type) throws AssertionError {
            if (eventVerified)
                throw new Error("Cannot expect single event, if previous events have already been verified.");
            return expectNextEventType(type).expectNoMoreEvents();
        }

        /**
         * Asserts that a single event was captured as part of the published event stream which matches the custom
         * assertion provided as {@link Consumer}.
         *
         * @param assertion the custom assertion
         * @return {@code this} for further assertions
         * @throws AssertionError if no or more events were captured or if thrown by the custom assertion
         * @param <E> the generic payload type
         */
        public <E> Expect expectSingleEventSatisfying(Consumer<E> assertion) throws AssertionError {
            if (eventVerified)
                throw new Error("Cannot expect single event, if previous events have already been verified.");
            return expectNextEventSatisfying(assertion).expectNoMoreEvents();
        }

        /**
         * Asserts that number of events captured as part of the published event stream matches the given number.
         *
         * @param num the number of expected events, may be zero
         * @return {@code this} for further assertions
         * @throws AssertionError if the number of published events differs
         */
        public Expect expectNumEvents(int num) throws AssertionError {
            if (num < 0) throw new IllegalArgumentException("Num must be zero or positive");

            if (capturedEvents.size() != num)
                throw new AssertionError("Number of expected events differs, expected " + num + " but captured: "
                        + capturedEvents.size());

            return this;
        }

        /**
         * Asserts that no event was captured as part of the published event stream.
         *
         * @return {@code this} for further assertions
         * @throws AssertionError if any event was published
         */
        public Expect expectNoEvents() throws AssertionError {
            return expectNumEvents(0);
        }

        /**
         * Skips the given number of {@linkplain StatefulAssertion next events} for upcoming assertions.
         *
         * @param num the number of events to skip
         * @return {@code this} for further assertions
         * @throws AssertionError if less events were captured than the requested number to skip
         */
        @StatefulAssertion
        public Expect skipEvents(int num) throws AssertionError {
            if (num <= 0) throw new IllegalArgumentException("Num must be positive");

            for (int i = 0; i < num; i++) {
                if (!nextEvent.hasNext()) throw new AssertionError("Not enough events captured for skipping");
                nextEvent.next();
            }

            return this;
        }

        /**
         * Asserts that any event's payload in the remaining set of captured events {@linkplain Object#equals(Object) is
         * equal} to the given payload.
         *
         * @param payload the expected payload
         * @return {@code this} for further assertions
         * @throws AssertionError if no events were captured or none of them does equal the expected one
         * @param <E> the generic payload type
         */
        public <E> Expect expectAnyEvent(E payload) throws AssertionError {
            return expectAnyEvent(it -> it.payload(payload));
        }

        /**
         * Asserts that any of the remaining events in the set of captured events asserts successfully using the given
         * {@linkplain EventAsserter event asserting consumer}.
         *
         * @param assertion consumer for further event assertion
         * @return {@code this} for further assertions
         * @throws AssertionError if no or more events were captured or if thrown by the event assertion consumer
         */
        public Expect expectAnyEvent(Consumer<EventAsserter> assertion) throws AssertionError {
            if (!nextEvent.hasNext()) throw new AssertionError("No more events captured to assert on");

            ArrayList<AssertionError> capturedErrors = new ArrayList<>();
            boolean anySuccessfullyAsserts =
                    capturedEvents.subList(nextEvent.nextIndex(), capturedEvents.size()).stream()
                            .map(e -> {
                                try {
                                    assertion.accept(new EventAsserter(command, e));
                                } catch (AssertionError error) {
                                    capturedErrors.add(error);
                                    return false;
                                }
                                return true;
                            })
                            .reduce(false, (a, b) -> a || b);

            if (!anySuccessfullyAsserts) {
                StringBuilder builder = new StringBuilder();
                builder.append(
                        "None of the remaining captured events matched the given assertion, the following assertion errors have been captured:\n");
                capturedErrors.forEach(error -> builder.append(error.getMessage() + "\n"));
                throw new AssertionError(builder.toString());
            }

            return this;
        }

        /**
         * Asserts that none of the remaining events in the captured event stream satisfies the given assertion
         * criteria. This is the inverse of {@link #expectAnyEvent(Consumer)}.
         *
         * <p>This method looks through all remaining events from the current iterator position and verifies that none
         * of them passes the provided assertion. If any event passes the assertion, an AssertionError is thrown.
         *
         * @param assertion consumer specifying the event assertion criteria that should not be satisfied
         * @return {@code this} for further assertions
         * @throws AssertionError if any remaining event satisfies the assertion criteria
         */
        public Expect expectNoEvent(Consumer<EventAsserter> assertion) throws AssertionError {
            if (!nextEvent.hasNext()) throw new AssertionError("No more events captured to assert on");

            ArrayList<CapturedEvent> matchingEvents = new ArrayList<>();
            boolean anySuccessfullyAsserts =
                    capturedEvents.subList(nextEvent.nextIndex(), capturedEvents.size()).stream()
                            .map(e -> {
                                try {
                                    assertion.accept(new EventAsserter(command, e));
                                    matchingEvents.add(e);
                                } catch (AssertionError error) {
                                    return false;
                                }
                                return true;
                            })
                            .reduce(false, (a, b) -> a || b);

            if (anySuccessfullyAsserts) {
                StringBuilder builder = new StringBuilder();
                builder.append("The following remaining captured events matched the given assertion:\n");
                matchingEvents.forEach(event -> builder.append(event.event().toString() + "\n"));
                System.out.println("builder:" + builder);
                throw new AssertionError(builder.toString());
            }

            return this;
        }

        /**
         * Asserts that any event in the remaining set of captured events {@linkplain Class#isAssignableFrom(Class)} is
         * assignable} to the given event type.
         *
         * @param type the expected event type
         * @return {@code this} for further assertions
         * @throws AssertionError if no events were captured or none of them is assignable to the expected type
         */
        public Expect expectAnyEventType(Class<?> type) throws AssertionError {
            return expectAnyEvent(it -> it.payloadType(type));
        }

        /**
         * Verifies that any event in the remaining set of captured events asserts using the given custom assertion
         * {@link Consumer}.
         *
         * @param assertion custom assertion
         * @return {@code this} for further assertions
         * @throws AssertionError if no events were captured or none of them was successfully asserted by the custom
         *     assertion
         * @param <E> the generic payload type
         */
        public <E> Expect expectAnyEventSatisfying(Consumer<E> assertion) throws AssertionError {
            return expectAnyEvent(it -> it.payloadSatisfying(assertion));
        }

        /**
         * Asserts that none of the remaining events in the captured event stream is assignable to the given type.
         *
         * @param type the event type that should not be present in the remaining events
         * @return {@code this} for further assertions
         * @throws AssertionError if any remaining event of the given type is found
         */
        public Expect expectNoEventOfType(Class<?> type) throws AssertionError {
            return expectNoEvent(it -> it.payloadType(type));
        }
    }

    /**
     * Fluent API helper class for asserting a captured events.
     *
     * @see CommandHandlingTestFixtureNew.Expect#expectNextEvent(Consumer)
     * @see CommandHandlingTestFixtureNew.Expect#expectSingleEvent(Consumer)
     * @see CommandHandlingTestFixtureNew.Expect#expectAnyEvent(Consumer)
     */
    public static class EventAsserter {

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
        public EventAsserter commandSubject() throws AssertionError {
            return subject(command.getSubject());
        }
    }
}
