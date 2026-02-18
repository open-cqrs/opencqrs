/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Interface for comprehensive assertions on captured events, providing access to payload, meta-data, and subject.
 * Instances are obtained via {@link ExpectDsl.EventValidator#asserting(Consumer)}.
 *
 * <p>The API follows a consistent pattern for each event aspect:
 *
 * <ul>
 *   <li>Direct comparison methods ({@link #payload}, {@link #metaData}, {@link #subject})
 *   <li>Consumer-based methods for custom assertions ({@link #payloadSatisfying}, {@link #metaDataSatisfying},
 *       {@link #subjectSatisfying})
 *   <li>Specialized convenience methods ({@link #payloadType}, {@link #payloadExtracting}, {@link #noMetaData},
 *       {@link #commandSubject})
 * </ul>
 *
 * <p>All methods return {@code this} to allow method chaining.
 */
public interface EventAsserting {

    /**
     * Asserts that the event payload is an instance of the specified type.
     *
     * @param type the expected payload class
     * @return {@code this} for further assertions
     * @throws AssertionError if the payload is not assignable to the expected type
     */
    EventAsserting payloadType(Class<?> type);

    /**
     * Asserts that the event payload equals the expected value using {@link Object#equals(Object)}.
     *
     * @param expected the expected payload
     * @return {@code this} for further assertions
     * @throws AssertionError if the payloads are not equal
     * @param <E> the payload type
     */
    <E> EventAsserting payload(
            E expected); // payload maybe is not the best name since in OpenCQRS terms "payload" here means the
    // event itself

    /**
     * Extracts a value from the event payload and asserts it equals the expected value. Useful for comparing specific
     * fields without matching the entire payload.
     *
     * <p>Example: {@code a.payloadExtracting((MyEvent e) -> e.name(), "expected-name")}
     *
     * @param extractor function to extract a value from the payload
     * @param expected the expected extracted value (may be {@code null})
     * @return {@code this} for further assertions
     * @throws AssertionError if the extracted values are not equal
     * @param <E> the payload type
     * @param <R> the extracted value type
     */
    <E, R> EventAsserting payloadExtracting(Function<E, R> extractor, R expected);

    /**
     * Asserts that the event payload satisfies custom assertions.
     *
     * @param assertion consumer receiving the payload
     * @return {@code this} for further assertions
     * @throws AssertionError if thrown by the consumer
     * @param <E> the payload type
     */
    <E> EventAsserting payloadSatisfying(Consumer<E> assertion);

    /**
     * Asserts that the event meta-data equals the expected map using {@link Object#equals(Object)}.
     *
     * @param expected the expected meta-data
     * @return {@code this} for further assertions
     * @throws AssertionError if the meta-data maps are not equal
     */
    EventAsserting metaData(Map<String, ?> expected);

    /**
     * Asserts that the event meta-data satisfies custom assertions.
     *
     * @param assertion consumer receiving the meta-data map
     * @return {@code this} for further assertions
     * @throws AssertionError if thrown by the consumer
     */
    EventAsserting metaDataSatisfying(Consumer<Map<String, ?>> assertion);

    /**
     * Asserts that the event has no meta-data (empty map).
     *
     * @return {@code this} for further assertions
     * @throws AssertionError if the meta-data is not empty
     */
    EventAsserting noMetaData();

    /**
     * Asserts that the event subject equals the expected value.
     *
     * @param expected the expected subject
     * @return {@code this} for further assertions
     * @throws AssertionError if the subjects are not equal
     */
    EventAsserting subject(String expected);

    /**
     * Asserts that the event subject satisfies custom assertions.
     *
     * @param assertion consumer receiving the subject
     * @return {@code this} for further assertions
     * @throws AssertionError if thrown by the consumer
     */
    EventAsserting subjectSatisfying(Consumer<String> assertion);

    /**
     * Asserts that the event subject equals the command's subject. Convenience method equivalent to
     * {@code subject(command.getSubject())}.
     *
     * @return {@code this} for further assertions
     * @throws AssertionError if the subjects do not match
     */
    EventAsserting commandSubject();
}
