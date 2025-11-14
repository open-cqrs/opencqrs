package com.opencqrs.framework.command.v2;

import com.opencqrs.framework.command.Command;

import java.util.function.Consumer;
import java.util.function.Function;

public interface ExpectDsl<I, R> {

    Initializing<I, R> when(Object command);

    interface Common<I, R> {
        All<I, R> allEvents();
        Next<I, R> nextEvents();
    }

    interface Initializing<I, R> {
        Succeeding<I, R> succeeds();
        Failing<I, R> fails();
    }

    interface Succeeding<I, R> {
        Succeeding<I, R> withoutEvents();
        Succeeding<I, R> havingResult(R expected);
        Succeeding<I, R> resultSatisfying(Consumer<R> assertion);
        Succeeding<I, R> havingState(I state);
        Succeeding<I, R> stateSatisfying(Consumer<I> assertion);
        <T> Succeeding<I, R> stateExtracting(Function<I, T> extractor, T expected);
        Common<I, R> and(); // Eventuell anderes Keyword als and(), evtl. then()?
    }

    interface Failing<I, R> {
        <T> Failing<I, R> throwing(Class<T> t);
        <T> Failing<I, R> throwsSatisfying(Consumer<T> assertion);
        Failing<I, R> violatingAnyCondition();
        Failing<I, R> violatingExactly(Command.SubjectCondition condition);
    }


    interface EventValidator<I, R> {
        EventValidator<I, R> comparing(Object event);
        <E> EventValidator<I, R> satisfying(Consumer<E> assertion);
        EventValidator<I, R> asserting(Consumer<EventAsserting> asserting);
        EventValidator<I, R> ofType(Class<?> type);
    }

    interface All<I, R> {

        All<I, R> count(int count);
        All<I, R> single(Consumer<EventValidator<I, R>> consumer);
        All<I, R> any(Consumer<EventValidator<I, R>> consumer, Consumer<EventValidator<I, R>>... consumers);
        All<I, R> exactly(Consumer<EventValidator<I, R>> consumer, Consumer<EventValidator<I, R>>... consumers); // Ist exactly() auf All terminating für All? Danach kann ja nichts mehr kommen und es müsste alles abgefragt sein. Es sei denn, man will danach noch mit z.B. single() auf eine Sache validieren?
        All<I, R> all(Consumer<EventValidator<I, R>> consumer);
        All<I, R> none(Consumer<EventValidator<I, R>> consumer, Consumer<EventValidator<I, R>>... consumers);
        Common<I, R> and();
    }

    interface Next<I, R> {
        Next<I, R> skip(int num);
        Next<I, R> noMore();

        /**
         * Single wird auch in nextEvents() gebraucht, denn wenn ich 10 Events habe, und skip(2) mache und dann auf
         * allEvents gehe, schaut er wieder auf alle 10. mit nextEvents().skip(2).single(e -> e.ofType))
         * kann ich sagen, das ich in den restlichen 8 Events noch 1 einziges von diesem Typ erwarte.
         * mit nextEvents().skip(2).exactly(e -> e.ofType()) würde ich sagen, dass ich jetzt als nächstes genau
         * ein Event des Typs erwarte. Das selbe gilt für none(). Das hat aber den Nachteil, dass danach alle Events
         * konsumiert wären.
         */
        Next<I, R> single(Consumer<EventValidator<I, R>> consumer); // konsumiert zwar events, setzt den iterator dann aber wieder an den Ausgang zurück
        Next<I, R> any(Consumer<EventValidator<I, R>>  consumer, Consumer<EventValidator<I, R>>... consumers); // Prüft Anzahl der übergebenen Events in beliebiger Reihenfolge
        Next<I, R> exactly(Consumer<EventValidator<I, R>>  consumer, Consumer<EventValidator<I, R>>... consumers); // Püft Anzahl der übergebenen Events in exakter Reihenfolge
        Next<I, R> none(Consumer<EventValidator<I, R>> consumer, Consumer<EventValidator<I, R>>... consumers);
        Common<I, R> and();
    }
}