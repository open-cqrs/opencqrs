/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.progress;

import java.util.function.Supplier;

/** Interface specifying operations for tracking progress of event processing groups. */
public interface ProgressTracker {

    /**
     * Retrieves the current {@link Progress} for the specified event processing group and partition.
     *
     * @param group the processing group identifier
     * @param partition the partition number
     * @return the current progress
     */
    Progress current(String group, long partition);

    /**
     * Proceeds the current {@link Progress} by executing the given {@link Supplier}, which in turn yields the new
     * progress for the specified event processing group and partition.
     *
     * @param group the processing group identifier
     * @param partition the partition number
     * @param execution the supplier returning the new progress, if executed successfully
     */
    void proceed(String group, long partition, Supplier<Progress> execution);
}
