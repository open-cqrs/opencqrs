/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.progress;

import com.opencqrs.framework.eventhandler.EventHandler;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * {@link ProgressTracker} implementation using an in-memory {@link Map}. <b>This implementation is discouraged for
 * {@link EventHandler}s that rely on persistent progress while processing events, since the
 * {@linkplain #current(String, long)} current progress} is reset upon restart of the JVM.</b>
 */
public class InMemoryProgressTracker implements ProgressTracker {

    private final ConcurrentMap<GroupPartition, Progress> ids = new ConcurrentHashMap<>();

    @Override
    public Progress current(String group, long partition) {
        return Optional.ofNullable(ids.get(new GroupPartition(group, partition)))
                .orElseGet(Progress.None::new);
    }

    @Override
    public void proceed(String group, long partition, Supplier<Progress> execution) {
        ids.put(new GroupPartition(group, partition), execution.get());
    }

    record GroupPartition(String group, long partition) {}
}
