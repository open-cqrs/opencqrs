/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.v2;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.command.*;
import com.opencqrs.framework.persistence.CapturedEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

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

    public static <I> Builder<I> withStateRebuildingHandlerDefinitions(
            StateRebuildingHandlerDefinition<I, ?>... definitions) {
        return new Builder(Arrays.stream(definitions).toList());
    }

    public static class Builder<I> {
        final List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions;

        private Builder(List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions) {
            this.stateRebuildingHandlerDefinitions = stateRebuildingHandlerDefinitions;
        }

        public <C extends Command, R> CommandHandlingTestFixture<I, C, R> using(
                CommandHandlerDefinition<I, C, R> definition) {
            return using(definition.instanceClass(), definition.handler());
        }

        public <C extends Command, R> CommandHandlingTestFixture<I, C, R> using(
                Class<I> instanceClass, CommandHandler<I, C, R> handler) {
            return new CommandHandlingTestFixture<>(instanceClass, stateRebuildingHandlerDefinitions, handler);
        }
    }

    public class Given<C extends Command> implements GivenDsl.Given<I, R> {
        
        sealed interface Stub<I> {

            record State<I>(I state) implements Stub<I> {}

            record Time<I>(Instant time) implements Stub<I> {}

            record TimeDelta<I>(Duration duration) implements Stub<I> {}

            record Event<I>(String id, Instant time, String subject, Object payload, Map<String, ?> metaData) implements Stub<I> {
                public Event() {
                    this(null, null, null, null, null);
                }
                public Event<I> withId(String id) {
                    return new Event<>(id, time(), subject(), payload(), metaData());
                }
                public Event<I> withTime(Instant time) {
                    return new Event<>(id(), time, subject(), payload(), metaData());
                }
                public Event<I> withSubject(String subject) {
                    return new Event<>(id(), time(), subject, payload(), metaData());
                }
                public Event<I> withPayload(Object payload) {
                    return new Event<>(id(), time(), subject(), payload, metaData());
                }
                public Event<I> withMetaData(Map<String, ?> metaData) {
                    return new Event<>(id(), time(), subject(), payload(), metaData);
                }
            }
        }

        record StubResult<I>(
                Class<I> instanceClass,
                I state,
                Instant time,
                Command command,
                List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions,
                Set<String> subjects) {
            
            public StubResult<I> withState(I newState) {
                return new StubResult<>(instanceClass(), newState, time(), command(), stateRebuildingHandlerDefinitions(), subjects());
            }
            
            public StubResult<I> withTime(Instant newTime) {
                return new StubResult<>(instanceClass(), state(), newTime, command(), stateRebuildingHandlerDefinitions(), subjects());
            }
            
            public StubResult<I> withSubject(String newSubject) {
                var newSubjects = new HashSet<>(subjects());
                newSubjects.add(newSubject);
                return new StubResult<>(instanceClass(), state(), time(), command(), stateRebuildingHandlerDefinitions(), newSubjects);
            }
            
            public StubResult<I> merge(Stub<I> stub) {
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
                        yield withSubject(rawEvent.subject()).withState(reference.get());
                    }
                };
            }
        }

        public static class GivenEvent<I, R> implements com.opencqrs.framework.command.GivenDsl.EventSpecifier<I, R> {
            Stub.Event<I> stub;

            GivenEvent(Stub.Event<I> stub) {
                this.stub = stub;
            }

            @Override
            public com.opencqrs.framework.command.GivenDsl.EventSpecifier<I, R> payload(Object payload) {
                stub = stub.withPayload(payload);
                return this;
            }

            @Override
            public com.opencqrs.framework.command.GivenDsl.EventSpecifier<I, R> time(Instant time) {
                stub = stub.withTime(time);
                return this;
            }

            @Override
            public com.opencqrs.framework.command.GivenDsl.EventSpecifier<I, R> subject(String subject) {
                stub = stub.withSubject(subject);
                return this;
            }

            @Override
            public com.opencqrs.framework.command.GivenDsl.EventSpecifier<I, R> id(String id) {
                stub = stub.withId(id);
                return this;
            }

            @Override
            public com.opencqrs.framework.command.GivenDsl.EventSpecifier<I, R> metaData(Map<String, ?> metaData) {
                stub = stub.withMetaData(metaData);
                return this;
            }
        }

        private final CommandHandler<I, C, R> commandHandler;
        private final List<Stub<I>> stubs = new ArrayList<>();
        private final String subject;

        private Given(CommandHandler<I, C, R> commandHandler, Instant time) {
            this.subject = null;
            this.commandHandler = commandHandler;
            stubs.add(new Stub.Time<>(time));
        }

        private Given(CommandHandler<I, C, R> commandHandler) {
            this(commandHandler, Instant.now());
        }

        private Given(String subject, CommandHandler<I, C, R> commandHandler, List<Stub<I>> stubs) {
            this.subject = subject;
            this.commandHandler = commandHandler;
            this.stubs.addAll(stubs);
        }

        private void addToStubs(Consumer<GivenEvent<I, R>> givenEvent) {
            GivenEvent<I, R> capture = new GivenEvent<>(new Stub.Event<>());
            givenEvent.accept(capture);

            Stub.Event<I> stub = capture.stub;
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
        public com.opencqrs.framework.command.GivenDsl.Given<I, R> nothing() {
            return this;
        }

        @Override
        public com.opencqrs.framework.command.GivenDsl.Given<I, R> time(Instant time) {
            stubs.add(new Stub.Time<>(time));
            return this;
        }

        @Override
        public com.opencqrs.framework.command.GivenDsl.Given<I, R> timeDelta(Duration delta) {
            stubs.add(new Stub.TimeDelta<>(delta));
            return this;
        }

        @Override
        public com.opencqrs.framework.command.GivenDsl.Given<I, R> state(I state) {
            stubs.add(new Stub.State<>(state));
            return this;
        }

        @Override
        public com.opencqrs.framework.command.GivenDsl.Given<I, R> events(Object... events) {
            for (Object event : events) {
                stubs.add(new Stub.Event<I>().withPayload(event));
            }
            return this;
        }

        @Override
        public com.opencqrs.framework.command.GivenDsl.Given<I, R> event(Consumer<com.opencqrs.framework.command.GivenDsl.EventSpecifier<I, R>> event) {
            addToStubs(event::accept);
            return this;
        }

        @Override
        public <CMD extends Command> com.opencqrs.framework.command.GivenDsl.Given<I, R> command(CommandHandlingTestFixture<I, CMD, ?> fixture, CMD command) {
            return command(fixture, command, Map.of());
        }

        @Override
        public <CMD extends Command> com.opencqrs.framework.command.GivenDsl.Given<I, R> command(CommandHandlingTestFixture<I, CMD, ?> fixture, CMD command, Map<String, ?> metaData) {
            var expect = (CommandHandlingTestFixture<I, CMD, ?>.Expect) fixture.givenStubs(stubs).when(command, metaData);
            
            expect.succeeds();
            
            expect.capturedEvents.forEach(capturedEvent -> 
                addToStubs(event -> event.subject(capturedEvent.subject())
                        .payload(capturedEvent.event())
                        .metaData(capturedEvent.metaData())));
            
            return this;
        }

        /**
         * Sets the default subject for subsequent events
         */
        @Override
        public com.opencqrs.framework.command.GivenDsl.Given<I, R> usingSubject(String subject) {
            return new Given<>(subject, commandHandler, stubs);
        }
        
        /**
         * Resets to using command subject for subsequent events
         */
        @Override
        public com.opencqrs.framework.command.GivenDsl.Given<I, R> usingCommandSubject() {
            return usingSubject(null);
        }
        
        @Override
        public Initializing<I, R> when(Object command) {
            return when(command, Map.of());
        }

        @Override
        public Initializing<I, R> when(Object command, Map<String, ?> metaData) {
            Command cmd = (Command) command;
            AtomicReference<StubResult<I>> stubResult = new AtomicReference<>(
                    new StubResult<>(instanceClass, null, Instant.now(), cmd, stateRebuildingHandlerDefinitions, Set.of()));
            stubs.forEach(stub -> stubResult.updateAndGet(result -> result.merge(stub)));
            
            I currentState = stubResult.get().state();
            
            CommandEventCapturer<I> eventCapturer = new CommandEventCapturer<>(
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
                R result = switch (commandHandler) {
                    case CommandHandler.ForCommand<I, C, R> handler -> 
                        handler.handle((C) cmd, eventCapturer);
                    case CommandHandler.ForInstanceAndCommand<I, C, R> handler ->
                        handler.handle(currentState, (C) cmd, eventCapturer);
                    case CommandHandler.ForInstanceAndCommandAndMetaData<I, C, R> handler ->
                        handler.handle(currentState, (C) cmd, metaData, eventCapturer);
                };
                
                return new Expect(
                        cmd,
                        eventCapturer.previousInstance.get(),
                        eventCapturer.getEvents(),
                        result,
                        null);
            } catch (Throwable t) {
                return new Expect(cmd, currentState, List.of(), null, t);
            }
        }
    }

    public GivenDsl.Given<I, R> given() {
        return new Given<>(commandHandler);
    }

    private Given<C> givenStubs(List<Given.Stub<I>> stubs) {
        Given<C> given = new Given<>(commandHandler);
        given.stubs.addAll(stubs);
        return given;
    }

    public class Expect implements Initializing<I, R>, Common<I, R> {
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
        
        public class Succeeding implements com.opencqrs.framework.command.ExpectDsl.Succeeding<I, R> {
            @Override
            public com.opencqrs.framework.command.ExpectDsl.Succeeding<I, R> withoutEvents() {
                if (!capturedEvents.isEmpty()) {
                    throw new AssertionError("Expected no events, but found " + capturedEvents.size());
                }
                return this;
            }

            @Override
            public com.opencqrs.framework.command.ExpectDsl.Succeeding<I, R> havingResult(R expected) {
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
            public com.opencqrs.framework.command.ExpectDsl.Succeeding<I, R> resultSatisfying(Consumer<R> assertion) {
                assertion.accept(result);
                return this;
            }

            @Override
            public com.opencqrs.framework.command.ExpectDsl.Succeeding<I, R> havingState(I expectedState) {
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
            public com.opencqrs.framework.command.ExpectDsl.Succeeding<I, R> stateSatisfying(Consumer<I> assertion) {
                assertion.accept(state);
                return this;
            }

            @Override
            public <T> com.opencqrs.framework.command.ExpectDsl.Succeeding<I, R> stateExtracting(Function<I, T> extractor, T expected) {
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
            public Common<I, R> and() {
                return Expect.this;
            }
        }
        
        public class Failing implements com.opencqrs.framework.command.ExpectDsl.Failing<I, R> {
            @Override
            public <T> com.opencqrs.framework.command.ExpectDsl.Failing<I, R> throwing(Class<T> t) {
                if (throwable == null) throw new AssertionError("No exception occurred, as expected");
                if (!t.isAssignableFrom(throwable.getClass())) {
                    throw new AssertionError("Captured exception has wrong type: " + throwable.getClass().getSimpleName(), throwable);
                }
                return this;
            }

            @Override
            public <T> com.opencqrs.framework.command.ExpectDsl.Failing<I, R> throwsSatisfying(Consumer<T> assertion) {
                if (throwable == null) throw new AssertionError("No exception occurred, as expected");
                assertion.accept((T) throwable);
                return this;
            }

            @Override
            public com.opencqrs.framework.command.ExpectDsl.Failing<I, R> violatingAnyCondition() {
                if (!(throwable instanceof CommandSubjectAlreadyExistsException || throwable instanceof CommandSubjectDoesNotExistException)) {
                    throw new AssertionError("Expected command subject condition not violated");
                }
                return this;
            }

            @Override
            public com.opencqrs.framework.command.ExpectDsl.Failing<I, R> violatingExactly(Command.SubjectCondition condition) {
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
        
        public class AllEvents implements All<I, R> {
            @Override
            public All<I, R> count(int count) {
                if (count < 0) throw new IllegalArgumentException("Count must be zero or positive");
                
                if (capturedEvents.size() != count) {
                    throw new AssertionError("Number of expected events differs, expected " + count + " but captured: " + capturedEvents.size());
                }
                
                return this;
            }

            @Override
            public All<I, R> single(Consumer<EventValidator<I, R>> consumer) {
                List<Consumer<EventValidator<I, R>>> allConsumers = new ArrayList<>();
                allConsumers.add(consumer);

                for (Consumer<EventValidator<I, R>> validatorConsumer : allConsumers) {
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
            public All<I, R> any(Consumer<EventValidator<I, R>> consumer, Consumer<EventValidator<I, R>>... consumers) {
                List<Consumer<EventValidator<I, R>>> allConsumers = new ArrayList<>();
                allConsumers.add(consumer);
                allConsumers.addAll(Arrays.asList(consumers));
                
                for (Consumer<EventValidator<I, R>> validatorConsumer : allConsumers) {
                    boolean found = false;
                    
                    for (CapturedEvent event : capturedEvents) {
                        try {
                            EventValidatorImpl validator = new EventValidatorImpl(event);
                            validatorConsumer.accept(validator);
                            found = true;
                            break;
                        } catch (AssertionError e) {
                        }
                    }
                    
                    if (!found) {
                        throw new AssertionError("Expected at least one event matching validator, but found none");
                    }
                }
                
                return this;
            }

            @Override
            public All<I, R> exactly(Consumer<EventValidator<I, R>> consumer, Consumer<EventValidator<I, R>>... consumers) {
                List<Consumer<EventValidator<I, R>>> allConsumers = new ArrayList<>();
                allConsumers.add(consumer);
                allConsumers.addAll(Arrays.asList(consumers));
                
                if (capturedEvents.size() != allConsumers.size()) {
                    throw new AssertionError("Expected exactly " + allConsumers.size() + " events, but found " + capturedEvents.size());
                }
                
                for (int i = 0; i < allConsumers.size(); i++) {
                    CapturedEvent event = capturedEvents.get(i);
                    Consumer<EventValidator<I, R>> validatorConsumer = allConsumers.get(i);
                    
                    EventValidatorImpl validator = new EventValidatorImpl(event);
                    validatorConsumer.accept(validator);
                }
                
                return this;
            }

            @Override
            public All<I, R> all(Consumer<EventValidator<I, R>> consumer) {
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
            public All<I, R> none(Consumer<EventValidator<I, R>> consumer, Consumer<EventValidator<I, R>>... consumers) {
                List<Consumer<EventValidator<I, R>>> allConsumers = new ArrayList<>();
                allConsumers.add(consumer);
                allConsumers.addAll(Arrays.asList(consumers));
                for (Consumer<EventValidator<I, R>> validatorConsumer : allConsumers) {
                    int matches = 0;
                    for (CapturedEvent event : capturedEvents) {
                        try {
                            EventValidatorImpl validator = new EventValidatorImpl(event);
                            validatorConsumer.accept(validator);
                            matches++;
                        } catch (AssertionError e) {
                        }
                    }
                    if (matches > 0) {
                        throw new AssertionError("Expected no events matching validator, but found " + matches);
                    }
                }
                return this;
            }

            @Override
            public Common<I, R> and() {
                return Expect.this;
            }
        }
        
        public class NextEvents implements Next<I, R> {
            @Override
            public Next<I, R> skip(int num) {
                for (int i = 0; i < num; i++) {
                    if (!nextEvent.hasNext()) {
                        throw new AssertionError("Cannot skip " + num + " events, only " + i + " remaining");
                    }
                    nextEvent.next();
                }
                return this;
            }

            @Override
            public Next<I, R> noMore() {
                if (nextEvent.hasNext()) {
                    throw new AssertionError("Expected no more events, but found more");
                }
                return this;
            }

            @Override
            public Next<I, R> single(Consumer<EventValidator<I, R>> consumer) {
                List<CapturedEvent> remainingEvents = new ArrayList<>(
                        capturedEvents.subList(nextEvent.nextIndex(), capturedEvents.size()));
                int matches = 0;
                for (CapturedEvent event : remainingEvents) {
                    try {
                        EventValidatorImpl validator = new EventValidatorImpl(event);
                        consumer.accept(validator);
                        matches++;
                    } catch (AssertionError e) {
                    }
                }
                if (matches == 0) {
                    throw new AssertionError("Expected exactly one event matching validator in remaining events, but found none");
                } else if (matches > 1) {
                    throw new AssertionError("Expected exactly one event matching validator in remaining events, but found " + matches);
                }
                return this;
            }

            @Override
            public Next<I, R> any(Consumer<EventValidator<I, R>> consumer, Consumer<EventValidator<I, R>>... consumers) {
                List<Consumer<EventValidator<I, R>>> allConsumers = new ArrayList<>();
                allConsumers.add(consumer);
                allConsumers.addAll(Arrays.asList(consumers));
                List<CapturedEvent> remainingEvents = new ArrayList<>(
                        capturedEvents.subList(nextEvent.nextIndex(), capturedEvents.size()));
                for (Consumer<EventValidator<I, R>> validatorConsumer : allConsumers) {
                    boolean found = false;
                    for (CapturedEvent event : remainingEvents) {
                        try {
                            EventValidatorImpl validator = new EventValidatorImpl(event);
                            validatorConsumer.accept(validator);
                            found = true;
                            break;
                        } catch (AssertionError e) {
                        }
                    }
                    if (!found) {
                        throw new AssertionError("Expected at least one remaining event matching validator, but found none");
                    }
                }
                return this;
            }

            @Override
            public Next<I, R> exactly(Consumer<EventValidator<I, R>> consumer, Consumer<EventValidator<I, R>>... consumers) {
                List<Consumer<EventValidator<I, R>>> allConsumers = new ArrayList<>();
                allConsumers.add(consumer);
                allConsumers.addAll(Arrays.asList(consumers));
                
                int expectedCount = allConsumers.size();
                
                for (int i = 0; i < expectedCount; i++) {
                    if (!nextEvent.hasNext()) {
                        throw new AssertionError("Expected " + expectedCount + " more events, but only " + i + " remaining");
                    }
                    
                    CapturedEvent event = nextEvent.next();
                    Consumer<EventValidator<I, R>> validatorConsumer = allConsumers.get(i);
                    
                    EventValidatorImpl validator = new EventValidatorImpl(event);
                    validatorConsumer.accept(validator);
                }
                
                return this;
            }

            @Override
            public Next<I, R> none(Consumer<EventValidator<I, R>> consumer, Consumer<EventValidator<I, R>>... consumers) {
                List<Consumer<EventValidator<I, R>>> allConsumers = new ArrayList<>();
                allConsumers.add(consumer);
                allConsumers.addAll(Arrays.asList(consumers));
                List<CapturedEvent> remainingEvents = new ArrayList<>(
                        capturedEvents.subList(nextEvent.nextIndex(), capturedEvents.size()));
                for (Consumer<EventValidator<I, R>> validatorConsumer : allConsumers) {
                    int matches = 0;
                    for (CapturedEvent event : remainingEvents) {
                        try {
                            EventValidatorImpl validator = new EventValidatorImpl(event);
                            validatorConsumer.accept(validator);
                            matches++;
                        } catch (AssertionError e) {
                        }
                    }
                    if (matches > 0) {
                        throw new AssertionError("Expected no remaining events matching validator, but found " + matches);
                    }
                }
                return this;
            }

            @Override
            public Common<I, R> and() {
                return Expect.this;
            }
        }

        @Override
        public com.opencqrs.framework.command.ExpectDsl.Succeeding<I, R> succeeds() {
            if(throwable != null) {
                throw new AssertionError("Expected successful execution but got exception", throwable);
            }
            return new Succeeding();
        }

        @Override
        public ExpectDsl.Failing<I, R> fails() {
            if (throwable == null) {
                throw new AssertionError("Expected failed execution but command succeeded");
            }
            return new Failing();
        }

        @Override
        public All<I, R> allEvents() {
            return new AllEvents();
        }

        @Override
        public Next<I, R> nextEvents() {
            return new NextEvents();
        }

        public class EventValidatorImpl implements EventValidator<I, R> {
            private final CapturedEvent event;
            
            public EventValidatorImpl(CapturedEvent event) {
                this.event = event;
            }
            
            @Override
            public EventValidator<I, R> comparing(Object expectedEvent) {
                return asserting(eventAsserter -> eventAsserter.payload(expectedEvent));
            }
            
            @Override
            public <E> EventValidator<I, R> satisfying(Consumer<E> assertion) {
                return asserting(eventAsserter -> eventAsserter.payloadSatisfying(assertion));
            }
            
            @Override
            public EventValidator<I, R> asserting(Consumer<com.opencqrs.framework.command.EventAsserting> asserting) {
                asserting.accept(new EventAsserter(command, event));
                return this;
            }
            
            @Override
            public EventValidator<I, R> ofType(Class<?> type) {
                if (!type.isAssignableFrom(event.event().getClass())) {
                    throw new AssertionError("Event type not as expected: " + event.event().getClass().getSimpleName() + 
                                           ", expected: " + type.getSimpleName());
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
