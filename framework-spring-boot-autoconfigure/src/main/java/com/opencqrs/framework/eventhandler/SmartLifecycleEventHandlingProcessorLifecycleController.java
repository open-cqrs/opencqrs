/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.context.SmartLifecycle;

/**
 * {@link SmartLifecycle} based implementation for the {@link EventHandlingProcessor}.
 *
 * @see EventHandlingProcessorAutoConfiguration#openCqrsSmartLifecycleEventHandlingProcessorLifecycleControllerFactory()
 */
class SmartLifecycleEventHandlingProcessorLifecycleController implements SmartLifecycle {

    private static final Logger log =
            Logger.getLogger(SmartLifecycleEventHandlingProcessorLifecycleController.class.getName());

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
        Thread.ofVirtual().start(() -> {
            try {
                Future<?> started = eventHandlingProcessor.start();
                running = true;
                started.get();
            } catch (ExecutionException | InterruptedException e) {
                log.log(Level.INFO, eventHandlingProcessor.eventProcessorForLogs() + " prematurely terminated", e);
            } finally {
                running = false;
            }
        });
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
