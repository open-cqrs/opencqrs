/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.serialization;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.EventCandidate;
import java.util.Map;

/**
 * Interface implemented for transforming Java event objects and additional meta-data to JSON-like maps, and vice versa.
 * Used for transformations to or from {@link Event#data()} and to {@link EventCandidate#data()}.
 *
 * <p>To ensure interoperability, implementations of {@code this} should conform to the same JSON schema for the
 * {@code data} event content, i.e. using {@code metadata} for {@link EventData#metaData()} and {@code payload} for
 * {@link EventData#payload()}, e.g. as follows:
 *
 * <pre>
 *     {
 *         "data": {
 *             "metadata": {
 *                 "userId": "345897345"
 *             },
 *             "payload": {
 *                 // serialized java object
 *             }
 *         },
 *         // other cloud event attributes omitted for brevity
 *     }
 * </pre>
 */
public interface EventDataMarshaller {

    /**
     * Converts the given {@link EventData} to a {@link Map} representation.
     *
     * @param data the event data
     * @return a JSON-like map representation
     * @param <E> the generic payload type
     */
    <E> Map<String, ?> serialize(EventData<E> data);

    /**
     * Converts a JSON-like {@link Map} representation to {@link EventData} using the given {@link Class} to determine
     * the payload type.
     *
     * @param json the JSON-like map representation
     * @param clazz the target type of the {@link EventData#payload()}
     * @return the event and meta-data
     * @param <E> the generic payload type
     */
    <E> EventData<E> deserialize(Map<String, ?> json, Class<E> clazz);
}
