package com.opencqrs.framework.command;

public class Main {

    public static void main(String[] args){
        CommandHandlingTestFixture<Object, Command, String> fixture = null;
        
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
        
        fixture
                .givenNothing()
                .when(testCommand)
                .succeeds()
                .withoutEvents()
                .havingResult("success")
                .resultSatisfying(result -> System.out.println("Result: " + result))
                .havingState(new Object())
                .stateSatisfying(state -> System.out.println("State: " + state))
                .stateExtracting(Object::toString, "expectedString")
                
                .and()
                
                .allEvents()
                .count(3)
                
                .single()
                    .comparing("expectedEvent")
                    .ofType(String.class)
                    .satisfying(e -> System.out.println("Single event: " + e))
                    .asserting(asserter -> asserter.payload("test"))
                .and()
                .any()
                    .comparing("anyExpectedEvent")
                    .ofType(Integer.class)
                    .satisfying(e -> System.out.println("Any event: " + e))
                .and()
                .none()
                    .ofType(Exception.class)
                    .satisfying(e -> System.out.println("Should not exist: " + e))
                .and()
                .exactly()
                    .comparing("event1", "event2", "event3")
                    .ofType(String.class, String.class, String.class)
                    .satisfying(e1 -> {}, e2 -> {}, e3 -> {})
                    .asserting(a1 -> {}, a2 -> {}, a3 -> {})
                .and()
                .single()
                    .comparing("expectedPayload")
                    .ofType(String.class)
                    .satisfying(event -> System.out.println("Single event: " + event))
                    .asserting(asserter -> asserter.payload("expectedPayload"))
                .and()
                .any()
                    .comparing("anyEvent")
                    .ofType(String.class)
                    .satisfying(e -> System.out.println("Any event: " + e))
                    .asserting(asserter -> asserter.payloadType(String.class))
                .and()
                .none()
                    .ofType(Exception.class)
                    .satisfying(e -> System.out.println("Should not exist: " + e))
                .and()
                .exactly("event1", "event2", "event3")
                .inAnyOrder("eventA", "eventB", "eventC")
                .notContainingType(Integer.class)
                .allSatisfying( // noch nicht glücklich mit
                    events -> System.out.println("All events: " + events),
                    events -> events.forEach(System.out::println)
                )
                
                .and()
                
                .nextEvents() // besserer Name für nextEvents() ?
                .skip(2)
                .andNoMore()
                .exactly("nextEvent1", "nextEvent2")
                .matchingTypes(String.class, Integer.class)
                .inAnyOrder("orderEvent1", "orderEvent2")
                .matchingTypesInAnyOrder(String.class, Double.class)
                .comparing("compareEvent")
                .comparing(asserter -> asserter.payload("comparePayload"))
                .comparingType(String.class)
                .satisfying(event -> System.out.println("Satisfying: " + event))
                .any("anyNextEvent")
                
                .and()
                
                .allEvents()
                .count(0);

        fixture
                .givenNothing()
                .when(testCommand)
                .fails()
                .throwing(RuntimeException.class)
                .throwsSatisfying(exception -> System.out.println("Exception: " + exception))
                .violatingAnyCondition()
                .violatingExactly(Command.SubjectCondition.PRISTINE);
    }
}
