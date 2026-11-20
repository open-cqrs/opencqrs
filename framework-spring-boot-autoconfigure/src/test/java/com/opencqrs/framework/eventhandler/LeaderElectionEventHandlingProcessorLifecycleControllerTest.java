/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.integration.leader.Context;

public class LeaderElectionEventHandlingProcessorLifecycleControllerTest {

    private final EventHandlingProcessor eventHandlingProcessor = mock(EventHandlingProcessor.class);
    private final Context context = mock(Context.class);
    private final CompletableFuture<Void> eventHandlingLoop = new CompletableFuture<>();

    private LeaderElectionEventHandlingProcessorLifecycleController grantedController(Duration stopTimeout) {
        doReturn("test-group").when(eventHandlingProcessor).getGroupId();
        doReturn(eventHandlingLoop).when(eventHandlingProcessor).start();
        var controller =
                new LeaderElectionEventHandlingProcessorLifecycleController(eventHandlingProcessor, stopTimeout);
        controller.onGranted(context);
        verify(eventHandlingProcessor).start();
        return controller;
    }

    @Test
    public void leadershipYieldedAfterEventHandlingLoopTerminated() {
        grantedController(Duration.ofSeconds(30));

        eventHandlingLoop.complete(null);

        verify(context, timeout(5000)).yield();
    }

    @Test
    public void leadershipYieldedOnRevocationOnlyAfterEventHandlingLoopTerminated() throws Exception {
        var controller = grantedController(Duration.ofSeconds(30));

        var revoked = CompletableFuture.runAsync(() -> controller.onRevoked(context));

        verify(eventHandlingProcessor, timeout(5000)).stop();
        verify(context, never()).yield();
        assertThat(revoked).as("onRevoked returned before loop terminated").isNotDone();

        eventHandlingLoop.complete(null);

        revoked.get(5, TimeUnit.SECONDS);
        verify(context, atLeastOnce()).yield();
    }

    @Test
    @Timeout(5)
    public void leadershipYieldedOnRevocationAfterTimeoutIfEventHandlingLoopDoesNotTerminate() {
        var controller = grantedController(Duration.ofMillis(100));

        controller.onRevoked(context);

        verify(eventHandlingProcessor).stop();
        verify(context).yield();
        assertThat(eventHandlingLoop).isNotDone();
    }

    @Test
    public void leadershipYieldedIfEventHandlingProcessorCannotBeStarted() {
        doReturn("test-group").when(eventHandlingProcessor).getGroupId();
        doThrow(new IllegalStateException("already started"))
                .when(eventHandlingProcessor)
                .start();
        var controller = new LeaderElectionEventHandlingProcessorLifecycleController(
                eventHandlingProcessor, Duration.ofSeconds(30));

        controller.onGranted(context);

        verify(context).yield();
    }
}
