/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.v2;

import com.opencqrs.framework.command.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

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
                    .given()
                    .event(e -> e.payload(new EventA("irrelevant")))
                    .when(new DummyCommand())
                    .succeeds();
        }

        @Test
        public void stateRebuildingHandlersCalledFilteredByEventClassAfterCommandExecution() {
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
                    .given()
                    .nothing()
                    .when(new DummyCommand())
                    .succeeds()
                    .then()
                    .allEvents()
                    .exactly(event -> event.ofType(EventA.class));
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
