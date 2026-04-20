/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * DSL interfaces for the "Expect" (or "Then") phase of command handling tests. This phase specifies and verifies the
 * expected outcomes after command execution, including success/failure status, published events, result values, and
 * final state.
 *
 * <p>The Expect phase follows the When phase in the Given-When-Then test pattern. It provides a fluent API for
 * asserting:
 *
 * <ul>
 *   <li>Whether the command succeeded or failed via {@link Outcome}
 *   <li>The command result and final instance state via {@link Succeeding}
 *   <li>Exception types and precondition violations via {@link Failing}
 *   <li>Published events via {@link All} and {@link Next}
 * </ul>
 *
 * @see GivenDsl
 * @see CommandHandlingTestFixture
 */
public interface ExpectDsl {

    /**
     * Entry point for specifying whether the command execution is expected to succeed or fail. This is the first
     * decision point after the When phase.
     */
    interface Outcome {

        /**
         * Asserts that the command execution succeeded (no exception was thrown). Returns a {@link Succeeding}
         * interface for further assertions on the result, state, and published events.
         *
         * @return a {@link Succeeding} interface for success-path assertions
         * @throws AssertionError if the command execution threw an exception
         */
        Succeeding succeeds();

        /**
         * Asserts that the command execution failed (an exception was thrown). Returns a {@link Failing} interface for
         * assertions on the exception type and precondition violations.
         *
         * @return a {@link Failing} interface for failure-path assertions
         * @throws AssertionError if the command execution succeeded
         */
        Failing fails();
    }

    /**
     * Fluent API for assertions after successful command execution. {@code Succeeding} is the central hub of the
     * assertion API — it can be reached from {@link Outcome#succeeds()}, from {@link All#and()}, from
     * {@link Next#and()}, or from any terminal method on {@link Next}.
     *
     * <p>All assertion methods return {@code Succeeding} to allow free chaining between result/state assertions and
     * event assertions:
     *
     * <pre>
     * .succeeds()
     *     .havingResult(expectedResult)
     *     .nextEvents()
     *     .matches(e -&gt; e.ofType(OrderPlacedEvent.class))
     *     .noMore()
     *     .allEvents()
     *     .count(1)
     *     .and()
     *     .havingState(expectedState);
     * </pre>
     */
    interface Succeeding {

        /**
         * Asserts that no events were published by the command handler.
         *
         * @return {@code this} for method chaining
         * @throws AssertionError if any events were published
         */
        Succeeding withoutEvents();

        /**
         * Asserts that the command handler returned the expected result using {@link Object#equals(Object)}. The result
         * is the value returned by the {@link CommandHandler} implementation.
         *
         * @param expected the expected result value (may be {@code null})
         * @return {@code this} for method chaining
         * @throws AssertionError if the results are not equal
         */
        Succeeding havingResult(Object expected);

        /**
         * Asserts that the command result satisfies custom assertions.
         *
         * <p>Example:
         *
         * <pre>
         * .succeeds()
         *     .resultSatisfying(result -&gt; assertThat(result).isInstanceOf(OrderId.class));
         * </pre>
         *
         * @param assertion a consumer receiving the command result for custom assertions
         * @return {@code this} for method chaining
         * @throws AssertionError if thrown by the consumer
         */
        Succeeding resultSatisfying(Consumer<Object> assertion);

        /**
         * Asserts that the final instance state equals the expected state using {@link Object#equals(Object)}. The
         * state is captured after all published events have been processed by the
         * {@link com.opencqrs.framework.command.StateRebuildingHandlerDefinition}s.
         *
         * @param state the expected final state
         * @return {@code this} for method chaining
         * @throws AssertionError if the states are not equal or no state was captured
         */
        Succeeding havingState(Object state);

        /**
         * Asserts that the final instance state satisfies custom assertions.
         *
         * <p>Example:
         *
         * <pre>
         * .succeeds()
         *     .stateSatisfying(state -&gt; {
         *         OrderState order = (OrderState) state;
         *         assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
         *     });
         * </pre>
         *
         * @param assertion a consumer receiving the final state for custom assertions
         * @return {@code this} for method chaining
         * @throws AssertionError if thrown by the consumer
         */
        Succeeding stateSatisfying(Consumer<Object> assertion);

        /**
         * Extracts a value from the final instance state and asserts it equals the expected value. Useful for verifying
         * specific state fields without matching the entire state object.
         *
         * <p>Example:
         *
         * <pre>
         * .succeeds()
         *     .stateExtracting(state -&gt; ((OrderState) state).status(), OrderStatus.CONFIRMED);
         * </pre>
         *
         * @param extractor function to extract a value from the state
         * @param expected the expected extracted value (may be {@code null})
         * @param <T> the extracted value type
         * @return {@code this} for method chaining
         * @throws AssertionError if the extracted values are not equal or no state was captured
         */
        <T> Succeeding stateExtracting(Function<Object, T> extractor, T expected);

        /**
         * Returns an interface for asserting against all captured events as a whole. Use this when you need to verify
         * the complete set of events without cursor-based navigation. Each call operates on the full event list.
         *
         * <p>Example:
         *
         * <pre>
         * .succeeds()
         *     .allEvents()
         *     .count(2)
         *     .exactly(
         *         e -&gt; e.ofType(OrderPlacedEvent.class),
         *         e -&gt; e.ofType(InventoryReservedEvent.class));
         * </pre>
         *
         * @return an {@link All} interface for event assertions
         * @see #nextEvents()
         */
        All allEvents();

        /**
         * Returns an interface for asserting against events sequentially using a cursor. Each call creates a fresh
         * cursor starting at position 0. Use this when you need to navigate through events in order, skip events, or
         * verify that no more events exist after a certain point.
         *
         * <p>Navigation methods ({@link Next#skipping(int)}, {@link Next#matches(Consumer)}) advance the cursor and
         * return {@link Next} for further navigation. Consuming methods ({@link Next#single(Consumer)},
         * {@link Next#any(Consumer)}, {@link Next#none(Consumer)}, {@link Next#exactly(Object...)},
         * {@link Next#noMore()}, {@link Next#remaining(int)}) consume remaining events and return {@link Succeeding}.
         *
         * <p>Example:
         *
         * <pre>
         * .succeeds()
         *     .nextEvents()
         *     .matches(e -&gt; e.ofType(OrderPlacedEvent.class))
         *     .skipping(1)
         *     .noMore()
         *     .havingResult(expectedId);
         * </pre>
         *
         * @return a {@link Next} interface for sequential event assertions
         * @see #allEvents()
         */
        Next nextEvents();
    }

    /**
     * Fluent API for assertions after failed command execution. Provides methods to verify the exception type and
     * precondition violations.
     *
     * <p>All assertion methods return {@code this} to allow method chaining:
     *
     * <pre>
     * .fails()
     *     .throwing(IllegalStateException.class)
     *     .throwsSatisfying(ex -&gt; assertThat(ex.getMessage()).contains("invalid"));
     * </pre>
     */
    interface Failing {

        /**
         * Asserts that the thrown exception is an instance of the specified type.
         *
         * @param t the expected exception class
         * @param <T> the exception type
         * @return {@code this} for method chaining
         * @throws AssertionError if the exception is not of the expected type
         */
        <T extends Throwable> Failing throwing(Class<T> t);

        /**
         * Asserts that the thrown exception matches the specified throwable by type and message. The actual exception
         * must be an instance of the same class and have an equal {@link Throwable#getMessage() message}.
         *
         * @param t the expected throwable to compare against
         * @param <T> the exception type
         * @return {@code this} for method chaining
         * @throws AssertionError if the exception type or message differs
         */
        <T extends Throwable> Failing throwing(T t);

        /**
         * Asserts that the thrown exception satisfies custom assertions.
         *
         * <p>Example:
         *
         * <pre>
         * .fails()
         *     .throwsSatisfying((IllegalArgumentException ex) -&gt;
         *         assertThat(ex.getMessage()).contains("order not found"));
         * </pre>
         *
         * @param assertion a consumer receiving the exception for custom assertions
         * @param <T> the exception type
         * @return {@code this} for method chaining
         * @throws AssertionError if thrown by the consumer
         */
        <T extends Throwable> Failing throwsSatisfying(Consumer<T> assertion);

        /**
         * Asserts that the command failed due to a {@link Command.SubjectCondition} violation. This matches either
         * {@link Command.SubjectCondition#PRISTINE} or {@link Command.SubjectCondition#EXISTS} violations.
         *
         * @return {@code this} for method chaining
         * @throws AssertionError if the failure was not due to a precondition violation
         */
        Failing violatingAnyCondition();

        /**
         * Asserts that the command failed due to a specific {@link Command.SubjectCondition} violation.
         *
         * <p>Example:
         *
         * <pre>
         * // Command requires the subject to not exist yet
         * .fails()
         *     .violatingExactly(Command.SubjectCondition.PRISTINE);
         * </pre>
         *
         * @param condition the expected violated condition ({@link Command.SubjectCondition#PRISTINE} or
         *     {@link Command.SubjectCondition#EXISTS})
         * @return {@code this} for method chaining
         * @throws IllegalArgumentException if {@link Command.SubjectCondition#NONE} is passed
         * @throws AssertionError if the failure was not due to the specified condition
         */
        Failing violatingExactly(Command.SubjectCondition condition);
    }

    /**
     * Fluent API interface for validating individual captured events. Instances are provided to consumers passed to
     * methods like {@link All#single(Consumer)}, {@link All#any(Consumer)} and similar.
     *
     * <p>All methods return {@code this} to allow chaining multiple validations on the same event.
     */
    interface EventValidator {

        /**
         * Asserts that the event payload equals the expected value using {@link Object#equals(Object)}. This is a
         * convenience method for simple equality checks.
         *
         * @param event the expected event payload
         * @return {@code this} for further validations
         * @throws AssertionError if the payloads are not equal
         */
        EventValidator comparing(Object event);

        /**
         * Asserts that the event payload satisfies custom assertions. The payload is passed directly to the consumer,
         * cast to the specified type. Use this for payload-only assertions.
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
         * Asserts that the event satisfies custom assertions using the full {@link EventAsserting} API. Use this when
         * you need to assert more than just the payload, such as meta-data or subject.
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

    /**
     * Fluent API for asserting against all captured events. This interface provides methods that operate on the
     * complete event list without maintaining cursor state.
     *
     * <p>Each method validates events against the entire captured event list and returns {@code this} for method
     * chaining, allowing multiple assertions to be combined:
     *
     * <pre>
     * .allEvents()
     *     .count(3)
     *     .single(e -&gt; e.ofType(OrderConfirmedEvent.class))
     *     .none(e -&gt; e.ofType(OrderCancelledEvent.class));
     * </pre>
     *
     * @see Next for cursor-based sequential event assertions
     */
    interface All {

        /**
         * Asserts that exactly the specified number of events were captured.
         *
         * @param count the expected number of events (must be non-negative)
         * @return {@code this} for method chaining
         * @throws AssertionError if the actual event count differs
         * @throws IllegalArgumentException if count is negative
         */
        All count(int count);

        /**
         * Asserts that exactly one event in the captured list matches the specified validation. The validation is
         * applied to each captured event individually, and exactly one must pass — but other non-matching events may
         * exist. This does <strong>not</strong> assert that only one event was captured in total.
         *
         * <p>To assert that exactly one event was captured <strong>and</strong> it matches, use
         * {@link #exactly(Object...)} with a single consumer, or combine with {@link #count(int)}:
         *
         * <pre>
         * .allEvents().exactly(e -&gt; e.ofType(PaymentReceivedEvent.class));
         * // or
         * .allEvents().count(1).single(e -&gt; e.ofType(PaymentReceivedEvent.class));
         * </pre>
         *
         * @param consumer a consumer that validates a single event via {@link EventValidator}
         * @return {@code this} for method chaining
         * @throws AssertionError if zero or more than one event matches
         */
        All single(Consumer<EventValidator> consumer);

        /**
         * Asserts that at least one event in the captured list matches the validation.
         *
         * @param consumer a consumer that validates events via {@link EventValidator}
         * @return {@code this} for method chaining
         * @throws AssertionError if no event matches
         */
        All any(Consumer<EventValidator> consumer);

        /**
         * Asserts that all captured events match the specified validation.
         *
         * @param consumer a consumer that validates each event via {@link EventValidator}
         * @return {@code this} for method chaining
         * @throws AssertionError if any event fails validation or no events exist
         */
        All all(Consumer<EventValidator> consumer);

        /**
         * Asserts that no captured events match the validation.
         *
         * @param consumer a consumer that validates events via {@link EventValidator}
         * @return {@code this} for method chaining
         * @throws AssertionError if any event matches
         */
        All none(Consumer<EventValidator> consumer);

        /**
         * Asserts that the captured events match exactly the provided event payloads in order, using
         * {@link Object#equals(Object)}. The number of captured events must equal the number of expected payloads —
         * both too few and too many events cause an error.
         *
         * <p>Example:
         *
         * <pre>
         * .allEvents()
         *     .exactly(new OrderPlacedEvent(...), new PaymentReceivedEvent(...));
         * </pre>
         *
         * @param events the expected event payloads in order
         * @return {@code this} for method chaining
         * @throws AssertionError if the captured event count doesn't match or any payload differs
         */
        All exactly(Object... events);

        /**
         * Returns to the {@link Succeeding} interface to access result/state assertions or switch to a different event
         * assertion mode ({@link Succeeding#allEvents()}, {@link Succeeding#nextEvents()}).
         *
         * @return a {@link Succeeding} interface
         */
        Succeeding and();
    }

    /**
     * Fluent API for asserting against events sequentially using a consuming cursor. This interface maintains position
     * state, allowing navigation through events in order.
     *
     * <p>The cursor starts at position 0 (first event). There are two categories of methods:
     *
     * <ul>
     *   <li><strong>Navigating</strong> ({@link #skipping(int)}, {@link #matches(Consumer)}) — advance the cursor and
     *       return {@code Next} for further chaining.
     *   <li><strong>Consuming</strong> ({@link #single(Consumer)}, {@link #any(Consumer)}, {@link #none(Consumer)},
     *       {@link #exactly(Object...)}, {@link #noMore()}, {@link #remaining(int)}) — consume remaining events and
     *       return {@link Succeeding} for result/state assertions or further event assertions.
     * </ul>
     *
     * <p>Example:
     *
     * <pre>
     * .nextEvents()
     *     .matches(e -&gt; e.ofType(OrderPlacedEvent.class))   // validate and consume first event
     *     .skipping(1)                                           // skip second event
     *     .noMore()                                              // assert no more events
     *     .allEvents()                                           // switch to all-events view
     *     .count(2);                                             // verify total count
     * </pre>
     *
     * @see All for non-sequential assertions on all events
     */
    interface Next {

        /**
         * Advances the cursor by the specified number of events. Subsequent assertions will operate on events starting
         * from the new cursor position.
         *
         * @param num the number of events to skip (must be non-negative)
         * @return {@code this} for method chaining
         * @throws AssertionError if skipping would move past the end of the event list
         */
        Next skipping(int num);

        /**
         * Asserts that no more events exist from the current cursor position.
         *
         * <p>Example:
         *
         * <pre>
         * .nextEvents()
         *     .skipping(2)
         *     .noMore()
         *     .havingResult(expectedId);
         * </pre>
         *
         * @return a {@link Succeeding} interface for further assertions
         * @throws AssertionError if events exist at or after the current cursor position
         */
        Succeeding noMore();

        /**
         * Asserts that exactly the specified number of events remain from the current cursor position. This is a
         * convenience shortcut for {@code skipping(count).noMore()}.
         *
         * @param count the expected number of remaining events (must be non-negative)
         * @return a {@link Succeeding} interface for further assertions
         * @throws AssertionError if the remaining event count differs
         */
        Succeeding remaining(int count);

        /**
         * Consumes all remaining events from the current cursor position and asserts that exactly one matches the
         * validation.
         *
         * @param consumer a consumer that validates a single event via {@link EventValidator}
         * @return a {@link Succeeding} interface for further assertions
         * @throws AssertionError if zero or more than one event matches
         */
        Succeeding single(Consumer<EventValidator> consumer);

        /**
         * Consumes all remaining events from the current cursor position and asserts that at least one matches the
         * validation.
         *
         * @param consumer a consumer that validates events via {@link EventValidator}
         * @return a {@link Succeeding} interface for further assertions
         * @throws AssertionError if no remaining event matches
         */
        Succeeding any(Consumer<EventValidator> consumer);

        /**
         * Consumes events from the current cursor position and asserts that the remaining event payloads match exactly
         * the provided payloads in order, using {@link Object#equals(Object)}. The number of remaining events must
         * equal the number of expected payloads.
         *
         * @param events the expected event payloads in order
         * @return a {@link Succeeding} interface for further assertions
         * @throws AssertionError if the remaining event count doesn't match or any payload differs
         */
        Succeeding exactly(Object... events);

        /**
         * Consumes all remaining events from the current cursor position and asserts that none match the validation.
         *
         * @param consumer a consumer that validates events via {@link EventValidator}
         * @return a {@link Succeeding} interface for further assertions
         * @throws AssertionError if any remaining event matches
         */
        Succeeding none(Consumer<EventValidator> consumer);

        /**
         * Consumes exactly one event at the current cursor position and validates it using the provided consumer.
         * Advances the cursor by one. This is a navigating operation that returns {@link Next} for further chaining.
         *
         * @param consumer a consumer that validates the event via {@link EventValidator}
         * @return {@code this} for further navigation
         * @throws AssertionError if no more events remain at the current cursor position, or if the event fails
         *     validation
         */
        Next matches(Consumer<EventValidator> consumer);

        /**
         * Returns to the {@link Succeeding} interface to access result/state assertions or switch to a different event
         * assertion mode ({@link Succeeding#allEvents()}, {@link Succeeding#nextEvents()}).
         *
         * @return a {@link Succeeding} interface
         */
        Succeeding and();
    }
}
