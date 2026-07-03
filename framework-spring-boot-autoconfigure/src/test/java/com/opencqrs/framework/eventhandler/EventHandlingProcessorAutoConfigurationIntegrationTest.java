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
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
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
            "opencqrs.event-handling.groups.inactive.life-cycle.controller-registration=testLifecycleRegistration",
            "opencqrs.event-handling.groups.inactive.life-cycle.partitions=1",
            "opencqrs.event-handling.groups.local.life-cycle.partitions=3",
            "opencqrs.event-handling.groups.local.life-cycle.controller=application-context",
            "opencqrs.event-handling.groups.distributed.life-cycle.partitions=2",
            "opencqrs.event-handling.groups.distributed.life-cycle.controller=leader-election",
        })
@Testcontainers
public class EventHandlingProcessorAutoConfigurationIntegrationTest {

    record FullyInitializedEvent(ApplicationContext context) {}

    static class ApplicationContextSmartLifecycleInitializationEventPublisher
            implements SmartLifecycle, ApplicationContextAware {

        private ApplicationContext applicationContext;
        private Boolean running = false;

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            this.applicationContext = applicationContext;
        }

        @Override
        public void start() {
            running = true;
            applicationContext.publishEvent(new FullyInitializedEvent(applicationContext));
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }
    }

    @TestConfiguration
    static class EventHandlingTestConfiguration {

        List<String> rolesLocked = Collections.synchronizedList(new ArrayList<>());
        List<ApplicationContext> contextsFullyInitialized = Collections.synchronizedList(new ArrayList<>());

        @Bean
        public DefaultLockRepository defaultLockRepository(DataSource dataSource) {
            return new DefaultLockRepository(dataSource);
        }

        @Bean
        public JdbcLockRegistry jdbcLockRegistry(LockRepository lockRepository) {
            return new JdbcLockRegistry(lockRepository);
        }

        @Bean
        public EventHandlingProcessorLifecycleRegistration testLifecycleRegistration() {
            return (registry, eventHandlingProcessorBeanName, processorSettings) -> {
                registry.registerBean(ApplicationContextSmartLifecycleInitializationEventPublisher.class);
            };
        }

        @Bean
        public ApplicationContextSmartLifecycleInitializationEventPublisher initializationTracker() {
            return new ApplicationContextSmartLifecycleInitializationEventPublisher();
        }

        @EventHandling("inactive")
        public void onInactive(Event event) {}

        @EventHandling("local")
        public void onLocal(Event event) {}

        @EventHandling("distributed")
        public void onDistributed(Event event) {}

        @EventListener
        public void onGranted(OnGrantedEvent event) {
            rolesLocked.add(event.getRole());
        }

        @EventListener
        public void onFullyInitialized(FullyInitializedEvent event) {
            contextsFullyInitialized.add(event.context());
        }
    }

    @Test
    public void eventHandlingProcessorContextInitializedAfterParentContext(
            @Autowired EventHandlingTestConfiguration eventHandlingTestConfiguration,
            @Autowired ApplicationContext applicationContext,
            @Autowired EventHandlingProcessorContext eventHandlingContext) {
        assertThat(eventHandlingTestConfiguration.contextsFullyInitialized)
                .containsExactly(applicationContext, eventHandlingContext.context());
    }

    @Test
    public void activeEventHandlingProcessorsRunning(@Autowired EventHandlingProcessorContext eventHandlingContext) {
        await().untilAsserted(() -> assertThat(AssertableApplicationContext.get(eventHandlingContext::context))
                .getBeans(EventHandlingProcessor.class)
                .hasSize(1 + 3 + 2)
                .allSatisfy((s, eh) -> {
                    if (!eh.getGroupId().equals("inactive")) {
                        assertThat(eh.isRunning()).isTrue();
                    }
                }));
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
