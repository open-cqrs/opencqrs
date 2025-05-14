/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.serialization;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.EventCandidate;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Represents unmarshalled {@link EventCandidate#data()} or {@link Event#data()}, respectively. The framework, in
 * contrast to the {@link com.opencqrs.esdb.client.EsdbClient}, distinguishes between an event's
 * {@linkplain EventData#payload() payload} and additional {@linkplain EventData#metaData() meta-data}. While the former
 * is used for storing actual Java objects, the latter one can store additional key-value pairs, if needed.
 *
 * <p><strong>Implementations of this interface may choose to ignore the {@link EventData#metaData() meta-data} when
 * marshalling, if there is no need for meta-data support within the application. However, be aware of event
 * immutability which may render events non-marshallable, in case the {@link EventDataMarshaller} implementation needs
 * to be changed, after events have already been stored.</strong>
 *
 * @param metaData the meta-data stored within {@link EventCandidate#data()} or {@link Event#data()}
 * @param payload the Java object payload stored within {@link EventCandidate#data()} or {@link Event#data()}
 * @param <E> the generic object type
 * @see EventDataMarshaller
 */
public record EventData<E>(@NotNull Map<String, ?> metaData, @NotNull E payload) {}
