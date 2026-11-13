/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies how {@link HttpRequestErrorHandler} distinguishes thread interruptions from transport failures, including
 * the resulting interrupt status of the calling thread. A mocked {@link HttpClient} reproduces the different exception
 * shapes deterministically. Each case runs on its own thread, so that the interrupt status cannot leak into other
 * tests.
 */
public class HttpRequestErrorHandlerInterruptionTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final HttpRequestErrorHandler subject = new HttpRequestErrorHandler(httpClient);
    private final HttpRequest request =
            HttpRequest.newBuilder(URI.create("http://localhost/test")).GET().build();

    static Stream<Arguments> interruptions() {
        return Stream.of(
                arguments(named(
                        "IOException caused by InterruptedException", new IOException(new InterruptedException()))),
                arguments(named("ClosedByInterruptException", new ClosedByInterruptException())),
                arguments(named(
                        "ClosedByInterruptException nested in IOException",
                        new IOException(new ClosedByInterruptException()))));
    }

    static Stream<Arguments> transportFailures() {
        return Stream.of(
                arguments(named("IOException", new IOException("connection reset"))),
                arguments(named("SocketTimeoutException", new SocketTimeoutException("read timed out"))),
                arguments(named("InterruptedIOException", new InterruptedIOException("timed out"))));
    }

    private record Outcome(Throwable thrown, boolean interruptFlagSet) {}

    private static Outcome runIsolated(Runnable runnable) throws InterruptedException {
        var outcome = new AtomicReference<Outcome>();
        Thread.ofVirtual()
                .start(() -> {
                    Throwable thrown = null;
                    try {
                        runnable.run();
                    } catch (Throwable t) {
                        thrown = t;
                    }
                    outcome.set(new Outcome(thrown, Thread.currentThread().isInterrupted()));
                })
                .join();
        return outcome.get();
    }

    @SuppressWarnings("unchecked")
    private void respondWith(int statusCode, Stream<String> body) throws Exception {
        HttpResponse<Stream<String>> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        doReturn(response).when(httpClient).send(any(), any());
    }

    private static Stream<String> failingBody(IOException e) {
        return Stream.<String>generate(() -> {
            throw new UncheckedIOException(e);
        });
    }

    @Test
    public void interruptedExceptionWhileSendingRequest() throws Exception {
        var interruptedException = new InterruptedException();
        when(httpClient.send(any(), any())).thenThrow(interruptedException);

        var outcome = runIsolated(() -> subject.handle(request));

        assertThat(outcome.thrown())
                .isInstanceOf(ClientException.InterruptedException.class)
                .hasCause(interruptedException);
        assertThat(outcome.interruptFlagSet()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("interruptions")
    public void interruptionWhileSendingRequest(IOException e) throws Exception {
        when(httpClient.send(any(), any())).thenThrow(e);

        var outcome = runIsolated(() -> subject.handle(request));

        assertThat(outcome.thrown())
                .isInstanceOf(ClientException.InterruptedException.class)
                .hasCause(e);
        assertThat(outcome.interruptFlagSet()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("transportFailures")
    public void transportFailureWhileSendingRequest(IOException e) throws Exception {
        when(httpClient.send(any(), any())).thenThrow(e);

        var outcome = runIsolated(() -> subject.handle(request));

        assertThat(outcome.thrown())
                .isInstanceOf(ClientException.TransportException.class)
                .hasCause(e);
        assertThat(outcome.interruptFlagSet()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("interruptions")
    public void interruptionWhileReadingResponseBody(IOException e) throws Exception {
        respondWith(200, failingBody(e));

        var outcome = runIsolated(() -> subject.handle(request, line -> fail("no line expected")));

        assertThat(outcome.thrown())
                .isInstanceOf(ClientException.InterruptedException.class)
                .hasCause(e);
        assertThat(outcome.interruptFlagSet()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("transportFailures")
    public void transportFailureWhileReadingResponseBody(IOException e) throws Exception {
        respondWith(200, failingBody(e));

        var outcome = runIsolated(() -> subject.handle(request, line -> fail("no line expected")));

        assertThat(outcome.thrown())
                .isInstanceOf(ClientException.TransportException.class)
                .hasCause(e);
        assertThat(outcome.interruptFlagSet()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("interruptions")
    public void interruptionWhileReadingErrorBodyKeepsStatusCodeAndInterruptStatus(IOException e) throws Exception {
        respondWith(500, failingBody(e));

        var outcome = runIsolated(() -> subject.handle(request, line -> fail("consumer must not be called")));

        assertThat(outcome.thrown())
                .isInstanceOfSatisfying(
                        ClientException.HttpException.HttpServerException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(500));
        assertThat(outcome.interruptFlagSet()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("transportFailures")
    public void transportFailureWhileReadingErrorBodyKeepsStatusCode(IOException e) throws Exception {
        respondWith(500, failingBody(e));

        var outcome = runIsolated(() -> subject.handle(request, line -> fail("consumer must not be called")));

        assertThat(outcome.thrown())
                .isInstanceOfSatisfying(
                        ClientException.HttpException.HttpServerException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(500));
        assertThat(outcome.interruptFlagSet()).isFalse();
    }
}
