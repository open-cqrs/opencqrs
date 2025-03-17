/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.partitioning;

import com.opencqrs.esdb.client.Event;
import java.util.Arrays;

/**
 * {@link ForRawEvent} implementation which reduced the {@link Event#subject()} path and reduces it to a configurable
 * level, i.e. reducing {@literal /book/4711/pages/42} to {@literal /book/4711} with level 2.
 */
public class PerConfigurableLevelSubjectEventSequenceResolver implements EventSequenceResolver.ForRawEvent {

    private final int keepLevels;

    public PerConfigurableLevelSubjectEventSequenceResolver(int keepLevels) {
        if (keepLevels < 1) {
            throw new IllegalArgumentException("at least one subject level must be kept: " + keepLevels);
        }
        this.keepLevels = keepLevels;
    }

    public int getKeepLevels() {
        return keepLevels;
    }

    private static String shortenSubject(String subject, int level) {
        var result = new StringBuffer();
        Arrays.stream(subject.splitWithDelimiters("/", -1))
                // subject is assumed to start with "/", so we skip the empty string
                .skip(1)
                .limit(level * 2L)
                .forEach(result::append);

        return result.toString();
    }

    @Override
    public String sequenceIdFor(Event rawEvent) {
        return shortenSubject(rawEvent.subject(), this.keepLevels);
    }
}
