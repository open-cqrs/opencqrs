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
        Succeeding<I, R> withNoEvents();
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


    interface All<I, R> { // Schaut immer auf alle, nicht auf alle verbleibenden

        // expectNumEvents
        All<I, R> count(int count);
        // expectSingleEvent(E payload)
        <E> All<I, R> single(E payload);
        // expectSingleEvent(Consumer<EventAsserter> assertion)
        All<I, R> singleAsserting(Consumer<EventAsserting> assertion);
        // expectSingleEventType
        All<I, R> singleType(Class<?> type);
        // expectSingleEventSatisfying(Consumer<E> assertion)
        <E> All<I, R> singleSatisfying(Consumer<E> assertion);
        // ? - Doppelt mit Next exactly?
        All<I, R> exactly(Object event, Object... events); // in Order und exakte Anzahl
        // ? - Doppelt mit Next inAnyOrder?
        All<I, R> inAnyOrder(Object... events);
        // expectAnyEvent(Consumer<EventAsserter> assertion)
        All<I, R> expectAnyEvent(Consumer<EventAsserting> assertion);
        // expectAnyEvent(E payload)
        <E> All<I, R> any(E payload);
        // expectAnyEventSatisfying
        All<I, R> anySatisfying(Consumer<EventAsserting> assertion);
        // expectAnyEventType
        All<I, R> anyType(Class<?> type);
        // expectNoEvents
        All<I, R> none(); // name doof - falscher Name für die Semantik die ausgedrückt werden soll
        // expectNoEvent(Consumer<EventAsserter> assertion)
        All<I, R> notContaining(Consumer<EventAsserting> assertion);
        // expectNoEventOfType(Class<?> type)
        All<I, R> notContainingType(Class<?> type);
        // expectEventsSatisfying(Consumer<List<Object>> assertion)
        All<I, R> allSatisfying(Consumer<List<Object>> assertion, Consumer<List<Object>>... assertions);

        // chain function
        Common<I, R> and();
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
        // ? - kann hier weg, oder?
        Next<I, R> any(Object e);

        // chain function
        Common<I, R> and();
    }
}