/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Flow;
import java.util.function.Function;

/**
 * Helper class to map errors from {@link HttpClient#send(HttpRequest, HttpResponse.BodyHandler)} to
 * {@link ClientException}.
 *
 * @see #handle(HttpRequest, Function)
 */
final class HttpRequestErrorHandler {

    private final HttpClient httpClient;

    HttpRequestErrorHandler(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * {@linkplain HttpClient#send(HttpRequest, HttpResponse.BodyHandler) Sends} the given {@link HttpRequest} mapping
     * exceptions and HTTP status codes to {@link ClientException}, if necessary.
     *
     * <p>This method expects a {@link java.net.http.HttpResponse.BodySubscriber} to handle the
     * {@link HttpResponse#body()} consumption. For long-running HTTP requests this may mean that it will expose
     * intermediate content chunks to other handlers, for instance using
     * {@linkplain java.net.http.HttpResponse.BodySubscribers#fromLineSubscriber(Flow.Subscriber) line-based chunking}.
     * The {@link java.net.http.HttpResponse.BodySubscriber} is created lazily using the given {@link Function}, in
     * order to be able to interpret {@link HttpHeaders} before starting the consumption process, e.g. for proper
     * encoding.
     *
     * <p>The {@link java.net.http.HttpResponse.BodySubscriber} will only be called, if the
     * {@link HttpResponse.ResponseInfo#statusCode()} is {@code 200}, otherwise an appropriate {@link ClientException}
     * is thrown. {@link ClientException}s thrown from the {@link java.net.http.HttpResponse.BodySubscriber} will be
     * propagated to the caller, any other exception will be wrapped appropriately.
     *
     * <p><strong>{@link java.net.http.HttpResponse.BodySubscriber}s should only throw {@link ClientException}s or
     * subclasses, as any other unchecked exceptions will be wrapped as
     * {@link ClientException.TransportException}.</strong>
     *
     * @param request the HTTP request to send
     * @param bodySubscriber the {@link java.net.http.HttpResponse.BodySubscriber} responsible for handling the HTTP
     *     response content
     * @return the extracted {@link HttpResponse#body()} from the HTTP request sent, may be {@code null} if the
     *     {@link java.net.http.HttpResponse.BodySubscriber} exposed its content otherwise, e.g. using a
     *     {@link Flow.Subscriber} and line-based chaunking
     * @param <T> generic {@link HttpResponse} type
     * @throws ClientException.TransportException in case of a communication error or any non {@link ClientException}
     *     thrown from the {@link java.net.http.HttpResponse.BodySubscriber}
     * @throws ClientException.InterruptedException in case of thread interruption caused by
     *     {@link InterruptedException}
     * @throws ClientException.HttpException.HttpClientException in case of a @{code 4xx} HTTP status code
     * @throws ClientException.HttpException.HttpServerException in case of a @{code 5xx} HTTP status code
     * @throws ClientException.HttpException in case of an unexpected HTTP status code
     * @throws ClientException (or subclasses) if thrown by the {@link java.net.http.HttpResponse.BodySubscriber}
     */
    <T> T handle(HttpRequest request, Function<HttpHeaders, HttpResponse.BodySubscriber<T>> bodySubscriber)
            throws ClientException {
        HttpResponse<ResponseHolder<T>> httpResponse;
        try {
            httpResponse = httpClient.send(request, responseInfo -> switch (responseInfo.statusCode()) {
                case 200 ->
                    HttpResponse.BodySubscribers.mapping(
                            bodySubscriber.apply(responseInfo.headers()), ResponseHolder.Success::new);
                default ->
                    HttpResponse.BodySubscribers.mapping(
                            HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(responseInfo.headers())),
                            s -> new ResponseHolder.Failure<>(responseInfo.statusCode(), s));
            });
        } catch (IOException e) {
            switch (e.getCause()) {
                case ClientException clientException -> throw clientException;
                default -> throw new ClientException.TransportException("failed to send request", e.getCause());
            }
        } catch (InterruptedException e) {
            throw new ClientException.InterruptedException("request interrupted", e);
        }

        switch (httpResponse.body()) {
            case ResponseHolder.Success<T> success -> {
                return success.result();
            }
            case ResponseHolder.Failure<T> failure -> {
                var statusCode = failure.statusCode();

                if (statusCode >= 400 && statusCode < 500) {
                    throw new ClientException.HttpException.HttpClientException(
                            "HTTP client request error: " + httpResponse.body(), statusCode);
                } else if (statusCode >= 500 && statusCode <= 599) {
                    throw new ClientException.HttpException.HttpServerException(
                            "HTTP server request error: " + httpResponse.body(), statusCode);
                } else {
                    throw new ClientException.HttpException(
                            "unexpected HTTP status error: " + httpResponse.body(), statusCode);
                }
            }
        }
    }

    sealed interface ResponseHolder<T> {
        record Success<T>(T result) implements ResponseHolder<T> {}

        record Failure<T>(int statusCode, String error) implements ResponseHolder<T> {}
    }
}
