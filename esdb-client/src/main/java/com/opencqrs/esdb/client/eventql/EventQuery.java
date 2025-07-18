/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.eventql;

/**
 * Encapsulates an EventQL query.
 *
 * @param queryString the EventQL query as string
 * @see EventQueryBuilder
 */
public record EventQuery(String queryString) {}
