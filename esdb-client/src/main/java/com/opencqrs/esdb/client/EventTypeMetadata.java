/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents metadata about an event type in the event store.
 *
 * @param eventType the event type identifier
 * @param isPhantom true if the event type has been registered via schema but no events have been written yet
 * @param schema optional JSON schema for this event type, null if no schema has been registered
 * @see EsdbClient#readEventTypes()
 */
public record EventTypeMetadata(@NotBlank String eventType, boolean isPhantom, JsonNode schema) {}
