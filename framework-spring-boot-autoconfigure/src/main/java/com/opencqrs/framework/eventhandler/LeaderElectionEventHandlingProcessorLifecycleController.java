/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.integration.leader.AbstractCandidate;
import org.springframework.integration.leader.Context;

/**
 * Life-cycle implementation for the {@link EventHandlingProcessor} which in turn is managed by a
 * {@link org.springframework.integration.support.leader.LockRegistryLeaderInitiator} bean using {link
 * {@link #onGranted(Context)}} and {@link #onRevoked(Context)}.
 *
 * <p>When leadership is revoked, the {@link EventHandlingProcessor} is {@linkplain EventHandlingProcessor#stop()
 * stopped} and leadership is only yielded once its event handling loop has actually terminated (bounded by a timeout of
 * 30 seconds), so that the next leader does not start processing the same partition while this one is still handling
 * its in-flight event.
 *
 * @see EventHandlingProcessorAutoConfiguration#openCqrsLeaderElectionEventHandlingProcessorLifecycleControllerFactory()
 */
class LeaderElectionEventHandlingProcessorLifecycleController extends AbstractCandidate {

    private static final Logger log =
            Logger.getLogger(LeaderElectionEventHandlingProcessorLifecycleController.class.getName());

    private final EventHandlingProcessor eventHandlingProcessor;
    private final Duration stopTimeout;
    private volatile CompletableFuture<Void> terminated = CompletableFuture.completedFuture(null);

    LeaderElectionEventHandlingProcessorLifecycleController(EventHandlingProcessor eventHandlingProcessor) {
        this(eventHandlingProcessor, Duration.ofSeconds(30));
    }

    LeaderElectionEventHandlingProcessorLifecycleController(
            EventHandlingProcessor eventHandlingProcessor, Duration stopTimeout) {
        super(
                null,
                "[group=" + eventHandlingProcessor.getGroupId() + ", partition=" + eventHandlingProcessor.getPartition()
                        + "]");
        this.eventHandlingProcessor = eventHandlingProcessor;
        this.stopTimeout = stopTimeout;
    }

    @Override
    public void onGranted(Context ctx) {
        log.info(() -> "leadership granted for " + eventHandlingProcessor.eventProcessorForLogs() + ": " + ctx);
        Future<?> started;
        try {
            started = eventHandlingProcessor.start();
        } catch (IllegalStateException e) {
            log.log(
                    Level.WARNING,
                    eventHandlingProcessor.eventProcessorForLogs() + " could not be started, yielding leadership",
                    e);
            ctx.yield();
            return;
        }
        var terminated = new CompletableFuture<Void>();
        this.terminated = terminated;
        Thread.ofVirtual().start(() -> {
            try {
                started.get();
            } catch (ExecutionException | InterruptedException e) {
                log.log(Level.INFO, eventHandlingProcessor.eventProcessorForLogs() + " prematurely terminated", e);
            } finally {
                terminated.complete(null);
                log.info(() -> eventHandlingProcessor.eventProcessorForLogs() + " yielding leadership: " + ctx);
                ctx.yield();
            }
        });
    }

    @Override
    public void onRevoked(Context ctx) {
        log.warning(() -> "leadership revoked for " + eventHandlingProcessor.eventProcessorForLogs() + ": " + ctx);
        eventHandlingProcessor.stop();
        boolean terminatedInTime = terminated
                .thenApply(ignored -> true)
                .completeOnTimeout(false, stopTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .join();
        if (!terminatedInTime) {
            log.warning(() -> eventHandlingProcessor.eventProcessorForLogs() + " did not terminate within "
                    + stopTimeout + " after leadership was revoked, yielding leadership anyway");
        }
        ctx.yield();
    }
}
