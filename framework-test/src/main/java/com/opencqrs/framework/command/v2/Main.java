package com.opencqrs.framework.command.v2;

import com.opencqrs.framework.command.*;

import java.time.Instant;
import java.util.Map;

public class Main {

    record EventA(String id) {}

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        StateRebuildingHandlerDefinition<Object, EventA> srhd = new StateRebuildingHandlerDefinition<>(
                Object.class, EventA.class,
                (StateRebuildingHandler.FromObject<Object, EventA>) (state, event) -> state != null ? state : new Object()
        );

        CommandHandlingTestFixture<Command> fixture = CommandHandlingTestFixture
                .withStateRebuildingHandlerDefinitions(srhd)
                .using(Object.class, new CommandHandler.ForInstanceAndCommand<Object, Command, String>() {
                    @Override
                    public String handle(Object state, Command command, CommandEventPublisher<Object> publisher) {
                        return "success";
                    }
                });

        CommandHandlingTestFixture<Command> anotherFixture = CommandHandlingTestFixture
                .withStateRebuildingHandlerDefinitions(srhd)
                .using(Object.class, new CommandHandler.ForInstanceAndCommand<Object, Command, String>() {
                    @Override
                    public String handle(Object state, Command command, CommandEventPublisher<Object> publisher) {
                        return "another-result";
                    }
                });

        Command testCommand = new Command() {
            @Override
            public String getSubject() {
                return "test-subject";
            }

            @Override
            public SubjectCondition getSubjectCondition() {
                return SubjectCondition.NONE;
            }
        };

        Command anotherCommand = new Command() {
            @Override
            public String getSubject() {
                return "another-subject";
            }

            @Override
            public SubjectCondition getSubjectCondition() {
                return SubjectCondition.NONE;
            }
        };

        fixture
                .given()
                // Example: provide an initial event via Given().event(...) using a framework test event type
                .usingSubject("/examples/subject")
                .event(e -> e
                        .payload(new EventA("4711"))
                        .time(Instant.now())
                        .metaData(Map.of("source", "given")))
                .usingCommandSubject()
                .command(anotherFixture, anotherCommand)
                .state(new Object())
                .time(Instant.now())
                .when(testCommand)
                .succeeds()
                .withoutEvents()
                .havingResult("success") // having gerade ziehen nach Vorlage der Validatoren
                .resultSatisfying(result -> System.out.println("Result: " + result)) // having gerade ziehen nach Vorlage der Validatoren
                .havingState(new Object()) // having gerade ziehen nach Vorlage der Validatoren
                .stateSatisfying(state -> System.out.println("State: " + state)) // state gerade ziehen nach Vorlage der Validatoren
                .stateExtracting(Object::toString, "expectedString") // state gerade ziehen nach Vorlage der Validatoren
                .then()
                .allEvents()
                .single(e -> e
                                .comparing("UserRegistered")
                                .ofType(String.class)
                                .satisfying(event -> {
                                    assert event != null;
                                    assert event.toString().contains("user");
                                })
                                .asserting(assertion -> assertion.payloadType(String.class)))
                .single(e -> {
                    e.ofType(String.class);
                    e.comparing("UserRegistered");
                    e.asserting(a -> a.noMetaData());
                    e.asserting(a -> a.subject("/users/123"));
                })
                .single(e -> e
                                .comparing("EmailSent")
                                .ofType(String.class)
                                .satisfying(event -> {
                                    assert event != null;
                                    assert event.toString().contains("email");
                                })
                                .asserting(assertion -> assertion.payload("EmailSent")))
                .single(e -> e
                                .comparing("AuditLogCreated")
                                .ofType(String.class)
                                .satisfying(event -> {
                                    assert event != null;
                                    assert event.toString().contains("audit");
                                })
                                .asserting(assertion -> assertion.payloadSatisfying(payload -> {
                                    assert payload.toString().startsWith("Audit");
                                })))
                .exactly(e -> e
                        .comparing("UserRegistered")
                )
                .then()
                .nextEvents()
                .single(e -> e
                                .comparing("Event1")
                                .ofType(String.class)
                                .satisfying(event -> {
                                    assert event != null;
                                    assert event.toString().contains("user");
                                })
                                .asserting(assertion -> assertion.payloadType(String.class)))
                .single(e -> e
                                .ofType(String.class)
                                .comparing("Event2"))
                .exactly(e -> e // Erwartet exakte Reihenfolge dieser Events // Betrachtet so viele Events wie übergeben
                        .ofType(String.class)
                )
                .any(e -> e // In Abgrenzung zu exactly - Erwartet übergebene e in beliebiger Reihenfolge // Betrachtet Anzahl übergebener e
                        .ofType(String.class)
                )
                .skip(2);

    }
}
