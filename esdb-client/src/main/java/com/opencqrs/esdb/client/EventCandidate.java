/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Candidate event for {@link Client#write(List, List) publication} to an event store.
 *
 * @param source identifies the originating source of publication
 * @param subject an absolute path identifying the subject that the event is related to
 * @param type uniquely identifies the event type, specifically for being able to interpret the contained data structure
 * @param data a generic map structure containing the event payload, which is going to be stored as JSON within the
 *     event store
 * @see Event
 * @see Client#write(List, List)
 */
public record EventCandidate(
        @NotBlank String source, @NotBlank String subject, @NotBlank String type, @NotNull Map<String, ?> data) {}
