---
title: Extension Points
description: Building Application Components using OpenCQRS
---

OpenCQRS offers three core extension points for application developers to implement their CQRS applications:
 
* `CommandHandler`{ title="com.opencqrs.framework.command.CommandHandler" } [definitions](command_handler/index.md) encapsulate the command handling logic for changing the system's state by publishing new events
* `StateRebuildingHandler`{ title="com.opencqrs.framework.command.StateRebuildingHandler" } [definitions](state_rebuilding_handler/index.md) support the reconstruction of write models from events, on which commands are executed
* `EventHandler`{ title="com.opencqrs.framework.eventhandler.EventHandler" } [definitions](event_handler/index.md) encapsulate event processing logic

## Command Handling

The following diagram shows s simplified command handling sequence involving the application specific extensions (highlighted in red): 

```mermaid
flowchart TB
    Start@{ shape: sm-circ }
    CommandRouter
    EventRepository
    CommandEventPublisher
    CommandHandler
    StateRebuildingHandler@{ shape: procs }
    Stop@{ shape: framed-circle }

    Start---|"(1) send command"|CommandRouter
    CommandRouter---|"(2) read events"|EventRepository
    CommandRouter---|"(3) rebuild state"|StateRebuildingHandler
    CommandRouter---|"(4) execute command"|CommandHandler
    CommandHandler---|"(5a) apply new event(s)"|StateRebuildingHandler
    CommandHandler---|"(5b) capture new event(s)"|CommandEventPublisher
    CommandEventPublisher---|"(6) write event(s)"|EventRepository
    CommandHandler---|"(7) return command result"|Stop
    
    classDef extension fill:indianred;
    class CommandHandler extension;
    class StateRebuildingHandler extension;
```

!!! tip
    For details about the command handling process, refer to [Command Router](../core_components/command_router/index.md).

## Event Processing

The following diagram shows a simplified asynchronous event processing loop involving the application specific extensions (highlighted in red):

```mermaid
flowchart TB
    Start@{ shape: sm-circ }
    EventRepository
    EventHandlingProcessor
    ProgressTracker
    EventHandler@{ shape: procs }
    
    Start---|"(1) start processing loop"|EventHandlingProcessor
    EventHandlingProcessor---|"(1) determine current progress"|ProgressTracker
    EventHandlingProcessor---|"(2) observe events"|EventRepository
    EventHandlingProcessor---|"(3a) handle event"|EventHandler
    EventHandlingProcessor---|"(3b) update progress"|ProgressTracker

    classDef extension fill:indianred;
    class EventHandler extension;
```

!!! tip
    For details about the event processing loop, refer to [Event Handling Processor](../core_components/event_handling_processor/index.md).