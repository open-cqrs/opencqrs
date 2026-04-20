/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.time.Instant;
import java.util.Map;

/**
 * Fluent API for specifying the attributes of a single given event. Instances are passed to the consumer in
 * {@link GivenDsl#event(java.util.function.Consumer)}.
 *
 * <p>The only required method is {@link #payload(Object)} - all other attributes have the following defaults if not
 * explicitly specified via {@code this}:
 *
 * <ul>
 *   <li>{@code subject} - uses the value from {@link GivenDsl#usingSubject(String)} or the command's subject
 *   <li>{@code time} - uses the value from {@link GivenDsl#time(Instant)} or {@link Instant#now()}
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
public interface EventSpecifierDsl {

    /**
     * Sets the event payload. This is the actual event object that will be passed to the
     * {@link StateRebuildingHandler}.
     *
     * <p><strong>This method must be called</strong> - an {@link IllegalArgumentException} is thrown if the event is
     * used without specifying a payload.
     *
     * @param payload the event payload object
     * @return {@code this} for method chaining
     */
    EventSpecifierDsl payload(Object payload);

    /**
     * Sets the timestamp for this specific event, overriding the value from {@link GivenDsl#time(Instant)}. This
     * timestamp is available in {@link StateRebuildingHandler.FromObjectAndRawEvent} via the raw event's {@code time()}
     * method.
     *
     * @param time the timestamp for this event
     * @return {@code this} for method chaining
     */
    EventSpecifierDsl time(Instant time);

    /**
     * Sets the subject for this specific event, overriding the value from {@link GivenDsl#usingSubject(String)}. This
     * subject is applied as the {@linkplain Event#subject() raw event's subject}.
     *
     * @param subject the subject for this event, or {@code null} to use the default
     * @return {@code this} for method chaining
     */
    EventSpecifierDsl subject(String subject);

    /**
     * Sets the unique identifier for this event. If not specified, a random UUID is generated. This id is available in
     * {@link StateRebuildingHandler.FromObjectAndRawEvent} via the raw event's {@code id()} method.
     *
     * @param id the unique identifier for this event
     * @return {@code this} for method chaining
     */
    EventSpecifierDsl id(String id);

    /**
     * Sets the meta-data for this event. Meta-data is available in
     * {@link StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent}.
     *
     * <p>If not specified, an empty map is used.
     *
     * @param metaData the meta-data map for this event
     * @return {@code this} for method chaining
     */
    EventSpecifierDsl metaData(Map<String, ?> metaData);
}
