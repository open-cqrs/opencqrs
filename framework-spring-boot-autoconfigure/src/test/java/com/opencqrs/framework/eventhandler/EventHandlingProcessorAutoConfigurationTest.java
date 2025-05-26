/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.eventhandler.partitioning.EventSequenceResolver;
import com.opencqrs.framework.eventhandler.partitioning.NoEventSequenceResolver;
import com.opencqrs.framework.eventhandler.partitioning.PerConfigurableLevelSubjectEventSequenceResolver;
import com.opencqrs.framework.eventhandler.partitioning.PerSubjectEventSequenceResolver;
import com.opencqrs.framework.eventhandler.progress.InMemoryProgressTracker;
import com.opencqrs.framework.eventhandler.progress.JdbcProgressTracker;
import com.opencqrs.framework.eventhandler.progress.ProgressTracker;
import com.opencqrs.framework.persistence.EventReader;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.util.backoff.ExponentialBackOff;

class EventHandlingProcessorAutoConfigurationTest {

    static class MyConfiguration {

        @EventHandling("a")
        public void handleA(Event event) {}

        @EventHandling("a")
        public void handleA(Object event, Map<String, ?> metaData) {}

        @Bean
        public EventHandlerDefinition<Object> ehdB() {
            return new EventHandlerDefinition<>("b", Object.class, (EventHandler.ForObject<Object>) e -> {});
        }

        @Bean
        @Profile("deactivated")
        public EventHandlerDefinition<Object> ehdC() {
            return new EventHandlerDefinition<>("c", Object.class, (EventHandler.ForObject<Object>) e -> {});
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    EventHandlingProcessorAutoConfiguration.class,
                    EventHandlingAnnotationProcessingAutoConfiguration.class))
            .withBean(EventReader.class, Mockito::mock);

    private void assertEventHandlingProcessorContext(
            ApplicationContextRunner runner, Consumer<AssertableApplicationContext> context) {
        runner.run(parent -> {
            parent.start();
            context.accept(AssertableApplicationContext.get(() ->
                    parent.getBean("openCqrsEventHandlingProcessorContext", ConfigurableApplicationContext.class)));
        });
    }

    @Test
    public void noEventProcessorsConfiguredWithoutEventHandlerDefinitions() {
        assertEventHandlingProcessorContext(runner, context -> {
            assertThat(context).doesNotHaveBean(EventHandlingProcessor.class);
            assertThat(context).doesNotHaveBean(EventHandlingProcessorLifecycleController.class);
        });
    }

    @Test
    public void eventProcessorsConfiguredUsingDefaultsForUniqueLockRegistryAndJdbcProgressTracker() {
        assertEventHandlingProcessorContext(
                runner.withUserConfiguration(MyConfiguration.class)
                        .withBean(LockRegistry.class, Mockito::mock)
                        .withBean(JdbcProgressTracker.class, Mockito::mock),
                context -> {
                    assertThat(context)
                            .getBeans(EventHandlingProcessor.class)
                            .hasSize(2)
                            .anySatisfy((beanName, bean) -> {
                                assertThat(bean.getGroupId()).isEqualTo("a");
                                assertThat(bean.getPartition()).isEqualTo(0);
                                assertThat(bean.subject).isEqualTo("/");
                                assertThat(bean.recursive).isTrue();
                                assertThat(bean.progressTracker).isInstanceOf(JdbcProgressTracker.class);
                                assertThat(bean.eventSequenceResolver)
                                        .isInstanceOfSatisfying(
                                                PerConfigurableLevelSubjectEventSequenceResolver.class,
                                                resolver -> assertThat(resolver.getKeepLevels())
                                                        .isEqualTo(2));
                                assertThat(bean.backoff.start().next())
                                        .isEqualTo(ExponentialBackOff.DEFAULT_INITIAL_INTERVAL);
                            })
                            .anySatisfy((beanName, bean) -> {
                                assertThat(bean.getGroupId()).isEqualTo("b");
                                assertThat(bean.getPartition()).isEqualTo(0);
                                assertThat(bean.subject).isEqualTo("/");
                                assertThat(bean.recursive).isTrue();
                                assertThat(bean.progressTracker).isInstanceOf(JdbcProgressTracker.class);
                                assertThat(bean.eventSequenceResolver)
                                        .isInstanceOfSatisfying(
                                                PerConfigurableLevelSubjectEventSequenceResolver.class,
                                                resolver -> assertThat(resolver.getKeepLevels())
                                                        .isEqualTo(2));
                                assertThat(bean.backoff.start().next())
                                        .isEqualTo(ExponentialBackOff.DEFAULT_INITIAL_INTERVAL);
                            })
                            .noneSatisfy((beanName, bean) -> assertThat(bean.getGroupId())
                                    .as("profile 'deactivated' not active")
                                    .isEqualTo("c"));

                    assertThat(context)
                            .getBeans(EventHandlingProcessorLifecycleController.class)
                            .hasSize(2)
                            .allSatisfy((beanName, bean) -> assertThat(bean)
                                    .isInstanceOf(LeaderElectionEventHandlingProcessorLifecycleController.class));
                    assertThat(context)
                            .getBeans(LockRegistryLeaderInitiator.class)
                            .hasSize(2)
                            .allSatisfy((beanName, bean) ->
                                    assertThat(bean.isRunning()).isTrue());
                });
    }

    @Test
    public void eventProcessorsConfiguredUsingPropertyOverrides() {
        var myLifecycleRegistration = mock(EventHandlingProcessorLifecycleRegistration.class);
        var lockRegistry2 = mock(LockRegistry.class);
        var myProgressTracker = mock(ProgressTracker.class);
        var mySequenceResolver = mock(EventSequenceResolver.ForRawEvent.class);
        assertEventHandlingProcessorContext(
                runner.withUserConfiguration(MyConfiguration.class)
                        .withBean(
                                "myLifecycleRegistration",
                                EventHandlingProcessorLifecycleRegistration.class,
                                () -> myLifecycleRegistration)
                        .withBean("lockRegistry1", LockRegistry.class, Mockito::mock)
                        .withBean("lockRegistry2", LockRegistry.class, () -> lockRegistry2)
                        .withBean(JdbcProgressTracker.class, Mockito::mock)
                        .withBean("myProgressTracker", ProgressTracker.class, () -> myProgressTracker)
                        .withBean("mySequenceResolver", EventSequenceResolver.class, () -> mySequenceResolver)
                        .withPropertyValues(
                                "spring.profiles.active=deactivated",
                                "cqrs.event-handling.standard.fetch.subject=/foo",
                                "cqrs.event-handling.groups.b.fetch.subject=/bar",
                                "cqrs.event-handling.standard.fetch.recursive=false",
                                "cqrs.event-handling.groups.b.fetch.recursive=true",
                                "cqrs.event-handling.standard.life-cycle.auto-start=false",
                                "cqrs.event-handling.groups.b.life-cycle.auto-start=true",
                                "cqrs.event-handling.standard.life-cycle.controller=application-context",
                                "cqrs.event-handling.groups.b.life-cycle.controller=leader-election",
                                "cqrs.event-handling.groups.b.life-cycle.lock-registry=lockRegistry2",
                                "cqrs.event-handling.groups.c.life-cycle.controller-registration=myLifecycleRegistration",
                                "cqrs.event-handling.standard.life-cycle.partitions=2",
                                "cqrs.event-handling.groups.b.life-cycle.partitions=1",
                                "cqrs.event-handling.standard.progress.tracking=in-memory",
                                "cqrs.event-handling.groups.b.progress.tracking=jdbc",
                                "cqrs.event-handling.groups.c.progress.tracker-ref=myProgressTracker",
                                "cqrs.event-handling.standard.sequence.resolution=no-sequence",
                                "cqrs.event-handling.groups.b.sequence.resolution=per-subject",
                                "cqrs.event-handling.groups.c.sequence.resolver-ref=mySequenceResolver",
                                "cqrs.event-handling.standard.retry.policy=none",
                                "cqrs.event-handling.groups.b.retry.policy=fixed",
                                "cqrs.event-handling.groups.b.retry.initial-interval=PT1M"),
                context -> {
                    assertThat(context)
                            .getBeans(EventHandlingProcessor.class)
                            .hasSize(5)
                            .anySatisfy((beanName, bean) -> {
                                assertThat(bean.getGroupId()).isEqualTo("a");
                                assertThat(bean.getPartition()).isEqualTo(0);
                                assertThat(bean.subject).isEqualTo("/foo");
                                assertThat(bean.recursive).isFalse();
                                assertThat(bean.progressTracker).isInstanceOf(InMemoryProgressTracker.class);
                                assertThat(bean.eventSequenceResolver).isInstanceOf(NoEventSequenceResolver.class);
                                assertThat(bean.backoff.start().next()).isEqualTo(-1);
                            })
                            .anySatisfy((beanName, bean) -> {
                                assertThat(bean.getGroupId()).isEqualTo("a");
                                assertThat(bean.getPartition()).isEqualTo(1);
                                assertThat(bean.subject).isEqualTo("/foo");
                                assertThat(bean.recursive).isFalse();
                                assertThat(bean.progressTracker).isInstanceOf(InMemoryProgressTracker.class);
                                assertThat(bean.eventSequenceResolver).isInstanceOf(NoEventSequenceResolver.class);
                                assertThat(bean.backoff.start().next()).isEqualTo(-1);
                            })
                            .anySatisfy((beanName, bean) -> {
                                assertThat(bean.getGroupId()).isEqualTo("b");
                                assertThat(bean.getPartition()).isEqualTo(0);
                                assertThat(bean.subject).isEqualTo("/bar");
                                assertThat(bean.recursive).isTrue();
                                assertThat(bean.progressTracker).isInstanceOf(JdbcProgressTracker.class);
                                assertThat(bean.eventSequenceResolver)
                                        .isInstanceOf(PerSubjectEventSequenceResolver.class);
                                assertThat(bean.backoff.start().next()).isEqualTo(60 * 1000);
                            })
                            .anySatisfy((beanName, bean) -> {
                                assertThat(bean.getGroupId()).isEqualTo("c");
                                assertThat(bean.getPartition()).isEqualTo(0);
                                assertThat(bean.subject).isEqualTo("/foo");
                                assertThat(bean.recursive).isFalse();
                                assertThat(bean.progressTracker)
                                        .as("progress tracker ref preceding")
                                        .isSameAs(myProgressTracker);
                                assertThat(bean.eventSequenceResolver)
                                        .as("sequence resolver ref preceding")
                                        .isSameAs(mySequenceResolver);
                                assertThat(bean.backoff.start().next()).isEqualTo(-1);
                            })
                            .anySatisfy((beanName, bean) -> {
                                assertThat(bean.getGroupId()).isEqualTo("c");
                                assertThat(bean.getPartition()).isEqualTo(1);
                                assertThat(bean.subject).isEqualTo("/foo");
                                assertThat(bean.recursive).isFalse();
                                assertThat(bean.progressTracker)
                                        .as("progress tracker ref preceding")
                                        .isSameAs(myProgressTracker);
                                assertThat(bean.progressTracker).isSameAs(myProgressTracker);
                                assertThat(bean.eventSequenceResolver)
                                        .as("sequence resolver ref preceding")
                                        .isSameAs(mySequenceResolver);
                                assertThat(bean.backoff.start().next()).isEqualTo(-1);
                            });
                    verify(myLifecycleRegistration, times(2)).registerLifecycleBean(any(), any(), any());
                    assertThat(context)
                            .getBeans(EventHandlingProcessorLifecycleController.class)
                            .as("life-cycle registration for c mocked")
                            .hasSize(3)
                            .anySatisfy((beanName, bean) -> assertThat(bean)
                                    .isInstanceOfSatisfying(
                                            SmartLifecycleEventHandlingProcessorLifecycleController.class,
                                            c -> assertThat(c.isRunning()).isFalse()))
                            .anySatisfy((beanName, bean) -> assertThat(bean)
                                    .isInstanceOfSatisfying(
                                            LeaderElectionEventHandlingProcessorLifecycleController.class,
                                            c -> assertThat(c.isRunning())
                                                    .as("leader not granted")
                                                    .isFalse()));
                    assertThat(context)
                            .getBeans(LockRegistryLeaderInitiator.class)
                            .hasSize(1)
                            .allSatisfy((beanName, bean) ->
                                    assertThat(bean.isRunning()).isTrue());
                });
    }

    @Test
    public void eventProcessorLifecycleConfiguredUsingApplicationContextIfLockRegistryNotOnClasspath() {
        assertEventHandlingProcessorContext(
                runner.withUserConfiguration(MyConfiguration.class)
                        .withClassLoader(new FilteredClassLoader(LockRegistry.class)),
                context -> {
                    assertThat(context)
                            .getBeans(EventHandlingProcessorLifecycleController.class)
                            .hasSize(2)
                            .allSatisfy((beanName, bean) -> assertThat(bean)
                                    .isInstanceOf(SmartLifecycleEventHandlingProcessorLifecycleController.class));
                });
    }

    @Test
    public void eventProcessorLifecycleConfiguredUsingApplicationContextIfAmbiguousLockRegistryBeans() {
        assertEventHandlingProcessorContext(
                runner.withUserConfiguration(MyConfiguration.class)
                        .withBean("lockRegistry1", LockRegistry.class, Mockito::mock)
                        .withBean("lockRegistry2", LockRegistry.class, Mockito::mock),
                context -> {
                    assertThat(context)
                            .getBeans(EventHandlingProcessorLifecycleController.class)
                            .hasSize(2)
                            .allSatisfy((beanName, bean) -> assertThat(bean)
                                    .isInstanceOf(SmartLifecycleEventHandlingProcessorLifecycleController.class));
                });
    }
}
