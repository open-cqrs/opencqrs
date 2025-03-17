/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Event data structure retrieved from an event store, conforming to the <a
 * href="https://github.com/cloudevents/spec">Cloud Events Specification</a>.
 *
 * @param source identifies the originating source of publication
 * @param subject an absolute path identifying the subject that the event is related to
 * @param type uniquely identifies the event type, specifically for being able to interpret the contained data structure
 * @param data a generic map structure containing the event payload
 * @param specVersion cloud events specification version
 * @param id a unique event identifier with respect to the originating event store
 * @param time the publication time-stamp
 * @param dataContentType the data content-type, always {@code application/json}
 * @param hash the hash of this event
 * @param predecessorHash the hash of the preceding event in the event store
 * @see EventCandidate
 * @see Client#read(String, Set)
 * @see Client#read(String, Set, Consumer)
 * @see Client#observe(String, Set, Consumer)
 */
public record Event(
        @NotBlank String source,
        @NotBlank String subject,
        @NotBlank String type,
        @NotNull Map<String, ?> data,
        @NotBlank String specVersion,
        @NotBlank String id,
        @NotNull Instant time,
        @NotBlank String dataContentType,
        String hash,
        @NotBlank String predecessorHash)
        implements Marshaller.ResponseElement {}
