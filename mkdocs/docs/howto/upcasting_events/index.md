---
description: How to evolve events using event upcasters
---

[Events](../../concepts/events/index.md) may evolve over time, requiring them to be migrated. As they are
immutable, they cannot be altered within the event store. [Event upcasting](../../concepts/upcasting/index.md)
resolves this enabling the developer to migrate events on-demand, that is in-memory, whenever an older version
of an event is loaded.

Implementations of {{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcaster") }} may be registered with
the {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }} to migrate raw
{{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }}s prior to mapping them to their appropriate Java event type.

## Custom Upcasters

Custom {{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcaster") }}s should be implemented by extending
{{ javadoc_class_ref("com.opencqrs.framework.upcaster.AbstractEventDataMarshallingEventUpcaster") }}. This relieves
the developer from dealing with the raw { javadoc_class_ref("com.opencqrs.esdb.client.Event") }} `data` and instead 
accessing the unmarshalled [payload and metata](../../reference/events/index.md#event-payload-and-metadata) directly.

The following example shows how to implement an upcaster, which upcasts events of type `com.opencqrs.library.book.purchased.v1`
to `com.opencqrs.library.book.purchased.v2` by renaming the `payload` attribute `name` to `surname`:

```java linenums="1" hl_lines="17 22-32"
import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.serialization.EventDataMarshaller;
import com.opencqrs.framework.upcaster.AbstractEventDataMarshallingEventUpcaster;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class BookPurchasedEventUpcaster extends AbstractEventDataMarshallingEventUpcaster {

    protected BookPurchasedEventUpcaster(EventDataMarshaller eventDataMarshaller) {
        super(eventDataMarshaller);
    }

    @Override
    public boolean canUpcast(Event event) {
        return event.type().equals("com.opencqrs.library.book.purchased.v1");
    }

    @Override
    protected Stream<MetaDataAndPayloadResult> doUpcast(Event event, Map<String, ?> metaData, Map<String, ?> payload) {
        Map<String, Object> newPayload = new HashMap<>(payload);
        newPayload.put("surname", payload.get("name"));
        newPayload.remove("name");
        
        return Stream.of(
                new MetaDataAndPayloadResult(
                        "com.opencqrs.library.book.purchased.v2",
                        metaData,
                        newPayload
                )
        );
    }
}
```

!!! danger "Avoiding Infinite Recursion"
    As there will be multiple {{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcaster") }}s within an application
    over time, it is essential to understand, that these will be applied to any event that they `canUpcast`. Accordingly, it is up
    to the developer to avoid infinite recursion, for instance by returning the same _unaltered_ event type or by providing
    different {{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcaster") }}s effectively switching types.

## Built-In Upcasters

{{ custom.framework_name }} provides the following built-in {{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcaster") }} implementations:

* {{ javadoc_class_ref("com.opencqrs.framework.upcaster.NoEventUpcaster") }} which __evicts__ an event matching the configured type, effectively removing it from the event stream with respect to the application code
* {{ javadoc_class_ref("com.opencqrs.framework.upcaster.TypeChangingUpcaster") }} which only changes the event's `type`, assuming no other changes need to be applied

## Registration

{{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcaster") }} instances can be defined as Spring Beans within the application context (1), 
for instance within a dedicated `OpenCqrsFrameworkConfiguration`, as follows:
{ .annotate }

1. Refer to [manual configuration](../../reference/core_components/event_repository/index.md#manual-configuration) if you want to register it manually with the {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }}.

```java hl_lines="17-20"
package com.opencqrs.example.configuration;

import com.opencqrs.framework.serialization.EventDataMarshaller;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class OpenCqrsFrameworkConfiguration {

    @Bean
    public BookPurchasedEventUpcaster bookPurchasedEventUpcaster(EventDataMarshaller eventDataMarshaller) {
        return new BookPurchasedEventUpcaster(eventDataMarshaller);
    }
}
```

!!! tip
    Alternatively, {{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcaster") }} implementations may simply be annotated
    using Spring's `@Component` annotation to be recognized as part of Spring's classpath component scanning.
