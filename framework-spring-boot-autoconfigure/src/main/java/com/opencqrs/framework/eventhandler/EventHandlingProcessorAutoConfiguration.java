/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import static java.util.stream.Collectors.groupingBy;

import com.opencqrs.framework.eventhandler.partitioning.*;
import com.opencqrs.framework.eventhandler.progress.InMemoryProgressTracker;
import com.opencqrs.framework.eventhandler.progress.JdbcProgressTracker;
import com.opencqrs.framework.eventhandler.progress.ProgressTracker;
import com.opencqrs.framework.persistence.EventReader;
import com.uber.nullaway.annotations.Initializer;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
                                EventHandlingProperties.ProcessorSettings.LifeCycle::controllerFactory),
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

    /** Internal helper class for logging the effective {@link EventHandlingProcessor} bean settings. */
    record Settings(
            String group,
            int partition,
            String subject,
            boolean recursive,
            String progressTracker,
            String sequenceResolver,
            EventHandlingProperties.ProcessorSettings.Retry retry) {}

    @Bean
    public EventHandlingProcessorRegistrar eventHandlingProcessorRegistrar(
            EventHandlingProperties eventHandlingProperties,
            List<EventHandlerDefinition<?>> eventHandlerDefinitions,
            Map<String, EventHandlingProcessorLifecycleControllerFactory> lifecycleControllerFactories) {
        return new EventHandlingProcessorRegistrar(
                eventHandlingProperties, eventHandlerDefinitions, lifecycleControllerFactories);
    }

    static class EventHandlingProcessorRegistrar implements BeanFactoryAware, SmartInitializingSingleton {
        private final EventHandlingProperties eventHandlingProperties;
        private final List<EventHandlerDefinition<?>> eventHandlerDefinitions;
        private final Map<String, EventHandlingProcessorLifecycleControllerFactory> lifecycleControllerFactories;
        private BeanFactory beanFactory;
        private BeanDefinitionRegistry beanRegistry;

        EventHandlingProcessorRegistrar(
                EventHandlingProperties eventHandlingProperties,
                List<EventHandlerDefinition<?>> eventHandlerDefinitions,
                Map<String, EventHandlingProcessorLifecycleControllerFactory> lifecycleControllerFactories) {
            this.eventHandlingProperties = eventHandlingProperties;
            this.eventHandlerDefinitions = eventHandlerDefinitions;
            this.lifecycleControllerFactories = lifecycleControllerFactories;
        }

        @Override
        @Initializer
        public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            this.beanFactory = beanFactory;
            this.beanRegistry = (BeanDefinitionRegistry) beanFactory;
        }

        @Override
        public void afterSingletonsInstantiated() {
            var defaults = new EventHandlingProperties.ProcessorSettings(
                    new EventHandlingProperties.ProcessorSettings.Fetch("/", true),
                    new EventHandlingProperties.ProcessorSettings.LifeCycle(
                            true, null, null, null, DEFAULT_ACTIVE_PARTITIONS),
                    new EventHandlingProperties.ProcessorSettings.Progress(null, null),
                    new EventHandlingProperties.ProcessorSettings.Sequencing(
                            EventHandlingProperties.ProcessorSettings.Sequencing.Resolution.PER_SECOND_LEVEL_SUBJECT,
                            null),
                    new EventHandlingProperties.ProcessorSettings.Retry(
                            EventHandlingProperties.ProcessorSettings.Retry.Policy.EXPONENTIAL_BACKOFF,
                            Duration.ofMillis(ExponentialBackOff.DEFAULT_INITIAL_INTERVAL),
                            Duration.ofMillis(ExponentialBackOff.DEFAULT_MAX_INTERVAL),
                            Duration.ofMillis(ExponentialBackOff.DEFAULT_MAX_ELAPSED_TIME),
                            ExponentialBackOff.DEFAULT_MULTIPLIER,
                            ExponentialBackOff.DEFAULT_MAX_ATTEMPTS));

            var defaultProgressTracker =
                    new RuntimeBeanReference("openCqrsInMemoryProgressTracker", ProgressTracker.class);
            var defaultSequenceResolver = new RuntimeBeanReference(
                    "openCqrsPerSecondLevelSubjectEventSequenceResolver",
                    PerConfigurableLevelSubjectEventSequenceResolver.class);
            var defaultLifecycleControllerFactory = lifecycleControllerFactories.get(
                    "openCqrsSmartLifecycleEventHandlingProcessorLifecycleControllerFactory");

            eventHandlerDefinitions.stream()
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

                        var progressTracker =
                                switch (processorSettings.progress().trackerRef()) {
                                    case null ->
                                        switch (processorSettings.progress().tracking()) {
                                            case IN_MEMORY -> defaultProgressTracker;
                                            case JDBC -> new RuntimeBeanReference(JdbcProgressTracker.class);
                                            case null -> {
                                                var uniqueJdbc = beanFactory
                                                        .getBeanProvider(JdbcProgressTracker.class)
                                                        .getIfUnique();
                                                yield switch (uniqueJdbc) {
                                                    case null -> {
                                                        var beanCount =
                                                                beanFactory
                                                                        .getBeanProvider(JdbcProgressTracker.class)
                                                                        .stream()
                                                                        .count();
                                                        if (beanCount == 0) {
                                                            log.info(() ->
                                                                    "no " + JdbcProgressTracker.class.getSimpleName()
                                                                            + " bean candidates found, using 'in-memory' progress tracking for event handling processor: "
                                                                            + group);
                                                        } else {
                                                            log.warning(() -> beanCount + " ambiguous "
                                                                    + JdbcProgressTracker.class.getSimpleName()
                                                                    + " bean candidates found, falling back to 'in-memory' progress tracking for event handling processor: "
                                                                    + group);
                                                        }
                                                        yield defaultProgressTracker;
                                                    }
                                                    default -> uniqueJdbc;
                                                };
                                            }
                                        };
                                    default ->
                                        new RuntimeBeanReference(
                                                processorSettings.progress().trackerRef(), ProgressTracker.class);
                                };

                        var sequenceResolver =
                                switch (processorSettings.sequence().resolverRef()) {
                                    case null ->
                                        switch (processorSettings.sequence().resolution()) {
                                            case PER_SECOND_LEVEL_SUBJECT -> defaultSequenceResolver;
                                            case PER_SUBJECT ->
                                                new RuntimeBeanReference(
                                                        "openCqrsPerSubjectEventSequenceResolver",
                                                        PerSubjectEventSequenceResolver.class);
                                            case NO_SEQUENCE ->
                                                new RuntimeBeanReference(
                                                        "openCqrsNoEventSequenceResolver",
                                                        NoEventSequenceResolver.class);
                                            case null -> defaultSequenceResolver;
                                        };
                                    default ->
                                        new RuntimeBeanReference(
                                                processorSettings.sequence().resolverRef(),
                                                EventSequenceResolver.class);
                                };

                        DefaultPartitionKeyResolver partitionKeyResolver = new DefaultPartitionKeyResolver(
                                processorSettings.lifeCycle().partitions());
                        for (int partition = 0;
                                partition < processorSettings.lifeCycle().partitions();
                                partition++) {
                            var beanName = "openCqrsEventHandlingProcessor_" + group + "_" + partition;
                            var bd = BeanDefinitionBuilder.genericBeanDefinition(EventHandlingProcessor.class)
                                    .addConstructorArgValue(partition)
                                    .addConstructorArgValue(
                                            processorSettings.fetch().subject())
                                    .addConstructorArgValue(
                                            processorSettings.fetch().recursive())
                                    .addConstructorArgValue(new RuntimeBeanReference(EventReader.class))
                                    .addConstructorArgValue(progressTracker)
                                    .addConstructorArgValue(sequenceResolver)
                                    .addConstructorArgValue(partitionKeyResolver)
                                    .addConstructorArgValue(ehds)
                                    .addConstructorArgValue(createBackOff(processorSettings.retry()))
                                    .getBeanDefinition();
                            beanRegistry.registerBeanDefinition(beanName, bd);

                            Settings settings = new Settings(
                                    group,
                                    partition,
                                    processorSettings.fetch().subject(),
                                    processorSettings.fetch().recursive(),
                                    progressTracker.toString(),
                                    sequenceResolver.toString(),
                                    processorSettings.retry());
                            log.info(() -> "registered event handling processor '" + beanName + "' with: " + settings);

                            var lifecycleControllerFactory =
                                    switch (processorSettings.lifeCycle().controllerFactory()) {
                                        case null ->
                                            switch (processorSettings
                                                    .lifeCycle()
                                                    .controller()) {
                                                case APPLICATION_CONTEXT -> defaultLifecycleControllerFactory;
                                                case LEADER_ELECTION ->
                                                    beanFactory.getBean(
                                                            "openCqrsLeaderElectionEventHandlingProcessorLifecycleControllerFactory",
                                                            EventHandlingProcessorLifecycleControllerFactory.class);
                                                case null -> {
                                                    // indirect check if LockRegistry is available on the class-path
                                                    if (!lifecycleControllerFactories.containsKey(
                                                            "openCqrsLeaderElectionEventHandlingProcessorLifecycleControllerFactory")) {
                                                        yield defaultLifecycleControllerFactory;
                                                    } else {
                                                        if (processorSettings
                                                                        .lifeCycle()
                                                                        .lockRegistry()
                                                                != null) {
                                                            yield beanFactory.getBean(
                                                                    "openCqrsLeaderElectionEventHandlingProcessorLifecycleControllerFactory",
                                                                    EventHandlingProcessorLifecycleControllerFactory
                                                                            .class);
                                                        }
                                                        var uniqueLockRegistry = beanFactory
                                                                .getBeanProvider(LockRegistry.class)
                                                                .getIfUnique();
                                                        yield switch (uniqueLockRegistry) {
                                                            case null -> {
                                                                var beanCount =
                                                                        beanFactory
                                                                                .getBeanProvider(LockRegistry.class)
                                                                                .stream()
                                                                                .count();
                                                                if (beanCount == 0) {
                                                                    log.info(() ->
                                                                            "no " + LockRegistry.class.getSimpleName()
                                                                                    + " bean candidates found, using 'application-context' lifecycle-controller for event handling processor: "
                                                                                    + group);
                                                                } else {
                                                                    log.warning(() -> beanCount + " ambiguous "
                                                                            + LockRegistry.class.getSimpleName()
                                                                            + " bean candidates found, falling back to 'application-context' lifecycle-controller for event handling processor: "
                                                                            + group);
                                                                }
                                                                yield defaultLifecycleControllerFactory;
                                                            }
                                                            default ->
                                                                lifecycleControllerFactories.get(
                                                                        "openCqrsLeaderElectionEventHandlingProcessorLifecycleControllerFactory");
                                                        };
                                                    }
                                                }
                                            };

                                        default ->
                                            beanFactory.getBean(
                                                    processorSettings
                                                            .lifeCycle()
                                                            .controllerFactory(),
                                                    EventHandlingProcessorLifecycleControllerFactory.class);
                                    };

                            var lifecycleController = Objects.requireNonNull(lifecycleControllerFactory)
                                    .createLifecycleBeanDefinition(
                                            new RuntimeBeanReference(beanName, EventHandlingProcessor.class),
                                            processorSettings.lifeCycle());

                            beanRegistry.registerBeanDefinition(beanName + "_lifeCycle", lifecycleController);
                        }
                    });
        }
    }

    @Bean
    public EventHandlingProcessorLifecycleControllerFactory
            openCqrsSmartLifecycleEventHandlingProcessorLifecycleControllerFactory() {
        return (eventHandlingProcessorReference, lifeCycleSettings) -> BeanDefinitionBuilder.genericBeanDefinition(
                        SmartLifecycleEventHandlingProcessorLifecycleController.class)
                .addConstructorArgValue(eventHandlingProcessorReference)
                .addPropertyValue("autoStartup", lifeCycleSettings.autoStart())
                .getBeanDefinition();
    }

    @Bean
    @ConditionalOnClass(LockRegistry.class)
    public EventHandlingProcessorLifecycleControllerFactory
            openCqrsLeaderElectionEventHandlingProcessorLifecycleControllerFactory() {
        return (eventHandlingProcessorReference, lifeCycleSettings) -> {
            var lockRegistryRef =
                    switch (lifeCycleSettings.lockRegistry()) {
                        case null -> new RuntimeBeanReference(LockRegistry.class);
                        default -> new RuntimeBeanReference(lifeCycleSettings.lockRegistry(), LockRegistry.class);
                    };

            return BeanDefinitionBuilder.genericBeanDefinition(LockRegistryLeaderInitiator.class)
                    .addConstructorArgValue(lockRegistryRef)
                    .addConstructorArgValue(BeanDefinitionBuilder.genericBeanDefinition(
                                    LeaderElectionEventHandlingProcessorLifecycleController.class)
                            .addConstructorArgValue(eventHandlingProcessorReference)
                            .getBeanDefinition())
                    .addPropertyValue("autoStartup", lifeCycleSettings.autoStart())
                    .getBeanDefinition();
        };
    }

    @Bean
    public InMemoryProgressTracker openCqrsInMemoryProgressTracker() {
        return new InMemoryProgressTracker();
    }

    @Bean
    public PerSubjectEventSequenceResolver openCqrsPerSubjectEventSequenceResolver() {
        return new PerSubjectEventSequenceResolver();
    }

    @Bean
    public PerConfigurableLevelSubjectEventSequenceResolver openCqrsPerSecondLevelSubjectEventSequenceResolver() {
        return new PerConfigurableLevelSubjectEventSequenceResolver(2);
    }

    @Bean
    public NoEventSequenceResolver openCqrsNoEventSequenceResolver() {
        return new NoEventSequenceResolver();
    }
}
