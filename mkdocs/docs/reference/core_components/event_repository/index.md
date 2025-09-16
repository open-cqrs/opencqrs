---
description: Java event persistence
---

The `framework` [module](../../modules/index.md) provides helper classes, encapsulating the persistence
of Java events. This relieves the user from:

* using the low-level [ESDB Client](../esdb_client/index.md) API directly
* dealing with [client-specific exceptions](../../exceptions/index.md#client-exceptions) in favor of [framework exceptions](../../exceptions/index.md#framework-exceptions)
* [upcasting events](../../../concepts/upcasting/index.md) older event representations if needed
* dealing with `payload` and `metaData` representation within [events](../../events/index.md)
* mapping JSON to Java (event) objects and vice versa, by applying an appropriate {{ javadoc_class_ref("com.opencqrs.framework.types.EventTypeResolver") }}

The {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventReader") }} interface defines operations for reading
events from the {{ esdb_ref() }}, including:

* reading raw {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }}s
* reading upcasted {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }}s
* reading Java event objects and their optional meta-data from {{ javadoc_class_ref("com.opencqrs.framework.serialization.EventData") }}

The {{ javadoc_class_ref("com.opencqrs.framework.persistence.ImmediateEventPublisher") }} interface defines operations for publishing
events to the {{ esdb_ref() }}, including:

* atomically publishing one or more Java event objects and their optional meta-data
* specifying additional preconditions which must be fulfilled when publishing

All these operations are implemented within the {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }} class.
Suitable [framework exceptions](../../exceptions/index.md#framework-exceptions) are thrown in case of an error.

## Configuration

An instance of {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }} can be obtained, 
either by manually instantiating it or using Spring Boot autoconfiguration.

!!! tip
    In either case it is required to make sure [Jackson Databind](https://github.com/FasterXML/jackson-databind) is
    included as dependency, unless a custom {{ javadoc_class_ref("com.opencqrs.framework.serialization.EventDataMarshaller") }} 
    implementation is used.

### Manual Configuration

An instance of {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }} can be created providing the necessary
configuration properties:

* an {{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbClient") }} instance for reading and publishing [events](../../events/index.md)
* an {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventSource") }} instance encapsulating the [`source`](../../events/index.md#events-and-eventcandidates) to be published
* an {{ javadoc_class_ref("com.opencqrs.framework.types.EventTypeResolver") }} instance used for resolving [event types](../../events/index.md#events-and-eventcandidates)
* an {{ javadoc_class_ref("com.opencqrs.framework.serialization.EventDataMarshaller") }} instance for serializing and deserializing [event payloads and meta-data](../../events/index.md#event-payload-and-metadata)
* an {{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcasters") }} instance optionally containing {{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcaster") }} instances for [event upcasting](../../../concepts/upcasting/index.md)

The following example shows, how to instantiate an {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }} using
a provided {{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbClient") }} instance, the built-in {{ javadoc_class_ref("com.opencqrs.framework.types.ClassNameEventTypeResolver") }},
the built-in {{ javadoc_class_ref("com.opencqrs.framework.serialization.JacksonEventDataMarshaller") }}, and an empty instance
of {{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcasters") }} (i.e. containing no upcasters):

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.esdb.client.EsdbClient;
import com.opencqrs.esdb.client.Option;
import com.opencqrs.framework.serialization.JacksonEventDataMarshaller;
import com.opencqrs.framework.types.ClassNameEventTypeResolver;
import com.opencqrs.framework.upcaster.EventUpcasters;

import java.util.Set;

public class EventRepositoryConfiguration {

    public static EventRepository eventRepository(EsdbClient esdbClient) {
        return new EventRepository(
                esdbClient,
                new EventSource("http://my-service.com"),
                new ClassNameEventTypeResolver(EventRepositoryConfiguration.class.getClassLoader()),
                new JacksonEventDataMarshaller(new ObjectMapper()),
                new EventUpcasters()
        );
    }
}
```

The correct configuration can be confirmed, for instance by calling `readAsObject()` as follows:

```java
public static void main(String[] args){
    EsdbClient client = ...;

    EventRepositoryConfiguration
            .eventRepository(client)
            .readAsObject("/books", Set.of(new Option.Recursive()))
            .forEach(System.out::println);
}
```

### Spring Boot Auto-Configuration

For Spring Boot applications using the `framework-spring-boot-starter` [module](../../modules/index.md)
and [Jackson Databind](https://github.com/FasterXML/jackson-databind)
{{ javadoc_class_ref("com.opencqrs.framework.persistence.EventPersistenceAutoConfiguration") }} provides both
an {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventSource") }} Spring bean configured based on the
`spring.application.name` and a fully configured {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }} 
Spring bean.

With that configuration in place the auto-configured {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }} instance
can be auto-wired within any other Spring bean, if needed. The configuration can be further customized by:

* overriding the {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }} Spring bean with an application-defined one
* by providing a custom {{ javadoc_class_ref("com.opencqrs.framework.types.EventTypeResolver") }} Spring bean
* by providing a custom {{ javadoc_class_ref("com.opencqrs.framework.serialization.EventDataMarshaller") }} Spring bean
* by defining {{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcaster") }} Spring beans to be registered within {{ javadoc_class_ref("com.opencqrs.framework.upcaster.EventUpcasters") }} automatically
