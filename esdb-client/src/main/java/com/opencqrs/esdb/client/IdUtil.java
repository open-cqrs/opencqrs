/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

/** Static helper methods related to {@link Event#id()}. */
public final class IdUtil {

    /**
     * Converts an {@link Event#id()} to a number.
     *
     * @param id the event id
     * @return the long number
     */
    public static Long fromEventId(String id) {
        return Long.valueOf(id);
    }
}
