/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.opencqrs.esdb.client.Event;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.jdbc.lock.LockRepository;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {
            "opencqrs.event-handling.groups.local.life-cycle.partitions=3",
            "opencqrs.event-handling.groups.local.life-cycle.controller=application-context",
            "opencqrs.event-handling.groups.distributed.life-cycle.partitions=2",
            "opencqrs.event-handling.groups.distributed.life-cycle.controller=leader-election",
        })
@Testcontainers
public class EventHandlingProcessorAutoConfigurationIntegrationTest {

    @TestConfiguration
    static class EventHandlingTestConfiguration {

        List<String> rolesLocked = Collections.synchronizedList(new ArrayList<>());

        @Bean
        public DefaultLockRepository defaultLockRepository(DataSource dataSource) {
            return new DefaultLockRepository(dataSource);
        }

        @Bean
        public JdbcLockRegistry jdbcLockRegistry(LockRepository lockRepository) {
            return new JdbcLockRegistry(lockRepository);
        }

        @EventHandling("local")
        public void onLocal(Event event) {}

        @EventHandling("distributed")
        public void onDistributed(Event event) {}

        @EventListener
        public void onGranted(OnGrantedEvent event) {
            rolesLocked.add(event.getRole());
        }
    }

    @Test
    public void eventHandlingProcessorsRunning(
            @Autowired @Qualifier("openCqrsEventHandlingProcessorContext")
                    ConfigurableApplicationContext eventHandlingContext) {
        await().untilAsserted(() -> assertThat(AssertableApplicationContext.get(() -> eventHandlingContext))
                .getBeans(EventHandlingProcessor.class)
                .hasSize(3 + 2)
                .allSatisfy((s, eh) -> assertThat(eh.isRunning()).isTrue()));
    }

    @Test
    public void persistentLocksOwned(@Autowired EventHandlingTestConfiguration configuration) {
        await().untilAsserted(() -> assertThat(configuration.rolesLocked)
                .containsExactlyInAnyOrder("[group=distributed, partition=0]", "[group=distributed, partition=1]"));
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
}
