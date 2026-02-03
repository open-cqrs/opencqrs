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

    /**
     * Fluent API interface for validating individual captured events. Instances are provided to consumers
     * passed to methods like {@link All#single(Consumer)}, {@link All#exactly(Consumer, Consumer[])} and similar.
     *
     * <p>All methods return {@code this} to allow chaining multiple validations on the same event.
     */
    interface EventValidator {

        /**
         * Asserts that the event payload equals the expected value using {@link Object#equals(Object)}.
         * This is a convenience method for simple equality checks.
         *
         * @param event the expected event payload
         * @return {@code this} for further validations
         * @throws AssertionError if the payloads are not equal
         */
        EventValidator comparing(Object event);

        /**
         * Asserts that the event payload satisfies custom assertions. The payload is passed directly
         * to the consumer, cast to the specified type. Use this for payload-only assertions.
         *
         * <p>Example: {@code e.satisfying((MyEvent ev) -> assertThat(ev.name()).isEqualTo("test"))}
         *
         * @param assertion a consumer receiving the event payload for custom assertions
         * @return {@code this} for further validations
         * @throws AssertionError if thrown by the consumer
         * @param <E> the expected event payload type
         * @see #asserting(Consumer) for full event access including meta-data and subject
         */
        <E> EventValidator satisfying(Consumer<E> assertion);

        /**
         * Asserts that the event satisfies custom assertions using the full {@link EventAsserting} API.
         * Use this when you need to assert more than just the payload, such as meta-data or subject.
         *
         * <p>Example: {@code e.asserting(a -> a.payloadType(MyEvent.class).subject("my-subject"))}
         *
         * @param asserting a consumer receiving an {@link EventAsserting} instance
         * @return {@code this} for further validations
         * @throws AssertionError if thrown by the consumer
         * @see #satisfying(Consumer) for simpler payload-only assertions
         */
        EventValidator asserting(Consumer<EventAsserting> asserting);

        /**
         * Asserts that the event payload is an instance of the specified type.
         *
         * @param type the expected event payload class
         * @return {@code this} for further validations
         * @throws AssertionError if the payload is not assignable to the expected type
         */
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
                // Hier nochmal darüber reden, was genau mit dem Iterator passieren soll / mit der subList passieren soll. Sollen für nächste Aufrufe wieder alle Events
                // da sein oder nur die verbleibenden?

        Next none(Consumer<EventValidator> consumer, Consumer<EventValidator>... consumers);

        Common then();
    }
}
