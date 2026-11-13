/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.context.SmartLifecycle;

/**
 * {@link SmartLifecycle} based implementation for the {@link EventHandlingProcessor}.
 *
 * <p>Stopping is asynchronous via {@link #stop(Runnable)}: the {@link EventHandlingProcessor} is
 * {@linkplain EventHandlingProcessor#stop() stopped} immediately, but the callback is only run once its event handling
 * loop has actually terminated, so that Spring waits for it (bounded by the configured shutdown phase timeout). The
 * synchronous {@link #stop()} waits for the termination as well, bounded by a timeout of 30 seconds.
 *
 * @see EventHandlingProcessorAutoConfiguration#openCqrsSmartLifecycleEventHandlingProcessorLifecycleControllerFactory()
 */
class SmartLifecycleEventHandlingProcessorLifecycleController implements SmartLifecycle {

    private static final Logger log =
            Logger.getLogger(SmartLifecycleEventHandlingProcessorLifecycleController.class.getName());

    private boolean autoStartup = true;
    private volatile boolean running = false;
    private volatile CompletableFuture<Void> terminated = CompletableFuture.completedFuture(null);
    private final EventHandlingProcessor eventHandlingProcessor;
    private final Duration stopTimeout;

    SmartLifecycleEventHandlingProcessorLifecycleController(EventHandlingProcessor eventHandlingProcessor) {
        this(eventHandlingProcessor, Duration.ofSeconds(30));
    }

    SmartLifecycleEventHandlingProcessorLifecycleController(
            EventHandlingProcessor eventHandlingProcessor, Duration stopTimeout) {
        this.eventHandlingProcessor = eventHandlingProcessor;
        this.stopTimeout = stopTimeout;
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
        Future<?> started = eventHandlingProcessor.start();
        var terminated = new CompletableFuture<Void>();
        this.terminated = terminated;
        running = true;
        Thread.ofVirtual().start(() -> {
            try {
                started.get();
            } catch (ExecutionException | InterruptedException e) {
                log.log(Level.INFO, eventHandlingProcessor.eventProcessorForLogs() + " prematurely terminated", e);
            } finally {
                running = false;
                terminated.complete(null);
            }
        });
    }

    @Override
    public void stop(Runnable callback) {
        running = false;
        eventHandlingProcessor.stop();
        terminated.whenComplete((ignored, t) -> callback.run());
    }

    @Override
    public void stop() {
        stop(() -> {});
        boolean terminatedInTime = terminated
                .thenApply(ignored -> true)
                .completeOnTimeout(false, stopTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .join();
        if (!terminatedInTime) {
            log.warning(() -> eventHandlingProcessor.eventProcessorForLogs() + " did not terminate within "
                    + stopTimeout + " after being stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
