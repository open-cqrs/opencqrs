/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.eventql;

import com.opencqrs.esdb.client.Event;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sealed base interface for handlers capable of processing
 * {@linkplain com.opencqrs.esdb.client.EsdbClient#query(EventQuery, EventQueryRowHandler, EventQueryErrorHandler)
 * query} result rows.
 */
public sealed interface EventQueryRowHandler {

    /**
     * {@link FunctionalInterface} to be implemented for consuming query result rows as {@link Event}. This can be used
     * for queries similar to {@code FROM e IN events ... PROJECT INTO e}.
     */
    @FunctionalInterface
    non-sealed interface AsEvent extends EventQueryRowHandler, Consumer<Event> {}

    /**
     * {@link FunctionalInterface} to be implemented for consuming query result rows as JSON maps. This can be used for
     * queries similar to {@code FROM e IN events ... PROJECT INTO { id: e.id, ... }}.
     */
    @FunctionalInterface
    non-sealed interface AsMap extends EventQueryRowHandler, Consumer<Map<String, ?>> {}

    /**
     * Interface to be implemented for consuming query result rows as JSON objects. This can be used for queries similar
     * to {@code FROM e IN events ... PROJECT INTO { id: e.id, ... }}.
     */
    non-sealed interface AsObject<T> extends EventQueryRowHandler, Consumer<T> {

        Class<T> type();
    }

    /**
     * {@link FunctionalInterface} to be implemented for consuming query result rows as scalar data types. This can be
     * used for queries similar to {@code FROM e IN events ... PROJECT INTO e.time }.
     */
    @FunctionalInterface
    non-sealed interface AsScalar<T> extends EventQueryRowHandler, Consumer<T> {}
}
