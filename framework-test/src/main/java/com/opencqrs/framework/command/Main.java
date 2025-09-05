package com.opencqrs.framework.command;

import java.time.Instant;

public class Main {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        CommandHandlingTestFixture<Object, Command, String> fixture = CommandHandlingTestFixture
                .withStateRebuildingHandlerDefinitions()
                .using(Object.class, new CommandHandler.ForInstanceAndCommand<Object, Command, String>() {
                    @Override
                    public String handle(Object state, Command command, CommandEventPublisher<Object> publisher) {
                        return "success";
                    }
                });

        CommandHandlingTestFixture<Object, Command, String> anotherFixture = CommandHandlingTestFixture
                .withStateRebuildingHandlerDefinitions()
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
                .command(anotherFixture, anotherCommand)
                .time(Instant.now())
                .when(testCommand)
                .succeeds()
                .withoutEvents()
                .havingResult("success") // having gerade ziehen nach Vorlage der Validatoren
                .resultSatisfying(result -> System.out.println("Result: " + result)) // having gerade ziehen nach Vorlage der Validatoren
                .havingState(new Object()) // having gerade ziehen nach Vorlage der Validatoren
                .stateSatisfying(state -> System.out.println("State: " + state)) // state gerade ziehen nach Vorlage der Validatoren
                .stateExtracting(Object::toString, "expectedString") // state gerade ziehen nach Vorlage der Validatoren
                .and()
                .allEvents()
                .single(e -> e // VarArgs aus single nehmen und schauen wo VarArgs Sinn ergeben
                                .comparing("UserRegistered")
                                .ofType(String.class)
                                .satisfying(event -> {
                                    assert event != null;
                                    assert event.toString().contains("user");
                                })
                                .asserting(assertion -> assertion.payloadType(String.class)),
                        e -> e
                                .comparing("EmailSent")
                                .ofType(String.class)
                                .satisfying(event -> {
                                    assert event != null;
                                    assert event.toString().contains("email");
                                })
                                .asserting(assertion -> assertion.payload("EmailSent")),
                        e -> e
                                .comparing("AuditLogCreated")
                                .ofType(String.class)
                                .satisfying(event -> {
                                    assert event != null;
                                    assert event.toString().contains("audit");
                                })
                                .asserting(assertion -> assertion.payloadSatisfying(payload -> {
                                    assert payload.toString().startsWith("Audit");
                                }))
                )
                .exactly(e -> e
                        .comparing("UserRegistered")
                )
                .and()
                .nextEvents()
                .single(e -> e // Jedes e mit all seinen Validatoren darf einmal vorkommen
                                .comparing("Event1")
                                .ofType(String.class)
                                .satisfying(event -> {
                                    assert event != null;
                                    assert event.toString().contains("user");
                                })
                                .asserting(assertion -> assertion.payloadType(String.class)),
                        e -> e
                                .ofType(String.class)
                                .comparing("Event2")
                )
                .exactly(e -> e // Erwartet exakte Reihenfolge dieser Events // Betrachtet so viele Events wie übergeben
                        .ofType(String.class)
                )
                .any(e -> e // In Abgrenzung zu exactly - Erwartet übergebene e in beliebiger Reihenfolge // Betrachtet Anzahl übergebener e
                        .ofType(String.class)
                )
                .skip(2);

    }
}
