/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import com.opencqrs.esdb.client.EsdbClient;
import com.opencqrs.esdb.client.Marshaller;
import com.opencqrs.framework.BookAddedEvent;
import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.eventhandler.partitioning.DefaultPartitionKeyResolver;
import com.opencqrs.framework.eventhandler.progress.JdbcProgressTracker;
import com.opencqrs.framework.eventhandler.progress.Progress;
import com.opencqrs.framework.persistence.ImmediateEventPublisher;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.jdbc.lock.LockRepository;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the {@link EventHandlingProcessor} for Spring Boot environments, focussed on fail-over,
 * partitioning, and retry handling in distributed environments.
 *
 * @see EventHandlingProcessorTest
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:tc:postgresql:latest:///postgres",
            "spring.sql.init.mode=always",
            "spring.sql.init.data-locations=classpath:event-handling-processor.sql",
        })
@Testcontainers
public class EventHandlingProcessorIntegrationTest {

    @TestConfiguration
    static class EventHandlingProcessorTestConfiguration {

        @Bean
        public JdbcProgressTracker jdbcProgressTracker(
                DataSource dataSource, PlatformTransactionManager transactionManager) {
            return new JdbcProgressTracker(dataSource, transactionManager);
        }
    }

    @Autowired
    private JdbcProgressTracker progressTracker;

    @Autowired
    private ImmediateEventPublisher immediateEventPublisher;

    final Map<String, ConfigurableApplicationContext> nodesRunning = new HashMap<>();

    /** Records a single handler invocation, written to by handlers in BOTH node contexts. */
    public record Handled(String node, String eventId) {}

    static final List<Handled> SINK = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    public void tearDown(@Autowired JdbcTemplate jdbcTemplate) {
        nodesRunning.forEach((s, ctx) -> ctx.close());
        nodesRunning.clear();

        SINK.clear();

        jdbcTemplate.execute("TRUNCATE EVENTHANDLER_LOCK");
        jdbcTemplate.execute("TRUNCATE EVENTHANDLER_PROGRESS");
        jdbcTemplate.execute("TRUNCATE SIDE_EFFECT");
    }

    private ConfigurableApplicationContext startNode(String node, String rootSubject, String... extraProperties) {
        var ctx = new SpringApplicationBuilder(NodeApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "test.node.label=" + node,
                        "spring.datasource.url=jdbc:tc:postgresql:latest:///postgres",
                        "spring.sql.init.mode=never",
                        "opencqrs.event-handling.standard.fetch.subject=" + rootSubject,
                        "opencqrs.event-handling.standard.life-cycle.auto-start=false",
                        "opencqrs.event-handling.standard.life-cycle.controller=leader-election",
                        "opencqrs.event-handling.standard.life-cycle.partitions=2",
                        "opencqrs.event-handling.standard.progress.tracking=jdbc",
                        "opencqrs.event-handling.standard.sequence.resolution=per-subject")
                .properties(extraProperties)
                .run();
        nodesRunning.put(node, ctx);

        return ctx;
    }

    private void startPartitionOnNode(String node, long partition, boolean awaitGranted) {
        var nodeCtx = nodesRunning.get(node);
        nodeCtx.getBeansOfType(LockRegistryLeaderInitiator.class).forEach((beanName, lockRegistryLeaderInitiator) -> {
            if (beanName.endsWith(partition + "_lifeCycle")) {
                lockRegistryLeaderInitiator.start();
                if (awaitGranted) {
                    await().until(() -> lockRegistryLeaderInitiator.getContext().isLeader());
                }
            }
        });
    }

    private String subjectForPartition(String root, long partition) {
        var partitionKeyResolver = new DefaultPartitionKeyResolver(2);
        for (int i = 0; ; i++) {
            String subject = root + "/" + i;
            if (partitionKeyResolver.resolve(subject) == partition) {
                return subject;
            }
        }
    }

    private String currentProgress(long partition) {
        return switch (progressTracker.current("failover", partition)) {
            case Progress.None ignored -> null;
            case Progress.Success success -> success.id();
        };
    }

    @Test
    public void distributesAndHandlesEachEventExactlyOnce() {
        var rootSubject = "/test/" + UUID.randomUUID();

        startNode("A", rootSubject);
        startNode("B", rootSubject);

        startPartitionOnNode("A", 0, true);
        startPartitionOnNode("B", 1, true);

        startPartitionOnNode("A", 1, false);
        startPartitionOnNode("B", 0, false);

        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 1), new BookAddedEvent("e1"));

        await().untilAsserted(() ->
                assertThat(SINK).containsExactlyInAnyOrder(new Handled("A", "e0"), new Handled("B", "e1")));
    }

    @Test
    public void inactivePartitionRecoveredOnceAvailableAgain() {
        var rootSubject = "/test/" + UUID.randomUUID();

        startNode("A", rootSubject);
        startNode("B", rootSubject);

        startPartitionOnNode("A", 0, true);

        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 1), new BookAddedEvent("e1"));
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e2"));
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 1), new BookAddedEvent("e3"));

        await().untilAsserted(() -> assertThat(SINK).containsExactly(new Handled("A", "e0"), new Handled("A", "e2")));

        startPartitionOnNode("B", 1, true);

        await().untilAsserted(() -> assertThat(SINK)
                .containsExactly(
                        new Handled("A", "e0"),
                        new Handled("A", "e2"),
                        new Handled("B", "e1"),
                        new Handled("B", "e3")));
    }

    @ParameterizedTest
    @CsvSource({
        "A, B", "B, A", "A, A", "B, B",
    })
    public void failsOverToSurvivingNodeOnGracefulShutdown(
            String firstPartitionActiveOn, String secondPartitionActiveOn) {
        var complementaryNodes = Map.of("A", "B", "B", "A");
        var rootSubject = "/test/" + UUID.randomUUID();

        startNode("A", rootSubject);
        startNode("B", rootSubject);

        startPartitionOnNode(firstPartitionActiveOn, 0, true);
        startPartitionOnNode(secondPartitionActiveOn, 1, true);

        startPartitionOnNode(complementaryNodes.get(firstPartitionActiveOn), 0, false);
        startPartitionOnNode(complementaryNodes.get(secondPartitionActiveOn), 1, false);

        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 1), new BookAddedEvent("e1"));
        await().untilAsserted(() -> assertThat(SINK)
                .containsExactlyInAnyOrder(
                        new Handled(firstPartitionActiveOn, "e0"), new Handled(secondPartitionActiveOn, "e1")));
        SINK.clear();

        nodesRunning.get(complementaryNodes.get(firstPartitionActiveOn)).close();
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e3"));
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 1), new BookAddedEvent("e4"));
        await().untilAsserted(() -> assertThat(SINK)
                .containsExactlyInAnyOrder(
                        new Handled(firstPartitionActiveOn, "e3"), new Handled(firstPartitionActiveOn, "e4")));
    }

    @Test
    public void recoversFromEventStoreConnectivityErrorNoFailOver() throws IOException {
        var rootSubject = "/test/" + UUID.randomUUID();

        startNode("A", rootSubject);
        startNode("B", rootSubject);

        startPartitionOnNode("A", 0, true);
        startPartitionOnNode("B", 1, true);

        startPartitionOnNode("A", 1, false);
        startPartitionOnNode("B", 0, false);

        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 1), new BookAddedEvent("e1"));
        // warm-up
        await().untilAsserted(() -> assertThat(SINK.size()).isEqualTo(2));
        SINK.clear();

        var proxyB = nodesRunning.get("B").getBean(Proxy.class);
        proxyB.disable();

        immediateEventPublisher.publish(subjectForPartition(rootSubject, 1), new BookAddedEvent("e2"));
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e3"));

        await().untilAsserted(() -> assertThat(SINK).containsExactly(new Handled("A", "e3")));

        proxyB.enable();

        await().untilAsserted(() -> assertThat(SINK).containsExactly(new Handled("A", "e3"), new Handled("B", "e2")));
    }

    @Test
    public void progressPersistedForEventsHandledSuccessfully() {
        var rootSubject = "/test/" + UUID.randomUUID();

        startNode("A", rootSubject);
        startPartitionOnNode("A", 0, true);

        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        var e1 = immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e1"));

        await().untilAsserted(() -> assertThat(currentProgress(0)).isEqualTo(e1.id()));
    }

    @Test
    public void progressResumedAfterRestartOnDifferentNode() {
        var rootSubject = "/test/" + UUID.randomUUID();

        var nodeA = startNode("A", rootSubject);
        startPartitionOnNode("A", 0, true);

        var e0 = immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        await().untilAsserted(() -> assertThat(currentProgress(0)).isEqualTo(e0.id()));

        nodeA.close();

        var e1 = immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e1"));

        startNode("B", rootSubject);
        startPartitionOnNode("B", 0, true);

        await().untilAsserted(() -> {
            assertThat(SINK)
                    .as("resumed processing, no duplicate expected")
                    .containsExactly(new Handled("A", "e0"), new Handled("B", "e1"));
            assertThat(currentProgress(0)).isEqualTo(e1.id());
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void eventHandlerSideEffectsTransactionallyRolledBack(
            boolean proceedTransactional, @Autowired JdbcTemplate jdbcTemplate) {
        var rootSubject = "/test/" + UUID.randomUUID();

        startNode(
                "A",
                rootSubject,
                "opencqrs.event-handling.standard.retry.max-attempts=0",
                "proceed.transactional=" + proceedTransactional,
                "test.event-handler.e0.fail.after-side-effect=true");
        startPartitionOnNode("A", 0, true);

        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        await().untilAsserted(() -> {
            assertThat(SINK).isNotEmpty();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SIDE_EFFECT", Integer.class))
                    .isEqualTo(proceedTransactional ? 0 : 1);
        });
    }

    @Test
    public void retriedHandlerErrorRecoversAndHandledExactlyOnce() {
        var rootSubject = "/test/" + UUID.randomUUID();

        startNode("A", rootSubject, "test.event-handler.e0.fail.num-transient=2");
        startPartitionOnNode("A", 0, true);

        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        var e1 = immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e1"));

        await().untilAsserted(() -> {
            assertThat(SINK).containsExactly(new Handled("A", "e0"), new Handled("A", "e1"));
            assertThat(currentProgress(0)).isEqualTo(e1.id());
        });
    }

    @Test
    public void retryBudgetResetPerEventIfRecovered() {
        var rootSubject = "/test/" + UUID.randomUUID();

        startNode(
                "A",
                rootSubject,
                "opencqrs.event-handling.standard.retry.max-attempts=3",
                "test.event-handler.e0.fail.num-transient=2",
                "test.event-handler.e1.fail.num-transient=2");
        startPartitionOnNode("A", 0, true);

        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e1"));

        await().untilAsserted(() -> assertThat(SINK).containsExactly(new Handled("A", "e0"), new Handled("A", "e1")));
    }

    @Test
    public void retryExhaustedSkipsEventAndAdvancesProgress() {
        var rootSubject = "/test/" + UUID.randomUUID();

        startNode(
                "A",
                rootSubject,
                "opencqrs.event-handling.standard.retry.max-attempts=0",
                "test.event-handler.e0.fail.always-transient=true");
        startPartitionOnNode("A", 0, true);

        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        var e1 = immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e1"));

        await().untilAsserted(() -> {
            assertThat(SINK).containsExactly(new Handled("A", "e1"));
            assertThat(currentProgress(0)).isEqualTo(e1.id());
        });
    }

    @Test
    public void skippedEventNotRedeliveredAfterFailover() {
        var rootSubject = "/test/" + UUID.randomUUID();

        var nodeA = startNode(
                "A",
                rootSubject,
                "opencqrs.event-handling.standard.retry.max-attempts=0",
                "test.event-handler.e0.fail.always-transient=true");
        startPartitionOnNode("A", 0, true);

        var e0 = immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        await().untilAsserted(() -> {
            assertThat(SINK).isEmpty();
            assertThat(currentProgress(0)).isEqualTo(e0.id());
        });

        nodeA.close();

        var e1 = immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e1"));

        startNode("B", rootSubject);
        startPartitionOnNode("B", 0, true);

        await().untilAsserted(() -> {
            assertThat(SINK).containsExactly(new Handled("B", "e1"));
            assertThat(currentProgress(0)).isEqualTo(e1.id());
        });
    }

    @Test
    public void nonTransientHandlerErrorBlocksPartitionAndIsNotSkipped() {
        var rootSubject = "/test/" + UUID.randomUUID();

        var nodeA = startNode(
                "A",
                rootSubject,
                "opencqrs.event-handling.standard.life-cycle.auto-start=true",
                "opencqrs.event-handling.standard.life-cycle.controller=application-context",
                "opencqrs.event-handling.standard.life-cycle.partitions=1",
                "test.event-handler.e1.fail.always-non-transient=true");

        var e0 = immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e1"));
        immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e2"));

        await().untilAsserted(() -> {
            assertThat(SINK).containsExactly(new Handled("A", "e0"));
            assertThat(currentProgress(0)).isEqualTo(e0.id());
            assertThat(nodeA.getBean(EventHandlingProcessor.class).isRunning()).isFalse();
        });
    }

    @Test
    public void inFlightRetryingEventCompletedByNewLeaderAfterFailover() {
        var rootSubject = "/test/" + UUID.randomUUID();

        var nodeA = startNode(
                "A",
                rootSubject,
                "opencqrs.event-handling.standard.life-cycle.auto-start=true",
                "opencqrs.event-handling.standard.life-cycle.controller=application-context",
                "opencqrs.event-handling.standard.life-cycle.partitions=1",
                "test.event-handler.e1.fail.always-transient=true");
        startPartitionOnNode("A", 0, true);

        var e0 = immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e0"));
        var e1 = immediateEventPublisher.publish(subjectForPartition(rootSubject, 0), new BookAddedEvent("e1"));

        await().untilAsserted(() -> {
            assertThat(SINK).containsExactly(new Handled("A", "e0"));
            assertThat(currentProgress(0)).isEqualTo(e0.id());
        });
        SINK.clear();

        nodeA.close();
        startNode("B", rootSubject);
        startPartitionOnNode("B", 0, true);

        await().untilAsserted(() -> {
            assertThat(SINK).containsExactly(new Handled("B", "e1"));
            assertThat(currentProgress(0)).isEqualTo(e1.id());
        });
    }

    @EnableAutoConfiguration
    static class NodeApplication {

        /** Per-context, mutable countdown of remaining transient failures per event id (from configuration). */
        private final Map<String, AtomicInteger> remainingTransientFailures = new ConcurrentHashMap<>();

        private static final int FIRST_PROXY_PORT = 8666;
        private static final int LAST_PROXY_PORT = 8697;
        private static final int PROXY_PORT_COUNT = LAST_PROXY_PORT - FIRST_PROXY_PORT + 1; // 32
        private static final AtomicInteger proxyPortSequence = new AtomicInteger();

        /** Hands out the next Toxiproxy listen port, cycling back to the start after the range is exhausted. */
        static int nextProxyPort() {
            return FIRST_PROXY_PORT + Math.floorMod(proxyPortSequence.getAndIncrement(), PROXY_PORT_COUNT);
        }

        private final Integer proxyPort = nextProxyPort();

        @Value("${test.node.label}")
        String label;

        @Bean(destroyMethod = "delete")
        public Proxy esdbProxy() throws IOException {
            var toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
            return toxiproxyClient.createProxy(
                    "esdb" + label,
                    "0.0.0.0:" + proxyPort,
                    esdb.getNetworkAliases().getFirst() + ":3000");
        }

        @Bean
        @DependsOn("esdbProxy")
        public EsdbClient esdbClient(Marshaller marshaller, HttpClient.Builder httpClientBuilder) {
            return new EsdbClient(
                    URI.create("http://" + toxiproxy.getHost() + ":" + toxiproxy.getMappedPort(proxyPort)),
                    "secret",
                    marshaller,
                    httpClientBuilder);
        }

        @Bean
        public DefaultLockRepository defaultLockRepository(DataSource dataSource) {
            var lockRepository = new DefaultLockRepository(dataSource);
            lockRepository.setPrefix("EVENTHANDLER_");
            return lockRepository;
        }

        @Bean
        public JdbcLockRegistry jdbcLockRegistry(LockRepository lockRepository) {
            return new JdbcLockRegistry(lockRepository);
        }

        @Bean
        public JdbcProgressTracker jdbcProgressTracker(
                @Value("${proceed.transactional:false}") boolean transactional,
                DataSource dataSource,
                PlatformTransactionManager transactionManager) {
            var bean = new JdbcProgressTracker(dataSource, transactionManager);
            bean.setProceedTransactionally(transactional);
            return bean;
        }

        @EventHandling("failover")
        public void on(BookAddedEvent event, @Autowired JdbcTemplate jdbcTemplate, @Autowired Environment environment) {
            var failure = Binder.get(environment)
                    .bindOrCreate("test.event-handler." + event.isbn() + ".fail", HandlerFailureConfig.class);

            if (failure.alwaysNonTransient()) {
                throw new CqrsFrameworkException.NonTransientException("non-transient failure for " + event.isbn());
            }
            if (failure.alwaysTransient()) {
                throw new RuntimeException("alwaysTransient failure for " + event.isbn());
            }
            if (failure.numTransient() > 0
                    && remainingTransientFailures
                                    .computeIfAbsent(event.isbn(), key -> new AtomicInteger(failure.numTransient()))
                                    .getAndDecrement()
                            > 0) {
                throw new RuntimeException("transient failure for " + event.isbn());
            }

            jdbcTemplate.update("INSERT INTO SIDE_EFFECT(EVENT_ID) VALUES (?)", event.isbn());
            SINK.add(new Handled(label, event.isbn()));

            if (failure.afterSideEffect()) {
                fail();
            }
        }

        /**
         * Failure behaviour for a <em>single</em> event id, bound on demand from {@code test.event-handler.<eventId>.*}
         * properties of the node's environment via the Spring {@link Binder} — so the event id is a property key rather
         * than being repeated inside every configuration value.
         *
         * @param numTransient number of times to fail (retryably) before succeeding
         * @param alwaysTransient always fail retryably (i.e. get skipped once the retry budget is exhausted)
         * @param alwaysNonTransient fail with a {@link CqrsFrameworkException.NonTransientException} (terminating)
         * @param afterSideEffect perform the side-effect and record to {@code SINK}, then fail (for transactional
         *     tests)
         */
        public record HandlerFailureConfig(
                @DefaultValue("0") int numTransient,
                @DefaultValue("false") boolean alwaysTransient,
                @DefaultValue("false") boolean alwaysNonTransient,
                @DefaultValue("false") boolean afterSideEffect) {}
    }

    static final Network NETWORK = Network.newNetwork();

    @Container
    static GenericContainer<?> esdb = new GenericContainer<>(
                    "docker.io/thenativeweb/eventsourcingdb:" + System.getProperty("esdb.version"))
            .withNetwork(NETWORK)
            .withNetworkAliases("esdb")
            .withExposedPorts(3000)
            .withCreateContainerCmdModifier(cmd -> cmd.withCmd(
                    "run",
                    "--api-token",
                    "secret",
                    "--data-directory-temporary",
                    "--http-enabled=true",
                    "--https-enabled=false"));

    @Container
    static final ToxiproxyContainer toxiproxy =
            new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0").withNetwork(NETWORK);

    @DynamicPropertySource
    static void esdbProperties(DynamicPropertyRegistry registry) throws IOException {
        registry.add("esdb.server.uri", () -> "http://" + esdb.getHost() + ":" + esdb.getFirstMappedPort());
        registry.add("esdb.server.api-token", () -> "secret");
    }
}
