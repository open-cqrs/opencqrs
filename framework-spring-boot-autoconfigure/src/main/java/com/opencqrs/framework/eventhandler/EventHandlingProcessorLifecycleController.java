/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

/**
 * Interface to be implemented by beans responsible for managing the life-cycle of an {@link EventHandlingProcessor}
 * bean.
 *
 * @see EventHandlingProcessor#start()
 * @see EventHandlingProcessor#stop()
 */
public interface EventHandlingProcessorLifecycleController {

    /**
     * States whether the associated {@link EventHandlingProcessor} is currently
     * {@linkplain EventHandlingProcessor#run()} running.
     *
     * @return {@code true} if it is running, {@code false} otherwise
     */
    boolean isRunning();
}
