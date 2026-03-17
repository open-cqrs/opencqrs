/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

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
 * @see EsdbClient#read(String, Set)
 * @see EsdbClient#read(String, Set, Consumer)
 * @see EsdbClient#observe(String, Set, Consumer)
 */
public record Event(
        String source,
        String subject,
        String type,
        Map<String, ?> data,
        String specVersion,
        String id,
        Instant time,
        String dataContentType,
        String hash,
        String predecessorHash)
        implements Marshaller.ResponseElement {}
