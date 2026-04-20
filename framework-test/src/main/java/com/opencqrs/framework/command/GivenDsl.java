/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent API for configuring test preconditions before command execution. Methods in this interface allow specifying
 * the initial state of the instance through various means:
 *
 * <ul>
 *   <li>Direct state injection via {@link #state(Object)}
 *   <li>Event-based state reconstruction via {@link #events(Object...)} or {@link #event(Consumer)}
 *   <li>Reusing events from another command execution via {@link #command(CommandHandlingTestFixture, Command)}
 * </ul>
 *
 * <p>Events provided through this interface are processed by the configured {@link StateRebuildingHandlerDefinition}s
 * to build the instance state. These events are <strong>not</strong> included in the captured events that can be
 * asserted in the Expect phase - only events published by the command under test are capturable.
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
 * @param <C> the command type
 * @see EventSpecifierDsl
 * @see ExpectDsl.Outcome
 * @see CommandHandlingTestFixture
 */
public interface GivenDsl<C extends Command> {

    /**
     * Entry point for the Given phase, returned by {@link CommandHandlingTestFixture#given()}. Extends {@link GivenDsl}
     * with the additional option to declare that no preconditions exist via {@link #nothing()}.
     *
     * <p>After calling {@link #nothing()}, only {@link Terminated#when(Command)} is available, preventing nonsensical
     * chains like {@code .event(...).nothing()} or {@code .nothing().nothing()}.
     */
    interface Initial<C extends Command> extends GivenDsl<C> {

        /**
         * Indicates that no preconditions exist for this test. The command will be executed against an empty/null
         * state. This is typically used for commands that create new instances.
         *
         * @return a {@link Terminated} interface that only allows transitioning to command execution
         */
        Terminated<C> nothing();
    }

    /**
     * Terminal state of the Given phase after calling {@link Initial#nothing()}. Only allows transitioning to command
     * execution via {@link #when(Command)}.
     */
    interface Terminated<C extends Command> {

        /**
         * Completes the Given phase and transitions to the When phase by executing the specified command.
         *
         * @param command the command to execute
         * @return an {@link ExpectDsl.Outcome} to specify expected outcomes
         */
        ExpectDsl.Outcome when(C command);

        /**
         * Completes the Given phase and transitions to the When phase by executing the specified command with
         * meta-data.
         *
         * @param command the command to execute
         * @param metaData the meta-data to pass to the command handler
         * @return an {@link ExpectDsl.Outcome} to specify expected outcomes
         */
        ExpectDsl.Outcome when(C command, Map<String, ?> metaData);
    }

    /**
     * Sets the timestamp for subsequent events added via {@link #events(Object...)} or {@link #event(Consumer)}. This
     * timestamp is applied to the {@linkplain com.opencqrs.esdb.client.Event#time() raw event's time} passed to
     * {@link StateRebuildingHandler}s.
     *
     * <p>If not specified, {@link Instant#now()} is used as the default timestamp.
     *
     * @param time the timestamp to use for subsequent events
     * @return {@code this} for method chaining
     * @see #timeDelta(Duration)
     */
    GivenDsl<C> time(Instant time);

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
    GivenDsl<C> timeDelta(Duration delta);

    /**
     * Directly sets the instance state without processing any events. This bypasses the
     * {@link StateRebuildingHandlerDefinition}s and injects the state directly.
     *
     * <p>Use this method when you want to test command behavior against a specific state without setting up the event
     * history that would normally produce that state.
     *
     * <p><strong>Note:</strong> This only affects preceding stubbings — subsequent {@link #events(Object...)} or
     * {@link #event(Consumer)} calls can still be applied. However, if no events are added after this call, no events
     * are present in the instance's history, which may affect precondition checks that rely on event existence (e.g.,
     * {@link Command.SubjectCondition#EXISTS}).
     *
     * @param state the state object to inject
     * @return {@code this} for method chaining
     */
    GivenDsl<C> state(Object state);

    /**
     * Adds multiple event payloads to be processed by {@link StateRebuildingHandlerDefinition}s. Each event payload is
     * wrapped with default values for subject, time, id, and meta-data.
     *
     * <p>The events are processed in the order provided, with each event potentially modifying the instance state
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
    GivenDsl<C> events(Object... events);

    /**
     * Adds a single event with full control over its attributes using the {@link EventSpecifierDsl} fluent API. This
     * method allows specifying the event payload along with optional attributes like subject, time, id, and meta-data.
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
     * @param event a consumer that configures the event via {@link EventSpecifierDsl}
     * @return {@code this} for method chaining
     * @throws IllegalArgumentException if {@link EventSpecifierDsl#payload(Object)} is not called
     * @throws IllegalArgumentException if no {@link StateRebuildingHandlerDefinition} matches the event's payload type
     * @see EventSpecifierDsl
     */
    GivenDsl<C> event(Consumer<EventSpecifierDsl> event);

    /**
     * Executes another command using the specified fixture and applies its captured events as given events for this
     * test. This is useful for setting up complex preconditions that result from prior command executions.
     *
     * <p>The events published by the given command are processed by this fixture's
     * {@link StateRebuildingHandlerDefinition}s to build the instance state.
     *
     * <p><strong>Note:</strong> The subject specified via {@link #usingSubject(String)} does <strong>not</strong> apply
     * to events from the given command - they retain their original subjects as published by the command handler.
     *
     * @param fixture the fixture to execute the command with
     * @param command the command to execute
     * @param <CMD> the command type
     * @return {@code this} for method chaining
     * @throws AssertionError if the given command execution fails
     */
    <CMD extends Command> GivenDsl<C> command(CommandHandlingTestFixture<CMD> fixture, CMD command);

    /**
     * Executes another command with meta-data using the specified fixture and applies its captured events as given
     * events for this test.
     *
     * @param fixture the fixture to execute the command with
     * @param command the command to execute
     * @param metaData the meta-data to pass to the command handler
     * @param <CMD> the command type
     * @return {@code this} for method chaining
     * @throws AssertionError if the given command execution fails
     * @see #command(CommandHandlingTestFixture, Command)
     */
    <CMD extends Command> GivenDsl<C> command(
            CommandHandlingTestFixture<CMD> fixture, CMD command, Map<String, ?> metaData);

    /**
     * Sets the subject to use for subsequent events added via {@link #events(Object...)} or {@link #event(Consumer)}.
     * This subject is used unless explicitly overridden per event using {@link EventSpecifierDsl#subject(String)}.
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
     * @see EventSpecifierDsl#subject(String)
     */
    GivenDsl<C> usingSubject(String subject);

    /**
     * Sets the subject for subsequent events to the command's subject (as returned by {@link Command#getSubject()}).
     * This is a convenience method equivalent to {@code usingSubject(command.getSubject())}.
     *
     * @return {@code this} for method chaining
     * @see #usingSubject(String)
     */
    GivenDsl<C> usingCommandSubject();

    /**
     * Completes the Given phase and transitions to the When phase by executing the specified command. The command is
     * executed against the state built from the given events (or injected directly via {@link #state(Object)}).
     *
     * @param command the command to execute
     * @return an {@link ExpectDsl.Outcome} to specify expected outcomes
     */
    ExpectDsl.Outcome when(C command);

    /**
     * Completes the Given phase and transitions to the When phase by executing the specified command with meta-data.
     * The meta-data is passed to the command handler if it accepts meta-data (e.g.,
     * {@link com.opencqrs.framework.command.CommandHandler.ForInstanceAndCommandAndMetaData}).
     *
     * @param command the command to execute
     * @param metaData the meta-data to pass to the command handler
     * @return an {@link ExpectDsl.Outcome} to specify expected outcomes
     */
    ExpectDsl.Outcome when(C command, Map<String, ?> metaData);
}
