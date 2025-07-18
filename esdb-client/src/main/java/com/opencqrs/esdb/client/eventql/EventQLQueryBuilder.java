/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.eventql;

public class EventQLQueryBuilder {

    private EventQLQueryBuilder() {}

    public static EventQLQuery fromQueryString(String queryString) {
        return new EventQLQuery(queryString);
    }
}
