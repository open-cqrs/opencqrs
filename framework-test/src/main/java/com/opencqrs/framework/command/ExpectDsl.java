package com.opencqrs.framework.command;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public interface ExpectDsl<I, R> {

    Initializing<I, R> when(Object command);

    interface Common<I, R> {
        All<I, R> allEvents();
        Next<I, R> nextEvents();
    }

    interface Initializing<I, R> {
        // expectSuccessfulExecution
        Succeeding<I, R> succeeds();
        // expectUnsuccessfulExecution
        // Terminating
        Failing<I, R> fails();
    }

    interface Succeeding<I, R> {
        Succeeding<I, R> withoutEvents();
        // expectResult
        Succeeding<I, R> havingResult(R expected);
        // expectResultSatisfying
        Succeeding<I, R> resultSatisfying(Consumer<R> assertion);
        // expectState
        Succeeding<I, R> havingState(I state);
        // expectStateSatisfying
        Succeeding<I, R> stateSatisfying(Consumer<I> assertion);
        // expectStateExtracting
        <T> Succeeding<I, R> stateExtracting(Function<I, T> extractor, T expected);
        // chain function
        Common<I, R> and();
    }

    interface Failing<I, R> {
        // expectException
        // Terminating
        <T> Failing<I, R> throwing(Class<T> t);
        // expectExceptionSatisfying
        // Terminating
        <T> Failing<I, R> throwsSatisfying(Consumer<T> assertion);
        // violatingCommandSubjectCondition()
        // Terminating
        Failing<I, R> violatingAnyCondition();
        // violatingCommandSubjectCondition(Command.SubjectCondition condition)
        // Terminating
        Failing<I, R> violatingExactly(Command.SubjectCondition condition);
    }


    interface All<I, R> {

        // expectNumEvents
        All<I, R> count(int count);
        
        // Mode switches - chainable
        Foo2<I, R, All<I, R>> single();
        Foo2<I, R, All<I, R>> any();
        Foo2<I, R, All<I, R>> none();
        Foo2<I, R, All<I, R>> exactly();
        
        // Direct methods (kept for backwards compatibility)
        All<I, R> exactly(Object event, Object... events);
        All<I, R> inAnyOrder(Object... events);
        All<I, R> notContainingType(Class<?> type);
        All<I, R> allSatisfying(Consumer<List<Object>> assertion, Consumer<List<Object>>... assertions);
        
        // chain function
        Common<I, R> and();
    }

    interface Foo<I, R, Parent> {
        Parent comparing(Object event);
        <E> Parent satisfying(Consumer<E> assertion);
        Parent asserting(Consumer<EventAsserting> asserting);
        Parent ofType(Class<?> type);
    }

    interface Foo2<I, R, Parent> {
        Foo2<I, R, Parent> comparing(Object... events);
        Foo2<I, R, Parent> ofType(Class<?>... types);
        <E> Foo2<I, R, Parent> satisfying(Consumer<E>... assertions);
        Foo2<I, R, Parent> asserting(Consumer<EventAsserting>... assertings);
        Parent and();
    }

    interface Foo3<I, R> {
        Foo3<I, R> comparing(Object event);
        <E> Foo3<I, R> satisfying(Consumer<E> assertion);
        Foo3<I, R> asserting(Consumer<EventAsserting> asserting);
        Foo3<I, R> ofType(Class<?> type);
    }

    interface Next<I, R> {
        // skipEvents
        Next<I, R> skip(int num);
        // expectNoMoreEvents
        Next<I, R> andNoMore();
        // expectEvents (in order)
        Next<I, R> exactly(Object event, Object... events); // wenn 2 events übergeben aber 10 da, dann schaue ich nur auf die nächsten 2
        // expectEventTypes (in order)
        Next<I, R> matchingTypes(Class<?> type, Class<?>... types);
        // expectEventsInAnyOrder
        Next<I, R> inAnyOrder(Object event, Object... events); // Was ist Abgrenzung zu All<I, R> inAnyOrder(Object... events)? - beide implementieren?
        // expectEventTypesInAnyOrder
        Next<I, R> matchingTypesInAnyOrder(Class<?> type, Class<?>... types);
        // expectNextEvent(Consumer<EventAsserter> assertion)
        <E> Next<I, R> comparing(E payload);
        // expectNextEvent(Consumer<EventAsserter> assertion)
        Next<I, R> comparing(Consumer<EventAsserting> assertion);
        // expectNextEventType(Class<?> type)
        Next<I, R> comparingType(Class<?> type);
        // expectNextEventSatisfying(Consumer<E> assertion)
        <E> Next<I, R> satisfying(Consumer<E> assertion);
        // ? - kann hier weg, oder? // Lookahead ob any noch erfüllt wird (ohne steps)
        Next<I, R> any(Object e);

        // chain function
        Common<I, R> and();
    }
}