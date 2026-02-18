/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.persistence.CapturedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class CommandHandlingTestFixture<C extends Command> {

    private final Class<?> instanceClass;
    private final List<StateRebuildingHandlerDefinition<Object, Object>> stateRebuildingHandlerDefinitions;
    final CommandHandler<?, C, ?> commandHandler;

    private CommandHandlingTestFixture(
            Class<?> instanceClass,
            List<StateRebuildingHandlerDefinition<Object, Object>> stateRebuildingHandlerDefinitions,
            CommandHandler<?, C, ?> commandHandler) {
        this.instanceClass = instanceClass;
        this.stateRebuildingHandlerDefinitions = stateRebuildingHandlerDefinitions;
        this.commandHandler = commandHandler;
    }

    @SafeVarargs
    public static <I> Builder<I> withStateRebuildingHandlerDefinitions(
            StateRebuildingHandlerDefinition<I, ?>... definitions) {
        return new Builder(Arrays.stream(definitions).toList());
    }

    public static class Builder<I> {
        final List<StateRebuildingHandlerDefinition<Object, Object>> stateRebuildingHandlerDefinitions;

        private Builder(List<StateRebuildingHandlerDefinition<Object, Object>> stateRebuildingHandlerDefinitions) {
            this.stateRebuildingHandlerDefinitions = stateRebuildingHandlerDefinitions;
        }

        public <C extends Command> CommandHandlingTestFixture<C> using(CommandHandlerDefinition<I, C, ?> definition) {
            return using(definition.instanceClass(), definition.handler());
        }

        public <C extends Command> CommandHandlingTestFixture<C> using(
                Class<I> instanceClass, CommandHandler<I, C, ?> handler) {
            return new CommandHandlingTestFixture<C>(instanceClass, stateRebuildingHandlerDefinitions, handler);
        }
    }

    class Given<C extends Command> implements GivenDsl.Given {

        sealed interface Stub {

            record State(Object state) implements Stub {}

            record Time(Instant time) implements Stub {}

            record TimeDelta(Duration duration) implements Stub {}

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
                Object state,
                Instant time,
                Command command,
                List<StateRebuildingHandlerDefinition<Object, Object>> stateRebuildingHandlerDefinitions,
                Set<String> subjects) {

            public StubResult withState(Object newState) {
                return new StubResult(
                        instanceClass(), newState, time(), command(), stateRebuildingHandlerDefinitions(), subjects());
            }

            public StubResult withTime(Instant newTime) {
                return new StubResult(
                        instanceClass(), state(), newTime, command(), stateRebuildingHandlerDefinitions(), subjects());
            }

            public StubResult withSubject(String newSubject) {
                var newSubjects = new HashSet<>(subjects());
                newSubjects.add(newSubject);
                return new StubResult(
                        instanceClass(), state(), time(), command(), stateRebuildingHandlerDefinitions(), newSubjects);
            }

            public StubResult merge(Stub stub) {
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

                        AtomicReference<Object> reference = new AtomicReference<>(state());
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
                        yield withSubject(rawEvent.subject()).withState(reference.get());
                    }
                };
            }
        }

        public static class GivenEvent implements GivenDsl.EventSpecifier {
            Stub.Event stub;

            GivenEvent(Stub.Event stub) {
                this.stub = stub;
            }

            @Override
            public GivenDsl.EventSpecifier payload(Object payload) {
                stub = stub.withPayload(payload);
                return this;
            }

            @Override
            public GivenDsl.EventSpecifier time(Instant time) {
                stub = stub.withTime(time);
                return this;
            }

            @Override
            public GivenDsl.EventSpecifier subject(String subject) {
                stub = stub.withSubject(subject);
                return this;
            }

            @Override
            public GivenDsl.EventSpecifier id(String id) {
                stub = stub.withId(id);
                return this;
            }

            @Override
            public GivenDsl.EventSpecifier metaData(Map<String, ?> metaData) {
                stub = stub.withMetaData(metaData);
                return this;
            }
        }

        private final CommandHandler<?, C, ?> commandHandler;
        private final List<Stub> stubs = new ArrayList<>();
        private final String subject;

        private Given(CommandHandler<?, C, ?> commandHandler, Instant time) {
            this.subject = null;
            this.commandHandler = commandHandler;
            stubs.add(new Stub.Time(time));
        }

        private Given(CommandHandler<?, C, ?> commandHandler) {
            this(commandHandler, Instant.now());
        }

        private Given(String subject, CommandHandler<?, C, ?> commandHandler, List<Stub> stubs) {
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
        public GivenDsl.Given nothing() {
            return this;
        }

        @Override
        public GivenDsl.Given time(Instant time) {
            stubs.add(new Stub.Time(time));
            return this;
        }

        @Override
        public GivenDsl.Given timeDelta(Duration delta) {
            stubs.add(new Stub.TimeDelta(delta));
            return this;
        }

        @Override
        public GivenDsl.Given state(Object state) {
            stubs.add(new Stub.State(state));
            return this;
        }

        @Override
        public GivenDsl.Given events(Object... events) {
            for (Object event : events) {
                addToStubs(e -> e.payload(event));
            }
            return this;
        }

        /**
         * Applies a single event using the {@link GivenDsl.EventSpecifier} consumer for fine-grained event
         * specification. At minimum, {@link GivenDsl.EventSpecifier#payload(Object)} must be called.
         *
         * <p>Use this method when you need to specify event properties beyond just the payload, such as
         * {@link GivenDsl.EventSpecifier#time(Instant)}, {@link GivenDsl.EventSpecifier#subject(String)},
         * {@link GivenDsl.EventSpecifier#id(String)}, or {@link GivenDsl.EventSpecifier#metaData(Map)}. For simple
         * payload-only events, consider using {@link #events(Object...)} instead.
         *
         * @param event event specification consumer
         * @return {@code this} for further fluent API calls
         * @throws IllegalArgumentException if {@link GivenDsl.EventSpecifier#payload(Object)} was not called
         * @see #events(Object...)
         */
        @Override
        public GivenDsl.Given event(Consumer<GivenDsl.EventSpecifier> event) {
            addToStubs(event);
            return this;
        }

        /**
         * Executes the given {@link Command} without meta-data using the {@link CommandHandler} encapsulated within the
         * given fixture to capture any new events published, which in turn will be applied to {@code this}. This is
         * useful for {@link CommandHandler}s publishing complex events, in favor of stubbing the events directly.
         *
         * <p><strong>Be aware that stubbed events can be specified more precisely than captured ones, since the
         * encapsulated {@link CommandHandler} is responsible for event publication using the
         * {@link CommandEventPublisher}. Hence, {@link GivenDsl.EventSpecifier#time(Instant)} and
         * {@link GivenDsl.EventSpecifier#id(String)} cannot be specified using this approach.</strong>
         *
         * @param fixture the fixture holding the command handler to execute
         * @param command the command to execute for event capturing
         * @return {@code this} for further fluent API calls
         * @throws AssertionError in case the given command did not execute successfully
         * @param <CMD> generic command type to execute
         */
        @Override
        public <CMD extends Command> GivenDsl.Given command(CommandHandlingTestFixture<CMD> fixture, CMD command) {
            return command(fixture, command, Map.of());
        }

        /**
         * Executes the given {@link Command} with meta-data using the {@link CommandHandler} encapsulated within the
         * given fixture to capture any new events published, which in turn will be applied to {@code this}. This is
         * useful for {@link CommandHandler}s publishing complex events, in favor of stubbing the events directly.
         *
         * <p><strong>Be aware that stubbed events can be specified more precisely than captured ones, since the
         * encapsulated {@link CommandHandler} is responsible for event publication using the
         * {@link CommandEventPublisher}. Hence, {@link GivenDsl.EventSpecifier#time(Instant)} and
         * {@link GivenDsl.EventSpecifier#id(String)} cannot be specified using this approach.</strong>
         *
         * @param fixture the fixture holding the command handler to execute
         * @param command the command to execute for event capturing
         * @param metaData the command meta-data
         * @return {@code this} for further fluent API calls
         * @throws AssertionError in case the given command did not execute successfully
         * @param <CMD> generic command type to execute
         */
        @Override
        public <CMD extends Command> GivenDsl.Given command(
                CommandHandlingTestFixture<CMD> fixture, CMD command, Map<String, ?> metaData) {
            CommandHandlingTestFixture<CMD>.Given<CMD> otherGiven = fixture.givenStubs(stubs);
            ExpectDsl.Initializing otherExpectInitializing = otherGiven.when(command, metaData);

            Expect otherExpect = (Expect) otherExpectInitializing;

            otherExpect.succeeds();

            otherExpect.capturedEvents.forEach(
                    capturedEvent -> addToStubs(event -> event.subject(capturedEvent.subject())
                            .payload(capturedEvent.event())
                            .metaData(capturedEvent.metaData())));

            return this;
        }

        /** Sets the default subject for subsequent events */
        @Override
        public GivenDsl.Given usingSubject(String subject) {
            return new Given<>(subject, commandHandler, stubs);
        }

        /** Resets to using command subject for subsequent events */
        @Override
        public GivenDsl.Given usingCommandSubject() {
            return usingSubject(null);
        }

        @Override
        public ExpectDsl.Initializing when(Object command) {
            return when(command, Map.of());
        }

        @Override
        @SuppressWarnings("unchecked")
        public ExpectDsl.Initializing when(Object command, Map<String, ?> metaData) {
            Command cmd = (Command) command;
            AtomicReference<StubResult> stubResult = new AtomicReference<>(new StubResult(
                    instanceClass, null, Instant.now(), cmd, stateRebuildingHandlerDefinitions, Set.of()));
            stubs.forEach(stub -> stubResult.updateAndGet(result -> result.merge(stub)));

            Object currentState = stubResult.get().state();

            CommandEventCapturer<Object> eventCapturer = new CommandEventCapturer<>(
                    currentState,
                    cmd.getSubject(),
                    stateRebuildingHandlerDefinitions.stream()
                            .filter(it -> it.instanceClass().equals(instanceClass))
                            .toList());

            var stateStubbed = stubs.stream().anyMatch(s -> s instanceof Stub.State);

            if (!stateStubbed) {
                switch (cmd.getSubjectCondition()) {
                    case PRISTINE -> {
                        if (stubResult.get().subjects().contains(cmd.getSubject())) {
                            return new Expect(
                                    cmd,
                                    currentState,
                                    List.of(),
                                    null,
                                    new CommandSubjectAlreadyExistsException("subject already exists", cmd));
                        }
                    }
                    case EXISTS -> {
                        if (!stubResult.get().subjects().contains(cmd.getSubject())) {
                            return new Expect(
                                    cmd,
                                    currentState,
                                    List.of(),
                                    null,
                                    new CommandSubjectDoesNotExistException("subject does not exist", cmd));
                        }
                    }
                    case NONE -> {}
                }
            }

            try {
                CommandHandler<Object, C, Object> typedHandler = (CommandHandler<Object, C, Object>) commandHandler;
                Object result =
                        switch (typedHandler) {
                            case CommandHandler.ForCommand<Object, C, Object> handler ->
                                handler.handle((C) cmd, eventCapturer);
                            case CommandHandler.ForInstanceAndCommand<Object, C, Object> handler ->
                                handler.handle(currentState, (C) cmd, eventCapturer);
                            case CommandHandler.ForInstanceAndCommandAndMetaData<Object, C, Object> handler ->
                                handler.handle(currentState, (C) cmd, metaData, eventCapturer);
                        };

                return new Expect(cmd, eventCapturer.previousInstance.get(), eventCapturer.getEvents(), result, null);
            } catch (Throwable t) {
                return new Expect(cmd, currentState, List.of(), null, t);
            }
        }
    }

    public GivenDsl.Given given() {
        return new Given<>(commandHandler);
    }

    private Given<C> givenStubs(List<Given.Stub> stubs) {
        Given<C> given = new Given<>(commandHandler);
        given.stubs.addAll(stubs);
        return given;
    }

    public static class Expect implements ExpectDsl.Initializing, ExpectDsl.Common {
        private final Command command;
        private final Object state;
        final List<CapturedEvent> capturedEvents;
        private final Object result;
        private final Throwable throwable;

        private Expect(
                Command command, Object state, List<CapturedEvent> capturedEvents, Object result, Throwable throwable) {
            this.command = command;
            this.state = state;
            this.capturedEvents = capturedEvents;
            this.result = result;
            this.throwable = throwable;
        }

        public class Succeeding implements ExpectDsl.Succeeding {
            @Override
            public ExpectDsl.Succeeding withoutEvents() {
                if (!capturedEvents.isEmpty()) {
                    throw new AssertionError("Expected no events, but found " + capturedEvents.size());
                }
                return this;
            }

            @Override
            public ExpectDsl.Succeeding havingResult(Object expected) {
                if (expected == null && result == null) return this;
                if (expected == null || (result == null) || !expected.equals(result)) {
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
            public ExpectDsl.Succeeding resultSatisfying(Consumer<Object> assertion) {
                assertion.accept(result);
                return this;
            }

            @Override
            public ExpectDsl.Succeeding havingState(Object expectedState) {
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
            public ExpectDsl.Succeeding stateSatisfying(Consumer<Object> assertion) {
                assertion.accept(state);
                return this;
            }

            @Override
            public <T> ExpectDsl.Succeeding stateExtracting(Function<Object, T> extractor, T expected) {
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
            public ExpectDsl.Common then() {
                return Expect.this;
            }
        }

        public class Failing implements ExpectDsl.Failing {
            @Override
            public <T> ExpectDsl.Failing throwing(Class<T> t) {
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
            public <T> ExpectDsl.Failing throwsSatisfying(Consumer<T> assertion) {
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

        public class AllEvents implements ExpectDsl.All {
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
                List<Consumer<ExpectDsl.EventValidator>> allConsumers = new ArrayList<>();
                allConsumers.add(consumer);

                for (Consumer<ExpectDsl.EventValidator> validatorConsumer : allConsumers) {
                    int matches = 0;

                    for (CapturedEvent event : capturedEvents) {
                        try {
                            EventValidatorImpl validator = new EventValidatorImpl(event);
                            validatorConsumer.accept(validator);
                            matches++;
                        } catch (AssertionError e) {
                        }
                    }

                    if (matches == 0) {
                        throw new AssertionError("Expected exactly one event matching validator, but found none");
                    } else if (matches > 1) {
                        throw new AssertionError("Expected exactly one event matching validator, but found " + matches);
                    }
                }

                return this;
            }

            @Override
            public ExpectDsl.All any(Consumer<ExpectDsl.EventValidator> consumer) {
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
            public ExpectDsl.All all(Consumer<ExpectDsl.EventValidator> consumer) {
                if (capturedEvents.isEmpty()) {
                    throw new AssertionError("Expected all events to match validator, but no events were captured");
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

            public ExpectDsl.Common then() {
                return Expect.this;
            }
        }

        public class NextEvents implements ExpectDsl.Next {
            private final ListIterator<CapturedEvent> nextEvent;

            NextEvents() {
                this.nextEvent = capturedEvents.listIterator();
            }

            @Override
            public ExpectDsl.Next skip(int num) {
                for (int i = 0; i < num; i++) {
                    if (!nextEvent.hasNext()) {
                        throw new AssertionError("Cannot skip " + num + " events, only " + i + " remaining");
                    }
                    nextEvent.next();
                }
                return this;
            }

            @Override
            public ExpectDsl.Next noMore() {
                if (nextEvent.hasNext()) {
                    throw new AssertionError("Expected no more events, but found more");
                }
                return this;
            }

            @Override
            public ExpectDsl.Common single(Consumer<ExpectDsl.EventValidator> consumer) {
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
                if (matches == 0) {
                    throw new AssertionError(
                            "Expected exactly one event matching validator in remaining events, but found none");
                } else if (matches > 1) {
                    throw new AssertionError(
                            "Expected exactly one event matching validator in remaining events, but found " + matches);
                }
                return Expect.this;
            }

            @Override
            public ExpectDsl.Common any(Consumer<ExpectDsl.EventValidator> consumer) {
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
                return Expect.this;
            }

            @Override
            @SafeVarargs
            public final ExpectDsl.Common exactly(
                    Consumer<ExpectDsl.EventValidator> consumer, Consumer<ExpectDsl.EventValidator>... consumers) {
                List<Consumer<ExpectDsl.EventValidator>> allConsumers = new ArrayList<>();
                allConsumers.add(consumer);
                allConsumers.addAll(Arrays.asList(consumers));

                for (int i = 0; i < allConsumers.size(); i++) {
                    if (!nextEvent.hasNext()) {
                        throw new AssertionError(
                                "Expected " + allConsumers.size() + " more events, but only " + i + " remaining");
                    }
                    CapturedEvent event = nextEvent.next();
                    EventValidatorImpl validator = new EventValidatorImpl(event);
                    allConsumers.get(i).accept(validator);
                }

                if (nextEvent.hasNext()) {
                    int remaining = 0;
                    while (nextEvent.hasNext()) {
                        nextEvent.next();
                        remaining++;
                    }
                    throw new AssertionError("Expected exactly " + allConsumers.size() + " events, but " + remaining
                            + " more remaining");
                }

                return Expect.this;
            }

            @Override
            public ExpectDsl.Common none(Consumer<ExpectDsl.EventValidator> consumer) {
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
                return Expect.this;
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

            public ExpectDsl.Common then() {
                return Expect.this;
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

        @Override
        public ExpectDsl.All allEvents() {
            return new AllEvents();
        }

        @Override
        public ExpectDsl.Next nextEvents() {
            return new NextEvents();
        }

        public class EventValidatorImpl implements ExpectDsl.EventValidator {
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
