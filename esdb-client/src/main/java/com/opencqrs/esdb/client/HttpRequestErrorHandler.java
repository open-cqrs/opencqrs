/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedByInterruptException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class to map errors from {@link HttpClient#send(HttpRequest, HttpResponse.BodyHandler)} to
 * {@link ClientException}.
 *
 * @see #handle(HttpRequest)
 * @see #handle(HttpRequest, Consumer)
 */
final class HttpRequestErrorHandler {

    private final HttpClient httpClient;

    HttpRequestErrorHandler(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * {@linkplain HttpClient#send(HttpRequest, HttpResponse.BodyHandler) Sends} the given {@link HttpRequest},
     * buffering the response body into a {@link String} and mapping exceptions and HTTP status codes to
     * {@link ClientException}, if necessary.
     *
     * <p>The response body is consumed on the calling thread and decoded using the character set specified in the
     * {@code Content-Type} response header, falling back to {@code UTF-8}.
     *
     * @param request the HTTP request to send
     * @return the response body, if the {@link HttpResponse#statusCode()} is {@code 200}
     * @throws ClientException.TransportException in case of a communication error
     * @throws ClientException.InterruptedException in case of thread interruption
     * @throws ClientException.HttpException.HttpClientException in case of a {@code 4xx} HTTP status code
     * @throws ClientException.HttpException.HttpServerException in case of a {@code 5xx} HTTP status code
     * @throws ClientException.HttpException in case of an unexpected HTTP status code
     */
    String handle(HttpRequest request) throws ClientException {
        var response = makeRequest(request, HttpResponse.BodyHandlers.ofString());
        var statusCode = response.statusCode();
        if (statusCode != 200) {
            throw mapErrorStatus(statusCode, response.body());
        }
        return response.body();
    }

    /**
     * {@linkplain HttpClient#send(HttpRequest, HttpResponse.BodyHandler) Sends} the given {@link HttpRequest}, lazily
     * reading the response body {@linkplain HttpResponse.BodyHandlers#ofLines() line by line} and passing each line to
     * the given handler, mapping exceptions and HTTP status codes to {@link ClientException}, if necessary.
     *
     * <p>Lines are read and passed to the handler on the calling thread. The response body is decoded using the
     * character set specified in the {@code Content-Type} response header, falling back to {@code UTF-8}. The
     * underlying response stream is always closed before this method returns.
     *
     * <p>The handler is only invoked, if the {@link HttpResponse#statusCode()} is {@code 200}, otherwise an appropriate
     * {@link ClientException} carrying the reconstructed error body is thrown. Any exception thrown from the handler
     * (including {@link ClientException}s and {@link UncheckedIOException}s) is propagated to the caller unchanged.
     * Only {@link IOException}s raised while reading (or closing) the response body itself are mapped to
     * {@link ClientException.InterruptedException} (if caused by thread interruption) or
     * {@link ClientException.TransportException} otherwise.
     *
     * @param request the HTTP request to send
     * @param lineHandler the consumer responsible for handling each line of a {@code 200} response
     * @throws ClientException.TransportException in case of a communication error
     * @throws ClientException.InterruptedException in case of thread interruption
     * @throws ClientException.HttpException.HttpClientException in case of a {@code 4xx} HTTP status code
     * @throws ClientException.HttpException.HttpServerException in case of a {@code 5xx} HTTP status code
     * @throws ClientException.HttpException in case of an unexpected HTTP status code
     * @throws ClientException (or subclasses) if thrown by the {@code lineHandler}
     */
    void handle(HttpRequest request, Consumer<String> lineHandler) throws ClientException {
        HttpResponse<Stream<String>> response = makeRequest(request, HttpResponse.BodyHandlers.ofLines());
        try (Stream<String> lines = response.body()) {
            var statusCode = response.statusCode();
            if (statusCode != 200) {
                throw mapErrorStatus(statusCode, readErrorBody(lines));
            }

            lines.forEach(line -> {
                try {
                    lineHandler.accept(line);
                } catch (UncheckedIOException e) {
                    throw new LineHandlerIOException(e);
                }
            });
        } catch (LineHandlerIOException e) {
            throw e.handlerException;
        } catch (UncheckedIOException e) {
            IOException cause =
                    Objects.requireNonNull(e.getCause(), "UncheckedIOException must have IOException cause");
            if (isInterruption(cause)) {
                Thread.currentThread().interrupt();
                throw new ClientException.InterruptedException("HTTP response consumption interrupted", cause);
            }
            throw new ClientException.TransportException("HTTP response consumption failed", cause);
        }
    }

    /**
     * Reads the error response body defensively, so that a failure while reading it does not hide the HTTP status code,
     * which determines the {@link ClientException} to be thrown. The thread's interrupt status is restored, if the read
     * was interrupted.
     */
    private static String readErrorBody(Stream<String> lines) {
        try {
            return lines.collect(Collectors.joining("\n"));
        } catch (UncheckedIOException e) {
            if (e.getCause() != null && isInterruption(e.getCause())) {
                Thread.currentThread().interrupt();
            }
            return "<unavailable: " + e.getCause() + ">";
        }
    }

    /**
     * Shields an {@link UncheckedIOException} thrown by the line handler from being mapped as a failure to read the
     * response body, so that it can be propagated to the caller unchanged.
     */
    private static final class LineHandlerIOException extends RuntimeException {

        private final UncheckedIOException handlerException;

        LineHandlerIOException(UncheckedIOException handlerException) {
            super(handlerException);
            this.handlerException = handlerException;
        }
    }

    private <T> HttpResponse<T> makeRequest(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
            throws ClientException {
        try {
            return httpClient.send(request, bodyHandler);
        } catch (IOException e) {
            throw switch (e.getCause()) {
                case null -> new ClientException.TransportException("failed to send request with unknown cause", e);
                default -> new ClientException.TransportException("failed to send request", e.getCause());
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClientException.InterruptedException("request interrupted", e);
        }
    }

    private ClientException.HttpException mapErrorStatus(int statusCode, String body) throws ClientException {
        if (statusCode >= 400 && statusCode < 500) {
            return new ClientException.HttpException.HttpClientException(
                    "HTTP client request error: " + body, statusCode);
        } else if (statusCode >= 500 && statusCode <= 599) {
            return new ClientException.HttpException.HttpServerException(
                    "HTTP server request error: " + body, statusCode);
        } else {
            return new ClientException.HttpException("unexpected HTTP status error: " + body, statusCode);
        }
    }

    /**
     * Determines whether the given {@link IOException} raised while consuming the response body represents a genuine
     * thread interruption. Blocking reads on the {@link HttpClient}'s response stream surface an interrupt as an
     * {@link IOException} wrapping an {@link InterruptedException} (the JDK re-sets the interrupt flag); an interrupt
     * during an interruptible channel operation may instead manifest as a {@link ClosedByInterruptException}.
     *
     * <p>Transport conditions such as timeouts or broken connections are deliberately <strong>not</strong> treated as
     * interruptions &mdash; the caller surfaces them as {@link ClientException.TransportException} (retryable). In
     * particular {@link java.io.InterruptedIOException} (whose subclass {@link java.net.SocketTimeoutException} denotes
     * a timeout, not a thread interrupt) is intentionally not matched here.
     */
    private static boolean isInterruption(IOException e) {
        if (e instanceof ClosedByInterruptException) {
            return true;
        }
        for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
            if (t instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }
}
