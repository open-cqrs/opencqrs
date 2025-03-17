---
description: Managing event evolution using ad-hoc migrations
---

[Events](../events/index.md) are _immutable_ by definition, since they __do not__ describe the status quo,
but something that happened in the past and thus can no longer be changed.

This accounts for all the information associated with an event, that is:
 
* its application-specific payload
* any application-specific meta-data
* any meta-data originating from the event store, for instance assigned id, time, etc.

Software, in contrast to that, is flexible and will be subject to change, sooner or later.
This may affect the classes and data structures representing the events in-memory, 
since most likely no application will operate directly on the raw event data. Such changes
typically include:

| Change      | Example                                                       |
|-------------|---------------------------------------------------------------|
| structural  | renamed attributes                                            |
| data type   | conversion from number to string                              |
| semantics   | changed interpretation of content/values                      |
| granularity | two separate events formerly expressed as one (or vice versa) |


??? note "Mutable data stores"
    While the immutability of events enforces us to deal with these changes, they
    are not restricted to event-sourced applications. Applications using mutable data
    stores, such as SQL databases, also have to _update_ their
    storage schema or _migrate_ the data stored therein, if necessary.

## Versioning

As events change or evolve over time, it is essential to know which _version_ of the event
needs to be mapped to which class representation. While one could introduce a new class for
every possible change, this would result in a massive maintenance overhead within the remaining
code base interacting with those events. More likely, events sharing a common semantic, such as
a `BookPurchasedEvent` should share the same class, even if the event changed over time.
The following diagram shows a scenario, where the raw events __A__ and __B__ evolved over
time (into _compatible_ __A'__, __A''__, and __B'__), still they should be mapped to the same Java class, when loaded:

``` mermaid
block-beta
    columns 1
    block:stream
        1["A"] space 2["B"] space 3["A'"] space 4["B"] space 5["C"] space 6["B'"] space 7["A''"]
    end
    
    1 --> 2
    2 --> 3
    3 --> 4
    4 --> 5
    5 --> 6
    6 --> 7
    
    space:2
    
    block:events
        BookPurchasedEvent space BookLentEvent space BookReturnedEvent
    end
    
    1 --> BookPurchasedEvent
    3 --> BookPurchasedEvent
    7 --> BookPurchasedEvent
    2 --> BookLentEvent
    4 --> BookLentEvent
    6 --> BookLentEvent
    5 --> BookReturnedEvent

    classDef classA fill:olive;
    class 1,3,7,BookPurchasedEvent classA
    classDef classB fill:navy;
    class 2,4,6,BookLentEvent classB
    classDef classC fill:sienna;
    class 5,BookReturnedEvent classC
```

Events __A__, __A'__, and __A''__, or __B__ and __B'__ respectively, can be considered different
_versions_ of the same event _type_. These should be stored within the event store upon
publishing new events for future read operations.
!!! note "Type vs. Version"
    An event store does not necessarily have to support both - _type_ and _version_ - for
    storing events. It is sufficient to encode an event version as part of the type identifier,
    for instance: `com.opencqrs.library.book.purchased.v1`. Reducing the evolutionary nature
    of events to a simple type identifier also bears the advantage, that the event store
    does not have to take care of event compatibility, as it considers each new version a
    different type.

## Upcasting

With the aforementioned type information stored as part of the raw events, it is possible to
migrate them to their newest version, before actually mapping them to the Java classes. This
process is called __upcasting__.

The so-called upcasters are programmed to transform raw events from any older _version_
or _type_ to a newer one. They are executed in an ad-hoc fashion, whenever an event
needs to be migrated, while the result of the transformation is never persisted. Accordingly,
the list of required upcasters is compounding over time, as the system evolves.

Moreover, upcasters are considered simple functions, transforming a single event into its
new representation (which may be that of two separate events as well). Accordingly, they
are usually chained to reduce the permutational complexity of having to maintain upcasters
for each combination of old and new version. The following diagram shows how such an 
_upcaster chain_ integrates with the process of loading events from the event store:

``` mermaid
block-beta
    columns 1
    block:stream
        1["A"] space 2["B"] space 3["A'"] space 4["B"] space 5["C"] space 6["B'"] space 7["A''"]
    end
    
    1 --> 2
    2 --> 3
    3 --> 4
    4 --> 5
    5 --> 6
    6 --> 7
    
    space:2
    
    block:upcasters
        U1["A to A'"] space U2["A' to A''"] space U3["B to B'"]
    end
    
    space:2
    
    block:events
        BookPurchasedEvent space BookLentEvent space BookReturnedEvent
    end
    
    1 --> U1 
    U1 --> U2 
    U2 --> BookPurchasedEvent
    3 --> U2
    7 --> BookPurchasedEvent
    2 --> U3
    U3 --> BookLentEvent
    4 --> U3
    6 --> BookLentEvent
    5 --> BookReturnedEvent

    classDef classA fill:olive;
    class 1,3,7,U1,U2,BookPurchasedEvent classA
    classDef classB fill:navy;
    class 2,4,6,U3,BookLentEvent classB
    classDef classC fill:sienna;
    class 5,BookReturnedEvent classC
```

With that mechanism in place, it is possible to evolve events over time, still preserving
the immutability of an event store.
