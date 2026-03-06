/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DSL interfaces for the "Given" phase of command handling tests. This phase establishes the preconditions before
 * command execution, including initial state, existing events, and timing information.
 *
 * <p>The Given phase is the first step in the Given-When-Then test pattern. It allows setting up the aggregate state by
 * providing events that will be processed by {@link StateRebuildingHandlerDefinition}s to reconstruct the state before
 * the command under test is executed.
 *
 * @see CommandHandlingTestFixture
 * @see ExpectDsl
 */
public interface GivenDsl {

    /**
     * Fluent API for configuring test preconditions before command execution. Methods in this interface allow
     * specifying the initial state of the aggregate through various means:
     *
     * <ul>
     *   <li>Direct state injection via {@link #state(Object)}
     *   <li>Event-based state reconstruction via {@link #events(Object...)} or {@link #event(Consumer)}
     *   <li>Reusing events from another command execution via {@link #command(CommandHandlingTestFixture, Command)}
     * </ul>
     *
     * <p>Events provided through this interface are processed by the configured
     * {@link StateRebuildingHandlerDefinition}s to build the aggregate state. These events are <strong>not</strong>
     * included in the captured events that can be asserted in the Expect phase - only events published by the command
     * under test are capturable.
     *
     * <p>The interface supports method chaining, allowing multiple setup operations to be combined fluently:
     *
     * <pre>
     * fixture.given()
     *     .time(Instant.parse("2024-01-01T10:00:00Z"))
     *     .usingSubject("/orders/123")
     *     .event(e -&gt; e.payload(new OrderCreatedEvent(...)))
     *     .timeDelta(Duration.ofHours(1))
     *     .event(e -&gt; e.payload(new OrderConfirmedEvent(...)))
     *     .when(new ShipOrderCommand(...))
     *     .succeeds();
     * </pre>
     *
     * @see EventSpecifier
     * @see ExpectDsl.Initializing
     */
    interface Given {

        /**
         * Indicates that no preconditions exist for this test. The command will be executed against an empty/null
         * state. This is typically used for commands that initiate new aggregates.
         *
         * <p>Example:
         *
         * <pre>
         * fixture.given()
         *     .nothing()
         *     .when(new PlaceOrderCommand(...))
         *     .succeeds();
         * </pre>
         *
         * @return {@code this} for method chaining
         */
        Given nothing();

        /**
         * Sets the timestamp for subsequent events added via {@link #events(Object...)} or {@link #event(Consumer)}.
         * This timestamp is used as the {@code time} attribute of the stubbed raw event passed to
         * {@link StateRebuildingHandler}s.
         *
         * <p>If not specified, {@link Instant#now()} is used as the default timestamp.
         *
         * @param time the timestamp to use for subsequent events
         * @return {@code this} for method chaining
         * @see #timeDelta(Duration)
         */
        Given time(Instant time);

        /**
         * Advances the current timestamp by the specified duration for subsequent events. This is useful for simulating
         * events that occurred at different points in time relative to each other.
         *
         * <p>Example:
         *
         * <pre>
         * fixture.given()
         *     .time(Instant.parse("2024-01-01T10:00:00Z"))
         *     .event(e -&gt; e.payload(new OrderPlacedEvent(...)))
         *     .timeDelta(Duration.ofHours(2))
         *     .event(e -&gt; e.payload(new PaymentReceivedEvent(...)))  // at 12:00:00
         *     .when(...)
         * </pre>
         *
         * @param delta the duration to add to the current timestamp
         * @return {@code this} for method chaining
         * @see #time(Instant)
         */
        Given timeDelta(Duration delta);

        /**
         * Directly sets the aggregate state without processing any events. This bypasses the
         * {@link StateRebuildingHandlerDefinition}s and injects the state directly.
         *
         * <p>Use this method when you want to test command behavior against a specific state without setting up the
         * event history that would normally produce that state.
         *
         * <p><strong>Note:</strong> When using this method, no events are added to the aggregate's history, which may
         * affect precondition checks that rely on event existence (e.g., {@link Command.SubjectCondition#EXISTS}).
         *
         * @param state the state object to inject
         * @return {@code this} for method chaining
         */
        Given state(Object state);

        /**
         * Adds multiple event payloads to be processed by {@link StateRebuildingHandlerDefinition}s. Each event payload
         * is wrapped with default values for subject, time, id, and meta-data.
         *
         * <p>The events are processed in the order provided, with each event potentially modifying the aggregate state
         * through its corresponding {@link StateRebuildingHandler}.
         *
         * <p>For more control over individual event attributes (subject, time, meta-data), use {@link #event(Consumer)}
         * instead.
         *
         * @param events the event payloads to add
         * @return {@code this} for method chaining
         * @throws IllegalArgumentException if no {@link StateRebuildingHandlerDefinition} matches an event's type
         * @see #event(Consumer)
         */
        Given events(Object... events);

        /**
         * Adds a single event with full control over its attributes using the {@link EventSpecifier} fluent API. This
         * method allows specifying the event payload along with optional attributes like subject, time, id, and
         * meta-data.
         *
         * <p>Example:
         *
         * <pre>
         * fixture.given()
         *     .event(e -&gt; e
         *         .payload(new OrderPlacedEvent(...))
         *         .subject("/orders/123")
         *         .time(Instant.parse("2024-01-01T10:00:00Z"))
         *         .metaData(Map.of("correlationId", "abc-123")))
         *     .when(...)
         * </pre>
         *
         * @param event a consumer that configures the event via {@link EventSpecifier}
         * @return {@code this} for method chaining
         * @throws IllegalArgumentException if {@link EventSpecifier#payload(Object)} is not called
         * @throws IllegalArgumentException if no {@link StateRebuildingHandlerDefinition} matches the event's payload
         *     type
         * @see EventSpecifier
         */
        Given event(Consumer<EventSpecifier> event);

        /**
         * Executes another command using the specified fixture and applies its captured events as given events for this
         * test. This is useful for setting up complex preconditions that result from prior command executions.
         *
         * <p>The events published by the given command are processed by this fixture's
         * {@link StateRebuildingHandlerDefinition}s to build the aggregate state.
         *
         * <p><strong>Note:</strong> The subject specified via {@link #usingSubject(String)} does <strong>not</strong>
         * apply to events from the given command - they retain their original subjects as published by the command
         * handler.
         *
         * <p>Example:
         *
         * <pre>
         * var placeOrderFixture = fixture.using(...);  // fixture that places orders
         *
         * fixture.given()
         *     .command(placeOrderFixture, new PlaceOrderCommand(...))
         *     .when(new CancelOrderCommand(...))
         *     .succeeds();
         * </pre>
         *
         * @param fixture the fixture to execute the command with
         * @param command the command to execute
         * @param <C> the command type
         * @return {@code this} for method chaining
         * @throws AssertionError if the given command execution fails
         */
        <C extends Command> Given command(CommandHandlingTestFixture<C> fixture, C command);

        /**
         * Executes another command with meta-data using the specified fixture and applies its captured events as given
         * events for this test.
         *
         * @param fixture the fixture to execute the command with
         * @param command the command to execute
         * @param metaData the meta-data to pass to the command handler
         * @param <C> the command type
         * @return {@code this} for method chaining
         * @throws AssertionError if the given command execution fails
         * @see #command(CommandHandlingTestFixture, Command)
         */
        <C extends Command> Given command(CommandHandlingTestFixture<C> fixture, C command, Map<String, ?> metaData);

        /**
         * Sets the subject to use for subsequent events added via {@link #events(Object...)} or
         * {@link #event(Consumer)}. This subject is used unless explicitly overridden per event using
         * {@link EventSpecifier#subject(String)}.
         *
         * <p>Example:
         *
         * <pre>
         * fixture.given()
         *     .usingSubject("/orders/123")
         *     .events(new OrderPlacedEvent(...), new PaymentReceivedEvent(...))  // both use /orders/123
         *     .usingSubject("/orders/456")
         *     .events(new OrderPlacedEvent(...))  // uses /orders/456
         *     .when(...)
         * </pre>
         *
         * @param subject the subject to use for subsequent events
         * @return {@code this} for method chaining
         * @see #usingCommandSubject()
         * @see EventSpecifier#subject(String)
         */
        Given usingSubject(String subject);

        /**
         * Sets the subject for subsequent events to the command's subject (as returned by
         * {@link Command#getSubject()}). This is a convenience method equivalent to
         * {@code usingSubject(command.getSubject())}.
         *
         * @return {@code this} for method chaining
         * @see #usingSubject(String)
         */
        Given usingCommandSubject();

        /**
         * Completes the Given phase and transitions to the When phase by executing the specified command. The command
         * is executed against the state built from the given events (or injected directly via {@link #state(Object)}).
         *
         * @param command the command to execute
         * @return an {@link ExpectDsl.Initializing} to specify expected outcomes
         */
        ExpectDsl.Initializing when(Object command);

        /**
         * Completes the Given phase and transitions to the When phase by executing the specified command with
         * meta-data. The meta-data is passed to the command handler if it accepts meta-data (e.g.,
         * {@link com.opencqrs.framework.command.CommandHandler.ForInstanceAndCommandAndMetaData}).
         *
         * @param command the command to execute
         * @param metaData the meta-data to pass to the command handler
         * @return an {@link ExpectDsl.Initializing} to specify expected outcomes
         */
        ExpectDsl.Initializing when(Object command, Map<String, ?> metaData);
    }

    /**
     * Fluent API for specifying the attributes of a single given event. Instances are passed to the consumer in
     * {@link Given#event(Consumer)}.
     *
     * <p>The only required method is {@link #payload(Object)} - all other attributes have sensible defaults:
     *
     * <ul>
     *   <li>{@code subject} - uses the value from {@link Given#usingSubject(String)} or the command's subject
     *   <li>{@code time} - uses the value from {@link Given#time(Instant)} or {@link Instant#now()}
     *   <li>{@code id} - generated randomly
     *   <li>{@code metaData} - empty map
     * </ul>
     *
     * <p>Example:
     *
     * <pre>
     * fixture.given()
     *     .event(e -&gt; e
     *         .payload(new OrderPlacedEvent("order-123", "customer-456"))
     *         .subject("/orders/order-123")
     *         .time(Instant.parse("2024-01-15T14:30:00Z"))
     *         .id("event-001")
     *         .metaData(Map.of("userId", "user-789")))
     *     .when(...)
     * </pre>
     */
    interface EventSpecifier {

        /**
         * Sets the event payload. This is the actual event object that will be passed to the
         * {@link StateRebuildingHandler}.
         *
         * <p><strong>This method must be called</strong> - an {@link IllegalArgumentException} is thrown if the event
         * is used without specifying a payload.
         *
         * @param payload the event payload object
         * @return {@code this} for method chaining
         */
        EventSpecifier payload(Object payload);

        /**
         * Sets the timestamp for this specific event, overriding the value from {@link Given#time(Instant)}. This
         * timestamp is available in {@link StateRebuildingHandler.FromObjectAndRawEvent} via the raw event's
         * {@code time()} method.
         *
         * @param time the timestamp for this event
         * @return {@code this} for method chaining
         */
        EventSpecifier time(Instant time);

        /**
         * Sets the subject for this specific event, overriding the value from {@link Given#usingSubject(String)}. This
         * subject is available in {@link StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent}.
         *
         * @param subject the subject for this event, or {@code null} to use the default
         * @return {@code this} for method chaining
         */
        EventSpecifier subject(String subject);

        /**
         * Sets the unique identifier for this event. If not specified, a random UUID is generated. This id is available
         * in {@link StateRebuildingHandler.FromObjectAndRawEvent} via the raw event's {@code id()} method.
         *
         * @param id the unique identifier for this event
         * @return {@code this} for method chaining
         */
        EventSpecifier id(String id);

        /**
         * Sets the meta-data for this event. Meta-data is available in
         * {@link StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent}.
         *
         * <p>If not specified, an empty map is used.
         *
         * @param metaData the meta-data map for this event
         * @return {@code this} for method chaining
         */
        EventSpecifier metaData(Map<String, ?> metaData);
    }
}
