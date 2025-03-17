/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import java.util.Map;

/**
 * Represents the current health status of the configured {@linkplain EsdbClient ESDB}.
 *
 * @param status the overall status
 * @param checks DB internal checks status
 */
public record Health(Status status, Map<String, ?> checks) {

    /** Health status. */
    public enum Status {
        pass,
        warn,
        fail,
    }
}
