/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "server.servlet.context-path=/",
        })
public class HttpRequestEventQueryErrorHandlerTest {

    static final String RESPONSE_MESSAGE = "test message";

    @TestConfiguration
    @RestController
    public static class MockServerConfiguration {

        @PostMapping("/api/status/{status}")
        public ResponseEntity<String> status(@PathVariable("status") int status) {
            return ResponseEntity.status(status).body(RESPONSE_MESSAGE);
        }

        @GetMapping("/api/charset/{charset}")
        public ResponseEntity<String> charset(@PathVariable("charset") String charSet) {
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, Charset.forName(charSet)))
                    .body(RESPONSE_MESSAGE);
        }
    }

    @LocalServerPort
    private Integer port;

    private HttpRequestErrorHandler subject = new HttpRequestErrorHandler(HttpClient.newHttpClient());

    private HttpRequest buildRequest(int status) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/status/" + status))
                .POST(HttpRequest.BodyPublishers.ofString("test"))
                .build();
    }

    @ParameterizedTest
    @MethodSource("successfulStatusCodes")
    public void handlesSuccessfulExecution(int statusCode) {
        String response = subject.handle(
                buildRequest(statusCode),
                httpHeaders -> HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(httpHeaders)));

        assertThat(response).isEqualTo(RESPONSE_MESSAGE);
    }

    public static Stream<Arguments> successfulStatusCodes() {
        return IntStream.rangeClosed(200, 200).mapToObj(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("clientErrorStatusCodes")
    public void handlesClientErrors(int statusCode) {
        assertThatThrownBy(() -> subject.handle(
                        buildRequest(statusCode),
                        httpHeaders -> HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(httpHeaders))))
                .isInstanceOfSatisfying(
                        ClientException.HttpException.HttpClientException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(statusCode))
                .hasMessageContaining(RESPONSE_MESSAGE);
    }

    public static Stream<Arguments> clientErrorStatusCodes() {
        return IntStream.rangeClosed(400, 499).mapToObj(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("serverErrorStatusCodes")
    public void handlesServerErrors(int statusCode) {
        assertThatThrownBy(() -> subject.handle(
                        buildRequest(statusCode),
                        httpHeaders -> HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(httpHeaders))))
                .isInstanceOfSatisfying(
                        ClientException.HttpException.HttpServerException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(statusCode))
                .hasMessageContaining(RESPONSE_MESSAGE);
    }

    public static Stream<Arguments> serverErrorStatusCodes() {
        return IntStream.rangeClosed(500, 599).mapToObj(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("serverUnknownStatusCodes")
    public void handlesUnknownErrors(int statusCode) {
        assertThatThrownBy(() -> subject.handle(
                        buildRequest(statusCode),
                        httpHeaders -> HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(httpHeaders))))
                .isInstanceOfSatisfying(
                        ClientException.HttpException.HttpException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(statusCode))
                .hasMessageContaining(RESPONSE_MESSAGE);
    }

    public static Stream<Arguments> serverUnknownStatusCodes() {
        return IntStream.concat(IntStream.rangeClosed(201, 203), IntStream.rangeClosed(206, 299))
                .mapToObj(Arguments::of);
    }

    @Test
    public void unwrapsClientExceptionFromBodySubscriber() {
        var exception = mock(ClientException.class);
        assertThatThrownBy(() -> subject.handle(
                        buildRequest(200),
                        httpHeaders -> HttpResponse.BodySubscribers.mapping(
                                HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(httpHeaders)), s -> {
                                    doReturn(s).when(exception).getMessage();
                                    throw exception;
                                })))
                .isSameAs(exception)
                .hasMessage(RESPONSE_MESSAGE);
    }

    @Test
    public void wrapsRuntimeExceptionsFromBodySubscriber() {
        var exception = new RuntimeException("test");
        assertThatThrownBy(() -> subject.handle(
                        buildRequest(200),
                        httpHeaders -> HttpResponse.BodySubscribers.mapping(
                                HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(httpHeaders)), s -> {
                                    throw exception;
                                })))
                .isInstanceOf(ClientException.TransportException.class)
                .hasCause(exception);
    }

    @Test
    public void wrapsErrorsFromBodySubscriber() {
        var error = new Error("test");
        assertThatThrownBy(() -> subject.handle(
                        buildRequest(200),
                        httpHeaders -> HttpResponse.BodySubscribers.mapping(
                                HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(httpHeaders)), s -> {
                                    throw error;
                                })))
                .isInstanceOf(ClientException.TransportException.class)
                .hasCause(error);
    }

    @Test
    public void handlesIOException() {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://no-such-host:" + port + "/api/status/" + 200))
                .POST(HttpRequest.BodyPublishers.ofString("test"))
                .build();

        assertThatThrownBy(() -> subject.handle(
                        request,
                        httpHeaders -> HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(httpHeaders))))
                .isInstanceOf(ClientException.TransportException.class)
                .hasCauseInstanceOf(ConnectException.class);
    }

    @Test
    public void handlesInterruptedException() throws InterruptedException, BrokenBarrierException {
        CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
            Thread.currentThread().interrupt();
            subject.handle(
                    buildRequest(200),
                    httpHeaders -> HttpResponse.BodySubscribers.ofString(Util.fromHttpHeaders(httpHeaders)));
            fail("should not be reached");
        });

        await().untilAsserted(() -> {
            assertThat(completableFuture.isCompletedExceptionally()).isTrue();
            assertThat(completableFuture.exceptionNow())
                    .isInstanceOf(ClientException.InterruptedException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
        });
    }

    @Test
    public void handlesResponseCharset() {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/charset/" + StandardCharsets.UTF_16.name()))
                .GET()
                .build();
        AtomicReference<Charset> charset = new AtomicReference<>();

        String response = subject.handle(request, httpHeaders -> {
            var responseCharset = Util.fromHttpHeaders(httpHeaders);
            charset.compareAndSet(null, responseCharset);
            return HttpResponse.BodySubscribers.ofString(responseCharset);
        });

        assertThat(response).isEqualTo(RESPONSE_MESSAGE);
        assertThat(charset).hasValue(StandardCharsets.UTF_16);
    }
}
