/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class SmartLifecycleEventHandlingProcessorLifecycleControllerTest {

    private final EventHandlingProcessor eventHandlingProcessor = mock(EventHandlingProcessor.class);
    private final CompletableFuture<Void> eventHandlingLoop = new CompletableFuture<>();

    private SmartLifecycleEventHandlingProcessorLifecycleController startedController(Duration stopTimeout) {
        doReturn(eventHandlingLoop).when(eventHandlingProcessor).start();
        var controller =
                new SmartLifecycleEventHandlingProcessorLifecycleController(eventHandlingProcessor, stopTimeout);
        controller.start();
        return controller;
    }

    @Test
    public void notRunningAfterEventHandlingLoopTerminated() {
        var controller = startedController(Duration.ofSeconds(30));
        assertThat(controller.isRunning()).isTrue();

        eventHandlingLoop.complete(null);

        await().untilAsserted(() -> assertThat(controller.isRunning()).isFalse());
    }

    @Test
    public void stopCallbackRunOnlyAfterEventHandlingLoopTerminated() throws InterruptedException {
        var controller = startedController(Duration.ofSeconds(30));
        var callbackRun = new CountDownLatch(1);

        controller.stop(callbackRun::countDown);

        verify(eventHandlingProcessor).stop();
        assertThat(controller.isRunning()).isFalse();
        assertThat(callbackRun.getCount())
                .as("callback run before loop terminated")
                .isEqualTo(1);

        eventHandlingLoop.complete(null);

        assertThat(callbackRun.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void stopWaitsForEventHandlingLoopTermination() throws Exception {
        var controller = startedController(Duration.ofSeconds(30));

        var stopped = CompletableFuture.runAsync(controller::stop);

        verify(eventHandlingProcessor, timeout(5000)).stop();
        assertThat(stopped).as("stop returned before loop terminated").isNotDone();

        eventHandlingLoop.complete(null);

        stopped.get(5, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(5)
    public void stopReturnsAfterTimeoutIfEventHandlingLoopDoesNotTerminate() {
        var controller = startedController(Duration.ofMillis(100));

        controller.stop();

        verify(eventHandlingProcessor).stop();
        assertThat(eventHandlingLoop).isNotDone();
    }
}
