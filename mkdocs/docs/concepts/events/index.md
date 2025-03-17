---
description: Capturing Significant Changes in a System
---

Events represent significant changes within a system. Unlike commands, which express an intent to perform an action, 
events record facts about what has already happened. They are immutable and serve as a reliable source of truth, 
enabling various components to react and maintain consistency in a distributed environment.

By treating events as first-class citizens, systems become more __adaptable, scalable, and resilient__, making them a 
powerful tool for modern software design.

## Key Characteristics of Events

- **Immutable** – Once an event is recorded, it cannot be changed. Any state changes must be represented as new events.
- **Ordered** – Events are typically stored and processed in the order they occurred.
- **Domain-Specific** – Events should clearly describe something meaningful in the business domain, e.g. `BookPurchasedEvent`, `ReaderRegisterEvent`, or `BookLentEvent`, ideally using past tense.

## Benefits of Using Events
- **Recording State Changes** – Instead of directly modifying state, a system captures changes as events.
- **Enabling Auditability** – A complete event history allows tracking of past actions, useful for debugging, analytics, and compliance.
- **Building Derived Data** – Events can be used to construct various views of the system’s state, optimized for different use cases.
- **Propagating Information** – Events notify other components about changes, ensuring they stay in sync.
- **Decoupled Processing** – Events enable loosely coupled components, as they allow different parts of the system to react asynchronously.

!!! note "Events and Event Sourcing"
    In some architectures, events are not just notifications but the primary source of truth.
    [Event Sourcing](../event_sourcing/index.md) uses stored events to reconstruct system state, ensuring 
    full traceability and state replay.

