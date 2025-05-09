/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import static java.util.stream.Collectors.groupingBy;

import com.opencqrs.framework.eventhandler.partitioning.*;
import com.opencqrs.framework.eventhandler.progress.InMemoryProgressTracker;
import com.opencqrs.framework.eventhandler.progress.JdbcProgressTracker;
import com.opencqrs.framework.eventhandler.progress.ProgressTracker;
import com.opencqrs.framework.persistence.EventReader;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

/**
 * {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for
 * {@link EventHandlingProcessor} and supporting beans.
 */
@AutoConfiguration
@EnableConfigurationProperties(EventHandlingProperties.class)
public class EventHandlingProcessorAutoConfiguration {

    private static Logger log = Logger.getLogger(EventHandlingProcessorAutoConfiguration.class.getName());

    private static <T, S> T extractUsingFallback(S from, S fallback, Function<S, T> extractor) {
        return Optional.ofNullable(from).map(extractor::apply).orElseGet(() -> extractor.apply(fallback));
    }

    private static EventHandlingProperties.ProcessorSettings mergeWith(
            EventHandlingProperties.ProcessorSettings settings, EventHandlingProperties.ProcessorSettings defaults) {
        return new EventHandlingProperties.ProcessorSettings(
                new EventHandlingProperties.ProcessorSettings.Fetch(
                        extractUsingFallback(
                                settings.fetch(),
                                defaults.fetch(),
                                EventHandlingProperties.ProcessorSettings.Fetch::subject),
                        extractUsingFallback(
                                settings.fetch(),
                                defaults.fetch(),
                                EventHandlingProperties.ProcessorSettings.Fetch::recursive)),
                new EventHandlingProperties.ProcessorSettings.LifeCycle(
                        extractUsingFallback(
                                settings.lifeCycle(),
                                defaults.lifeCycle(),
                                EventHandlingProperties.ProcessorSettings.LifeCycle::autoStart),
                        extractUsingFallback(
                                settings.lifeCycle(),
                                defaults.lifeCycle(),
                                EventHandlingProperties.ProcessorSettings.LifeCycle::controller),
                        extractUsingFallback(
                                settings.lifeCycle(),
                                defaults.lifeCycle(),
                                EventHandlingProperties.ProcessorSettings.LifeCycle::controllerRegistration),
                        extractUsingFallback(
                                settings.lifeCycle(),
                                defaults.lifeCycle(),
                                EventHandlingProperties.ProcessorSettings.LifeCycle::lockRegistry),
                        extractUsingFallback(
                                settings.lifeCycle(),
                                defaults.lifeCycle(),
                                EventHandlingProperties.ProcessorSettings.LifeCycle::partitions)),
                new EventHandlingProperties.ProcessorSettings.Progress(
                        extractUsingFallback(
                                settings.progress(),
                                defaults.progress(),
                                EventHandlingProperties.ProcessorSettings.Progress::tracking),
                        extractUsingFallback(
                                settings.progress(),
                                defaults.progress(),
                                EventHandlingProperties.ProcessorSettings.Progress::trackerRef)),
                new EventHandlingProperties.ProcessorSettings.Sequencing(
                        extractUsingFallback(
                                settings.sequence(),
                                defaults.sequence(),
                                EventHandlingProperties.ProcessorSettings.Sequencing::resolution),
                        extractUsingFallback(
                                settings.sequence(),
                                defaults.sequence(),
                                EventHandlingProperties.ProcessorSettings.Sequencing::resolverRef)),
                new EventHandlingProperties.ProcessorSettings.Retry(
                        extractUsingFallback(
                                settings.retry(),
                                defaults.retry(),
                                EventHandlingProperties.ProcessorSettings.Retry::policy),
                        extractUsingFallback(
                                settings.retry(),
                                defaults.retry(),
                                EventHandlingProperties.ProcessorSettings.Retry::initialInterval),
                        extractUsingFallback(
                                settings.retry(),
                                defaults.retry(),
                                EventHandlingProperties.ProcessorSettings.Retry::maxInterval),
                        extractUsingFallback(
                                settings.retry(),
                                defaults.retry(),
                                EventHandlingProperties.ProcessorSettings.Retry::maxElapsedTime),
                        extractUsingFallback(
                                settings.retry(),
                                defaults.retry(),
                                EventHandlingProperties.ProcessorSettings.Retry::multiplier),
                        extractUsingFallback(
                                settings.retry(),
                                defaults.retry(),
                                EventHandlingProperties.ProcessorSettings.Retry::maxAttempts)));
    }

    private static BackOff createBackOff(EventHandlingProperties.ProcessorSettings.Retry retry) {
        var springBackOff =
                switch (retry.policy()) {
                    case NONE -> new FixedBackOff(0, 0);
                    case FIXED -> new FixedBackOff(retry.initialInterval().toMillis(), retry.maxAttempts());
                    case EXPONENTIAL_BACKOFF -> {
                        var result = new ExponentialBackOff();
                        result.setInitialInterval(retry.initialInterval().toMillis());
                        result.setMaxInterval(retry.maxInterval().toMillis());
                        result.setMaxElapsedTime(retry.maxElapsedTime().toMillis());
                        result.setMultiplier(retry.multiplier());
                        result.setMaxAttempts(retry.maxAttempts());
                        yield result;
                    }
                };

        return () -> new BackOff.Execution() {
            private final BackOffExecution execution = springBackOff.start();

            @Override
            public long next() {
                return execution.nextBackOff();
            }
        };
    }

    public static final long DEFAULT_ACTIVE_PARTITIONS = 1;

    private static BeanDefinitionRegistryPostProcessor eventHandlingProcessorBeanRegistration(
            EventHandlingProperties eventHandlingProperties,
            List<EventHandlerDefinition> eventHandlerDefinitions,
            ApplicationContext parentContext) {
        var defaults = new EventHandlingProperties.ProcessorSettings(
                new EventHandlingProperties.ProcessorSettings.Fetch("/", true),
                new EventHandlingProperties.ProcessorSettings.LifeCycle(
                        true, null, null, null, DEFAULT_ACTIVE_PARTITIONS),
                new EventHandlingProperties.ProcessorSettings.Progress(null, null),
                new EventHandlingProperties.ProcessorSettings.Sequencing(
                        EventHandlingProperties.ProcessorSettings.Sequencing.Resolution.PER_SECOND_LEVEL_SUBJECT, null),
                new EventHandlingProperties.ProcessorSettings.Retry(
                        EventHandlingProperties.ProcessorSettings.Retry.Policy.EXPONENTIAL_BACKOFF,
                        Duration.ofMillis(ExponentialBackOff.DEFAULT_INITIAL_INTERVAL),
                        Duration.ofMillis(ExponentialBackOff.DEFAULT_MAX_INTERVAL),
                        Duration.ofMillis(ExponentialBackOff.DEFAULT_MAX_ELAPSED_TIME),
                        ExponentialBackOff.DEFAULT_MULTIPLIER,
                        ExponentialBackOff.DEFAULT_MAX_ATTEMPTS));

        return registry -> eventHandlerDefinitions.stream()
                .filter(ehd -> ehd.group() != null)
                .collect(groupingBy(EventHandlerDefinition::group))
                .forEach((group, ehds) -> {
                    var standardSettings = Optional.ofNullable(eventHandlingProperties.standard())
                            .map(s -> mergeWith(s, defaults))
                            .orElse(defaults);
                    var processorSettings = Optional.ofNullable(eventHandlingProperties.groups())
                            .map(groups -> groups.get(group))
                            .map(s -> mergeWith(s, standardSettings))
                            .orElse(standardSettings);

                    var fallbackLifecycleRegistration = parentContext.getBean(
                            "smartLifecycleEventHandlingProcessorLifecycleControllerRegistration",
                            EventHandlingProcessorLifecycleRegistration.class);
                    var lifecycleRegistration =
                            switch (processorSettings.lifeCycle().controllerRegistration()) {
                                case null ->
                                    switch (processorSettings.lifeCycle().controller()) {
                                        case APPLICATION_CONTEXT -> fallbackLifecycleRegistration;
                                        case LEADER_ELECTION -> {
                                            var lockRegistry =
                                                    switch (processorSettings
                                                            .lifeCycle()
                                                            .lockRegistry()) {
                                                        case null -> parentContext.getBean(LockRegistry.class);
                                                        default ->
                                                            parentContext.getBean(
                                                                    processorSettings
                                                                            .lifeCycle()
                                                                            .lockRegistry(),
                                                                    LockRegistry.class);
                                                    };
                                            yield parentContext
                                                    .getBeanProvider(
                                                            LeaderElectionLifecycleConfiguration.Registration.class)
                                                    .getObject(lockRegistry);
                                        }
                                        // indirect check if LockRegistry is available on the class-path
                                        case null ->
                                            switch (parentContext
                                                    .getBeanProvider(LeaderElectionLifecycleConfiguration.class)
                                                    .getIfAvailable()) {
                                                case null -> fallbackLifecycleRegistration;
                                                default -> {
                                                    var uniqueLockRegistry = parentContext
                                                            .getBeanProvider(LockRegistry.class)
                                                            .getIfUnique();
                                                    yield switch (uniqueLockRegistry) {
                                                        case null -> {
                                                            log.warning(() ->
                                                                    "multiple " + LockRegistry.class.getSimpleName()
                                                                            + " bean candidates found, falling back to 'application-context' controller for event handling processor: "
                                                                            + group);
                                                            yield fallbackLifecycleRegistration;
                                                        }
                                                        default ->
                                                            parentContext
                                                                    .getBeanProvider(
                                                                            LeaderElectionLifecycleConfiguration
                                                                                    .Registration.class)
                                                                    .getObject(uniqueLockRegistry);
                                                    };
                                                }
                                            };
                                    };
                                default ->
                                    parentContext.getBean(
                                            processorSettings.lifeCycle().controllerRegistration(),
                                            EventHandlingProcessorLifecycleRegistration.class);
                            };

                    var defaultProgressTracker =
                            parentContext.getBean("inMemoryProgressTracker", ProgressTracker.class);
                    var progressTracker =
                            switch (processorSettings.progress().trackerRef()) {
                                case null ->
                                    switch (processorSettings.progress().tracking()) {
                                        case IN_MEMORY -> defaultProgressTracker;
                                        case JDBC -> parentContext.getBean(JdbcProgressTracker.class);
                                        case null -> {
                                            var uniqueJdbc = parentContext
                                                    .getBeanProvider(JdbcProgressTracker.class)
                                                    .getIfUnique();
                                            yield switch (uniqueJdbc) {
                                                case null -> {
                                                    log.warning(() ->
                                                            "multiple " + JdbcProgressTracker.class.getSimpleName()
                                                                    + " bean candidates found, falling back to 'in-memory' progress tracking for event handling processor: "
                                                                    + group);
                                                    yield defaultProgressTracker;
                                                }
                                                default -> uniqueJdbc;
                                            };
                                        }
                                    };
                                default ->
                                    parentContext.getBean(
                                            processorSettings.progress().trackerRef(), ProgressTracker.class);
                            };

                    var defaultSequenceResolver = parentContext.getBean(
                            "perSecondLevelSubjectEventSequenceResolver",
                            PerConfigurableLevelSubjectEventSequenceResolver.class);
                    var sequenceResolver =
                            switch (processorSettings.sequence().resolverRef()) {
                                case null ->
                                    switch (processorSettings.sequence().resolution()) {
                                        case PER_SECOND_LEVEL_SUBJECT -> defaultSequenceResolver;
                                        case PER_SUBJECT ->
                                            parentContext.getBean(
                                                    "perSubjectEventSequenceResolver",
                                                    PerSubjectEventSequenceResolver.class);
                                        case NO_SEQUENCE ->
                                            parentContext.getBean(
                                                    "noEventSequenceResolver", NoEventSequenceResolver.class);
                                        case null -> defaultSequenceResolver;
                                    };
                                default ->
                                    parentContext.getBean(
                                            processorSettings.sequence().resolverRef(), EventSequenceResolver.class);
                            };

                    DefaultPartitionKeyResolver partitionKeyResolver = new DefaultPartitionKeyResolver(
                            processorSettings.lifeCycle().partitions());
                    for (int partition = 0;
                            partition < processorSettings.lifeCycle().partitions();
                            partition++) {
                        RootBeanDefinition processor = new RootBeanDefinition();
                        processor.setBeanClass(EventHandlingProcessor.class);

                        ConstructorArgumentValues values = new ConstructorArgumentValues();
                        values.addGenericArgumentValue(partition);
                        values.addGenericArgumentValue(processorSettings.fetch().subject());
                        values.addGenericArgumentValue(processorSettings.fetch().recursive());
                        values.addGenericArgumentValue(new RuntimeBeanReference(EventReader.class));

                        values.addGenericArgumentValue(progressTracker);
                        values.addGenericArgumentValue(sequenceResolver);
                        values.addGenericArgumentValue(partitionKeyResolver);

                        GenericBeanDefinition backOff = new GenericBeanDefinition();
                        backOff.setBeanClass(BackOff.class);
                        backOff.setInstanceSupplier(() -> createBackOff(processorSettings.retry()));
                        values.addGenericArgumentValue(backOff);

                        values.addGenericArgumentValue(ehds);
                        processor.setConstructorArgumentValues(values);

                        var beanName = "eventHandlingProcessor_" + group + "_" + partition;
                        registry.registerBeanDefinition(beanName, processor);

                        Settings settings = new Settings(
                                group,
                                partition,
                                processorSettings.fetch().subject(),
                                processorSettings.fetch().recursive(),
                                progressTracker.toString(),
                                sequenceResolver.toString(),
                                processorSettings.retry());
                        log.info(() -> "registered event handling processor '" + beanName + "' with: " + settings);

                        lifecycleRegistration.registerLifecycleBean(registry, beanName, processorSettings);
                    }
                });
    }

    /** Internal helper class for logging the effective {@link EventHandlingProcessor} bean settings. */
    record Settings(
            String group,
            int partition,
            String subject,
            boolean recursive,
            String progressTracker,
            String sequenceResolver,
            EventHandlingProperties.ProcessorSettings.Retry retry) {}

    @Bean(initMethod = "refresh")
    public GenericApplicationContext eventHandlingProcessorContext(
            ApplicationContext applicationContext,
            EventHandlingProperties eventHandlingProperties,
            List<EventHandlerDefinition> eventHandlerDefinitions) {
        GenericApplicationContext context = new GenericApplicationContext(applicationContext);
        context.addBeanFactoryPostProcessor(eventHandlingProcessorBeanRegistration(
                eventHandlingProperties, eventHandlerDefinitions, applicationContext));
        return context;
    }

    @Bean
    public EventHandlingProcessorLifecycleRegistration
            smartLifecycleEventHandlingProcessorLifecycleControllerRegistration() {
        return (registry, eventHandlingProcessorBeanName, processorSettings) -> {
            RootBeanDefinition processorLifecycle = new RootBeanDefinition();
            processorLifecycle.setBeanClass(SmartLifecycleEventHandlingProcessorLifecycleController.class);
            ConstructorArgumentValues processorArgs = new ConstructorArgumentValues();
            processorArgs.addGenericArgumentValue(new RuntimeBeanReference(eventHandlingProcessorBeanName));
            processorLifecycle.setConstructorArgumentValues(processorArgs);
            MutablePropertyValues lifeCycleProperties = new MutablePropertyValues();
            lifeCycleProperties.add("autoStartup", processorSettings.lifeCycle().autoStart());
            processorLifecycle.setPropertyValues(lifeCycleProperties);

            var beanName = eventHandlingProcessorBeanName + "_lifecycle";
            registry.registerBeanDefinition(beanName, processorLifecycle);

            log.info(() ->
                    "registered application context life-cycle controller '" + beanName + "' with: Settings[autoStart="
                            + processorSettings.lifeCycle().autoStart() + "]");
        };
    }

    @Configuration
    @ConditionalOnClass(LockRegistry.class)
    public static class LeaderElectionLifecycleConfiguration {

        /**
         * Internal helper class for logging the effective {@link EventHandlingProcessorLifecycleController} settings.
         */
        record Settings(boolean autoStartup, String lockRegistry) {}

        public record Registration(LockRegistry lockRegistry) implements EventHandlingProcessorLifecycleRegistration {
            @Override
            public void registerLifecycleBean(
                    BeanDefinitionRegistry registry,
                    String eventHandlingProcessorBeanName,
                    EventHandlingProperties.ProcessorSettings processorSettings) {
                RootBeanDefinition processorLifecycle = new RootBeanDefinition();
                processorLifecycle.setBeanClass(LeaderElectionEventHandlingProcessorLifecycleController.class);
                ConstructorArgumentValues processorArgs = new ConstructorArgumentValues();
                processorArgs.addGenericArgumentValue(new RuntimeBeanReference(eventHandlingProcessorBeanName));
                processorLifecycle.setConstructorArgumentValues(processorArgs);
                registry.registerBeanDefinition(eventHandlingProcessorBeanName + "_lifecycle", processorLifecycle);

                RootBeanDefinition lockRegistryLeaderInitiator = new RootBeanDefinition();
                lockRegistryLeaderInitiator.setBeanClass(LockRegistryLeaderInitiator.class);
                ConstructorArgumentValues initiatorArgs = new ConstructorArgumentValues();
                initiatorArgs.addGenericArgumentValue(lockRegistry);
                initiatorArgs.addGenericArgumentValue(processorLifecycle);
                lockRegistryLeaderInitiator.setConstructorArgumentValues(initiatorArgs);
                MutablePropertyValues initiatorProperties = new MutablePropertyValues();
                initiatorProperties.add(
                        "autoStartup", processorSettings.lifeCycle().autoStart());
                lockRegistryLeaderInitiator.setPropertyValues(initiatorProperties);

                var beanName = eventHandlingProcessorBeanName + "_lockRegistryLeaderInitiator";
                registry.registerBeanDefinition(beanName, lockRegistryLeaderInitiator);

                log.info(() -> "registered leader election life-cycle controller '" + beanName + "' with: "
                        + new Settings(processorSettings.lifeCycle().autoStart(), lockRegistry.toString()));
            }
        }

        @Bean
        @Scope("prototype")
        public Registration leaderElectionEventHandlingProcessorLifecycleControllerRegistration(
                LockRegistry lockRegistry) {
            return new Registration(lockRegistry);
        }
    }

    @Bean
    public InMemoryProgressTracker inMemoryProgressTracker() {
        return new InMemoryProgressTracker();
    }

    @Bean
    public PerSubjectEventSequenceResolver perSubjectEventSequenceResolver() {
        return new PerSubjectEventSequenceResolver();
    }

    @Bean
    public PerConfigurableLevelSubjectEventSequenceResolver perSecondLevelSubjectEventSequenceResolver() {
        return new PerConfigurableLevelSubjectEventSequenceResolver(2);
    }

    @Bean
    public NoEventSequenceResolver noEventSequenceResolver() {
        return new NoEventSequenceResolver();
    }
}
