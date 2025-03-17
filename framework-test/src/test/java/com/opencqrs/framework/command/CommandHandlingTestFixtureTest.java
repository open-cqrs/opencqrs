/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.persistence.CapturedEvent;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class CommandHandlingTestFixtureTest {

    @Nested
    public class Setup {

        @Test
        public void stateRebuildingHandlersCalledFilteredByInstanceClassBeforeCommandExecution() {
            StateRebuildingHandlerDefinition[] eshds = {
                new StateRebuildingHandlerDefinition(
                        State.class, EventA.class, (StateRebuildingHandler.FromObject) (i, e) -> {
                            throw new AssertionError("wrong state rebuilding handler called");
                        }),
                new StateRebuildingHandlerDefinition(
                        AnotherState.class, EventA.class, (StateRebuildingHandler.FromObject)
                                (i, e) -> new AnotherState())
            };
            CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(eshds)
                    .using(new CommandHandlerDefinition(
                            AnotherState.class,
                            DummyCommand.class,
                            (CommandHandler.ForInstanceAndCommand<AnotherState, DummyCommand, Void>) (i, c, p) -> {
                                assertThat(i).isNotNull();
                                return null;
                            }))
                    .given(new EventA("irrelevant"))
                    .when(new DummyCommand())
                    .expectSuccessfulExecution();
        }

        @Test
        public void stateRebuildingHandlersCalledFilteredByInstanceClassAfterCommandExecution() {
            StateRebuildingHandlerDefinition[] eshds = {
                new StateRebuildingHandlerDefinition(
                        State.class, EventA.class, (StateRebuildingHandler.FromObject) (i, e) -> {
                            throw new AssertionError("wrong state rebuilding handler called");
                        }),
                new StateRebuildingHandlerDefinition(
                        AnotherState.class, EventA.class, (StateRebuildingHandler.FromObject)
                                (i, e) -> new AnotherState())
            };
            CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(eshds)
                    .using(new CommandHandlerDefinition(
                            AnotherState.class,
                            DummyCommand.class,
                            (CommandHandler.ForInstanceAndCommand<AnotherState, DummyCommand, Void>) (i, c, p) -> {
                                assertThat(i).isNull();
                                p.publish(new EventA("irrelevant"));
                                return null;
                            }))
                    .givenNothing()
                    .when(new DummyCommand())
                    .expectSuccessfulExecution()
                    .expectSingleEventType(EventA.class);
        }
    }

    @Nested
    public class Given {

        CommandHandlingTestFixture.Builder<State> subject =
                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions();

        @Nested
        @DisplayName("givenNothing")
        public class GivenNothing {

            @Test
            public void noState() {
                StateRebuildingHandler.FromObjectAndRawEvent<State, Object> stateRebuildingHandler = mock();
                AtomicReference<State> slot = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                State.class, Object.class, stateRebuildingHandler))
                        .using(State.class, (CommandHandler.ForInstanceAndCommand<State, DummyCommand, Void>)
                                (i, c, p) -> {
                                    slot.set(i);
                                    return null;
                                })
                        .givenNothing()
                        .when(new DummyCommand())
                        .expectSuccessfulExecution();

                assertThat(slot.get()).isNull();
                verifyNoInteractions(stateRebuildingHandler);
            }
        }

        @Nested
        @DisplayName("givenTime/andGivenTime")
        public class GivenTime {

            @Test
            public void defaultTimeInitialized() {
                AtomicReference<Instant> slot = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                State.class, Object.class, (StateRebuildingHandler.FromObjectAndRawEvent<State, Object>)
                                        (state, event, raw) -> {
                                            slot.set(raw.time());
                                            return new State(true);
                                        }))
                        .using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>) (c, p) -> null)
                        .given(new EventA("test"))
                        .when(new DummyCommand())
                        .expectSuccessfulExecution();

                assertThat(slot)
                        .hasValueSatisfying(instant -> assertThat(instant).isInThePast());
            }

            @Test
            public void givenSpecificTimeInitialized() {
                Instant expected = Instant.now().plusSeconds(42);
                AtomicReference<Instant> slot = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                State.class, Object.class, (StateRebuildingHandler.FromObjectAndRawEvent<State, Object>)
                                        (state, event, raw) -> {
                                            slot.set(raw.time());
                                            return new State(true);
                                        }))
                        .using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>) (c, p) -> null)
                        .givenTime(expected)
                        .andGiven(new EventA("test"))
                        .when(new DummyCommand())
                        .expectSuccessfulExecution();

                assertThat(slot).hasValue(expected);
            }

            @Test
            public void andGivenSpecificTimeInitialized() {
                Instant expected = Instant.now().plusSeconds(42);
                AtomicReference<Instant> slot = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                State.class, Object.class, (StateRebuildingHandler.FromObjectAndRawEvent<State, Object>)
                                        (state, event, raw) -> {
                                            slot.set(raw.time());
                                            return new State(true);
                                        }))
                        .using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>) (c, p) -> null)
                        .givenNothing()
                        .andGivenTime(expected)
                        .andGiven(new EventA("test"))
                        .when(new DummyCommand())
                        .expectSuccessfulExecution();

                assertThat(slot).hasValue(expected);
            }
        }

        @Nested
        @DisplayName("andGivenTimeDelta")
        public class AndGivenTimeDelta {

            @Test
            public void andGivenTimeDeltaInitialized() {
                Instant instant = Instant.now().plusSeconds(42);
                AtomicReference<Instant> slot1 = new AtomicReference<>();
                AtomicReference<Instant> slot2 = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition<>(
                                        State.class, EventA.class, (StateRebuildingHandler.FromObjectAndRawEvent<
                                                        State, EventA>)
                                                (state, event, raw) -> {
                                                    slot1.set(raw.time());
                                                    return new State(true);
                                                }),
                                new StateRebuildingHandlerDefinition<>(
                                        State.class, EventB.class, (StateRebuildingHandler.FromObjectAndRawEvent<
                                                        State, EventB>)
                                                (state, event, raw) -> {
                                                    slot2.set(raw.time());
                                                    return state;
                                                }))
                        .using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>) (c, p) -> null)
                        .givenTime(instant)
                        .andGiven(new EventA("test"))
                        .andGivenTimeDelta(Duration.ofDays(1))
                        .andGiven(new EventB(42L))
                        .when(new DummyCommand())
                        .expectSuccessfulExecution();

                assertThat(slot1).hasValue(instant);
                assertThat(slot2).hasValueSatisfying(i -> assertThat(i).isEqualTo(instant.plus(Duration.ofDays(1))));
            }
        }

        @Nested
        @DisplayName("givenState")
        public class GivenState {

            @Test
            public void stateInitialized() {
                AtomicReference<State> slot = new AtomicReference<>();
                State s = new State(true);

                subject.using(State.class, (CommandHandler.ForInstanceAndCommand<State, DummyCommand, Void>)
                                (i, c, p) -> {
                                    slot.set(i);
                                    return null;
                                })
                        .givenState(s)
                        .when(new DummyCommand())
                        .expectSuccessfulExecution();

                assertThat(slot.get()).isSameAs(s);
            }
        }

        @Nested
        @DisplayName("given(Consumer<GivenEvent>)/andGiven(Consumer<GivenEvent>)")
        public class GivenEvent {

            @Test
            public void givenEventAppliedButNotExpectable() {
                Instant instant = Instant.now().minusSeconds(13443);

                AtomicReference<EventA> payload = new AtomicReference<>();
                AtomicReference<Map<String, ?>> metaData = new AtomicReference<>();
                AtomicReference<String> subject = new AtomicReference<>();
                AtomicReference<Event> raw = new AtomicReference<>();
                AtomicReference<State> state = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                State.class,
                                EventA.class,
                                (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<State, EventA>)
                                        (i, e, m, s, r) -> {
                                            payload.set(e);
                                            metaData.set(m);
                                            subject.set(s);
                                            raw.set(r);
                                            return new State(true);
                                        }))
                        .using(State.class, (CommandHandler.ForInstanceAndCommand<State, DummyCommand, Void>)
                                (i, c, p) -> {
                                    state.set(i);
                                    return null;
                                })
                        .given(it -> it.payload(new EventA("test"))
                                .metaData(Map.of("key", 42L))
                                .subject("/test/event-a")
                                .id("001")
                                .time(instant))
                        .when(new DummyCommand())
                        .expectNoEvents();

                assertThat(payload).hasValue(new EventA("test"));
                assertThat(metaData).hasValue(Map.of("key", 42L));
                assertThat(subject).hasValue("/test/event-a");
                assertThat(raw).hasValueSatisfying(event -> {
                    assertThat(event.id()).isEqualTo("001");
                    assertThat(event.subject()).isEqualTo("/test/event-a");
                    assertThat(event.time()).isEqualTo(instant);
                });
                assertThat(state).hasValue(new State(true));
            }

            @Test
            public void andGivenEventAppliedButNotExpectable() {
                Instant instant = Instant.now().minusSeconds(13443);

                AtomicReference<EventA> payload = new AtomicReference<>();
                AtomicReference<Map<String, ?>> metaData = new AtomicReference<>();
                AtomicReference<String> subject = new AtomicReference<>();
                AtomicReference<Event> raw = new AtomicReference<>();
                AtomicReference<State> state = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                State.class,
                                EventA.class,
                                (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<State, EventA>)
                                        (i, e, m, s, r) -> {
                                            payload.set(e);
                                            metaData.set(m);
                                            subject.set(s);
                                            raw.set(r);
                                            return new State(true);
                                        }))
                        .using(State.class, (CommandHandler.ForInstanceAndCommand<State, DummyCommand, Void>)
                                (i, c, p) -> {
                                    state.set(i);
                                    return null;
                                })
                        .givenNothing()
                        .andGiven(it -> it.payload(new EventA("test"))
                                .metaData(Map.of("key", 42L))
                                .subject("/test/event-a")
                                .id("001")
                                .time(instant))
                        .when(new DummyCommand())
                        .expectNoEvents();

                assertThat(payload).hasValue(new EventA("test"));
                assertThat(metaData).hasValue(Map.of("key", 42L));
                assertThat(subject).hasValue("/test/event-a");
                assertThat(raw).hasValueSatisfying(event -> {
                    assertThat(event.id()).isEqualTo("001");
                    assertThat(event.subject()).isEqualTo("/test/event-a");
                    assertThat(event.time()).isEqualTo(instant);
                });
                assertThat(state).hasValue(new State(true));
            }

            @Test
            public void missingPayloadDetected() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, p) -> null)
                                        .given(it -> it.metaData(Map.of("key", 42L))
                                                .subject("/test/event-a")
                                                .id("001")
                                                .time(Instant.now()))
                                        .when(new DummyCommand()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContainingAll("payload()", "must be specified");
            }

            @Test
            public void missingEventSourcingHandlerDetected() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, p) -> null)
                                        .given(it -> it.payload(new EventB(42L)))
                                        .when(new DummyCommand()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContainingAll(
                                "suitable state rebuilding handler definition", EventB.class.getSimpleName());
            }
        }

        @Nested
        @DisplayName("given(Object[])/andGiven(Object[])")
        public class GivenEvents {

            @Test
            public void noEventsApplied() {
                StateRebuildingHandler.FromObjectAndRawEvent<State, Object> stateRebuildingHandler = mock();
                AtomicReference<State> slot = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                State.class, Object.class, stateRebuildingHandler))
                        .using(State.class, (CommandHandler.ForInstanceAndCommand<State, DummyCommand, Void>)
                                (i, c, p) -> {
                                    slot.set(i);
                                    return null;
                                })
                        .given()
                        .when(new DummyCommand())
                        .expectNoEvents();

                assertThat(slot.get()).isNull();
                verifyNoInteractions(stateRebuildingHandler);
            }

            @Test
            public void singleGivenEventAppliedButNotExpectable() {
                AtomicReference<EventA> payload = new AtomicReference<>();
                AtomicReference<Map<String, ?>> metaData = new AtomicReference<>();
                AtomicReference<String> subject = new AtomicReference<>();
                AtomicReference<Event> raw = new AtomicReference<>();
                AtomicReference<State> state = new AtomicReference<>();

                DummyCommand command = new DummyCommand();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                State.class,
                                EventA.class,
                                (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<State, EventA>)
                                        (i, e, m, s, r) -> {
                                            payload.set(e);
                                            metaData.set(m);
                                            subject.set(s);
                                            raw.set(r);
                                            return new State(true);
                                        }))
                        .using(State.class, (CommandHandler.ForInstanceAndCommand<State, DummyCommand, Void>)
                                (i, c, p) -> {
                                    state.set(i);
                                    return null;
                                })
                        .given(new EventA("Ted"))
                        .when(command)
                        .expectNoEvents();

                assertThat(payload).hasValue(new EventA("Ted"));
                assertThat(metaData).hasValue(Map.of());
                assertThat(subject).hasValue(command.getSubject());
                assertThat(raw).hasValueSatisfying(event -> {
                    assertThat(event.id()).isNotBlank();
                    assertThat(event.subject()).isEqualTo(command.getSubject());
                    assertThat(event.time()).isInThePast();
                });
                assertThat(state).hasValue(new State(true));
            }

            @Test
            public void multipleGivenEventsAppliedButNotExpectable() {
                AtomicReference<EventB> payload = new AtomicReference<>();
                AtomicReference<Map<String, ?>> metaData = new AtomicReference<>();
                AtomicReference<String> subject = new AtomicReference<>();
                AtomicReference<Event> raw = new AtomicReference<>();
                AtomicReference<State> state = new AtomicReference<>();

                DummyCommand command = new DummyCommand();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition<>(
                                        State.class,
                                        EventA.class,
                                        (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<
                                                        State, EventA>)
                                                (i, e, m, s, r) -> {
                                                    return new State(false);
                                                }),
                                new StateRebuildingHandlerDefinition<>(
                                        State.class,
                                        EventB.class,
                                        (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<
                                                        State, EventB>)
                                                (i, e, m, s, r) -> {
                                                    assertThat(i.valid).isFalse();

                                                    payload.set(e);
                                                    metaData.set(m);
                                                    subject.set(s);
                                                    raw.set(r);
                                                    return new State(true);
                                                }))
                        .using(State.class, (CommandHandler.ForInstanceAndCommand<State, DummyCommand, Void>)
                                (i, c, p) -> {
                                    state.set(i);
                                    return null;
                                })
                        .given(new EventA("Test"), new EventB(42L))
                        .when(command)
                        .expectNoEvents();

                assertThat(payload).hasValue(new EventB(42L));
                assertThat(metaData).hasValue(Map.of());
                assertThat(subject).hasValue(command.getSubject());
                assertThat(raw).hasValueSatisfying(event -> {
                    assertThat(event.id()).isNotBlank();
                    assertThat(event.subject()).isEqualTo(command.getSubject());
                    assertThat(event.time()).isInThePast();
                });
                assertThat(state).hasValue(new State(true));
            }

            @Test
            public void multipleAndGivenEventsAppliedButNotExpectable() {
                AtomicReference<EventB> payload = new AtomicReference<>();
                AtomicReference<Map<String, ?>> metaData = new AtomicReference<>();
                AtomicReference<String> subject = new AtomicReference<>();
                AtomicReference<Event> raw = new AtomicReference<>();
                AtomicReference<State> state = new AtomicReference<>();

                DummyCommand command = new DummyCommand();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition<>(
                                        State.class,
                                        EventA.class,
                                        (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<
                                                        State, EventA>)
                                                (i, e, m, s, r) -> {
                                                    return new State(false);
                                                }),
                                new StateRebuildingHandlerDefinition<>(
                                        State.class,
                                        EventB.class,
                                        (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<
                                                        State, EventB>)
                                                (i, e, m, s, r) -> {
                                                    assertThat(i.valid).isFalse();

                                                    payload.set(e);
                                                    metaData.set(m);
                                                    subject.set(s);
                                                    raw.set(r);
                                                    return new State(true);
                                                }))
                        .using(State.class, (CommandHandler.ForInstanceAndCommand<State, DummyCommand, Void>)
                                (i, c, p) -> {
                                    state.set(i);
                                    return null;
                                })
                        .given(new EventA("Test"))
                        .andGiven(new EventB(42L))
                        .when(command)
                        .expectNoEvents();

                assertThat(payload).hasValue(new EventB(42L));
                assertThat(metaData).hasValue(Map.of());
                assertThat(subject).hasValue(command.getSubject());
                assertThat(raw).hasValueSatisfying(event -> {
                    assertThat(event.id()).isNotBlank();
                    assertThat(event.subject()).isEqualTo(command.getSubject());
                    assertThat(event.time()).isInThePast();
                });
                assertThat(state).hasValue(new State(true));
            }

            @Test
            public void missingEventSourcingHandlerDetected() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, p) -> null)
                                        .given(new EventB(42L))
                                        .when(new DummyCommand()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContainingAll(
                                "suitable state rebuilding handler definition", EventB.class.getSimpleName());
            }
        }

        @Nested
        @DisplayName("givenCommand/andGivenCommand")
        public class GivenCommand {

            @Test
            public void givenCommandCapturedEventsApplied() {
                Instant instant = Instant.now().minusSeconds(42);

                var builder = CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                        new StateRebuildingHandlerDefinition<>(
                                State.class,
                                EventA.class,
                                (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<State, EventA>)
                                        (i, e, m, s, raw) -> {
                                            assertThat(i).isNull();
                                            assertThat(e).isEqualTo(new EventA("test"));
                                            assertThat(m).isEqualTo(Map.of("key01", 42L));
                                            assertThat(raw.time()).isEqualTo(instant);
                                            assertThat(raw.id()).isEqualTo("id001");

                                            return new State(false);
                                        }),
                        new StateRebuildingHandlerDefinition<>(
                                State.class, EventB.class, (StateRebuildingHandler.FromObjectAndMetaDataAndSubject<
                                                State, EventB>)
                                        (i, e, m, s) -> {
                                            assertThat(i).isNotNull();
                                            assertThat(e).isEqualTo(new EventB(42L));
                                            assertThat(m).isEqualTo(Map.of("key02", true, "key03", "hello"));
                                            assertThat(s).isEqualTo(new DummyCommand().getSubject() + "/suffix");

                                            return new State(!i.valid());
                                        }));

                var givenCommandFixture = builder.using(
                        State.class,
                        (CommandHandler.ForInstanceAndCommandAndMetaData<State, DummyCommand, Void>) (i, c, m, p) -> {
                            assertThat(i).isEqualTo(new State(false));
                            p.publishRelative(
                                    "suffix", new EventB(42L), Map.of("key02", true, "key03", m.get("key03")));
                            return null;
                        });

                builder.using(State.class, (CommandHandler.ForInstanceAndCommandAndMetaData<
                                        State, DummyCommand, Boolean>)
                                (i, c, m, p) -> i.valid())
                        .given(it -> it.payload(new EventA("test"))
                                .metaData(Map.of("key01", 42L))
                                .subject("/test/42")
                                .time(instant)
                                .id("id001"))
                        .andGivenCommand(givenCommandFixture, new DummyCommand(), Map.of("key03", "hello"))
                        .when(new DummyCommand())
                        .expectResult(true);
            }

            @Test
            public void givenCommandExecutionFailureDetected() {
                var givenCommandFixture =
                        subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>) (c, p) -> {
                            throw new RuntimeException("givenCommand");
                        });

                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, p) -> null)
                                        .givenCommand(givenCommandFixture, new DummyCommand()))
                        .isInstanceOf(AssertionError.class)
                        .hasCauseInstanceOf(RuntimeException.class);
            }
        }
    }

    @Nested
    public class Expect {

        CommandHandlingTestFixture.Builder<State> subject =
                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                        eshIdentity(EventA.class), eshIdentity(EventB.class), eshIdentity(EventC.class));

        @Nested
        @DisplayName("expectSuccessfulExecution")
        public class ExpectSuccessfulExecution {

            @Test
            public void successfulExecution_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectSuccessfulExecution())
                        .doesNotThrowAnyException();
            }

            @Test
            public void unsuccessfulExecution_failing() {
                RuntimeException error = new RuntimeException("command handling error");
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    throw error;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectSuccessfulExecution())
                        .isInstanceOf(AssertionError.class)
                        .hasCauseReference(error)
                        .hasMessageContainingAll("Failed execution");
            }
        }

        @Nested
        @DisplayName("expectException")
        public class ExpectException {

            @Test
            public void failedExecution_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            throw new RuntimeException();
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectException(RuntimeException.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void failedExecutionWrongErrorType_failing() {
                RuntimeException exception = new RuntimeException();
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    throw exception;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectException(Error.class))
                        .isInstanceOf(AssertionError.class)
                        .hasCauseReference(exception)
                        .hasMessageContainingAll(
                                "Captured exception", "wrong type", RuntimeException.class.getSimpleName());
            }

            @Test
            public void successfulExecution_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> null)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectException(RuntimeException.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("No exception occurred");
            }
        }

        @Nested
        @DisplayName("expectExceptionSatisfying")
        public class ExpectExceptionSatisfying {

            @Test
            public void assertedException_successfully() {
                RuntimeException exception = new RuntimeException();
                AtomicReference<Throwable> asserted = new AtomicReference<>();
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            throw exception;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectExceptionSatisfying(asserted::set))
                        .doesNotThrowAnyException();

                assertThat(asserted.get()).isSameAs(exception);
            }

            @Test
            public void assertedException_errorsPropagated() {
                RuntimeException assertionError = new RuntimeException();
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    throw new IllegalArgumentException();
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectExceptionSatisfying(e -> {
                                            throw assertionError;
                                        }))
                        .isSameAs(assertionError);
            }

            @Test
            public void successfulExecution_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> null)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectExceptionSatisfying(e -> {}))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("No exception occurred");
            }
        }

        @Nested
        @DisplayName("expectResult")
        public class ExpectResult {

            @Test
            public void equalResult_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Long>)
                                        (c, p) -> 42L)
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectResult(42L))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualResult_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Long>)
                                                (c, p) -> 24L)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectResult(42L))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("result expected", "24", "differs", "42");
            }
        }

        @Nested
        @DisplayName("expectResultSatisfying")
        public class ExpectResultSatisfying {

            @Test
            public void assertedResult_successfully() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Long>)
                                        (c, p) -> 42L)
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectResultSatisfying(r -> {}))
                        .doesNotThrowAnyException();
            }

            @Test
            public void assertedResult_errorPropagated() {
                AssertionError error = new AssertionError();
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Long>)
                                                (c, p) -> 42L)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectResultSatisfying(r -> {
                                            throw error;
                                        }))
                        .isSameAs(error);
            }
        }

        @Nested
        @DisplayName("expectState")
        public class ExpectState {

            CommandHandlingTestFixture.Builder<State> subject =
                    CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                            new StateRebuildingHandlerDefinition<>(
                                    State.class, EventA.class, (StateRebuildingHandler.FromObject<State, EventA>)
                                            (instance, event) -> new State(false)),
                            new StateRebuildingHandlerDefinition<>(
                                    State.class, EventB.class, (StateRebuildingHandler.FromObject<State, EventB>)
                                            (instance, event) -> new State(!instance.valid)));

            @Test
            public void equalState_successful() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectState(new State(true)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualState_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectState(new State(false)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "expected to be equal",
                                "captured",
                                "State[valid=true]",
                                "differs",
                                "State[valid=false]");
            }

            @Test
            public void nonEqualState_successfulIfCommandHandlerFails() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventB(42L));
                                            throw new RuntimeException("test");
                                        })
                                .givenState(new State(true))
                                .when(new DummyCommand())
                                .expectException(RuntimeException.class)
                                .expectState(new State(true)))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("expectStateExtracting")
        public class ExpectStateExtracting {

            CommandHandlingTestFixture.Builder<State> subject =
                    CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                            new StateRebuildingHandlerDefinition<>(
                                    State.class, EventA.class, (StateRebuildingHandler.FromObject<State, EventA>)
                                            (instance, event) -> new State(false)),
                            new StateRebuildingHandlerDefinition<>(
                                    State.class, EventB.class, (StateRebuildingHandler.FromObject<State, EventB>)
                                            (instance, event) -> new State(!instance.valid)));

            @Test
            public void equalExtractedState_successful() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectStateExtracting(State::valid, true))
                        .doesNotThrowAnyException();
            }

            @Test
            public void equalExtractedNullState_successful() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .givenState(new State(null))
                                .when(new DummyCommand())
                                .expectStateExtracting(State::valid, null))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualExtractedState_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectStateExtracting(State::valid, false))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected to be equal", "captured", "true", "differs", "false");
            }

            @Test
            public void nonEqualExtractedNullState_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectStateExtracting(State::valid, null))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected to be equal", "captured", "true", "differs", "null");
            }
        }

        @Nested
        @DisplayName("expectStateSatisfying")
        public class ExpectStateSatisfying {

            CommandHandlingTestFixture.Builder<State> subject =
                    CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                            new StateRebuildingHandlerDefinition<>(
                                    State.class, EventA.class, (StateRebuildingHandler.FromObject<State, EventA>)
                                            (instance, event) -> new State(false)),
                            new StateRebuildingHandlerDefinition<>(
                                    State.class, EventB.class, (StateRebuildingHandler.FromObject<State, EventB>)
                                            (instance, event) -> new State(!instance.valid)));

            @Test
            public void assertedEvent_successfully() {
                AtomicReference<Object> state = new AtomicReference<>();
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectStateSatisfying(state::set))
                        .doesNotThrowAnyException();

                assertThat(state.get()).isEqualTo(new State(true));
            }

            @Test
            public void assertedEvent_errorsPropagated() {
                RuntimeException assertionError = new RuntimeException();
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectStateSatisfying(e -> {
                                            throw assertionError;
                                        }))
                        .isSameAs(assertionError);
            }
        }

        @Nested
        @DisplayName("expectEvents")
        public class ExpectEvents {

            @Test
            public void equalEvents_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectEvents(new EventA("Hugo"), new EventB(42L)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_ignoredNotFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectEvents(new EventA("Hugo")))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectEvents(new EventA("Ted")))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected to be equal", "EventA[name=Ted]", "EventA[name=Hugo]");
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> null)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectEvents(new EventA("Hugo")))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected", EventA.class.getSimpleName());
            }

            @Test
            public void partialMissingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectEvents(new EventA("Hugo"), new EventB(42L)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected", EventB.class.getSimpleName());
            }
        }

        @Nested
        @DisplayName("expectEventTypes")
        public class ExpectEventTypes {

            @Test
            public void equalEventTypes_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectEventTypes(EventA.class, EventB.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_ignoredNotFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectEventTypes(EventA.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualEventTypes_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectEventTypes(EventB.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Event type not as expected", EventA.class.getSimpleName());
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> null)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectEventTypes(EventA.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected", EventA.class.getSimpleName());
            }

            @Test
            public void partialMissingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectEventTypes(EventA.class, EventB.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected", EventB.class.getSimpleName());
            }
        }

        @Nested
        @DisplayName("expectEventsSatisfying")
        public class ExpectEventsSatisfying {

            @Test
            public void assertedEvents_successfully() {
                AtomicReference<List<Object>> events = new AtomicReference<>();
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectEventsSatisfying(events::set))
                        .doesNotThrowAnyException();

                assertThat(events.get()).containsExactly(new EventA("Hugo"), new EventB(42L));
            }

            @Test
            public void assertedEvents_errorsPropagated() {
                RuntimeException assertionError = new RuntimeException();
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectEventsSatisfying(objects -> {
                                            throw assertionError;
                                        }))
                        .isSameAs(assertionError);
            }
        }

        @Nested
        @DisplayName("expectEventsInAnyOrder")
        public class ExpectEventsInAnyOrder {

            @Test
            public void equalEventsInOrder_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectEventsInAnyOrder(new EventA("Hugo"), new EventB(42L)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void equalEventsOutOfOrder_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventB(42L));
                                            publisher.publish(new EventA("Hugo"));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectEventsInAnyOrder(new EventA("Hugo"), new EventB(42L)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_ignoredNotFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventB(42L));
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventA("Ignored"));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectEventsInAnyOrder(new EventA("Hugo"), new EventB(42L)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventB(43L));
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectEventsInAnyOrder(new EventA("Hugo"), new EventB(42L)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("following events were captured", "did not match", "EventB[size=43]");
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectEventsInAnyOrder(new EventA("Hugo"), new EventB(42L)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("No more events captured", "expected 1 more events");
            }
        }

        @Nested
        @DisplayName("expectEventTypesInAnyOrder")
        public class ExpectEventTypesInAnyOrder {

            @Test
            public void equalEventTypesInOrder_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectEventTypesInAnyOrder(EventA.class, EventB.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void equalEventTypesOutOfOrder_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventB(42L));
                                            publisher.publish(new EventA("Hugo"));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectEventTypesInAnyOrder(EventA.class, EventB.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_ignoredNotFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventB(42L));
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventA("Ignored"));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectEventTypesInAnyOrder(EventA.class, EventB.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualEventType_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventB(43L));
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectEventTypesInAnyOrder(EventA.class, EventC.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "following events were captured", "did not match", EventB.class.getSimpleName());
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectEventTypesInAnyOrder(EventA.class, EventB.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("No more events captured", "expected 1 more events");
            }
        }

        @Nested
        @DisplayName("expectNextEvent(E payload)")
        public class ExpectNextEvent_Payload {

            @Test
            public void equalEvents_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNextEvent(new EventA("Hugo"))
                                .expectNextEvent(new EventB(42L)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_ignoredNotFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNextEvent(new EventA("Hugo")))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(43L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEvent(new EventA("Hugo"))
                                        .expectNextEvent(new EventB(42L)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected to be equal", "EventB[size=43]", "EventB[size=42]");
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventB(43L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEvent(new EventB(43L))
                                        .expectNextEvent(new EventA("Hugo")))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected", EventA.class.getSimpleName());
            }
        }

        @Nested
        @DisplayName("expectNextEvent(Consumer<EventAsserter>)")
        public class ExpectNextEvent_EventAsserter {

            @Test
            public void successfulEventAssertion_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNextEvent(it -> {})
                                .expectNextEvent(it -> {}))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_ignoredNotFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNextEvent(it -> {}))
                        .doesNotThrowAnyException();
            }

            @Test
            public void unsucessfulEventAssertion_failing() {
                AssertionError assertionError = new AssertionError("custom");
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEvent(it -> {
                                            throw assertionError;
                                        }))
                        .isSameAs(assertionError);
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> null)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEvent(it -> {}))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("No more events captured.");
            }
        }

        @Nested
        @DisplayName("expectNextEventType")
        public class ExpectNextEventType {

            @Test
            public void equalEventType_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNextEventType(EventA.class)
                                .expectNextEventType(EventB.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEventTypes_ignoredNotFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNextEventType(EventA.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualEventType_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(43L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEventType(EventB.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected", EventA.class.getSimpleName());
            }

            @Test
            public void missingEventType_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventB(43L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEventType(EventB.class)
                                        .expectNextEventType(EventA.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected", EventA.class.getSimpleName());
            }
        }

        @Nested
        @DisplayName("expectNextEventSatisfying")
        public class ExpectNextEventSatisfying {

            @Test
            public void assertedEvent_successfully() {
                AtomicReference<Object> event = new AtomicReference<>();
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNextEventSatisfying(event::set))
                        .doesNotThrowAnyException();

                assertThat(event.get()).isEqualTo(new EventA("Hugo"));
            }

            @Test
            public void assertedEvent_errorsPropagated() {
                RuntimeException assertionError = new RuntimeException();
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEventSatisfying(e -> {
                                            throw assertionError;
                                        }))
                        .isSameAs(assertionError);
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> null)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEventSatisfying(e -> {}))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("No more events captured");
            }
        }

        @Nested
        @DisplayName("expectNoMoreEvents")
        public class ExpectNoMoreEvents {

            @Test
            public void noMoreEvents_successful() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNextEvent(new EventA("Hugo"))
                                .expectNoMoreEvents())
                        .doesNotThrowAnyException();
            }

            @Test
            public void noEvents_successful() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNoMoreEvents())
                        .doesNotThrowAnyException();
            }

            @Test
            public void moreEvents_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNoMoreEvents())
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("No more events expected", "but got", new EventA("Hugo").toString());
            }
        }

        @Nested
        @DisplayName("expectSingleEvent(E payload)")
        public class ExpectSingleEvent_Payload {

            @Test
            public void equalEvent_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectSingleEvent(new EventA("Hugo")))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectSingleEvent(new EventA("Hugo")))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("No more events expected", "but got", new EventB(42L).toString());
            }

            @Test
            public void nonEqualEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectSingleEvent(new EventA("Ted")))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected to be equal", "EventA[name=Ted]", "EventA[name=Hugo]");
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> null)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectSingleEvent(new EventA("Hugo")))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected", EventA.class.getSimpleName());
            }

            @Test
            public void previousEventsVerified_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEvent(new EventA("Hugo"))
                                        .expectSingleEvent(new Object()))
                        .isInstanceOf(Error.class)
                        .hasMessageContainingAll("previous events", "already", "verified");
            }
        }

        @Nested
        @DisplayName("expectSingleEvent(Consumer<EventAsserter>)")
        public class ExpectSingleEvent_EventAsserter {

            @Test
            public void successfulEventAssertion_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectSingleEvent(it -> {}))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectSingleEvent(it -> {}))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("No more events expected", "but got", new EventB(42L).toString());
            }

            @Test
            public void unsucessfulEventAssertion_failing() {
                AssertionError assertionError = new AssertionError("custom");
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEvent(it -> {
                                            throw assertionError;
                                        }))
                        .isSameAs(assertionError);
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> null)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectSingleEvent(it -> {}))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("No more events captured.");
            }

            @Test
            public void previousEventsVerified_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEvent(new EventA("Hugo"))
                                        .expectSingleEvent(it -> {}))
                        .isInstanceOf(Error.class)
                        .hasMessageContainingAll("previous events", "already", "verified");
            }
        }

        @Nested
        @DisplayName("expectSingleEventType")
        public class ExpectSingleEventType {

            @Test
            public void equalEventType_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectSingleEventType(EventA.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectSingleEventType(EventA.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("No more events expected", "but got", new EventB(42L).toString());
            }

            @Test
            public void nonEqualEventType_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectSingleEventType(EventB.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("type not as expected", EventA.class.getSimpleName());
            }

            @Test
            public void missingEventType_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> null)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectSingleEventType(EventA.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected", EventA.class.getSimpleName());
            }

            @Test
            public void previousEventsVerified_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEvent(new EventA("Hugo"))
                                        .expectSingleEventType(Object.class))
                        .isInstanceOf(Error.class)
                        .hasMessageContainingAll("previous events", "already", "verified");
            }
        }

        @Nested
        @DisplayName("expectSingleEventSatisfying")
        public class ExpectSingleEventSatisfying {

            @Test
            public void assertedEvent_successfully() {
                AtomicReference<Object> event = new AtomicReference<>();
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectSingleEventSatisfying(event::set))
                        .doesNotThrowAnyException();

                assertThat(event.get()).isEqualTo(new EventA("Hugo"));
            }

            @Test
            public void assertedEvent_errorsPropagated() {
                RuntimeException assertionError = new RuntimeException();
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectSingleEventSatisfying(e -> {
                                            throw assertionError;
                                        }))
                        .isSameAs(assertionError);
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> null)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectSingleEventSatisfying(e -> {}))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("No more events captured");
            }

            @Test
            public void previousEventsVerified_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEvent(new EventA("Hugo"))
                                        .expectSingleEventSatisfying(e -> {}))
                        .isInstanceOf(Error.class)
                        .hasMessageContainingAll("previous events", "already", "verified");
            }
        }

        @Nested
        @DisplayName("expectNumEvents")
        public class ExpectNumEvents {

            @Test
            public void numEventsMatch_successful() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNumEvents(2)
                                .skipEvents(1)
                                .expectNumEvents(2))
                        .doesNotThrowAnyException();
            }

            @Test
            public void numEventsMismatch_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .skipEvents(1)
                                        .expectNumEvents(3))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Number of expected events", "expected 3", "captured: 2");
            }
        }

        @Nested
        @DisplayName("expectNoEvents")
        public class ExpectNoEvents {

            @Test
            public void noEventsCaptured_successful() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNoEvents())
                        .doesNotThrowAnyException();
            }

            @Test
            public void eventsCaptured_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .skipEvents(2)
                                        .expectNoEvents())
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Number of expected events", "expected 0", "captured: 2");
            }

            @Test
            public void noEventsCaptured_successfulIfCommandHandlerFailingAfterEventPublication() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            throw new RuntimeException("test");
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectException(RuntimeException.class)
                                .expectNoEvents())
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("skipEvents")
        public class SkipEvents {

            @Test
            public void capturedEvents_skipped() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .skipEvents(1)
                                .expectNextEventType(EventB.class)
                                .expectNoMoreEvents())
                        .doesNotThrowAnyException();
            }

            @Test
            public void allCapturedEvents_skipped() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .skipEvents(2)
                                .expectNoMoreEvents())
                        .doesNotThrowAnyException();
            }

            @Test
            public void capturedEventsInsufficient_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .skipEvents(3))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Not enough events");
            }
        }

        @Nested
        @DisplayName("expectAnyEvent(E payload)")
        public class ExpectAnyEvent_Payload {

            @Test
            public void equalEvents_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectAnyEvent(new EventB(42L))
                                .expectAnyEvent(new EventA("Hugo")))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_ignoredNotFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectAnyEvent(new EventB(42L)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void eventMatched_positionRestored() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectAnyEvent(new EventB(42L))
                                .expectNextEventType(EventA.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(43L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectAnyEvent(new EventB(42L)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "None of the remaining captured events matched the given assertion",
                                "expected to be equal",
                                "EventB[size=43]",
                                "EventB[size=42]");
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(43L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEventType(EventA.class)
                                        .expectAnyEvent(new EventA("Hugo")))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "None of the remaining captured events matched the given assertion",
                                "expected",
                                EventA.class.getSimpleName());
            }
        }

        @Nested
        @DisplayName("expectAnyEvent(Consumer<EventAsserter>)")
        public class ExpectAnyEvent_EventAsserter {

            @Test
            public void successfulEventAssertion_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectAnyEvent(it -> it.payload(new EventB(42L)))
                                .expectAnyEvent(it -> it.payload(new EventA("Hugo"))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_ignoredNotFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectAnyEvent(it -> it.payload(new EventB(42L))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void eventMatched_positionRestored() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectAnyEvent(it -> it.payload(new EventB(42L)))
                                .expectNextEventType(EventA.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void unsucessfulEventAssertion_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(43L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectAnyEvent(it -> it.payload(new EventB(42L))))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "None of the remaining captured events matched the given assertion",
                                "expected to be equal",
                                "EventB[size=43]",
                                "EventB[size=42]");
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(43L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNextEventType(EventA.class)
                                        .expectAnyEvent(it -> it.payload(new EventA("Hugo"))))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "None of the remaining captured events matched the given assertion",
                                "expected",
                                EventA.class.getSimpleName());
            }

            @Test
            public void multipleAssertionErrors_allCaptured() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("HugoA"));
                                                    publisher.publish(new EventA("HugoB"));
                                                    publisher.publish(new EventB(100L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectAnyEvent(it -> it.payload(new EventA("HugoC"))))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "None of the remaining captured events matched the given assertion, the following assertion errors have been captured:",
                                "expected",
                                EventA.class.getSimpleName(),
                                "HugoA",
                                "HugoB",
                                EventB.class.getSimpleName());
            }
        }

        @Nested
        @DisplayName("expectNoEvent(Consumer<EventAsserter>)")
        public class ExpectNoEvent_EventAsserter {

            @Test
            public void singleEventAssertingSuccessfully_shouldFail() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNoEvent(it -> it.payload(new EventA("Hugo"))))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "The following remaining captured events matched the given assertion:",
                                EventA.class.getSimpleName());
            }

            @Test
            public void multipleEventsAssertingSuccessfully_shouldFail() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo1"));
                                                    publisher.publish(new EventA("Hugo2"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNoEvent(it -> it.payloadSatisfying(p -> {
                                            if (p instanceof EventA) {
                                                assertThat(((EventA) p).name).startsWith("Hugo");
                                            }
                                        })))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "The following remaining captured events matched the given assertion:",
                                EventA.class.getSimpleName(),
                                "Hugo1",
                                "Hugo2");
            }

            @Test
            public void noEventSuccessfullyAsserting_successful() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNoEvent(it -> it.payload(new EventC())))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("expectAnyEventType")
        public class ExpectAnyEventType {

            @Test
            public void equalEvents_notFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectAnyEventType(EventB.class)
                                .expectAnyEventType(EventA.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void additionalEvents_ignoredNotFailing() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectAnyEventType(EventB.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void eventMatched_positionRestored() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectAnyEventType(EventB.class)
                                .expectNextEventType(EventA.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventB(43L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectAnyEventType(EventA.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "None of the remaining captured events matched the given assertion",
                                "expected",
                                EventB.class.getSimpleName());
            }
        }

        @Nested
        @DisplayName("expectAnyEventSatisfying")
        public class ExpectAnyEventSatisfying {

            @Test
            public void assertedEvent_successfully() {
                AtomicReference<Object> event = new AtomicReference<>();
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectAnyEventSatisfying(
                                        e -> assertThat(e).isInstanceOfSatisfying(EventB.class, event::set)))
                        .doesNotThrowAnyException();

                assertThat(event.get()).isEqualTo(new EventB(42L));
            }

            @Test
            public void assertedEvent_assertionErrorsAggregated() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectAnyEventSatisfying(e -> {
                                            throw new AssertionError(
                                                    "ERROR: " + e.getClass().getSimpleName());
                                        }))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "None of the remaining captured events matched the given assertion",
                                "ERROR: EventA",
                                "ERROR: EventB");
            }

            @Test
            public void assertedEvent_nonAssertionErrorsPropagated() {
                RuntimeException assertionError = new RuntimeException();
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectAnyEventSatisfying(e -> {
                                            throw assertionError;
                                        }))
                        .isSameAs(assertionError);
            }

            @Test
            public void missingEvent_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> null)
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectAnyEventSatisfying(e -> {}))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("No more events captured");
            }
        }

        @Nested
        @DisplayName("expectNoEventOfType")
        public class ExpectNoEventOfType {

            @Test
            public void noEventOfType_successful() {
                assertThatCode(() -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .givenNothing()
                                .when(new DummyCommand())
                                .expectNoEventOfType(EventC.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void eventOfType_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventB(42L));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNoEventOfType(EventB.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "The following remaining captured events matched the given assertion",
                                EventB.class.getSimpleName());
            }

            @Test
            public void subclassEventOfType_failing() {
                assertThatThrownBy(
                                () -> subject.using(State.class, (CommandHandler.ForCommand<State, DummyCommand, Void>)
                                                (c, publisher) -> {
                                                    publisher.publish(new EventA("Hugo"));
                                                    return null;
                                                })
                                        .givenNothing()
                                        .when(new DummyCommand())
                                        .expectNoEventOfType(Serializable.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "The following remaining captured events matched the given assertion",
                                EventA.class.getSimpleName());
            }
        }
    }

    @Nested
    @DisplayName("EventAsserter")
    public class EventAsserterTests {

        private final Command command = new DummyCommand();

        private final CapturedEvent captured =
                new CapturedEvent(command.getSubject(), new EventA("test"), Map.of("key1", true), List.of());

        private final CommandHandlingTestFixture.EventAsserter subject =
                new CommandHandlingTestFixture.EventAsserter(command, captured);

        @Nested
        @DisplayName("payloadType")
        public class PayloadType {

            @ParameterizedTest
            @ValueSource(classes = {Object.class, Serializable.class, EventA.class})
            public void isAssignable_notFailing(Class<?> type) {
                assertThatCode(() -> subject.payloadType(type)).doesNotThrowAnyException();
            }

            @ParameterizedTest
            @ValueSource(classes = {EventB.class, EventC.class, Runnable.class})
            public void isNotAssignable_failing(Class<?> type) {
                assertThatThrownBy(() -> subject.payloadType(type))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "Event type",
                                "expected",
                                captured.event().getClass().getSimpleName());
            }
        }

        @Nested
        @DisplayName("payload")
        public class Payload {

            @Test
            public void equalPayload_notFailing() {
                assertThatCode(() -> subject.payload(new EventA("test"))).doesNotThrowAnyException();
            }

            @Test
            public void nonEqualPayloadContent_failing() {
                EventA expected = new EventA("another");
                assertThatThrownBy(() -> subject.payload(expected))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "payload", "equal", captured.event().toString(), "differs", expected.toString());
            }

            @Test
            public void nonEqualPayloadType_failing() {
                EventB expected = new EventB(42L);
                assertThatThrownBy(() -> subject.payload(expected))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "payload", "equal", captured.event().toString(), "differs", expected.toString());
            }
        }

        @Nested
        @DisplayName("payloadExtracting")
        public class PayloadExtracting {

            @Test
            public void equalProperty_notFailing() {
                assertThatCode(() -> subject.payloadExtracting(EventA::name, "test"))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nullProperty_notFailing() {
                var subject = new CommandHandlingTestFixture.EventAsserter(
                        command, new CapturedEvent(command.getSubject(), new EventA(null), Map.of(), List.of()));

                assertThatCode(() -> subject.payloadExtracting(EventA::name, null))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualProperty_failing() {
                assertThatThrownBy(() -> subject.payloadExtracting(EventA::name, "foobar"))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Extracted payload", "test", "differs", "foobar");
            }

            @Test
            public void nullProperty_failing() {
                assertThatThrownBy(() -> subject.payloadExtracting(EventA::name, null))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Extracted payload", "test", "differs", "null");
            }
        }

        @Nested
        @DisplayName("payloadSatisfying")
        public class PayloadSatisfying {

            @Test
            public void assertedSuccessfully() {
                assertThatCode(() -> subject.payloadSatisfying(o -> {})).doesNotThrowAnyException();
            }

            @Test
            public void assertedUnsuccessfully_errorPropagated() {
                var assertionError = new AssertionError("test");

                assertThatThrownBy(() -> subject.payloadSatisfying(o -> {
                            throw assertionError;
                        }))
                        .isSameAs(assertionError);
            }
        }

        @Nested
        @DisplayName("metaData")
        public class MetaData {

            @Test
            public void equalMetaData_notFailing() {
                assertThatCode(() -> subject.metaData(captured.metaData())).doesNotThrowAnyException();
            }

            @Test
            public void nonEqualMetaData_failing() {
                Map<String, Long> expected = Map.of("key2", 42L);
                assertThatThrownBy(() -> subject.metaData(expected))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "meta-data", "equal", captured.metaData().toString(), "differs", expected.toString());
            }
        }

        @Nested
        @DisplayName("metaDataSatisfying")
        public class MetaDataSatisfying {

            @Test
            public void assertedSuccessfully() {
                assertThatCode(() -> subject.metaDataSatisfying(o -> {})).doesNotThrowAnyException();
            }

            @Test
            public void assertedUnsuccessfully_errorPropagated() {
                var assertionError = new AssertionError("test");

                assertThatThrownBy(() -> subject.metaDataSatisfying(o -> {
                            throw assertionError;
                        }))
                        .isSameAs(assertionError);
            }
        }

        @Nested
        @DisplayName("noMetaData")
        public class NoMetaData {

            @Test
            public void empty_notFailing() {
                var subject = new CommandHandlingTestFixture.EventAsserter(
                        command,
                        new CapturedEvent(command.getSubject(), new EventA("irrelevant"), Map.of(), List.of()));

                assertThatCode(subject::noMetaData).doesNotThrowAnyException();
            }

            @Test
            public void nonEmptyMetaData_failing() {
                assertThatThrownBy(subject::noMetaData)
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll(
                                "Empty",
                                "meta-data",
                                "found",
                                captured.metaData().toString());
            }
        }

        @Nested
        @DisplayName("subject")
        public class Subject {

            @Test
            public void equalSubject_notFailing() {
                assertThatCode(() -> subject.subject(captured.subject())).doesNotThrowAnyException();
            }

            @Test
            public void nonEqualSubject_failing() {
                assertThatThrownBy(() -> subject.subject("another"))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("subject", "equal", captured.subject(), "differs", "another");
            }
        }

        @Nested
        @DisplayName("subjectSatisfying")
        public class SubjectSatisfying {

            @Test
            public void assertedSuccessfully() {
                assertThatCode(() -> subject.subjectSatisfying(o -> {})).doesNotThrowAnyException();
            }

            @Test
            public void assertedUnsuccessfully_errorPropagated() {
                var assertionError = new AssertionError("test");

                assertThatThrownBy(() -> subject.subjectSatisfying(o -> {
                            throw assertionError;
                        }))
                        .isSameAs(assertionError);
            }
        }

        @Nested
        @DisplayName("commandSubject")
        public class CommandSubject {

            @Test
            public void equalSubject_notFailing() {
                assertThatCode(subject::commandSubject).doesNotThrowAnyException();
            }

            @Test
            public void nonEqualSubject_failing() {
                var subject = new CommandHandlingTestFixture.EventAsserter(
                        command, new CapturedEvent("anotherSubject", new EventA("irrelevant"), Map.of(), List.of()));

                assertThatThrownBy(subject::commandSubject)
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("subject", "equal", command.getSubject(), "differs", "anotherSubject");
            }
        }
    }

    static class DummyCommand implements Command {
        @Override
        public String getSubject() {
            return "dummy";
        }
    }

    record State(Boolean valid) {}

    record AnotherState() {}

    record EventA(String name) implements Serializable {}

    record EventB(Long size) {}

    record EventC() {}

    private static <E> StateRebuildingHandlerDefinition<State, E> eshIdentity(Class<E> eventClass) {
        return new StateRebuildingHandlerDefinition<>(
                State.class, eventClass, (StateRebuildingHandler.FromObject<State, E>)
                        (instance, event) -> Optional.ofNullable(instance).orElse(new State(true)));
    }
}
