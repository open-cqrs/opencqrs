/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import org.springframework.context.SmartLifecycle;

/**
 * {@link EventHandlingProcessorLifecycleController} implementation that implements {@link SmartLifecycle} to delegate
 * life-cycle handling to the Spring application context.
 */
class SmartLifecycleEventHandlingProcessorLifecycleController
        implements EventHandlingProcessorLifecycleController, SmartLifecycle {

    private boolean autoStartup = true;
    private boolean running = false;
    private final EventHandlingProcessor eventHandlingProcessor;

    SmartLifecycleEventHandlingProcessorLifecycleController(EventHandlingProcessor eventHandlingProcessor) {
        this.eventHandlingProcessor = eventHandlingProcessor;
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    @Override
    public void start() {
        eventHandlingProcessor.start();
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        eventHandlingProcessor.stop();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
