/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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
public class HttpRequestErrorHandlerTest {

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

        final CyclicBarrier slowFinishBarrier = new CyclicBarrier(2);

        /**
         * Commits the response (status + headers) and pushes a single line to the client via
         * {@link HttpServletResponse#flushBuffer()} — deterministic, without relying on servlet buffer sizes — then
         * holds the connection open so the client consumes {@code line1} and blocks on the next read. Used to exercise
         * interruption during lazy body consumption.
         */
        @GetMapping("/api/slow")
        public void slow(HttpServletResponse response)
                throws IOException, InterruptedException, BrokenBarrierException {
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getOutputStream().write("line1\n".getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
            slowFinishBarrier.await();
        }

        final CyclicBarrier brokenFinishBarrier = new CyclicBarrier(2);

        /**
         * Commits the response and pushes {@code line1}, then (on barrier release) aborts the already-committed
         * response by throwing — so the container closes the connection mid-stream and the client's blocked read fails
         * with an {@link IOException} (premature end of stream), which {@link HttpRequestErrorHandler} maps to
         * {@link ClientException.TransportException} rather than an interruption. Used to exercise a broken connection
         * during lazy body consumption.
         */
        @GetMapping("/api/broken")
        public void broken(HttpServletResponse response)
                throws IOException, InterruptedException, BrokenBarrierException {
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getOutputStream().write("line1\n".getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
            brokenFinishBarrier.await();
            throw new IOException("aborting the committed response to break the connection");
        }
    }

    @LocalServerPort
    private Integer port;

    private final HttpRequestErrorHandler subject = new HttpRequestErrorHandler(HttpClient.newHttpClient());

    private HttpRequest buildRequest(int status) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/status/" + status))
                .POST(HttpRequest.BodyPublishers.ofString("test"))
                .build();
    }

    @Import(MockServerConfiguration.class)
    abstract class Base {

        private final BiFunction<HttpRequest, Boolean, String> responseSupplier;

        Base(BiFunction<HttpRequest, Boolean, String> responseSupplier) {
            this.responseSupplier = responseSupplier;
        }

        @ParameterizedTest
        @MethodSource("successfulStatusCodes")
        public void handlesSuccessfulExecution(int statusCode) {
            String response = responseSupplier.apply(buildRequest(statusCode), true);

            assertThat(response).isEqualTo(RESPONSE_MESSAGE);
        }

        public static Stream<Arguments> successfulStatusCodes() {
            return IntStream.rangeClosed(200, 200).mapToObj(Arguments::of);
        }

        @ParameterizedTest
        @MethodSource("clientErrorStatusCodes")
        public void handlesClientErrors(int statusCode) {
            assertThatThrownBy(() -> responseSupplier.apply(buildRequest(statusCode), false))
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
            assertThatThrownBy(() -> responseSupplier.apply(buildRequest(statusCode), false))
                    .isInstanceOfSatisfying(
                            ClientException.HttpException.HttpServerException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(statusCode))
                    .hasMessageContaining(RESPONSE_MESSAGE);
        }

        public static Stream<Arguments> serverErrorStatusCodes() {
            return IntStream.rangeClosed(500, 599).mapToObj(Arguments::of);
        }

        @ParameterizedTest
        @MethodSource("unexpectedStatusCodes")
        public void handlesUnexpectedStatusCodes(int statusCode) {
            assertThatThrownBy(() -> responseSupplier.apply(buildRequest(statusCode), false))
                    .isInstanceOfSatisfying(ClientException.HttpException.class, e -> assertThat(e.getStatusCode())
                            .isEqualTo(statusCode))
                    .hasMessageContaining(RESPONSE_MESSAGE);
        }

        public static Stream<Arguments> unexpectedStatusCodes() {
            return IntStream.concat(IntStream.rangeClosed(201, 203), IntStream.rangeClosed(206, 299))
                    .mapToObj(Arguments::of);
        }

        @Test
        public void handlesResponseCharset() {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://localhost:" + port + "/api/charset/" + StandardCharsets.UTF_16.name()))
                    .GET()
                    .build();

            String response = responseSupplier.apply(request, true);

            assertThat(response).isEqualTo(RESPONSE_MESSAGE);
        }

        @Test
        public void handlesIOException() {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://no-such-host:" + port + "/api/status/200"))
                    .POST(HttpRequest.BodyPublishers.ofString("test"))
                    .build();

            assertThatThrownBy(() -> responseSupplier.apply(request, false))
                    .isInstanceOf(ClientException.TransportException.class)
                    .hasCauseInstanceOf(ConnectException.class);
        }

        @Test
        public void handlesInterruptedException() {
            CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                Thread.currentThread().interrupt();
                responseSupplier.apply(buildRequest(200), false);
                fail("should not be reached");
            });

            await().untilAsserted(() -> {
                assertThat(completableFuture.isCompletedExceptionally()).isTrue();
                assertThat(completableFuture.exceptionNow())
                        .isInstanceOf(ClientException.InterruptedException.class)
                        .hasCauseInstanceOf(InterruptedException.class);
            });
        }
    }

    @Nested
    class SynchronousResponse extends Base {

        SynchronousResponse() {
            super((request, ignored) -> subject.handle(request));
        }
    }

    @Nested
    class StreamingResponse extends Base {

        StreamingResponse() {
            super((request, success) -> {
                var response = new StringBuffer();
                subject.handle(request, stream -> {
                    if (success) {
                        stream.forEach(response::append);
                    } else {
                        fail("consumer must not be called in error case");
                    }
                });
                return response.toString();
            });
        }

        @ParameterizedTest
        @ValueSource(
                classes = {
                    ClientException.class,
                    IllegalStateException.class,
                })
        public void propagatesRuntimeExceptionsFromConsumer(Class<? extends RuntimeException> exceptionClass) {
            var exception = mock(exceptionClass);

            assertThatThrownBy(() -> subject.handle(buildRequest(200), stream -> {
                        throw exception;
                    }))
                    .isSameAs(exception);
        }

        @Test
        @Timeout(10)
        public void handlesInterruptionWhileConsumingStream(@Autowired MockServerConfiguration config)
                throws InterruptedException, BrokenBarrierException {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/slow"))
                    .GET()
                    .build();

            var firstLineConsumed = new CountDownLatch(1);
            var caught = new AtomicReference<Throwable>();
            var interruptFlagSet = new AtomicBoolean(false);

            Thread consumer = Thread.ofVirtual().start(() -> {
                try {
                    // consumes "line1", then blocks on the next read, since no more content is sent
                    subject.handle(request, stream -> stream.forEach(line -> firstLineConsumed.countDown()));
                } catch (Throwable t) {
                    interruptFlagSet.set(Thread.currentThread().isInterrupted());
                    caught.set(t);
                }
            });

            firstLineConsumed.await();
            consumer.interrupt();
            consumer.join();

            // release the blocking handler, to avoid unnecessary long graceful shutdown after the test execution
            config.slowFinishBarrier.await();

            assertThat(caught.get()).isInstanceOf(ClientException.InterruptedException.class);
            assertThat(interruptFlagSet).isTrue();
        }

        @Test
        @Timeout(10)
        public void handlesBrokenConnectionWhileConsumingStream(@Autowired MockServerConfiguration config)
                throws InterruptedException, BrokenBarrierException {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/broken"))
                    .GET()
                    .build();

            var firstLineConsumed = new CountDownLatch(1);
            var caught = new AtomicReference<Throwable>();

            Thread consumer = Thread.ofVirtual().start(() -> {
                try {
                    // consumes "line1", then blocks on the next read, since the promised content never arrives
                    subject.handle(request, stream -> stream.forEach(line -> firstLineConsumed.countDown()));
                } catch (Throwable t) {
                    caught.set(t);
                }
            });

            firstLineConsumed.await();
            // release the handler → it returns with the body incomplete → the connection breaks mid-stream
            config.brokenFinishBarrier.await();
            consumer.join();

            assertThat(caught.get()).isInstanceOf(ClientException.TransportException.class);
        }
    }
}
