---
description: Event Processing
---

The `framework` [module](../../modules/index.md) provides {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }}
as the core component for [asynchronous event handling](../../extension_points/event_handler/index.md). Once executed on a worker thread it 
is responsible for dispatching any existing or newly published events _in order_ to suitable {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }}
instances for [read model projection](../../../concepts/event_sourcing/index.md#projecting-a-read-model), as depicted exemplarily in the following diagram:

![Event Dispatching](01_ehp_basic.svg)

Multiple {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} instances are typically configured and spawned for different sets
of {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} instances, belonging to the same processing group (typically one per read model). 
Each group then independently observes the event stream and processes
the events at its own speed. The {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} tracks the progress of the last event -
successfully processed by its processing group - by means of a configured {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.ProgressTracker") }}.

!!! tip "Durable Progress"
    The {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.ProgressTracker") }} is responsible for tracking the position within the
    event stream that is handled by the {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }}. Accordingly, it controls
    the event position within the event stream, at which processing continues in case of errors. In order to avoid excessive reprocessing of previously
    processed events, implementations storing their progress in persistent storage, such as a database, should be used.
    {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.JdbcProgressTracker") }} can be used for SQL databases in Spring applications.

The following diagram depicts two independent processing groups at different positions within the event stream:

![Multiple Event Processing Groups](02_ehp_groups.svg)

## Event Processing Loop

Event processing (once started) comprises the following steps:

1. determining the latest position (progress) within the global event stream up to which events have been processed previously for this group and partition, if available
2. starting to consume and observe any newer event starting from that position
3. [upcasting](../../../concepts/upcasting/index.md) and deserializing the event, if necessary
4. determining if an event needs to be handled by this partition, otherwise skipping it because it is relevant for another partition
5. determining and calling suitable {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} instances, otherwise skipping the event because it isn't relevant for the processing group
6. retrying event handlers failing with non-persistent errors
7. updating the progress within the global event stream, if all {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} instances succeeded or the event was skipped
8. continuing with next available event, until the event processing loop is interrupted or terminated

!!! warning "Event Handler Idempotency"
    Event processing logic contained within {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} instances is assumed to be idempotent, in
    order to be _repeatable_ for a specific event, in case of errors. Since the event processing loop may terminate unexpectedly, before being able to update its
    progress, resources and read models may already have been updated by some of the handlers. This is especially true for event handlers
    dealing with non-transactional resources, for instance handlers sending emails. The {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }}
    effectively guarantees [at-least-once delivery](https://www.cloudcomputingpatterns.org/at_least_once_delivery/) with respect to event handling.

    Idempotency can be achieved for {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} instances participating in the same
    transaction as the configured {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.ProgressTracker") }} instance.
    {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.JdbcProgressTracker") }} for instance can be configured to process its associated
    handlers transactionally by means of the underlying JDBC database connection, used to update the progress. If all participating handlers solely
    rely on the very same database resource, they may choose to participate within that transaction. This effectively makes the processing loop idempotent, since
    all side-effects are covered by the same transaction, guaranteing atomicity.

## Error Handling and Retry

Errors occurring within the event processing loop may either terminate the event processing loop or are subject to retry, according to the following
criteria:

| Origin                                                                      | Exception                                                                                                       | Example                                                                                                                           | Behavior                   |
|-----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|----------------------------|
| {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} | {{ javadoc_class_ref("com.opencqrs.framework.CqrsFrameworkException.NonTransientException") }} or any sub-class | event handler projection logic calling framework components throwing [non-recoverable errors](../../exceptions/index.md)          | processing loop terminated |
| {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} | any other `java.lang.Throwable` or sub-class                                                                    | any other application-specific exception thrown                                                                                   | event subject to retry     |
| framework component(s)                                                      | {{ javadoc_class_ref("com.opencqrs.framework.CqrsFrameworkException.TransientException") }} or any sub-class    | [recoverable errors](../../exceptions/index.md) such as connection errors while observing events                                  | event subject to retry  |
| any                                                                         | `java.lang.InterruptedException` or `java.util.RejectedExecutionException` or any sub-class                     | thread interruption while shutting down the {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} | processing loop terminated |
| framework component(s)                                                    | any other `java.lang.Throwable` or sub-class                                                                    | unexpected errors, for instance failure to update the progress                                                                    | processing loop terminated |

In case of a recoverable error, events may be _retried_. In that case a configurable {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.BackOff") }} instance determines
the back-off delay, before the event is actually reprocessed. This delay may change (i.e. increase exponentially) for every retry attempt for the same event and
may as well signal the event to be skipped, if `-1` is returned. Skipped events will no longer be retried and the event processing loop continues with the next
event, after updating its progress.

## Configuration

An instance of {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} can be obtained,
either by manually instantiating and starting it (per group and partition) or using Spring Boot autoconfiguration.

### Manual Configuration

An instance of {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} can be created providing the necessary
configuration properties:

* the active partition number to be assigned to (starting from zero)
* the subject to observe for existing or new events published
* a flag indicating if the subject shall be observed {{ javadoc_class_ref("com.opencqrs.esdb.client.Option.Recursive") }} or not
* an {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventReader") }} instance for observing [events](../../events/index.md)
* a {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.ProgressTracker") }} instance tracking the processing progress for the processing group and specified partition
* an {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.partitioning.EventSequenceResolver") }} instance to derive a sequence id from an event
* a {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.partitioning.PartitionKeyResolver") }} instance derive the assigned partition number from an event's sequence id
* a list of {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }} instances belonging the same processing group
* a {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.BackOff") }} instance used to determine the back-off delay before retrying failed event handlers

The following example shows, how to instantiate and start an {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} using
a provided {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventReader") }} and a list of {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }}
(containing the [event handling logic](../../extension_points/event_handler/index.md)). The example applies the following additional configuration settings:

* a single partition (`0`) is used to handle all events ({{ javadoc_class_ref("com.opencqrs.framework.eventhandler.partitioning.DefaultPartitionKeyResolver") }} with `1` active partition)
* events sharing the same subject are processed sequentially ({{ javadoc_class_ref("com.opencqrs.framework.eventhandler.partitioning.PerSubjectEventSequenceResolver") }}), which is irrelevant as long as only one active partition is used
* events are observed from the root subject `/` recursively
* progress is tracked in-memory, i.e. all events will be reprocessed upon restart of the JVM ({{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.InMemoryProgressTracker") }})
* events will be retried infinitely with a static back-off interval of 1000 ms

```java
import com.opencqrs.framework.eventhandler.partitioning.DefaultPartitionKeyResolver;
import com.opencqrs.framework.eventhandler.partitioning.PerSubjectEventSequenceResolver;
import com.opencqrs.framework.eventhandler.progress.InMemoryProgressTracker;
import com.opencqrs.framework.persistence.EventReader;

import java.util.List;

public class EventHandlingProcessorConfiguration {

    public static EventHandlingProcessor eventHandlingProcessor(
            EventReader eventReader,
            List<EventHandlerDefinition> eventHandlerDefinitions
    ) {
        return new EventHandlingProcessor(
                0,
                "/",
                true,
                eventReader,
                new InMemoryProgressTracker(),
                new PerSubjectEventSequenceResolver(),
                new DefaultPartitionKeyResolver(1),
                eventHandlerDefinitions,
                () -> () -> 1000
        );
    }
}
```

The {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} can be verified by starting it asynchronously as follows:

```java
public static void main(String[] args){
    var eventHandlingProcessor = eventHandlingProcessor(...);

    new Thread(eventHandlingProcessor).start();
}
```

### Spring Boot Auto-Configuration

For Spring Boot applications using the `framework-spring-boot-starter` [module](../../modules/index.md)
{{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessorAutoConfiguration") }} provides
fully configured {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} Spring beans per
processing group for all {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }} beans
defined within the same Spring application context.

The processor beans can be fully configured using {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProperties") }}.
The configuration provides suitable defaults, which may be overridden for all processing groups and/or per individual processing group,
e.g. as follows:

```properties
# settings applying to all processing groups
opencqrs.event-handling.standard.retry.policy=exponential_backoff
opencqrs.event-handling.standard.retry.max-attempts=5

# settings applying to processing group 'books' only
opencqrs.event-handling.groups.books.fetch.subject=/books
opencqrs.event-handling.groups.books.life-cycle.partitions=2

# settings applying to processing group 'backup' only
opencqrs.event-handling.groups.backup.life-cycle.auto-start=false
```

With that configuration in place autoconfigured {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} instances
will be configured for all processing groups (based on the {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }} beans within the Spring context) as follows:

* all failed events will be retried up to 5 times with exponentially increasing delays
* all processors, except those for the 'backup' processing group, will be started automatically
* all processors, except those for the 'books' processing group, will observe events from `/` recursively
* for the 'books' processing group:
    * events will be observed from `/books` only
    * two processor instances will be spawned each of them processing one of the two configured partitions independently

!!! info "Property Inheritance"
    {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProperties") }} provide configuration on different levels in the
    following order of precedence:
    
    1. properties defined per processing group, i.e. using the prefix `opencqrs.event-handling.groups.<processing-group>`
    2. properties defined for all processing groups, i.e. using the prefix `opencqrs.event-handling.standard`
    3. built-in default values, which may vary dependending on the beans available within the Spring application context

The following table lists all {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProperties") }} that can be
configured per processing group or for all groups and their default values (prefix ommitted for brevity):

| property                             | description                                                                                                                                                       | default value(s)                                                                                                                                                                                                                                                   |
|--------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `fetch.subject`                      | the root subject to fetch/observe                                                                                                                                 | `/`                                                                                                                                                                                                                                                                |
| `fetch.recursive`                    | whether events are fetched recursively with respect to the root subject                                                                                           | `true`                                                                                                                                                                                                                                                             |
| `life-cycle.auto-start`              | whether the processor is started automatically                                                                                                                    | `true`                                                                                                                                                                                                                                                             |
| `life-cycle.controller`              | whether the processor is activated (and shut down) by the Spring application context or using distributed leader election                                         | `leader_election` if a suitable Spring Integration `org.springframework.integration.support.locks.LockRegistry` bean has been defined, `application_context` otherwise                                                                                             |
| `life-cycle.controller-registration` | a custom {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessorLifecycleRegistration") }} bean reference overriding `life-cycle.controller` | n/a                                                                                                                                                                                                                                                                |
| `life-cycle.lock-registry`           | a custom Spring Integration `org.springframework.integration.support.locks.LockRegistry` bean reference to use if `life-cycle.controller` is `leader_election`    | n/a                                                                                                                                                                                                                                                                |
| `life-cycle.partitions`              | the number of parallel partitions used to process the event stream                                                                                                | 1                                                                                                                                                                                                                                                                  |
| `progress.tracking`                  | the progress tracker implementation to be used                                                                                                                    | `jdbc` for _persistent (durable) progress tracking_ if a unique {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.JdbcProgressTracker") }} bean is available within the application context, `in_memory` for transient progress tracking otherwise |
| `progress.tracker-ref`               | a custom {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.ProgressTracker") }} bean reference overriding `progress.tracking`                    |                                                                                                                                                                                                                                                                    |
| `sequence.resolution`                | specifies how the _event sequence identifier_ is resolved in order to determine whether a partition is responsible for processing an event                        | `per_second_level_subject` referering to event subject up to the second level, e.g. `/books/4711`, ignoring any deeper subject hierarchies                                                                                                                         |
| `sequence.resolver-ref`              | a custom {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.partitioning.EventSequenceResolver") }} bean reference overriding `sequence.resolution`        | n/a                                                                                                                                                                                                                                                                |
| `retry.policy`                       | specifies how failed events are retried                                                                                                                           | `exponential_backoff`                                                                                                                                                                                                                                              |
| `retry.initial-interval`             | the initial delay to wait before retrying a failed event unless `retry.policy` is `none`                                                                          | `2s`                                                                                                                                                                                                                                                               |
| `retry.max-interval`                 | the maximum delay to wait before retrying a failed event (only relevant if `retry.policy` is `exponential_backoff`)                                               | `30s`                                                                                                                                                                                                                                                              |
| `retry.max-elapsed-time`             | the maximum cumulated day for all retries before giving up on retrying a failed event (only relevant if `retry.policy` is `exponential_backoff`)                  | ∞                                                                                                                                                                                                                                                                  |
| `retry.multiplier`                   | the multiplier applied to the retry interval upon repeated attempts (only relevant if `retry.policy` is `exponential_backoff`)                                                                                              | `1.5`                                                                                                                                                                                                                                                              |
| `retry.max-attempts`                 | the maximum number of retry attempts before giving up on retrying a failed event unless `retry.policy` is `none`                                                  | ∞                                                                                                                                                                                                                                                            |

## Scalability

{{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} instances may be scaled individually per
processing group using multiple partitions. This enables multiple {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} instances -
one for each partition - to process the same event stream in parallel. For this to work out, each of them needs
to know both its own partition number and the number of total partitions. With the help of the configured {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.partitioning.EventSequenceResolver") }}
it determines each event's _sequence id_, before it gets processed within the event processing loop. This id is mapped onto a target
partition number using a deterministic algorithm. Hence, each processor instance may decide, whether it is responsible for processing a specific
event, autonomously, skipping any events from other partitions. That is, while all processors observe the same event stream, they only handle those events
relevant for their own partition.

!!! danger "Configuring multiple partitions"
    Configuring multiple partitions per processing group implies, that progress is tracked per partition, as well. Accordingly,
    tracked event positions will differ, as processors proceed at different speeds. It is therefore not advised to increase or
    decrease the number of partitions for processing groups using durable (persistent) progress tracking, as this would require
    merging or splitting the partitions in a controlled manner. {{ custom.framework_name }} currently does not support dynamic
    up- or downscaling of partitions.

## Fail-Over and Redundancy

While {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} instances can be scaled using partitioning,
it must be assured that exactly one instance is running per processing group and partition. More instances processing the same partition will
result in duplicate event processing, which may cause undesirable results, if the event handlers aren't fully idempotent, for instance sending
duplicate emails to customers. An unclaimed partition currently not processed by any processor instance, on the other hand, may result in events
not being processed at all.

!!! tip "Redundant JVMs"
    To assure, there are always sufficient {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} instances
    available for all partitions of all processing groups, multiple JVMs should be used to compensate for errors. Typically, this is
    achieved by cloud infrastructure, such as Kubernetes.

To ensure the number of active {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} instances matches the number
of processing groups and their partitions, their life-cycle needs to controlled even across multiple JVMs. {{ custom.framework_name }} provides
out of the box life-cycle management based on [Spring Integration Leadership Event Handling](https://docs.spring.io/spring-integration/reference/leadership-event-handling.html),
which is based on the concept of _distributed locks_, for instance using JDBC and a dedicated SQL lock table.

The following diagram depicts multiple {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} instances running on
different JVMs, processing a `books` processing group with two partitions.

![Scaling & Failover](03_ehp_partitioning_failover.svg)

- Each of the JVMs provides sufficient instances to process the maximum number of configured partitions.
- However, each of those instances needs to claim a distributed lock per partition, in order to become the leader responsible for processing.
- Currently, the locked/active partitions are evenly distributed across the two JVMs.
- Each partition tracks its own progress within the event stream.
- The inactive instances currently waiting for a lock to become available are spare instances, which will be activated, if a leader stops or fails unexpectedly.
- Events are filtered according to their sequence id, in order to determine, if they are relevant for the current partition, and skipped otherwise.

In case any of the two JVMs is shut down or fails unexpectedly, the spare instances will claim the expired locks and continue processing, starting
at the latest event position stored by the configured {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.ProgressTracker") }}.

!!! note "Active Partition Distribution"
    {{ custom.framework_name }} currently won't limit the number of maximum concurrent active partitions per JVM. Accordingly, their
    distribution cannot be controlled either. Most likely, the first JVM starting within a clustered environment will claim all locks
    and hence process all partitions, while any further JVM will provide spare instances only.
