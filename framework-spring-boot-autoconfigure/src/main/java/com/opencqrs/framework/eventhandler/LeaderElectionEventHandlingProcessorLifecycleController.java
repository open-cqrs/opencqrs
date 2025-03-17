/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.integration.leader.AbstractCandidate;
import org.springframework.integration.leader.Context;

/**
 * {@link EventHandlingProcessorLifecycleController} implementation which in turn is managed by a
 * {@link org.springframework.integration.support.leader.LockRegistryLeaderInitiator} bean using {link
 * {@link #onGranted(Context)}} and {@link #onRevoked(Context)}.
 */
class LeaderElectionEventHandlingProcessorLifecycleController extends AbstractCandidate
        implements EventHandlingProcessorLifecycleController {

    private static final Logger log =
            Logger.getLogger(LeaderElectionEventHandlingProcessorLifecycleController.class.getName());

    private final EventHandlingProcessor eventHandlingProcessor;
    private boolean running = false;

    LeaderElectionEventHandlingProcessorLifecycleController(EventHandlingProcessor eventHandlingProcessor) {
        super(
                null,
                "[group=" + eventHandlingProcessor.getGroupId() + ", partition=" + eventHandlingProcessor.getPartition()
                        + "]");
        this.eventHandlingProcessor = eventHandlingProcessor;
    }

    @Override
    public void onGranted(Context ctx) {
        log.info(() -> "leadership granted for " + eventHandlingProcessor.eventProcessorForLogs() + ": " + ctx);
        CompletableFuture.runAsync(() -> {
            try {
                Future<?> started = eventHandlingProcessor.start();
                running = true;
                started.get();
            } catch (ExecutionException | InterruptedException e) {
                log.log(Level.INFO, eventHandlingProcessor.eventProcessorForLogs() + " prematurely terminated", e);
            } finally {
                log.info(() -> eventHandlingProcessor.eventProcessorForLogs() + " yielding leadership: " + ctx);
                running = false;
                ctx.yield();
            }
        });
    }

    @Override
    public void onRevoked(Context ctx) {
        log.warning(() -> "leadership revoked for " + eventHandlingProcessor.eventProcessorForLogs() + ": " + ctx);
        eventHandlingProcessor.stop();
        running = false;
        ctx.yield();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
