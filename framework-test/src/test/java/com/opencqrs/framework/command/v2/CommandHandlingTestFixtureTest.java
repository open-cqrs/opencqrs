/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.opencqrs.framework.command.*;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class CommandHandlingTestFixtureTest {

    @Nested
    public class Setup {

        @Test
        public void stateRebuildingHandlersCalledFilteredByInstanceClassBeforeCommandExecution() {
            StateRebuildingHandlerDefinition[] eshds = {
                new StateRebuildingHandlerDefinition(
                        DummyState.class, EventA.class, (StateRebuildingHandler.FromObject) (i, e) -> {
                            throw new AssertionError("wrong state rebuilding handler called");
                        }),
                new StateRebuildingHandlerDefinition(
                        AnotherDummyState.class, EventA.class, (StateRebuildingHandler.FromObject)
                                (i, e) -> new AnotherDummyState())
            };

            CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(eshds)
                    .using(new CommandHandlerDefinition(
                            AnotherDummyState.class,
                            DummyCommand.class,
                            (CommandHandler.ForInstanceAndCommand<AnotherDummyState, DummyCommand, Void>) (i, c, p) -> {
                                assertThat(i).isNotNull();
                                return null;
                            }))
                    .given()
                    .event(e -> e.payload(new EventA("irrelevant")))
                    .when(new DummyCommand())
                    .succeeds();
        }

        @Test
        public void stateRebuildingHandlersCalledFilteredByEventClassAfterCommandExecution() {
            StateRebuildingHandlerDefinition[] eshds = {
                new StateRebuildingHandlerDefinition(
                        DummyState.class, EventA.class, (StateRebuildingHandler.FromObject) (i, e) -> {
                            throw new AssertionError("wrong state rebuilding handler called");
                        }),
                new StateRebuildingHandlerDefinition(
                        AnotherDummyState.class, EventA.class, (StateRebuildingHandler.FromObject)
                                (i, e) -> new AnotherDummyState())
            };

            CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(eshds)
                    .using(new CommandHandlerDefinition(
                            AnotherDummyState.class,
                            DummyCommand.class,
                            (CommandHandler.ForInstanceAndCommand<AnotherDummyState, DummyCommand, Void>) (i, c, p) -> {
                                assertThat(i).isNull();
                                p.publish(new EventA("irrelevant"));
                                return null;
                            }))
                    .given()
                    .nothing()
                    .when(new DummyCommand())
                    .succeeds()
                    .then()
                    .allEvents()
                    .exactly(event -> event.ofType(EventA.class));
        }

        @Test
        public void stateFromPreviousHandlerPassedToNext() {
            List<DummyState> receivedStates = new ArrayList<>();

            CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                            new StateRebuildingHandlerDefinition<>(
                                    DummyState.class,
                                    EventA.class,
                                    (StateRebuildingHandler.FromObject<DummyState, EventA>) (state, event) -> {
                                        receivedStates.add(state);
                                        return new DummyState(true);
                                    }),
                            new StateRebuildingHandlerDefinition<>(
                                    DummyState.class,
                                    EventB.class,
                                    (StateRebuildingHandler.FromObject<DummyState, EventB>) (state, event) -> {
                                        receivedStates.add(state);
                                        return new DummyState(false);
                                    }))
                    .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>) (c, p) -> null)
                    .given()
                    .events(new EventA("first"), new EventB(42L))
                    .when(new DummyCommand())
                    .succeeds();

            assertThat(receivedStates).hasSize(2);
            assertThat(receivedStates.get(0)).isNull();
            assertThat(receivedStates.get(1)).isEqualTo(new DummyState(true));
        }
    }

    @Nested
    public class Given {
        CommandHandlingTestFixture.Builder<DummyState> subject =
                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions();

        @Nested
        @DisplayName("Nothing")
        public class Nothing {

            @Test
            public void noState() {
                StateRebuildingHandler.FromObjectAndRawEvent<DummyState, Object> stateRebuildingHandler = mock();
                AtomicReference<DummyState> slot = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                DummyState.class, Object.class, stateRebuildingHandler))
                        .using(DummyState.class, (CommandHandler.ForInstanceAndCommand<DummyState, DummyCommand, Void>)
                                (i, c, p) -> {
                                    slot.set(i);
                                    return null;
                                })
                        .given()
                        .nothing()
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(slot.get()).isNull();
                verifyNoMoreInteractions(stateRebuildingHandler);
            }
        }

        @Nested
        @DisplayName("Time")
        public class Time {

            @Test
            public void defaultTimeInitialized() {
                AtomicReference<Instant> slot = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                DummyState.class, Object.class, (StateRebuildingHandler.FromObjectAndRawEvent<
                                                DummyState, Object>)
                                        (state, event, raw) -> {
                                            slot.set(raw.time());
                                            return new DummyState(true);
                                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .event(e -> e.payload(new EventA("test")))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(slot)
                        .hasValueSatisfying(instant -> assertThat(instant).isInThePast());
            }

            @Test
            public void givenSpecificTimeInitialized() {
                Instant expected = Instant.now().plusSeconds(42);
                AtomicReference<Instant> slot = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                DummyState.class, Object.class, (StateRebuildingHandler.FromObjectAndRawEvent<
                                                DummyState, Object>)
                                        (state, event, raw) -> {
                                            slot.set(raw.time());
                                            return new DummyState(true);
                                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .time(expected)
                        .event(e -> e.payload(new EventA("test")))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(slot).hasValue(expected);
            }
        }

        @Nested
        @DisplayName("TimeDelta")
        public class TimeDelta {

            @Test
            public void timeDeltaInitialized() {
                Instant instant = Instant.now().plusSeconds(42);
                AtomicReference<Instant> slot1 = new AtomicReference<>();
                AtomicReference<Instant> slot2 = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition<>(
                                        DummyState.class, EventA.class, (StateRebuildingHandler.FromObjectAndRawEvent<
                                                        DummyState, EventA>)
                                                (state, event, raw) -> {
                                                    slot1.set(raw.time());
                                                    return new DummyState(true);
                                                }),
                                new StateRebuildingHandlerDefinition<>(
                                        DummyState.class, EventB.class, (StateRebuildingHandler.FromObjectAndRawEvent<
                                                        DummyState, EventB>)
                                                (state, event, raw) -> {
                                                    slot2.set(raw.time());
                                                    return state;
                                                }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .time(instant)
                        .event(event -> event.payload(new EventA("test")))
                        .timeDelta(Duration.ofDays(1))
                        .event(event -> event.payload(new EventB(42L)))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(slot1).hasValue(instant);
                assertThat(slot2).hasValue(instant.plus(Duration.ofDays(1)));
            }
        }

        @Nested
        @DisplayName("State")
        public class State {

            @Test
            public void stateInitialized() {
                AtomicReference<DummyState> slot = new AtomicReference<>();
                DummyState s = new DummyState(true);

                subject.using(DummyState.class, (CommandHandler.ForInstanceAndCommand<DummyState, DummyCommand, Void>)
                                (i, c, p) -> {
                                    slot.set(i);
                                    return null;
                                })
                        .given()
                        .state(s)
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(slot.get()).isSameAs(s);
            }
        }

        @Nested
        @DisplayName("UsingSubject/UsingCommandSubject")
        public class UsingSubject {

            @Test
            public void subjectAppliedToGivenEvents() {
                AtomicReference<String> subjectA = new AtomicReference<>();
                AtomicReference<String> subjectB = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition(
                                        DummyState.class,
                                        EventA.class,
                                        (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<
                                                        Object, EventA>)
                                                (i, e, m, s, r) -> {
                                                    subjectA.set(s);
                                                    return new DummyState(true);
                                                }),
                                new StateRebuildingHandlerDefinition(
                                        DummyState.class,
                                        EventB.class,
                                        (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<
                                                        Object, EventB>)
                                                (i, e, m, s, r) -> {
                                                    subjectB.set(s);
                                                    return new DummyState(true);
                                                }))
                        .using(DummyState.class, (CommandHandler.ForInstanceAndCommand<Object, DummyCommand, Void>)
                                (i, c, p) -> null)
                        .given()
                        .usingSubject("/test-subject/a")
                        .event(e -> e.payload(new EventA("test")))
                        .usingSubject("/test-subject/b")
                        .event(e -> e.subject(null).payload(new EventB(42L)))
                        .when(new DummyCommand())
                        .succeeds()
                        .then()
                        .allEvents()
                        .count(0);

                assertThat(subjectA).hasValue("/test-subject/a");
                assertThat(subjectB).hasValue("/test-subject/b");
            }
        }

        @Nested
        @DisplayName("Events")
        public class Events {

            @Test
            public void singleEventPayloadPassedToStateRebuildingHandler() {
                AtomicReference<String> capturedName = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                DummyState.class, EventA.class, (StateRebuildingHandler.FromObject<DummyState, EventA>)
                                        (state, event) -> {
                                            capturedName.set(event.name());
                                            return new DummyState(true);
                                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .events(new EventA("singleEventAppliedToState"))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(capturedName).hasValue("singleEventAppliedToState");
            }

            @Test
            public void multipleEventPayloadsPassedToRespectiveHandlers() {
                AtomicReference<String> capturedName = new AtomicReference<>();
                AtomicReference<Long> capturedSize = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition<>(
                                        DummyState.class,
                                        EventA.class,
                                        (StateRebuildingHandler.FromObject<DummyState, EventA>) (state, event) -> {
                                            capturedName.set(event.name());
                                            return new DummyState(true);
                                        }),
                                new StateRebuildingHandlerDefinition<>(
                                        DummyState.class,
                                        EventB.class,
                                        (StateRebuildingHandler.FromObject<DummyState, EventB>) (state, event) -> {
                                            capturedSize.set(event.size());
                                            return new DummyState(true);
                                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .events(new EventA("multipleEventsAppliedToState"), new EventB(999L))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(capturedName).hasValue("multipleEventsAppliedToState");
                assertThat(capturedSize).hasValue(999L);
            }

            @Test
            public void eventsProcessedInInsertionOrder() {
                List<String> callOrder = new ArrayList<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition<>(
                                        DummyState.class,
                                        EventA.class,
                                        (StateRebuildingHandler.FromObject<DummyState, EventA>) (state, event) -> {
                                            callOrder.add("A");
                                            return state != null ? state : new DummyState(true);
                                        }),
                                new StateRebuildingHandlerDefinition<>(
                                        DummyState.class,
                                        EventB.class,
                                        (StateRebuildingHandler.FromObject<DummyState, EventB>) (state, event) -> {
                                            callOrder.add("B");
                                            return state != null ? state : new DummyState(true);
                                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .events(new EventB(1L), new EventA("first"))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(callOrder).containsExactly("B", "A");
            }

            @Test
            public void noEventsAddedWhenCalledWithoutArguments() {
                StateRebuildingHandler.FromObject<DummyState, Object> stateRebuildingHandler = mock();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                DummyState.class, Object.class, stateRebuildingHandler))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .events()
                        .when(new DummyCommand())
                        .succeeds();

                verifyNoMoreInteractions(stateRebuildingHandler);
            }

            @Test
            public void throwsWhenNoStateRebuildingHandlerMatchesEventType() {
                List<String> called = new ArrayList<>();

                var fixture = CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition<>(
                                        DummyState.class,
                                        EventA.class,
                                        (StateRebuildingHandler.FromObject<DummyState, EventA>) (state, event) -> {
                                            called.add("A");
                                            return state != null ? state : new DummyState(true);
                                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null);

                var exception = assertThrows(
                        IllegalArgumentException.class,
                        () -> fixture.given().events(new EventB(42L)).when(new DummyCommand()));

                assertThat(called).isEmpty();
                assertThat(exception.getMessage()).contains("No suitable state rebuilding handler definition found");
                assertThat(exception.getMessage()).contains("EventB");
            }
        }

        @Nested
        @DisplayName("Event")
        public class Event {

            @Test
            public void eventPayloadPassedToStateRebuildingHandler() {
                AtomicReference<String> capturedName = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                DummyState.class, EventA.class, (StateRebuildingHandler.FromObject<DummyState, EventA>)
                                        (state, event) -> {
                                            capturedName.set(event.name());
                                            return new DummyState(true);
                                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .event(e -> e.payload(new EventA("test")))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(capturedName).hasValue("test");
            }

            @Test
            public void throwsIllegalArgumentExceptionWhenPayloadNotSpecified() {
                var fixture = CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition<>(
                                        DummyState.class, EventA.class, (StateRebuildingHandler.FromObject<
                                                        DummyState, EventA>)
                                                (state, event) -> new DummyState(true)))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null);

                var exception = assertThrows(
                        IllegalArgumentException.class, () -> fixture.given().event(e -> {}));

                assertThat(exception.getMessage()).contains("Event payload must be specified using payload()");
            }
        }

        @Nested
        @DisplayName("Command")
        public class Command {

            @Test
            public void capturedEventsFromOtherFixtureAppliedAsGivenEvents() {
                AtomicReference<String> capturedEventName = new AtomicReference<>();

                var publishingFixture = CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition<>(
                                        DummyState.class, EventA.class, (StateRebuildingHandler.FromObject<
                                                        DummyState, EventA>)
                                                (state, event) -> new DummyState(true)))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>) (c, p) -> {
                            p.publish(new EventA("fromOtherFixture"));
                            return null;
                        });

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                DummyState.class, EventA.class, (StateRebuildingHandler.FromObject<DummyState, EventA>)
                                        (state, event) -> {
                                            capturedEventName.set(event.name());
                                            return new DummyState(true);
                                        }))
                        .using(DummyState.class, (CommandHandler.ForInstanceAndCommand<DummyState, DummyCommand, Void>)
                                (i, c, p) -> null)
                        .given()
                        .command(publishingFixture, new DummyCommand())
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(capturedEventName).hasValue("fromOtherFixture");
            }

            @Test
            public void metaDataPassedToOtherCommandHandler() {
                AtomicReference<Map<String, ?>> capturedMetaData = new AtomicReference<>();

                var otherFixture = CommandHandlingTestFixture.<DummyState>withStateRebuildingHandlerDefinitions()
                        .using(DummyState.class, (CommandHandler.ForInstanceAndCommandAndMetaData<
                                        DummyState, DummyCommand, Void>)
                                (i, c, m, p) -> {
                                    capturedMetaData.set(m);
                                    return null;
                                });

                CommandHandlingTestFixture.<DummyState>withStateRebuildingHandlerDefinitions()
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .command(otherFixture, new DummyCommand(), Map.of("testKey", "testValue"))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(capturedMetaData.get().get("testKey")).isEqualTo("testValue");
            }
        }
    }

    static class DummyCommand implements Command {

        private final SubjectCondition subjectCondition;

        DummyCommand() {
            this(SubjectCondition.NONE);
        }

        DummyCommand(SubjectCondition subjectCondition) {
            this.subjectCondition = subjectCondition;
        }

        @Override
        public String getSubject() {
            return "dummy";
        }

        @Override
        public SubjectCondition getSubjectCondition() {
            return subjectCondition;
        }
    }

    record DummyState(Boolean valid) {}

    record AnotherDummyState() {}

    record EventA(String name) implements Serializable {}

    record EventB(Long size) {}

    record EventC() {}

    private static <E> StateRebuildingHandlerDefinition<DummyState, E> eshIdentity(Class<E> eventClass) {
        return new StateRebuildingHandlerDefinition<>(
                DummyState.class, eventClass, (StateRebuildingHandler.FromObject<DummyState, E>)
                        (instance, event) -> Optional.ofNullable(instance).orElse(new DummyState(true)));
    }
}
