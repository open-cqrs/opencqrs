/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Candidate event for {@link EsdbClient#write(List, List) publication} to a <a
 * href="https://github.com/cloudevents/spec">Cloud Events Specification</a> conforming event store.
 *
 * @param source identifies the originating source of publication
 * @param subject an absolute path identifying the subject that the event is related to
 * @param type uniquely identifies the event type, specifically for being able to interpret the contained data structure
 * @param data a generic map structure containing the event payload, which is going to be stored as JSON within the
 *     event store
 * @param traceParent the candidate's 'traceparent' header, according to the W3C Trace Context standard
 * @param traceState the candidate's 'tracestate' header, according to the W3C Trace Context standard
 * @see Event
 * @see EsdbClient#write(List, List)
 */
public record EventCandidate(
        @NotBlank String source,
        @NotBlank String subject,
        @NotBlank String type,
        @NotNull Map<String, ?> data,
        String traceParent,
        String traceState) {

    /**
     * Convenience constructor for EventCandidates with no tracing data available.
     *
     * @param source
     * @param subject
     * @param type
     * @param data
     */
    public EventCandidate(
            @NotBlank String source, @NotBlank String subject, @NotBlank String type, @NotNull Map<String, ?> data) {
        this(source, subject, type, data, null, null);
    }
}
