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
                .single(e -> e.comparing(new Object()))
//                .single("singleEvent")
                .singleAsserting(asserter -> asserter.payload("expectedPayload"))
                .singleType(String.class)
                .singleSatisfying(event -> System.out.println("Single event: " + event))
                .exactly().comparing("event1", "event2").ofType(Integer.class).and() // wie verhindern wir, dass jemand das and() weg lässt
                .exactly("event1", "event2", "event3")
                .inAnyOrder("eventA", "eventB", "eventC")
                .expectAnyEvent(asserter -> asserter.payload("anyEvent"))
                .any().comparing(new Object())
                .anySatisfying(asserter -> asserter.payloadType(String.class))
                .anyType(String.class)
                .none().asserting(asserter -> asserter.payload("forbiddenEvent"))
                .notContaining(asserter -> asserter.payload("forbiddenEvent"))
                .notContainingType(Integer.class)
                .all().satisfying(events -> System.out.println("All events: " + events))
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
                .comparing("compareEvent") // next().comparing()
                .comparing(asserter -> asserter.payload("comparePayload"))
                .comparingType(String.class) //next().ofType()
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
