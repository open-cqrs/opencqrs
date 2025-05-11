/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public class EsdbClientIntegrationTest {

    private static final String TEST_SOURCE = "tag://test-execution";

    @Autowired
    private Client client;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private Marshaller marshaller;

    private final Map<Class<? extends Option>, ? extends Option> options = Map.of(
            Option.Recursive.class, new Option.Recursive(),
            Option.Order.class, new Option.Order(Option.Order.Type.CHRONOLOGICAL),
            Option.LowerBoundInclusive.class, new Option.LowerBoundInclusive("42"),
            Option.LowerBoundExclusive.class, new Option.LowerBoundExclusive("43"),
            Option.UpperBoundInclusive.class, new Option.UpperBoundInclusive("548"),
            Option.UpperBoundExclusive.class, new Option.UpperBoundExclusive("549"),
            Option.FromLatestEvent.class,
                    new Option.FromLatestEvent(
                            "subject", "type", Option.FromLatestEvent.IfEventIsMissing.READ_NOTHING));

    @Nested
    @DisplayName("/api/v1/verify-api-token")
    public class VerifyApiToken {

        @Test
        public void authenticate() throws ClientException {
            assertThatCode(() -> client.authenticate()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("/api/v1/health")
    public class HealthCheck {

        @Test
        public void healthStatusRetrieved() {
            assertThat(client.health()).satisfies(health -> {
                assertThat(health.status()).isNotNull().isNotEqualTo(Health.Status.fail);
                assertThat(health.checks()).isNotEmpty();
            });
        }
    }

    @Nested
    @DisplayName("/api/v1/write-events")
    public class WriteEvents {

        @Test
        public void badRequestDetected() {
            assertThatThrownBy(() -> client.write(
                            List.of(new EventCandidate(
                                    TEST_SOURCE, "in valid subject", "com.opencqrs.books-added.v1", Map.of())),
                            List.of()))
                    .isInstanceOfSatisfying(
                            ClientException.HttpException.HttpClientException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(400));
        }

        @Test
        public void singleEventPublishedForPristineSubject() {
            String subject = randomSubject();

            Map<String, Object> data = objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class);

            List<Event> published = client.write(
                    List.of(new EventCandidate(TEST_SOURCE, subject, "com.opencqrs.books-added.v1", data)),
                    List.of(new Precondition.SubjectIsPristine(subject)));

            assertThat(published).singleElement().satisfies(e -> {
                assertThat(e.source()).isEqualTo(TEST_SOURCE);
                assertThat(e.subject()).isEqualTo(subject);
                assertThat(e.type()).isEqualTo("com.opencqrs.books-added.v1");
                assertThat(e.specVersion()).isEqualTo("1.0");
                assertThat(e.dataContentType()).isEqualTo("application/json");
                assertThat(e.id()).isNotBlank();
                assertThat(e.time()).isBeforeOrEqualTo(Instant.now());
                assertThat(e.hash()).isNotBlank();
                assertThat(e.predecessorHash()).isNotBlank();
                assertThat(e.data()).isEqualTo(data);
            });
        }

        @Test
        public void singleEventNotPublishedForImpureSubject() {
            String subject = randomSubject();

            var eventCandidate = new EventCandidate(
                    TEST_SOURCE,
                    subject,
                    "com.opencqrs.books-added.v1",
                    objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class));

            client.write(List.of(eventCandidate), List.of(new Precondition.SubjectIsPristine(subject)));

            assertThatThrownBy(() ->
                            client.write(List.of(eventCandidate), List.of(new Precondition.SubjectIsPristine(subject))))
                    .isInstanceOfSatisfying(ClientException.HttpException.HttpClientException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(409);
                        assertThat(e.getMessage()).contains("precondition failed");
                    });

            assertThat(client.read(subject, Set.of()))
                    .as("no more events written")
                    .hasSize(1);
        }

        @Test
        public void singleEventPublishedForExistingSubject() {
            String subject = randomSubject();

            List<Event> added = client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            subject,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class))),
                    List.of(new Precondition.SubjectIsPristine(subject)));

            Map<String, Object> data = objectMapper.convertValue(new BookRemovedEvent("pages missing"), Map.class);

            List<Event> published = client.write(
                    List.of(new EventCandidate(TEST_SOURCE, subject, "com.opencqrs.books-removed.v1", data)),
                    List.of(new Precondition.SubjectIsOnEventId(
                            subject, added.getFirst().id())));

            assertThat(published).singleElement().satisfies(e -> {
                assertThat(e.source()).isEqualTo(TEST_SOURCE);
                assertThat(e.subject()).isEqualTo(subject);
                assertThat(e.type()).isEqualTo("com.opencqrs.books-removed.v1");
                assertThat(e.specVersion()).isEqualTo("1.0");
                assertThat(e.dataContentType()).isEqualTo("application/json");
                assertThat(e.id()).isNotBlank();
                assertThat(e.time()).isBeforeOrEqualTo(Instant.now());
                assertThat(e.hash()).isNotBlank();
                assertThat(e.predecessorHash()).isNotBlank();
                assertThat(e.data()).isEqualTo(data);
            });
        }

        @Test
        public void singleEventNotPublishedForConflictingSubject() {
            String subject = randomSubject();

            List<Event> added = client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            subject,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class))),
                    List.of(new Precondition.SubjectIsPristine(subject)));

            client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            subject,
                            "com.opencqrs.books-removed.v1",
                            objectMapper.convertValue(new BookRemovedEvent("pages missing"), Map.class))),
                    List.of());

            assertThatThrownBy(() -> client.write(
                            List.of(new EventCandidate(
                                    TEST_SOURCE,
                                    subject,
                                    "com.opencqrs.books-removed.v1",
                                    objectMapper.convertValue(new BookRemovedEvent("pages missing"), Map.class))),
                            List.of(new Precondition.SubjectIsOnEventId(
                                    subject, added.getFirst().id()))))
                    .isInstanceOfSatisfying(ClientException.HttpException.HttpClientException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(409);
                        assertThat(e.getMessage()).contains("precondition failed");
                    });

            assertThat(client.read(subject, Set.of()))
                    .as("no additional events written")
                    .hasSize(2);
        }

        @Test
        public void multipleEventsPublishedSuccessfully() {
            String subject = randomSubject();

            Map<String, Object> data1 = objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class);
            Map<String, Object> data2 = objectMapper.convertValue(new BookRemovedEvent("pages"), Map.class);

            List<Event> published = client.write(
                    List.of(
                            new EventCandidate(TEST_SOURCE, subject, "com.opencqrs.books-added.v1", data1),
                            new EventCandidate(TEST_SOURCE, subject, "com.opencqrs.books-removed.v1", data2)),
                    List.of(new Precondition.SubjectIsPristine(subject)));

            assertThat(published)
                    .hasSize(2)
                    .allSatisfy(e -> {
                        assertThat(e.source()).isEqualTo(TEST_SOURCE);
                        assertThat(e.subject()).isEqualTo(subject);
                        assertThat(e.type()).startsWith("com.opencqrs.books-").endsWith("v1");
                        assertThat(e.specVersion()).isEqualTo("1.0");
                        assertThat(e.dataContentType()).isEqualTo("application/json");
                        assertThat(e.id()).isNotBlank();
                        assertThat(e.time()).isBeforeOrEqualTo(Instant.now());
                        assertThat(e.hash()).isNotBlank();
                        assertThat(e.predecessorHash()).isNotBlank();
                    })
                    .anySatisfy(e -> {
                        assertThat(e.data()).isEqualTo(data1);
                    })
                    .anySatisfy(e -> {
                        assertThat(e.data()).isEqualTo(data2);
                    });
        }
    }

    @Nested
    @DisplayName("/api/v1/observe-events")
    public class ObserveEvents {

        @Test
        public void badRequestDetected() {
            assertThatThrownBy(() -> client.observe("/", Set.of(new Option.LowerBoundExclusive("")), event -> {}))
                    .isInstanceOfSatisfying(
                            ClientException.HttpException.HttpClientException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(400));
        }

        @ParameterizedTest
        @ValueSource(
                classes = {
                    Option.Order.class,
                    Option.UpperBoundInclusive.class,
                    Option.UpperBoundExclusive.class,
                })
        @Timeout(5)
        public void unsupportedRequestOptionsChecked(Class<? extends Option> unsupported) {
            assertThatThrownBy(() -> client.observe("/", Set.of(options.get(unsupported)), event -> {}))
                    .isInstanceOf(ClientException.InvalidUsageException.class);
        }

        @Test
        public void unsupportedLowerBoundOptionCombinationDetected() {
            assertThatThrownBy(() -> client.observe(
                            "/",
                            Set.of(
                                    options.get(Option.LowerBoundInclusive.class),
                                    options.get(Option.LowerBoundExclusive.class)),
                            event -> {}))
                    .isInstanceOf(ClientException.InvalidUsageException.class);
        }

        @Test
        public void heartBeatsProperlyIgnored() {
            var heartBeatConsumed = new AtomicInteger(0);
            doAnswer((Answer<Marshaller.ResponseElement>) invocationOnMock -> {
                        Marshaller.ResponseElement element =
                                (Marshaller.ResponseElement) invocationOnMock.callRealMethod();
                        if (element instanceof Marshaller.ResponseElement.Heartbeat) {
                            heartBeatConsumed.incrementAndGet();
                        }
                        return element;
                    })
                    .when(marshaller)
                    .fromReadOrObserveResponseLine(anyString());

            var observedEvents = new ConcurrentLinkedDeque<Event>();

            CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(
                    () -> client.observe("/", Set.of(new Option.Recursive()), observedEvents::add));

            String subject = randomSubject();
            client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            subject,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class))),
                    List.of(new Precondition.SubjectIsPristine(subject)));

            await().untilAsserted(() -> {
                assertThat(observedEvents).isNotEmpty();
                assertThat(heartBeatConsumed.get()).isGreaterThan(3);
            });

            assertThat(completableFuture.isDone()).isFalse();
        }

        @Test
        public void eventsProperlyObservedRecursivelyBlocking() {
            var observedEvents = new ConcurrentLinkedDeque<Event>();

            CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(
                    () -> client.observe("/", Set.of(new Option.Recursive()), observedEvents::add));

            for (int i = 0; i < 5; i++) {
                String subject = randomSubject();
                client.write(
                        List.of(new EventCandidate(
                                TEST_SOURCE,
                                subject,
                                "com.opencqrs.books-added.v1",
                                objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class))),
                        List.of(new Precondition.SubjectIsPristine(subject)));

                await().untilAsserted(() -> assertThat(observedEvents.poll())
                        .isNotNull()
                        .satisfies(e -> {
                            assertThat(e.source()).isEqualTo(TEST_SOURCE);
                            assertThat(e.subject()).isEqualTo(subject);
                            assertThat(e.type()).isEqualTo("com.opencqrs.books-added.v1");
                            assertThat(e.specVersion()).isEqualTo("1.0");
                            assertThat(e.dataContentType()).isEqualTo("application/json");
                            assertThat(e.id()).isNotBlank();
                            assertThat(e.time()).isBeforeOrEqualTo(Instant.now());
                            assertThat(e.predecessorHash()).isNotBlank();
                            assertThat(objectMapper.convertValue(e.data(), BookAddedEvent.class))
                                    .isEqualTo(new BookAddedEvent("JRR Tolkien", "LOTR"));
                        }));
            }

            assertThat(completableFuture.isDone()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(ints = 1000)
        @Timeout(5)
        public void eventsProperlyObservedWithoutHttpThreadPoolStarvation(int threadCount) {
            String subject = randomSubject();

            client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            subject,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class))),
                    List.of(new Precondition.SubjectIsPristine(subject)));

            final CyclicBarrier cyclicBarrier = new CyclicBarrier(threadCount + 1);
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    awaitWrappingCheckedExceptions(cyclicBarrier);

                    client.observe("/", Set.of(new Option.Recursive()), event -> {
                        awaitWrappingCheckedExceptions(cyclicBarrier);
                    });
                });
            }

            awaitWrappingCheckedExceptions(cyclicBarrier);
            awaitWrappingCheckedExceptions(cyclicBarrier);
        }

        private void awaitWrappingCheckedExceptions(CyclicBarrier barrier) {
            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nested
    @DisplayName("/api/v1/read-events")
    public class ReadEvents {

        @Test
        public void badRequestDetected() {
            assertThatThrownBy(() -> client.read("/", Set.of(new Option.LowerBoundInclusive(""))))
                    .isInstanceOfSatisfying(
                            ClientException.HttpException.HttpClientException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(400));
        }

        @Disabled("enable if unsupported options specified")
        @ParameterizedTest
        @ValueSource(classes = {})
        public void unsupportedRequestOptionsChecked(Class<? extends Option> unsupported) {
            assertThatThrownBy(() -> client.read("/", Set.of(options.get(unsupported)), event -> {}))
                    .isInstanceOf(ClientException.InvalidUsageException.class);
        }

        @Test
        public void unsupportedLowerBoundOptionCombinationDetected() {
            assertThatThrownBy(() -> client.read(
                            "/",
                            Set.of(
                                    options.get(Option.LowerBoundInclusive.class),
                                    options.get(Option.LowerBoundExclusive.class)),
                            event -> {}))
                    .isInstanceOf(ClientException.InvalidUsageException.class);
        }

        @Test
        public void unsupportedUpperBoundOptionCombinationDetected() {
            assertThatThrownBy(() -> client.read(
                            "/",
                            Set.of(
                                    options.get(Option.UpperBoundInclusive.class),
                                    options.get(Option.UpperBoundExclusive.class)),
                            event -> {}))
                    .isInstanceOf(ClientException.InvalidUsageException.class);
        }

        @Test
        public void eventsReadRecursivelyConsuming() {
            var consumedEvents = new ArrayList<Event>();

            var subjects = List.of(randomSubject(), randomSubject(), randomSubject());

            subjects.forEach(subject -> client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            subject,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class))),
                    List.of(new Precondition.SubjectIsPristine(subject))));

            client.read("/", Set.of(new Option.Recursive()), consumedEvents::add);

            assertThat(consumedEvents).hasSizeGreaterThanOrEqualTo(subjects.size());
            subjects.forEach(subject -> assertThat(consumedEvents).anySatisfy(e -> {
                assertThat(e.source()).isEqualTo(TEST_SOURCE);
                assertThat(e.subject()).isEqualTo(subject);
                assertThat(e.type()).isEqualTo("com.opencqrs.books-added.v1");
                assertThat(e.specVersion()).isEqualTo("1.0");
                assertThat(e.dataContentType()).isEqualTo("application/json");
                assertThat(e.id()).isNotBlank();
                assertThat(e.time()).isBeforeOrEqualTo(Instant.now());
                assertThat(e.predecessorHash()).isNotBlank();
                assertThat(objectMapper.convertValue(e.data(), BookAddedEvent.class))
                        .isEqualTo(new BookAddedEvent("JRR Tolkien", "LOTR"));
            }));
        }

        @Test
        public void eventsReadNonRecursivelyAccumulating() {
            String subject = randomSubject();

            client.write(
                    List.of(
                            new EventCandidate(
                                    TEST_SOURCE,
                                    subject,
                                    "com.opencqrs.books-added.v1",
                                    objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class)),
                            new EventCandidate(
                                    TEST_SOURCE,
                                    subject + "/pages/42",
                                    "com.opencqrs.books-page-damaged.v1",
                                    objectMapper.convertValue(new BookPageDamagedEvent(), Map.class)),
                            new EventCandidate(
                                    TEST_SOURCE,
                                    subject,
                                    "com.opencqrs.books-removed.v1",
                                    objectMapper.convertValue(new BookRemovedEvent("page damaged"), Map.class))),
                    List.of(new Precondition.SubjectIsPristine(subject)));

            assertThat(client.read(subject, Set.of()))
                    .as("recursive events ignored")
                    .hasSize(2)
                    .anySatisfy(e -> {
                        assertThat(e.source()).isEqualTo(TEST_SOURCE);
                        assertThat(e.subject()).isEqualTo(subject);
                        assertThat(e.type()).isEqualTo("com.opencqrs.books-added.v1");
                        assertThat(e.specVersion()).isEqualTo("1.0");
                        assertThat(e.dataContentType()).isEqualTo("application/json");
                        assertThat(e.id()).isNotBlank();
                        assertThat(e.time()).isBeforeOrEqualTo(Instant.now());
                        assertThat(e.predecessorHash()).isNotBlank();
                        assertThat(objectMapper.convertValue(e.data(), BookAddedEvent.class))
                                .isEqualTo(new BookAddedEvent("JRR Tolkien", "LOTR"));
                    })
                    .anySatisfy(e -> {
                        assertThat(e.source()).isEqualTo(TEST_SOURCE);
                        assertThat(e.subject()).isEqualTo(subject);
                        assertThat(e.type()).isEqualTo("com.opencqrs.books-removed.v1");
                        assertThat(e.specVersion()).isEqualTo("1.0");
                        assertThat(e.dataContentType()).isEqualTo("application/json");
                        assertThat(e.id()).isNotBlank();
                        assertThat(e.time()).isBeforeOrEqualTo(Instant.now());
                        assertThat(e.predecessorHash()).isNotBlank();
                        assertThat(objectMapper.convertValue(e.data(), BookRemovedEvent.class))
                                .isEqualTo(new BookRemovedEvent("page damaged"));
                    });
        }
    }

    private String randomSubject() {
        return "/books/" + UUID.randomUUID();
    }

    @Container
    static GenericContainer<?> esdb = new GenericContainer<>(
                    "docker.io/thenativeweb/eventsourcingdb:" + System.getProperty("esdb.version"))
            .withExposedPorts(3000)
            .withCreateContainerCmdModifier(cmd -> cmd.withCmd(
                    "run",
                    "--api-token",
                    "secret",
                    "--data-directory-temporary",
                    "--http-enabled=true",
                    "--https-enabled=false"));

    @DynamicPropertySource
    static void esdbProperties(DynamicPropertyRegistry registry) {
        registry.add("esdb.server.uri", () -> "http://" + esdb.getHost() + ":" + esdb.getFirstMappedPort());
        registry.add("esdb.server.api-token", () -> "secret");
    }

    public record BookAddedEvent(String author, String title) {}

    public record BookPageDamagedEvent() {}

    public record BookRemovedEvent(String reason) {}
}
