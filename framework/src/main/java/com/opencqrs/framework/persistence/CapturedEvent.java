/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.persistence;

import com.opencqrs.esdb.client.Precondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Record capturing an event publication intent.
 *
 * @param subject the subject the event is going to be published to
 * @param event the event object to be published
 * @param metaData the event meta-data to be published
 * @param preconditions the preconditions that must not be violated when publishing
 */
public record CapturedEvent(
        @NotBlank String subject,
        @NotNull Object event,
        @NotNull Map<String, ?> metaData,
        @NotNull List<Precondition> preconditions) {
    /**
     * Convenience constructor, if no meta-data or preconditions are needed.
     *
     * @param subject the subject the event is going to be published to
     * @param event the event object to be published
     */
    public CapturedEvent(String subject, Object event) {
        this(subject, event, Map.of(), List.of());
    }
}
