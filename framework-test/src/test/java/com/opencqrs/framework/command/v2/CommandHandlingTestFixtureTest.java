/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.v2;

import com.opencqrs.framework.command.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
                        DummyState.class, Object.class, (StateRebuildingHandler.FromObjectAndRawEvent<DummyState, Object>)
                        (state, event, raw) -> {
                            slot.set(raw.time());
                            return new DummyState(true);
                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>) (c, p) -> null)
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
                        DummyState.class, Object.class, (StateRebuildingHandler.FromObjectAndRawEvent<DummyState, Object>)
                        (state, event, raw) -> {
                            slot.set(raw.time());
                            return new DummyState(true);
                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>) (c, p) -> null)
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
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>) (c, p) -> null)
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
