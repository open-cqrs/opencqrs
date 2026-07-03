/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;

/**
 * Wrapper for a @{@link ConfigurableApplicationContext} that implements @{@link SmartLifecycle} in order to
 * {@link ConfigurableApplicationContext#refresh()} and {@link ConfigurableApplicationContext#close()} accordingly.
 *
 * @param context the context to control as part of this life-cycle
 * @param running running state
 * @see EventHandlingProcessorAutoConfiguration#openCqrsEventHandlingProcessorContext(ApplicationContext,
 *     EventHandlingProperties, List)
 */
public record EventHandlingProcessorContext(ConfigurableApplicationContext context, AtomicBoolean running)
        implements SmartLifecycle {

    @Override
    public void start() {
        context.refresh();
        running.set(true);
    }

    @Override
    public void stop() {
        running.set(false);
        context.close();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
