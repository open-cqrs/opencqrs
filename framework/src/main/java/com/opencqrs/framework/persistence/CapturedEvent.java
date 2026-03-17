/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.persistence;

import com.opencqrs.esdb.client.Precondition;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Record capturing an event publication intent.
 *
 * @param subject the subject the event is going to be published to
 * @param event the event object to be published
 * @param metaData the event meta-data to be published
 * @param preconditions the preconditions that must not be violated when publishing
 */
public record CapturedEvent(
        String subject,
        Object event,
        Map<String, ?> metaData,
        List<Precondition> preconditions) {
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
