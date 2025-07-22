/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.eventql;

/**
 * EventQuery based on EventQL
 *
 * @param queryString the EventQL query as string
 */
public record EventQuery(String queryString) {}
