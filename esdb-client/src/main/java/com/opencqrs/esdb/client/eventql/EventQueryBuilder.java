/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.eventql;

/** Builder for {@link EventQuery} instances */
public class EventQueryBuilder {

    private EventQueryBuilder() {}

    /**
     * Constructs an {@link EventQuery} from a query string without further checks.
     *
     * @param queryString the EventQL query represented as string
     * @return the created {@link EventQuery}
     */
    public static EventQuery fromEventQlString(String queryString) {
        return new EventQuery(queryString);
    }
}
