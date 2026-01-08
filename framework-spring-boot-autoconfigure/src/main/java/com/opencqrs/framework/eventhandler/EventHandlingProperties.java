/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import com.opencqrs.framework.eventhandler.partitioning.EventSequenceResolver;
import com.opencqrs.framework.eventhandler.partitioning.NoEventSequenceResolver;
import com.opencqrs.framework.eventhandler.partitioning.PerConfigurableLevelSubjectEventSequenceResolver;
import com.opencqrs.framework.eventhandler.partitioning.PerSubjectEventSequenceResolver;
import com.opencqrs.framework.eventhandler.progress.InMemoryProgressTracker;
import com.opencqrs.framework.eventhandler.progress.JdbcProgressTracker;
import com.opencqrs.framework.eventhandler.progress.ProgressTracker;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for {@linkplain EventHandlingProcessorAutoConfiguration auto-configured}
 * {@link EventHandlingProcessor}s. The {@link ProcessorSettings} defined within this class enable overriding of the
 * {@linkplain EventHandlingProcessorAutoConfiguration built-in defaults}.
 *
 * @param standard Default settings valid for all event processing groups, unless overridden using "groups".
 * @param groups Event processing group specific override settings. Merged with "standard" settings.
 */
@ConfigurationProperties("opencqrs.event-handling")
public record EventHandlingProperties(ProcessorSettings standard, Map<String, ProcessorSettings> groups) {

    /**
     * Configuration settings for an instance of {@link EventHandlingProcessor}.
     *
     * @param fetch Event fetching settings.
     * @param lifeCycle Life-cycle configuration settings.
     * @param progress Progress tracking configuration settings.
     * @param sequence Event sequence resolution configuration.
     * @param retry Error retry configuration.
     */
    public record ProcessorSettings(
            Fetch fetch, LifeCycle lifeCycle, Progress progress, Sequencing sequence, Retry retry) {
        /**
         * Configures event stream fetching.
         *
         * @param subject The subject to fetch events from.
         * @param recursive Whether events shall be fetched recursively with respect to the specified "subject".
         */
        public record Fetch(String subject, Boolean recursive) {}

        /**
         * Configures {@linkplain EventHandlingProcessorLifecycleRegistration life-cycle registration}.
         *
         * @param autoStart Whether the event handling processor shall be started automatically.
         * @param controller The built-in life-cycle controller to use, unless "controller-registration" is specified.
         *     Defaults to "leader-election", if a unique "lock-registry" bean is available within the application
         *     context, "application-context" otherwise.
         * @param controllerRegistration Custom {@link EventHandlingProcessorLifecycleRegistration} bean reference to
         *     use.
         * @param lockRegistry Custom lock registry to use, if "leader-election" applies.
         * @param partitions The number of parallel instances (partitions) to start for this event processor. <strong>It
         *     is important to note, that partitioning of {@link EventHandlingProcessor}s cannot be changed later,
         *     unless the processor is completely transient, i.e. it's side-effects are completely idempotent and no
         *     persistent progress tracking is needed. In all other cases, starting a partitioned processor results in
         *     partitioned {@link Progress} and parallel processing will {@linkplain ProgressTracker#proceed(String,
         *     long, Supplier) proceed the progress} independently according to the configured {@link Sequencing}.
         *     Hence, reducing or increasing the number of partitions must only be performed administratively, while all
         *     instances are stopped and have the same progress. Furthermore, persistent progress needs to be duplicated
         *     for new partitions, manually.</strong>
         */
        public record LifeCycle(
                Boolean autoStart,
                Controller controller,
                String controllerRegistration,
                String lockRegistry,
                Long partitions) {

            /** The built-in {@link EventHandlingProcessorLifecycleController} type. */
            public enum Controller {

                /**
                 * Life-cycle is controlled by the {@link org.springframework.context.ApplicationContext}.
                 *
                 * @see SmartLifecycleEventHandlingProcessorLifecycleController
                 */
                APPLICATION_CONTEXT,

                /**
                 * Life-cycle is controlled by a
                 * {@link org.springframework.integration.support.leader.LockRegistryLeaderInitiator} bean using an
                 * underlying {@link org.springframework.integration.support.locks.LockRegistry}.
                 *
                 * @see LeaderElectionEventHandlingProcessorLifecycleController
                 */
                LEADER_ELECTION,
            }
        }

        /**
         * Configures which {@link ProgressTracker} to use.
         *
         * @param tracking The built-in progress tracker to use, unless "tracker-ref" is specified. Defaults to "jdbc",
         *     if a unique {@link JdbcProgressTracker} bean is available within the application context, "in-memory"
         *     otherwise.
         * @param trackerRef Custom progress tracker to use.
         */
        public record Progress(Tracker tracking, String trackerRef) {
            /** The built-in {@link ProgressTracker} type. */
            public enum Tracker {

                /**
                 * Progress is tracked in-memory.
                 *
                 * @see InMemoryProgressTracker
                 */
                IN_MEMORY,

                /**
                 * Progress is tracked using a JDBC data-source.
                 *
                 * @see JdbcProgressTracker
                 */
                JDBC,
            }
        }

        /**
         * Configures which {@link EventSequenceResolver} to use. <strong>As described for
         * {@link LifeCycle#partitions()} this configuration should be changed carefully, once partitioned
         * {@link EventHandlingProcessor} instances have been started.</strong>
         *
         * @param resolution The built-in event sequence resolution to use, unless "resolver-ref" is specified.
         * @param resolverRef Custom resolver to use.
         */
        public record Sequencing(Resolution resolution, String resolverRef) {

            /** The built-in {@link EventSequenceResolver} type. */
            public enum Resolution {

                /**
                 * (Partitioned) instances process events without any pre-defined sequence.
                 *
                 * @see NoEventSequenceResolver
                 */
                NO_SEQUENCE,

                /**
                 * (Partitioned) instances process events in-order per (same) subject.
                 *
                 * @see PerSubjectEventSequenceResolver
                 */
                PER_SUBJECT,

                /**
                 * (Partitioned) instances process events in-order per second-level subject, i.e. using "/book/4711" as
                 * the id for subject "/book/4711/pages/43".
                 *
                 * @see PerConfigurableLevelSubjectEventSequenceResolver
                 */
                PER_SECOND_LEVEL_SUBJECT,
            }
        }

        /**
         * Error handling retry configuration settings. Configuration includes back-off settings, to let erroneous event
         * handlers recover from (transient) errors between retries. <b>For {@link Policy#NONE} all other settings are
         * ignored.</b>
         *
         * @param policy The retry policy to apply in case of errors.
         * @param initialInterval The initial back-off interval. Defaults to
         *     {@link org.springframework.util.backoff.ExponentialBackOff#DEFAULT_INITIAL_INTERVAL}.
         * @param maxInterval The maximum back-off interval. Defaults to
         *     {@link org.springframework.util.backoff.ExponentialBackOff#DEFAULT_MAX_INTERVAL}.
         * @param maxElapsedTime The maximum elapsed time duration before retry is cancelled. Defaults to
         *     {@link org.springframework.util.backoff.ExponentialBackOff#DEFAULT_MAX_ELAPSED_TIME} ms.
         * @param multiplier The time interval multiplier used "exponential_backoff". Defaults to
         *     {@link org.springframework.util.backoff.ExponentialBackOff#DEFAULT_MULTIPLIER}.
         * @param maxAttempts The maximum number of attempts before retry is cancelled. Defaults to
         *     {@link org.springframework.util.backoff.ExponentialBackOff#DEFAULT_MAX_ATTEMPTS}.
         */
        public record Retry(
                Policy policy,
                Duration initialInterval,
                Duration maxInterval,
                Duration maxElapsedTime,
                Double multiplier,
                Long maxAttempts) {

            /** The back-off policy. */
            public enum Policy {
                /** No back-off. */
                NONE,

                /** Back-off using fixed time interval. */
                FIXED,

                /** Back-off using exponential time interval. */
                EXPONENTIAL_BACKOFF;
            }
        }
    }
}
