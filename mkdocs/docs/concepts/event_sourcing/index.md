---
description: Reconstructing State from Events
---

Event Sourcing is a design pattern in which all changes to the state of a system are captured as a sequence of 
immutable [events](../events/index.md). Instead of storing only the current state, the system persists every state-changing event, 
allowing the entire history to be reconstructed at any point in time.

By focusing on events as the primary source of truth, however, the current state needs to be reconstructed from the event
history both for applying further changes to the system's state and for querying it. In combination with CQRS this
is referred to as reconstruction of the __write model__, for applying state changes using commands, and projection of __read models__ for
querying, respectively.

## Reconstructing the Write Model

The information needed to decide, whether a state change can be applied to the system, is referred to as __write model__.
In order to reconstruct it from the events stored within the system, not the entire event history is actually required. This is
due to the fact, that changes are applied to a specific instance of a domain object, e.g. a book copy. Thus, only those
events related to that specific instance, are actually needed to reconstruct its state, e.g. all event belonging to `Book A`
as shown below:

``` mermaid
%%{init: { 'logLevel': 'debug', 'theme': 'default', "themeVariables": {
    "cScale0": "olive",
    "cScale2": "olive",
    "cScale4": "olive"
 } } }%%
timeline
    Book A: BookPurchasedEvent
    Book B: BookPurchasedEvent
    Book A: BookLentEvent
    Reader X: ReaderRegisteredEvent
    Book A: BookReturnedEvent
```

Accordingly, sourcing events to reconstruct the write model for a specific domain object represents a filtered query
of the entire event history, with the domain object identifier as the filter criteria. All relevant events are then
combined (in order) to reproduce the write model, before any new changes are applied.

!!! info
    As long as the maximum number of events per domain object is feasible, this operation can be performed on-demand
    resulting in an in-memory write model representation, no matter how big the entire event history actually is.

## Projecting a Read Model

The goal of a _read model_ is not to support command execution, but to expose the event history to the outside world in
a suitable representation, the so-called __projection__. Generally, these come in two flavors:

1. persistent state representations stored within a dedicated datastore for querying, e.g. a SQL database
2. changes applied to other systems, e.g. mails sent to registered users or REST API calls made to third-party systems

Both have in common, that the projected data isn't limited to dedicated domain object instances. Therefore, the entire
event history has to be processed to project a read model. However, the projection may only be interested in certain
event types, e.g. all `BookLentEvent` and `BookReturnedEvent` instances to track the book availability.

Accordingly, read model projections usually use some kind of (persistent) progress tracking, in order to avoid
re-projecting the entire event history repeatedly, both to avoid duplicate changes applied to other systems and to
guarantee stable performance.