---
description: Command Routing
---

The `framework` [module](../../modules/index.md) provides {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }}
as the core component responsible for [command execution](../../extension_points/command_handler/index.md). Command execution can be triggered via one of the `send()` methods, providing a
{{ javadoc_class_ref("com.opencqrs.framework.command.Command") }} instance and an optional command meta-data map. It comprises the following steps:

1. routing the {{ javadoc_class_ref("com.opencqrs.framework.command.Command") }} instance to the responsible {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler") }} by means of a suitable {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}
2. reading the relevant, upcasted Java event objects using the [event repository](../event_repository/index.md)
3. reconstructing the command execution state using the {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler") }} instances defined within the configured {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }} instances
4. optionally caching the reconstructed state for future command executions
5. executing the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler") }} and capturing any Java object events published via the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandEventPublisher") }} and the optional command result (or exception)
6. publishing the captured events atomically using the [event repository](../event_repository/index.md) with suitable preconditions to avoid inconsistent writes due to race conditions

## Configuration

An instance of {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} can be obtained,
either by manually instantiating it or using Spring Boot autoconfiguration.

### Manual Configuration

An instance of {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} can be created providing the necessary
configuration properties:

* an {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventReader") }} instance for reading [events](../../events/index.md)
* an {{ javadoc_class_ref("com.opencqrs.framework.persistence.ImmediateEventPublisher") }} instance for publishing new Java object events
* a list of {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }} instances
* a list of {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }} instances
* an optional {{ javadoc_class_ref("com.opencqrs.framework.command.cache.StateRebuildingCache") }} instance for caching reconstructed states
* an optional {{ javadoc_class_ref("com.opencqrs.framework.metadata.PropagationMode") }} specifying, if and how command meta-data is to be propagated to the published events
* an optional set `java.util.Set<String>` specifying, which command meta-data keys (and their corresponding values) to propagate to the published events

The following example shows, how to instantiate a {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} using a provided
{{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }}, a list of {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}
(containing the [command handling logic](../../extension_points/command_handler/index.md)),
and a list of {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}
(containing the [state rebuilding logic](../../extension_points/state_rebuilding_handler/index.md)).

```java
package com.opencqrs.framework.command;

import com.opencqrs.framework.persistence.EventRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommandRouterConfiguration {

    public static CommandRouter commandRouter(
            EventRepository eventRepository,
            List<CommandHandlerDefinition> commandHandlerDefinitions,
            List<StateRebuildingHandlerDefinition> stateRebuildingHandlerDefinitions
    ) {
        return new CommandRouter(
                eventRepository,
                eventRepository,
                commandHandlerDefinitions,
                stateRebuildingHandlerDefinitions
        );
    }
}
```

The correct configuration can be confirmed, by sending a suitable command (covered by any of the supplied {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}) as follows:

```java
public static void main(String[] args){
    var commandRouter = commandRouter(...);

    commandRouter.send(new MyCommand(42L)); // MyCommand class omitted for brevity
}
```

### Spring Boot Auto-Configuration

For Spring Boot applications using the `framework-spring-boot-starter` [module](../../modules/index.md)
{{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouterAutoConfiguration") }} provides a fully configured
{{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} Spring bean. The bean can be configured
with respect to instance caching and meta-data propagation using the properties defined within
{{ javadoc_class_ref("com.opencqrs.framework.command.cache.CommandHandlingCacheProperties") }} and
{{ javadoc_class_ref("com.opencqrs.framework.metadata.MetaDataPropagationProperties") }}, respectivly,
e.g. as follows:

```properties
opencqrs.command-handling.cache.type=in_memory
opencqrs.metadata.propagation.keys=requestId
```

!!! note
    {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouterAutoConfiguration") }} automatically picks up
    all Spring beans of type {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }} and
    {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}, respectively. Those
    will be used to [rebuild the write model prior to command execution](../../../concepts/event_sourcing/index.md#reconstructing-the-write-model)
    and [executing command handlers](../../extension_points/command_handler/index.md).

With that configuration in place the autoconfigured {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} instance
can be auto-wired within any other Spring bean, if needed. The configuration can be further customized by:

* overriding the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} Spring bean with an application-defined one
* by providing a custom {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }}