package com.opencqrs.framework.command;

import java.util.List;
import java.util.function.Consumer;

public interface ExpectDsl {

    Initial when(Object command);

    interface Common {
        All allEvents();
        Next nextEvents();
    }

    interface Initial extends Common{
        // expectSuccessfulExecution
        Succeeds succeeds();
        // expectUnsuccessfulExecution
        // Terminating
        Fails fails();
    }

    interface Succeeds {
        Succeeds withNoEvents();
        // expectResult
        Succeeds returning(Object result);
        // expectResultSatisfying
        Succeeds returnsSatisfying(Consumer<Object> result);
        // expectState
        Succeeds withState(Object state);
        // expectStateSatisfying
        Succeeds withStateSatisfying(Consumer<Object> assertion);
        // chain function
        Common and();
    }

    interface Fails {
        // expectException
        // Terminating
        Fails throwing(Class<? extends Throwable> exception);
        // expectExceptionSatisfying
        // Terminating
        Fails throwsSatisfying(Consumer<Throwable> assertion);
        // subject violation
        Fails violatingCommandSubjectCondition();
        Fails violatingCommandSubjectCondition(Command.SubjectCondition);
    }


    interface All {

        // expectNumEvents
        All count(int count);
        // ? - Doppelt mit Next exactly?
        All exactly(Object... events);
        // ? - Doppelt mit Next inAnyOrder?
        All inAnyOrder(Object... events);
        // expectAnyEvent
        All any(Object e);
        // expectAnyEventSatisfying
        All anySatisfying(Consumer<Object> assertion);
        // expectAnyEventType
        All anyType(Class<?> type);
        // expectNoEvents
        All none();
        // expectNoEvent(Consumer<EventAsserter> assertion)
        All notContaining(Consumer<Object> assertion);
        // expectNoEventOfType(Class<?> type)
        All notContainingType(Class<?> type);


        // chain function
        Common and();
    }

    interface Next {
        // skipEvents
        Next skip(int num);
        // expectNoMoreEvents
        Next andNoMore();
        // expectEvents (in order)
        Next exactly(Object... events);
        // expectEventTypes (in order)
        Next matching(Object... eventTypes);
        // expectEventsInAnyOrder
        Next inAnyOrder(Object... events);
        // expectEventTypesInAnyOrder
        Next matchingInAnyOrder(Object... events);
        // expectNextEvent(E payload)
        Next comparing(Object event);
        // expectNextEvent(Consumer<EventAsserter> assertion)
        Next comparing(Consumer<Object> assertion);
        // expectNextEventType(Class<?> type)
        Next comparingType(Class<?> type);
        // expectEventsSatisfying
        Next satisfying(Consumer<Object> consumer);
        // ?
        Next any(Object e);

        Common and();

        // exactly, comparing und matching überarbeiten/abgrenzen/konsolidieren (für all und für next)

        // single shortcuts? Oder über chaining.

        // Tendenz: withState() bleibt

        // To be discussed:
        // TODO - Was war hier gemeint?
        // Alle Events haben ein subject usw...
        // All allSatisfying(Consumer<Object> assertion);


        // Liste mit Events und ich schaue in jedes einzeln und mache assertion auf einzelnem Event
        // All satisfying(Consumer<List<Object>> assertion);
    }
}
