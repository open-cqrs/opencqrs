/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

            @Test
            public void usingCommandSubjectAppliedToEvent() {
                AtomicReference<String> capturedSubject = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition(
                                        DummyState.class,
                                        EventA.class,
                                        (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<
                                                        Object, EventA>)
                                                (i, e, m, s, r) -> {
                                                    capturedSubject.set(s);
                                                    return new DummyState(true);
                                                }))
                        .using(DummyState.class, (CommandHandler.ForInstanceAndCommand<Object, DummyCommand, Void>)
                                (i, c, p) -> null)
                        .given()
                        .usingCommandSubject()
                        .event(e -> e.payload(new EventA("test")))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(capturedSubject).hasValue("dummy");
            }

            @Test
            public void subjectNotAppliedToGivenEventsIfExplicitlySpecified() {
                AtomicReference<String> capturedSubject = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition(
                                        DummyState.class,
                                        EventA.class,
                                        (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<
                                                        Object, EventA>)
                                                (i, e, m, s, r) -> {
                                                    capturedSubject.set(s);
                                                    return new DummyState(true);
                                                }))
                        .using(DummyState.class, (CommandHandler.ForInstanceAndCommand<Object, DummyCommand, Void>)
                                (i, c, p) -> null)
                        .given()
                        .usingSubject("/default-subject")
                        .event(e -> e.subject("/explicit-subject").payload(new EventA("test")))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(capturedSubject).hasValue("/explicit-subject");
            }

            @Test
            public void subjectNotAppliedToEventsPublishedByGivenCommand() {
                AtomicReference<String> capturedSubject = new AtomicReference<>();

                var publishingFixture = CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition<>(
                                        DummyState.class, EventA.class, (StateRebuildingHandler.FromObject<
                                                        DummyState, EventA>)
                                                (state, event) -> new DummyState(true)))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>) (c, p) -> {
                            p.publish(new EventA("fromOtherFixture"));
                            return null;
                        });

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                                new StateRebuildingHandlerDefinition(
                                        DummyState.class,
                                        EventA.class,
                                        (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<
                                                        Object, EventA>)
                                                (i, e, m, s, r) -> {
                                                    capturedSubject.set(s);
                                                    return new DummyState(true);
                                                }))
                        .using(DummyState.class, (CommandHandler.ForInstanceAndCommand<Object, DummyCommand, Void>)
                                (i, c, p) -> null)
                        .given()
                        .usingSubject("/should-not-be-used")
                        .command(publishingFixture, new DummyCommand())
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(capturedSubject).hasValue("dummy");
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

            @Test
            public void eventTimePassedToStateRebuildingHandler() {
                Instant expectedTime = Instant.now().minusSeconds(12345);
                AtomicReference<Instant> capturedTime = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                DummyState.class, EventA.class, (StateRebuildingHandler.FromObjectAndRawEvent<
                                                DummyState, EventA>)
                                        (state, event, raw) -> {
                                            capturedTime.set(raw.time());
                                            return new DummyState(true);
                                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .event(e -> e.payload(new EventA("test")).time(expectedTime))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(capturedTime).hasValue(expectedTime);
            }

            @Test
            public void eventIdPassedToStateRebuildingHandler() {
                AtomicReference<String> capturedId = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                DummyState.class, EventA.class, (StateRebuildingHandler.FromObjectAndRawEvent<
                                                DummyState, EventA>)
                                        (state, event, raw) -> {
                                            capturedId.set(raw.id());
                                            return new DummyState(true);
                                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .event(e -> e.payload(new EventA("test")).id("custom-event-id"))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(capturedId).hasValue("custom-event-id");
            }

            @Test
            public void eventMetaDataPassedToStateRebuildingHandler() {
                AtomicReference<Map<String, ?>> capturedMetaData = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                DummyState.class,
                                EventA.class,
                                (StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<DummyState, EventA>)
                                        (state, event, metaData, subject, raw) -> {
                                            capturedMetaData.set(metaData);
                                            return new DummyState(true);
                                        }))
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, p) -> null)
                        .given()
                        .event(e -> e.payload(new EventA("test")).metaData(Map.of("key", "value", "num", 42L)))
                        .when(new DummyCommand())
                        .succeeds();

                assertThat(capturedMetaData.get().get("key")).isEqualTo("value");
                assertThat(capturedMetaData.get().get("num")).isEqualTo(42L);
            }

            @Test
            public void givenEventAppliedButNotExpectable() {
                AtomicReference<DummyState> capturedState = new AtomicReference<>();

                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(new StateRebuildingHandlerDefinition<>(
                                DummyState.class,
                                EventA.class,
                                (StateRebuildingHandler.FromObject<DummyState, EventA>)
                                        (state, event) -> new DummyState(true)))
                        .using(DummyState.class, (CommandHandler.ForInstanceAndCommand<DummyState, DummyCommand, Void>)
                                (instance, c, p) -> {
                                    capturedState.set(instance);
                                    p.publish(new EventA("fromCommand"));
                                    return null;
                                })
                        .given()
                        .event(e -> e.payload(new EventA("givenEvent")))
                        .when(new DummyCommand())
                        .succeeds()
                        .then()
                        .allEvents()
                        .count(1)
                        .exactly(e -> e.satisfying((EventA ev) -> assertThat(ev.name()).isEqualTo("fromCommand")));

                assertThat(capturedState.get()).isNotNull();
                assertThat(capturedState.get().valid()).isTrue();
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

            @Test
            public void givenCommandExecutionFailureDetected() {
                var failingFixture = CommandHandlingTestFixture.<DummyState>withStateRebuildingHandlerDefinitions()
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>) (c, p) -> {
                            throw new RuntimeException("givenCommand failed");
                        });

                assertThatThrownBy(
                                () -> CommandHandlingTestFixture.<DummyState>withStateRebuildingHandlerDefinitions()
                                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                                (c, p) -> null)
                                        .given()
                                        .command(failingFixture, new DummyCommand())
                                        .when(new DummyCommand())
                                        .succeeds())
                        .isInstanceOf(AssertionError.class)
                        .hasCauseInstanceOf(RuntimeException.class)
                        .hasRootCauseMessage("givenCommand failed");
            }
        }

        @Nested
        @DisplayName("When")
        public class When {

            @Test
            public void whenWithMetaDataPassedToCommandHandler() {
                AtomicReference<Map<String, ?>> capturedMetaData = new AtomicReference<>();

                CommandHandlingTestFixture.<DummyState>withStateRebuildingHandlerDefinitions()
                        .using(DummyState.class, (CommandHandler.ForInstanceAndCommandAndMetaData<
                                        DummyState, DummyCommand, Void>)
                                (i, c, m, p) -> {
                                    capturedMetaData.set(m);
                                    return null;
                                })
                        .given()
                        .nothing()
                        .when(new DummyCommand(), Map.of("whenKey", "whenValue"))
                        .succeeds();

                assertThat(capturedMetaData.get().get("whenKey")).isEqualTo("whenValue");
            }
        }
    }

    @Nested
    public class Expect {

        CommandHandlingTestFixture.Builder<DummyState> subject =
                CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                        eshIdentity(EventA.class), eshIdentity(EventB.class), eshIdentity(EventC.class));

        @Nested
        @DisplayName("succeeds")
        public class Succeeds {

            @Test
            public void successfulExecution_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds())
                        .doesNotThrowAnyException();
            }

            @Test
            public void unsuccessfulExecution_failing() {
                RuntimeException error = new RuntimeException("command handling error");
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            throw error;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds())
                        .isInstanceOf(AssertionError.class)
                        .hasCauseReference(error);
            }
        }

        @Nested
        @DisplayName("succeeds().withoutEvents")
        public class SucceedsWithoutEvents {

            @Test
            public void noEventsCaptured_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .withoutEvents())
                        .doesNotThrowAnyException();
            }

            @Test
            public void eventsCaptured_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("test"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .withoutEvents())
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Expected no events", "found", "1");
            }
        }

        @Nested
        @DisplayName("succeeds().havingResult")
        public class SucceedsHavingResult {

            @Test
            public void equalResult_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Long>)
                                        (c, p) -> 42L)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .havingResult(42L))
                        .doesNotThrowAnyException();
            }

            @Test
            public void bothNull_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, p) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .havingResult(null))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualResult_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Long>)
                                        (c, p) -> 24L)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .havingResult(42L))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("result", "24", "differs", "42");
            }

            @Test
            public void nullActualNonNullExpected_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Long>)
                                        (c, p) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .havingResult(42L))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("result", "differs", "42");
            }
        }

        @Nested
        @DisplayName("succeeds().resultSatisfying")
        public class SucceedsResultSatisfying {

            @Test
            public void resultPassedToConsumer() {
                AtomicReference<Object> captured = new AtomicReference<>();

                subject.using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Long>)
                                (c, p) -> 42L)
                        .given()
                        .nothing()
                        .when(new DummyCommand())
                        .succeeds()
                        .resultSatisfying(captured::set);

                assertThat(captured.get()).isEqualTo(42L);
            }

            @Test
            public void consumerExecutedSuccessfully() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Long>)
                                        (c, p) -> 42L)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .resultSatisfying(r -> assertThat(r).isEqualTo(42L)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void consumerError_propagated() {
                AssertionError error = new AssertionError("custom error");
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Long>)
                                        (c, p) -> 42L)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .resultSatisfying(r -> {
                                    throw error;
                                }))
                        .isSameAs(error);
            }
        }

        @Nested
        @DisplayName("succeeds().havingState")
        public class SucceedsHavingState {

            CommandHandlingTestFixture.Builder<DummyState> stateSubject =
                    CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                            new StateRebuildingHandlerDefinition<>(
                                    DummyState.class, EventA.class, (StateRebuildingHandler.FromObject<DummyState, EventA>)
                                            (instance, event) -> new DummyState(false)),
                            new StateRebuildingHandlerDefinition<>(
                                    DummyState.class, EventB.class, (StateRebuildingHandler.FromObject<DummyState, EventB>)
                                            (instance, event) -> new DummyState(!instance.valid())));

            @Test
            public void equalState_notFailing() {
                assertThatCode(() -> stateSubject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .havingState(new DummyState(true)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualState_failing() {
                assertThatThrownBy(() -> stateSubject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .havingState(new DummyState(false)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("State", "expected to be equal", "differs");
            }

            @Test
            public void noStateCaptured_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .havingState(new DummyState(true)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("No state captured");
            }
        }

        @Nested
        @DisplayName("succeeds().stateSatisfying")
        public class SucceedsStateSatisfying {

            CommandHandlingTestFixture.Builder<DummyState> stateSubject =
                    CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                            new StateRebuildingHandlerDefinition<>(
                                    DummyState.class, EventA.class, (StateRebuildingHandler.FromObject<DummyState, EventA>)
                                            (instance, event) -> new DummyState(false)),
                            new StateRebuildingHandlerDefinition<>(
                                    DummyState.class, EventB.class, (StateRebuildingHandler.FromObject<DummyState, EventB>)
                                            (instance, event) -> new DummyState(!instance.valid())));

            @Test
            public void statePassedToConsumer() {
                AtomicReference<Object> captured = new AtomicReference<>();

                stateSubject
                        .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                (c, publisher) -> {
                                    publisher.publish(new EventA("Hugo"));
                                    publisher.publish(new EventB(42L));
                                    return null;
                                })
                        .given()
                        .nothing()
                        .when(new DummyCommand())
                        .succeeds()
                        .stateSatisfying(captured::set);

                assertThat(captured.get()).isEqualTo(new DummyState(true));
            }

            @Test
            public void consumerError_propagated() {
                AssertionError error = new AssertionError("custom error");
                assertThatThrownBy(() -> stateSubject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .stateSatisfying(s -> {
                                    throw error;
                                }))
                        .isSameAs(error);
            }
        }

        @Nested
        @DisplayName("succeeds().stateExtracting")
        public class SucceedsStateExtracting {

            CommandHandlingTestFixture.Builder<DummyState> stateSubject =
                    CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(
                            new StateRebuildingHandlerDefinition<>(
                                    DummyState.class, EventA.class, (StateRebuildingHandler.FromObject<DummyState, EventA>)
                                            (instance, event) -> new DummyState(false)),
                            new StateRebuildingHandlerDefinition<>(
                                    DummyState.class, EventB.class, (StateRebuildingHandler.FromObject<DummyState, EventB>)
                                            (instance, event) -> new DummyState(!instance.valid())));

            @Test
            public void equalExtractedState_notFailing() {
                assertThatCode(() -> stateSubject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .stateExtracting(s -> ((DummyState) s).valid(), true))
                        .doesNotThrowAnyException();
            }

            @Test
            public void bothNull_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .state(new DummyState(null))
                                .when(new DummyCommand())
                                .succeeds()
                                .stateExtracting(s -> ((DummyState) s).valid(), null))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualExtractedState_failing() {
                assertThatThrownBy(() -> stateSubject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("Hugo"));
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .stateExtracting(s -> ((DummyState) s).valid(), false))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Extracted state", "expected to be equal", "true", "differs", "false");
            }

            @Test
            public void noStateCaptured_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .stateExtracting(s -> ((DummyState) s).valid(), true))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("No state captured");
            }
        }

        @Nested
        @DisplayName("fails")
        public class Fails {

            @Test
            public void unsuccessfulExecution_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            throw new RuntimeException("command handling error");
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .fails())
                        .doesNotThrowAnyException();
            }

            @Test
            public void successfulExecution_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .fails())
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("Expected failed execution but command succeeded");
            }
        }

        @Nested
        @DisplayName("fails().throwing")
        public class FailsThrowing {

            @Test
            public void matchingExceptionType_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            throw new RuntimeException("test error");
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .fails()
                                .throwing(RuntimeException.class))
                        .doesNotThrowAnyException();
            }

            @Test
            public void wrongExceptionType_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            throw new RuntimeException("test error");
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .fails()
                                .throwing(IllegalStateException.class))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("wrong type", "RuntimeException");
            }
        }

        @Nested
        @DisplayName("fails().throwsSatisfying")
        public class FailsThrowsSatisfying {

            @Test
            public void exceptionPassedToConsumer() {
                RuntimeException error = new RuntimeException("specific error message");
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            throw error;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .fails()
                                .throwsSatisfying((RuntimeException e) -> {
                                    assertThat(e).isSameAs(error);
                                    assertThat(e.getMessage()).isEqualTo("specific error message");
                                }))
                        .doesNotThrowAnyException();
            }

            @Test
            public void consumerError_propagated() {
                AssertionError consumerError = new AssertionError("consumer assertion failed");
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            throw new RuntimeException("test error");
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .fails()
                                .throwsSatisfying((RuntimeException e) -> {
                                    throw consumerError;
                                }))
                        .isSameAs(consumerError);
            }
        }

        @Nested
        @DisplayName("fails().violatingAnyCondition")
        public class FailsViolatingAnyCondition {

            @Test
            public void pristineViolation_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .event(e -> e.payload(new EventA("existing")))
                                .when(new DummyCommand(Command.SubjectCondition.PRISTINE))
                                .fails()
                                .violatingAnyCondition())
                        .doesNotThrowAnyException();
            }

            @Test
            public void existsViolation_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand(Command.SubjectCondition.EXISTS))
                                .fails()
                                .violatingAnyCondition())
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("fails().violatingExactly")
        public class FailsViolatingExactly {

            @Test
            public void pristineViolation_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .event(e -> e.payload(new EventA("existing")))
                                .when(new DummyCommand(Command.SubjectCondition.PRISTINE))
                                .fails()
                                .violatingExactly(Command.SubjectCondition.PRISTINE))
                        .doesNotThrowAnyException();
            }

            @Test
            public void existsViolation_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand(Command.SubjectCondition.EXISTS))
                                .fails()
                                .violatingExactly(Command.SubjectCondition.EXISTS))
                        .doesNotThrowAnyException();
            }

            @Test
            public void noneCondition_throwsIllegalArgument() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .event(e -> e.payload(new EventA("existing")))
                                .when(new DummyCommand(Command.SubjectCondition.PRISTINE))
                                .fails()
                                .violatingExactly(Command.SubjectCondition.NONE))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("NONE cannot be violated");
            }
        }

        @Nested
        @DisplayName("allEvents().count")
        public class AllEventsCount {

            @Test
            public void matchingCount_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .count(2))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonMatchingCount_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .count(3))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("expected", "3", "captured", "2");
            }

            @Test
            public void negativeCount_throwsIllegalArgument() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .count(-1))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Count must be zero or positive");
            }
        }

        @Nested
        @DisplayName("allEvents().single")
        public class AllEventsSingle {

            @Test
            public void singleMatch_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .single(e -> e.ofType(EventA.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void noMatch_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .single(e -> e.ofType(EventC.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("exactly one", "found none");
            }

            @Test
            public void multipleMatches_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventA("two"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .single(e -> e.ofType(EventA.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("exactly one", "found 2");
            }
        }

        @Nested
        @DisplayName("allEvents().any")
        public class AllEventsAny {

            @Test
            public void matchFound_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .any(e -> e.ofType(EventA.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void multipleMatches_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventA("two"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .any(e -> e.ofType(EventA.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void noMatchFound_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .any(e -> e.ofType(EventC.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("at least one", "found none");
            }
        }

        @Nested
        @DisplayName("allEvents().exactly")
        public class AllEventsExactly {

            @Test
            public void allMatchInOrder_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.ofType(EventA.class), e -> e.ofType(EventB.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void countMismatch_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.ofType(EventA.class), e -> e.ofType(EventB.class), e -> e.ofType(EventC.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("exactly", "3", "found", "2");
            }

            @Test
            public void wrongOrder_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.ofType(EventB.class), e -> e.ofType(EventA.class)))
                        .isInstanceOf(AssertionError.class);
            }
        }

        @Nested
        @DisplayName("allEvents().all")
        public class AllEventsAll {

            @Test
            public void allMatch_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventA("two"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .all(e -> e.ofType(EventA.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void someDontMatch_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .all(e -> e.ofType(EventA.class)))
                        .isInstanceOf(AssertionError.class);
            }

            @Test
            public void noEvents_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .all(e -> e.ofType(EventA.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("no events were captured");
            }
        }

        @Nested
        @DisplayName("allEvents().none")
        public class AllEventsNone {

            @Test
            public void noMatch_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .none(e -> e.ofType(EventC.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void matchFound_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .none(e -> e.ofType(EventA.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("no events matching", "found 1");
            }
        }

        @Nested
        @DisplayName("nextEvents().skip")
        public class NextEventsSkip {

            @Test
            public void skipWithinBounds_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            publisher.publish(new EventC());
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .skip(2))
                        .doesNotThrowAnyException();
            }

            @Test
            public void skipBeyondBounds_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .skip(3))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Cannot skip", "3", "only", "2");
            }

            @Test
            public void skipZero_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .skip(0))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("nextEvents().noMore")
        public class NextEventsNoMore {

            @Test
            public void noEventsRemaining_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> null)
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .noMore())
                        .doesNotThrowAnyException();
            }

            @Test
            public void eventsRemaining_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .noMore())
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("Expected no more events, but found more");
            }
        }

        @Nested
        @DisplayName("nextEvents().single")
        public class NextEventsSingle {

            @Test
            public void singleMatch_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .single(e -> e.ofType(EventA.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void noMatch_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .single(e -> e.ofType(EventC.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("exactly one", "remaining", "found none");
            }

            @Test
            public void multipleMatches_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventA("two"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .single(e -> e.ofType(EventA.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("exactly one", "remaining", "found 2");
            }

            @Test
            public void singleMatchAfterSkip_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            publisher.publish(new EventC());
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .skip(1)
                                .single(e -> e.ofType(EventB.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void noMatchAfterSkip_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            publisher.publish(new EventC());
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .skip(2)
                                .single(e -> e.ofType(EventA.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("exactly one", "remaining", "found none");
            }
        }

        @Nested
        @DisplayName("nextEvents().any")
        public class NextEventsAny {

            @Test
            public void matchFound_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .any(e -> e.ofType(EventA.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void noMatchFound_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .any(e -> e.ofType(EventC.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("at least one", "remaining", "found none");
            }

            @Test
            public void multipleMatches_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventA("two"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .any(e -> e.ofType(EventA.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void matchFoundAfterSkip_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            publisher.publish(new EventC());
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .skip(1)
                                .any(e -> e.ofType(EventB.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void noMatchAfterSkip_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            publisher.publish(new EventC());
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .skip(2)
                                .any(e -> e.ofType(EventA.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("at least one", "remaining", "found none");
            }
        }

        @Nested
        @DisplayName("nextEvents().exactly")
        public class NextEventsExactly {

            @Test
            public void allMatchInOrder_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .exactly(e -> e.ofType(EventA.class), e -> e.ofType(EventB.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void notEnoughRemaining_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .exactly(e -> e.ofType(EventA.class), e -> e.ofType(EventB.class)))
                        .isInstanceOf(AssertionError.class);
            }

            @Test
            public void wrongOrder_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .exactly(e -> e.ofType(EventB.class), e -> e.ofType(EventA.class)))
                        .isInstanceOf(AssertionError.class);
            }

            @Test
            public void matchAfterSkip_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            publisher.publish(new EventC());
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .skip(1)
                                .exactly(e -> e.ofType(EventB.class), e -> e.ofType(EventC.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void cursorNotAdvanced() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .exactly(e -> e.ofType(EventA.class), e -> e.ofType(EventB.class))
                                .exactly(e -> e.ofType(EventA.class), e -> e.ofType(EventB.class)))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("nextEvents().none")
        public class NextEventsNone {

            @Test
            public void noMatch_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .none(e -> e.ofType(EventC.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void matchFound_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .none(e -> e.ofType(EventA.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("no remaining events matching", "found 1");
            }

            @Test
            public void noMatchAfterSkip_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .skip(1)
                                .none(e -> e.ofType(EventA.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void matchAfterSkip_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            publisher.publish(new EventB(2L));
                                            publisher.publish(new EventC());
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .nextEvents()
                                .skip(1)
                                .none(e -> e.ofType(EventB.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("no remaining events matching", "found 1");
            }
        }

        @Nested
        @DisplayName("EventValidator.ofType")
        public class EventValidatorOfType {

            @Test
            public void matchingType_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.ofType(EventA.class)))
                        .doesNotThrowAnyException();
            }

            @Test
            public void wrongType_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.ofType(EventB.class)))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Event type not as expected", "EventA", "EventB");
            }
        }

        @Nested
        @DisplayName("EventValidator.comparing")
        public class EventValidatorComparing {

            @Test
            public void equalPayload_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.comparing(new EventA("one"))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualPayload_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.comparing(new EventA("other"))))
                        .isInstanceOf(AssertionError.class);
            }

            @Test
            public void wrongType_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.comparing(new EventB(1L))))
                        .isInstanceOf(AssertionError.class);
            }
        }

        @Nested
        @DisplayName("EventValidator.satisfying")
        public class EventValidatorSatisfying {

            @Test
            public void assertionPasses_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.satisfying((EventA a) -> assertThat(a.name()).isEqualTo("one"))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void assertionFails_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.satisfying((EventA a) -> assertThat(a.name()).isEqualTo("other"))))
                        .isInstanceOf(AssertionError.class);
            }
        }

        @Nested
        @DisplayName("EventValidator.asserting")
        public class EventValidatorAsserting {

            @Test
            public void assertionPasses_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadType(EventA.class))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void assertionFails_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadType(EventB.class))))
                        .isInstanceOf(AssertionError.class);
            }
        }

        @Nested
        @DisplayName("EventAsserter.payloadType")
        public class EventAsserterPayloadType {

            @Test
            public void matchingType_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadType(EventA.class))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void wrongType_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadType(EventB.class))))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Event type not as expected", "EventA");
            }
        }

        @Nested
        @DisplayName("EventAsserter.payload")
        public class EventAsserterPayload {

            @Test
            public void equalPayload_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payload(new EventA("one")))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualPayload_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payload(new EventA("other")))))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("payloads expected to be equal", "differs");
            }
        }

        @Nested
        @DisplayName("EventAsserter.payloadExtracting")
        public class EventAsserterPayloadExtracting {

            @Test
            public void equalExtractedString_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("test"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadExtracting((EventA ev) -> ev.name(), "test"))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void equalExtractedLong_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadExtracting((EventB ev) -> ev.size(), 42L))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualExtractedString_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("one"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadExtracting((EventA ev) -> ev.name(), "other"))))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Extracted payload expected to be equal", "one", "differs", "other");
            }

            @Test
            public void nonEqualExtractedLong_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventB(42L));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadExtracting((EventB ev) -> ev.size(), 99L))))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Extracted payload expected to be equal", "42", "differs", "99");
            }

            @Test
            public void bothNull_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA(null));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadExtracting((EventA ev) -> ev.name(), null))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void extractedNullExpectedNonNull_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA(null));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadExtracting((EventA ev) -> ev.name(), "something"))))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Extracted payload expected to be equal", "differs", "something");
            }

            @Test
            public void wrongEventType_throwsClassCastException() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("test"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadExtracting((EventB ev) -> ev.size(), 42L))))
                        .isInstanceOf(ClassCastException.class);
            }
        }

        @Nested
        @DisplayName("EventAsserter.payloadSatisfying")
        public class EventAsserterPayloadSatisfying {

            @Test
            public void assertionPasses_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("test"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadSatisfying((EventA ev) -> assertThat(ev.name()).isEqualTo("test")))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void assertionFails_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("test"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.payloadSatisfying((EventA ev) -> assertThat(ev.name()).isEqualTo("wrong")))))
                        .isInstanceOf(AssertionError.class);
            }
        }

        @Nested
        @DisplayName("EventAsserter.metaData")
        public class EventAsserterMetaData {

            @Test
            public void equalMetaData_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("test"), Map.of("key", "value"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.metaData(Map.of("key", "value")))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualMetaData_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("test"), Map.of("key", "value"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.metaData(Map.of("key", "other")))))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("meta-data expected to be equal", "differs");
            }
        }

        @Nested
        @DisplayName("EventAsserter.metaDataSatisfying")
        public class EventAsserterMetaDataSatisfying {

            @Test
            public void assertionPasses_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("test"), Map.of("key", "value"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.metaDataSatisfying(meta -> assertThat(meta.get("key")).isEqualTo("value")))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void assertionFails_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("test"), Map.of("key", "value"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.metaDataSatisfying(meta -> assertThat(meta.get("key")).isEqualTo("wrong")))))
                        .isInstanceOf(AssertionError.class);
            }
        }

        @Nested
        @DisplayName("EventAsserter.noMetaData")
        public class EventAsserterNoMetaData {

            @Test
            public void emptyMetaData_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("test"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.noMetaData())))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEmptyMetaData_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("test"), Map.of("key", "value"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.noMetaData())))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("Empty event meta-data expected", "key", "value");
            }
        }

        @Nested
        @DisplayName("EventAsserter.subject")
        public class EventAsserterSubject {

            @Test
            public void equalSubject_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publishRelative("child", new EventA("irrelevant"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.subject("dummy/child"))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void nonEqualSubject_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publishRelative("child", new EventA("irrelevant"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.subject("dummy/other"))))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("subject expected to be equal", "dummy/child", "differs", "dummy/other");
            }
        }

        @Nested
        @DisplayName("EventAsserter.subjectSatisfying")
        public class EventAsserterSubjectSatisfying {

            @Test
            public void assertionPasses_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publishRelative("child", new EventA("irrelevant"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.subjectSatisfying(s -> assertThat(s).endsWith("/child")))))
                        .doesNotThrowAnyException();
            }

            @Test
            public void assertionFails_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publishRelative("child", new EventA("irrelevant"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.subjectSatisfying(s -> assertThat(s).endsWith("/other")))))
                        .isInstanceOf(AssertionError.class);
            }
        }

        @Nested
        @DisplayName("EventAsserter.commandSubject")
        public class EventAsserterCommandSubject {

            @Test
            public void eventSubjectMatchesCommandSubject_notFailing() {
                assertThatCode(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publish(new EventA("irrelevant"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.commandSubject())))
                        .doesNotThrowAnyException();
            }

            @Test
            public void eventSubjectDiffersFromCommandSubject_failing() {
                assertThatThrownBy(() -> subject
                                .using(DummyState.class, (CommandHandler.ForCommand<DummyState, DummyCommand, Void>)
                                        (c, publisher) -> {
                                            publisher.publishRelative("child", new EventA("irrelevant"));
                                            return null;
                                        })
                                .given()
                                .nothing()
                                .when(new DummyCommand())
                                .succeeds()
                                .then()
                                .allEvents()
                                .exactly(e -> e.asserting(a -> a.commandSubject())))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContainingAll("subject expected to be equal", "dummy/child", "differs", "dummy");
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
