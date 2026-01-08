/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.opencqrs.esdb.client.eventql.EventQueryBuilder;
import com.opencqrs.esdb.client.eventql.EventQueryErrorHandler;
import com.opencqrs.esdb.client.eventql.EventQueryRowHandler;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Testcontainers
public class EsdbClientIntegrationTest {

    private static final String TEST_SOURCE = "tag://test-execution";

    @Autowired
    private EsdbClient client;

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
    @DisplayName("/api/v1/ping")
    public class Ping {

        @Test
        public void ping() throws ClientException {
            assertThatCode(() -> client.ping()).doesNotThrowAnyException();
        }
    }

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

        @Test
        public void singleEventPublishedForPopulatedSubject() {
            String subject = randomSubject();

            client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            subject,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class))),
                    List.of(new Precondition.SubjectIsPristine(subject)));

            Map<String, Object> data = objectMapper.convertValue(new BookRemovedEvent("pages missing"), Map.class);

            List<Event> published = client.write(
                    List.of(new EventCandidate(TEST_SOURCE, subject, "com.opencqrs.books-removed.v1", data)),
                    List.of(new Precondition.SubjectIsPopulated(subject)));

            assertThat(published).singleElement().satisfies(e -> {
                assertThat(e.source()).isEqualTo(TEST_SOURCE);
                assertThat(e.subject()).isEqualTo(subject);
                assertThat(e.type()).isEqualTo("com.opencqrs.books-removed.v1");
                assertThat(e.data()).isEqualTo(data);
            });

            assertThat(client.read(subject, Set.of())).as("both events written").hasSize(2);
        }

        @Test
        public void singleEventNotPublishedForUnpopulatedSubject() {
            String subject = randomSubject();

            var eventCandidate = new EventCandidate(
                    TEST_SOURCE,
                    subject,
                    "com.opencqrs.books-removed.v1",
                    objectMapper.convertValue(new BookRemovedEvent("pages missing"), Map.class));

            assertThatThrownBy(() -> client.write(
                            List.of(eventCandidate), List.of(new Precondition.SubjectIsPopulated(subject))))
                    .isInstanceOfSatisfying(ClientException.HttpException.HttpClientException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(409);
                        assertThat(e.getMessage()).contains("precondition failed");
                    });

            assertThat(client.read(subject, Set.of())).as("no events written").isEmpty();
        }

        @Test
        public void singleEventPublishedWhenEventQlQueryTrue() {
            String subject = randomSubject();

            client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            subject,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class))),
                    List.of(new Precondition.SubjectIsPristine(subject)));

            Map<String, Object> data = objectMapper.convertValue(new BookRemovedEvent("pages missing"), Map.class);

            List<Event> published = client.write(
                    List.of(new EventCandidate(TEST_SOURCE, subject, "com.opencqrs.books-removed.v1", data)),
                    List.of(new Precondition.EventQlQueryIsTrue(EventQueryBuilder.fromEventQlString(
                            "FROM e IN events WHERE e.subject == '" + subject + "' PROJECT INTO COUNT() > 0"))));

            assertThat(published).singleElement().satisfies(e -> {
                assertThat(e.source()).isEqualTo(TEST_SOURCE);
                assertThat(e.subject()).isEqualTo(subject);
                assertThat(e.type()).isEqualTo("com.opencqrs.books-removed.v1");
                assertThat(e.data()).isEqualTo(data);
            });
        }

        @Test
        public void singleEventNotPublishedWhenEventQlQueryFalse() {
            String subject = randomSubject();

            var eventCandidate = new EventCandidate(
                    TEST_SOURCE,
                    subject,
                    "com.opencqrs.books-added.v1",
                    objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class));

            assertThatThrownBy(() -> client.write(
                            List.of(eventCandidate),
                            List.of(new Precondition.EventQlQueryIsTrue(
                                    EventQueryBuilder.fromEventQlString("FROM e IN events WHERE e.subject == '"
                                            + subject + "' PROJECT INTO COUNT() > 0")))))
                    .isInstanceOfSatisfying(ClientException.HttpException.HttpClientException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(409);
                        assertThat(e.getMessage()).contains("precondition failed");
                    });

            assertThat(client.read(subject, Set.of())).as("no events written").isEmpty();
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
        @ValueSource(ints = 8)
        @Timeout(60)
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
                                    objectMapper.convertValue(new BookPageDamagedEvent(42L), Map.class)),
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

    @Nested
    @DisplayName("/api/v1/run-eventql-query")
    public class Query {

        String subject = randomSubject();
        EventCandidate eventCandidate;

        @MockitoBean
        EventQueryErrorHandler errorHandler;

        @BeforeEach
        @Timeout(2) // deadlock may occur when writing after querying
        public void setup() {
            eventCandidate = new EventCandidate(
                    TEST_SOURCE,
                    subject,
                    "com.opencqrs.books-added.v1",
                    objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class));
            client.write(List.of(eventCandidate), List.of(new Precondition.SubjectIsPristine(subject)));
        }

        @Test
        public void queryForEventSuccessfully() {
            var ref = new AtomicReference<Event>();

            client.query(
                    EventQueryBuilder.fromEventQlString(
                            "FROM e IN events WHERE e.subject == '" + subject + "' PROJECT INTO e"),
                    (EventQueryRowHandler.AsEvent) ref::set,
                    errorHandler);

            assertThat(ref).hasValueSatisfying(event -> {
                assertThat(event)
                        .usingRecursiveComparison()
                        .ignoringFields("id", "time", "hash", "predecessorHash")
                        .isEqualTo(new Event(
                                eventCandidate.source(),
                                subject,
                                eventCandidate.type(),
                                eventCandidate.data(),
                                "1.0",
                                null,
                                null,
                                "application/json",
                                null,
                                null));
                assertThat(event.id()).isNotBlank();
                assertThat(event.time()).isBeforeOrEqualTo(Instant.now());
                assertThat(event.hash()).isNotBlank();
                assertThat(event.predecessorHash()).isNotBlank();
            });
            verifyNoInteractions(errorHandler);
        }

        @Test
        public void queryForEventDeserializationFailing() {
            EventQueryRowHandler.AsEvent rowHandler = mock();

            client.query(
                    EventQueryBuilder.fromEventQlString(
                            "FROM e IN events WHERE e.subject == '" + subject + "' PROJECT INTO { time: e.subject }"),
                    rowHandler,
                    errorHandler);

            verifyNoInteractions(rowHandler);
            verify(errorHandler).marshallingError(isA(ClientException.MarshallingException.class), anyString());
        }

        @Test
        public void queryForMapSuccessfully() {
            var ref = new AtomicReference<Map<String, ?>>();

            client.query(
                    EventQueryBuilder.fromEventQlString(
                            "FROM e IN events WHERE e.subject == '" + subject + "' PROJECT INTO e.data"),
                    (EventQueryRowHandler.AsMap) ref::set,
                    errorHandler);

            assertThat(ref).hasValueSatisfying(map -> {
                assertThat(map).hasEntrySatisfying("author", s -> assertThat(s).isEqualTo("JRR Tolkien"));
            });
            verifyNoInteractions(errorHandler);
        }

        @Test
        public void queryForMapDeserializationFailing() {
            EventQueryRowHandler.AsMap rowHandler = mock();

            client.query(
                    EventQueryBuilder.fromEventQlString(
                            "FROM e IN events WHERE e.subject == '" + subject + "' PROJECT INTO e.id"),
                    rowHandler,
                    errorHandler);

            verifyNoInteractions(rowHandler);
            verify(errorHandler).marshallingError(isA(ClientException.MarshallingException.class), anyString());
        }

        @Test
        public void queryForObjectSuccessfully() {
            var ref = new AtomicReference<BookAddedEvent>();

            client.query(
                    EventQueryBuilder.fromEventQlString(
                            "FROM e IN events WHERE e.subject == '" + subject + "' PROJECT INTO e.data"),
                    new EventQueryRowHandler.AsObject<BookAddedEvent>() {
                        @Override
                        public void accept(BookAddedEvent bookAddedEvent) {
                            ref.set(bookAddedEvent);
                        }

                        @Override
                        public Class<BookAddedEvent> type() {
                            return BookAddedEvent.class;
                        }
                    },
                    errorHandler);

            assertThat(ref).hasValueSatisfying(e -> assertThat(e.author).isEqualTo("JRR Tolkien"));
            verifyNoInteractions(errorHandler);
        }

        @Test
        public void queryForObjectDeserializationFailing() {
            EventQueryRowHandler.AsObject<BookPageDamagedEvent> rowHandler = mock();
            doReturn(BookPageDamagedEvent.class).when(rowHandler).type();

            client.query(
                    EventQueryBuilder.fromEventQlString(
                            "FROM e IN events WHERE e.subject == '" + subject + "' PROJECT INTO { page : e.subject }"),
                    rowHandler,
                    errorHandler);

            verify(rowHandler, never()).accept(any());
            verify(errorHandler).marshallingError(isA(ClientException.MarshallingException.class), anyString());
        }

        @Test
        public void queryForScalarStringSuccessfully() {
            var ref = new AtomicReference<String>();

            client.query(
                    EventQueryBuilder.fromEventQlString(
                            "FROM e IN events WHERE e.subject == '" + subject + "' PROJECT INTO e.id"),
                    (EventQueryRowHandler.AsScalar<String>) ref::set,
                    errorHandler);

            assertThat(ref).hasValueSatisfying(id -> assertThat(id).isNotBlank());
            verifyNoInteractions(errorHandler);
        }

        @Test
        public void queryForScalarIntSuccessfully() {
            var ref = new AtomicReference<Integer>();

            client.query(
                    EventQueryBuilder.fromEventQlString(
                            "FROM e IN events WHERE e.subject == '" + subject + "' PROJECT INTO e.id AS INT"),
                    (EventQueryRowHandler.AsScalar<Integer>) ref::set,
                    errorHandler);

            assertThat(ref).hasValueSatisfying(id -> assertThat(id).isPositive());
            verifyNoInteractions(errorHandler);
        }

        @Test
        public void queryForScalarDeserializationFailing() {
            var ref = new AtomicReference<Integer>();
            EventQueryRowHandler.AsScalar<Integer> rowHandler = ref::set;

            client.query(
                    EventQueryBuilder.fromEventQlString(
                            "FROM e IN events WHERE e.subject == '" + subject + "' PROJECT INTO e.time"),
                    rowHandler,
                    errorHandler);

            assertThat(ref.get()).isNull();
            verify(errorHandler).marshallingError(isA(ClientException.MarshallingException.class), anyString());
        }
    }

    @Nested
    @DisplayName("/api/v1/read-subjects")
    public class ReadSubjects {

        @Test
        public void readSubjectsForRoot() {
            // Arrange: Write events for different subjects
            String subject1 = randomSubject();
            String subject2 = randomSubject();

            client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            subject1,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("Author 1", "Title 1"), Map.class))),
                    List.of());

            client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            subject2,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("Author 2", "Title 2"), Map.class))),
                    List.of());

            // Act: Read all subjects from root
            List<String> subjects = client.readSubjects("/");

            // Assert: Should contain both subjects and root itself
            assertThat(subjects).contains("/", subject1, subject2);
        }

        @Test
        public void readSubjectsForSpecificSubject() {
            // Arrange: Write events for hierarchical subjects
            String baseSubject = randomSubject();
            String childSubject1 = baseSubject + "/child1";
            String childSubject2 = baseSubject + "/child2";

            client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            baseSubject,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("Author", "Base"), Map.class))),
                    List.of());

            client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            childSubject1,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("Author", "Child 1"), Map.class))),
                    List.of());

            client.write(
                    List.of(new EventCandidate(
                            TEST_SOURCE,
                            childSubject2,
                            "com.opencqrs.books-added.v1",
                            objectMapper.convertValue(new BookAddedEvent("Author", "Child 2"), Map.class))),
                    List.of());

            // Act: Read subjects for base subject
            List<String> subjects = client.readSubjects(baseSubject);

            // Assert: Should contain base subject and children
            assertThat(subjects).containsExactlyInAnyOrder(baseSubject, childSubject1, childSubject2);
        }

        @Test
        public void readSubjectsForNonExistentSubject() {
            // Act: Read subjects for non-existent subject
            List<String> subjects = client.readSubjects("/non-existent");

            // Assert: Should return empty list (or at least not fail)
            assertThat(subjects).isNotNull().isEmpty();
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

    public record BookPageDamagedEvent(Long page) {}

    public record BookRemovedEvent(String reason) {}
}
