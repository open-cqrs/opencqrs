/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.v2;

import com.opencqrs.framework.command.Command;
import java.util.function.Consumer;
import java.util.function.Function;

public interface ExpectDsl {

    Initializing when(Object command);

    interface Common {
        All allEvents();

        Next nextEvents();
    }

    interface Initializing {
        Succeeding succeeds();

        Failing fails();
    }

    interface Succeeding {
        Succeeding withoutEvents();

        Succeeding havingResult(Object expected);

        Succeeding resultSatisfying(Consumer<Object> assertion);

        Succeeding havingState(Object state);

        Succeeding stateSatisfying(Consumer<Object> assertion);

        <T> Succeeding stateExtracting(Function<Object, T> extractor, T expected);

        Common then();
    }

    interface Failing {
        <T> Failing throwing(Class<T> t);

        <T> Failing throwsSatisfying(Consumer<T> assertion);

        Failing violatingAnyCondition();

        Failing violatingExactly(Command.SubjectCondition condition);
    }

    interface EventValidator {
        EventValidator comparing(Object event);

        <E> EventValidator satisfying(Consumer<E> assertion);

        EventValidator asserting(Consumer<EventAsserting> asserting);

        EventValidator ofType(Class<?> type);
    }

    interface All {

        All count(int count);

        All single(Consumer<EventValidator> consumer);

        All any(Consumer<EventValidator> consumer, Consumer<EventValidator>... consumers);

        All exactly(
                Consumer<EventValidator> consumer,
                Consumer<EventValidator>...
                        consumers); // Ist exactly() auf All terminating für All? Danach kann ja nichts mehr kommen und
        // es müsste alles abgefragt sein. Es sei denn, man will danach noch mit z.B.
        // single() auf eine Sache validieren?

        All all(Consumer<EventValidator> consumer);

        All none(Consumer<EventValidator> consumer, Consumer<EventValidator>... consumers);

        Common then();
    }

    interface Next {
        Next skip(int num);

        Next noMore();

        /**
         * Single wird auch in nextEvents() gebraucht, denn wenn ich 10 Events habe, und skip(2) mache und dann auf
         * allEvents gehe, schaut er wieder auf alle 10. mit nextEvents().skip(2).single(e -> e.ofType)) kann ich sagen,
         * das ich in den restlichen 8 Events noch 1 einziges von diesem Typ erwarte. mit nextEvents().skip(2).exactly(e
         * -> e.ofType()) würde ich sagen, dass ich jetzt als nächstes genau ein Event des Typs erwarte. Das selbe gilt
         * für none(). Das hat aber den Nachteil, dass danach alle Events konsumiert wären.
         */
        Next single(
                Consumer<EventValidator>
                        consumer); // konsumiert zwar events, setzt den iterator dann aber wieder an den Ausgang zurück

        Next any(
                Consumer<EventValidator> consumer,
                Consumer<EventValidator>... consumers); // Prüft Anzahl der übergebenen Events in beliebiger Reihenfolge

        Next exactly(
                Consumer<EventValidator> consumer,
                Consumer<EventValidator>... consumers); // Püft Anzahl der übergebenen Events in exakter Reihenfolge

        Next none(Consumer<EventValidator> consumer, Consumer<EventValidator>... consumers);

        Common then();
    }
}
