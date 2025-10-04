---
description: How to define event handling logic
---

Within CQRS applications event handling logic is responsible for 
[projecting read models](../../../concepts/event_sourcing/index.md#projecting-a-read-model)
in order for clients to be able to query the application state. The [event handling processor](../../core_components/event_handling_processor/index.md)
is responsible for executing all relevant {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }}s
based on the [tracked events](../../events/index.md).

## Events and Read Models

Events represent facts or state changes within a CQRS/ES application. Accordingly, they can be used to
[project read models](../../../concepts/event_sourcing/index.md#projecting-a-read-model). A _read model_ may be anything
ranging from SQL database projections to emails sent to customers. {{ custom.framework_name }} makes no assumptions
about read models, but focuses on delivering events to the appropriate event handlers.

Events may be consumed in two flavors in order to project read models:

- as plain Java object representing an [event's payload](../../events/index.md#event-payload-and-metadata)
- as _raw_ {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }} if more information is required, such as its `id` or `hash`

!!! warning "Raw Event Representations"
    Events used to project read models will always be obtained from the [event repository](../../core_components/event_repository/index.md).
    Accordingly, _raw event representation_ in that case refers to an instance of {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }}, whose
    `data` has already been [upcasted](../../../concepts/upcasting/index.md), to ensure it can be mapped properly to the appropriate Java event class.

The following is an example of a Java event class representing a previously purchased book:

```java
public record BookPurchasedEvent(
        String isbn, 
        String author, 
        String title, 
        long numPages
) {}
```

## Event Handler Definitions

{{ custom.framework_name }} requires developers to provide {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }}s,
encapsulating the event processing logic. These need to be registered with a {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }},
in order to be processed asynchronously. {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }} requires 
the following parameters for its construction:

1. A `java.lang.String` identifying the processing group. Only definitions of the same group may be registered with the same {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} instance. 
2. A `java.lang.Class` used to identify which type of assignable Java event objects can be applied.
3. An {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} encapsulating the event processing logic.

The {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} within its 
[event processing loop](../../core_components/event_handling_processor/index.md#event-processing-loop), takes into account
all registered {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }}s matching the assignable event type.

!!! warning "Event Handling Constraints"
    Class inheritance may be leveraged to assign events to {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }}s
    defined using a more generic Java event type, even `java.lang.Object`. If multiple {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }}s 
    are capable of handling the event, all of them are used to project the read model, however, __with no specific order__.

## Event Handlers

There are three different types of {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} that may be extended
to define the actual event processing logic; all of them are functional interfaces:

1. {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler.ForObject") }} if only the Java event object is sufficient.
2. {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler.ForObjectAndMetaData") }} if additional access to the event's meta-data is required.
3. {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler.ForObjectAndMetaDataAndRawEvent") }} if additional access to the event's meta-data ,and the raw {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }} is required.

All three types require the developer to specify the event type as generic.

!!! info "Choosing an {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} Type"
    The choice of a suitable {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} is for _syntactical_ reasons only, especially
    when using Java or Kotlin lambda expressions to implement them. The choice has no effect on the [event processing](../index.md#event-processing) itself.

## Registration

All {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }}s _within the same processing group_ 
need to be created and registered with an instance of {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }}, 
before entering the [event processing loop](../../core_components/event_handling_processor/index.md#event-processing-loop).

### Manual Registration

A list of {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }} can be registered with a
[manually configured](../../core_components/event_handling_processor/index.md#manual-configuration)
{{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} programmatically as in the following example:

```java
public static EventHandlingProcessor eventHandlingProcessor(
        EventReader eventReader,
        BookStore store
) {
    EventHandlerDefinition<BookPurchasedEvent> bookPurchased = new EventHandlerDefinition<>(
            "book-catalog",
            BookPurchasedEvent.class,
            (EventHandler.ForObject<BookPurchasedEvent>) (event) -> {
                store.register(event.isbn()); /* (1)! */
            }
    );
    return new EventHandlingProcessor(
            0,
            "/",
            true,
            eventReader,
            new InMemoryProgressTracker(),
            new PerSubjectEventSequenceResolver(),
            new DefaultPartitionKeyResolver(1),
            List.of(bookPurchased),
            () -> () -> 1000
    );
}
```

1. Example representing a persistent book store (read model), which is being updated by the event handler.


### Spring Boot based Registration

When using the [Spring Boot auto-configured event handling processors](../../core_components/event_handling_processor/index.md#spring-boot-auto-configuration)
{{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }}s can be defined as Spring beans, in order to be
auto-wired with a processing group specific {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} instance created by
the {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessorAutoConfiguration") }}.

#### Event Handler Definition using @Bean Methods

{{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }}s can be defined using `org.springframework.context.annotation.Bean`
annotated methods, as shown in the following example.

```java
@Configuration
public class BookStoreProjector {

      @Bean
      public EventHandlerDefinition<BookPurchasedEvent> bookPurchased(BookStore store /* (1)! */) {
            return new EventHandlerDefinition<>(
                  "book-catalog",
                  BookPurchasedEvent.class,
                  (EventHandler.ForObject<BookPurchasedEvent>) (event) -> {
                      store.register(event.isbn());
                  }
            );
      }
}
```

1. The {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }} may access additional auto-wired beans, which can be defined
   as auto-wired dependencies within the `@Bean` annotated method signature, though this is rarely needed.

#### Event Handler Definition using Annotations

{{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }}s may be defined - even in combination with `@Bean` definitions -
using the {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandling") }} annotation on any suitable event handling method. This greatly
simplifies the definition, since developers are relieved from explicitly instantiating {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlerDefinition") }}
and choosing a proper {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandler") }} subtype, as shown in the following example:

```java
@Component
public class BookStoreProjector {

      @EventHandling("book-catalog")
      public void on(BookPurchasedEvent event, @Autowired BookStore store) {
          store.register(event.isbn());
      }
}
```

!!! tip "Avoiding redundant Processing Group Definitions"
    {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandling") }} requires a processing group identifier to assign the
    annotated method to the correct {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }} instance for processing.
    In order to avoid redundant definitions of the group identifier, custom meta-annotations may be used instead, e.g. as follows:

    ```java
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @EventHandling("book-catalog")
    public @interface BookCatalogHandler {}
    ```

The following rules apply with respect to {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandling") }} annotated definitions,
enforced by {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingAnnotationProcessingAutoConfiguration") }} during Spring
context initialization:

* The method must be defined within a Spring bean with _singleton_ scope.
* The method name can be chosen arbitrary.
* The method must return `void`.
* The method arguments can be ordered arbitrarily, as needed.
* The method arguments must be non-repeatable and un-ambiguous.
* The method must have at least one (or any combination) of the following parameters:
    * the Java object event type
    * a `java.util.Map<String, ?>` containing the event meta-data
    * a raw {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }}
* Any number of `@Autowired` annotated parameters may be defined for injection of dependent Spring beans.
* The method may optionally be annotated with Spring's `@Transactional` annotation to enforce transaction semantics for the method execution.

!!! warning "Lazy Resolution of Dependencies"
    Method parameters annotated with `@Autowired` are lazily injected upon state reconstruction or event publication. Accordingly, failures to resolve
    those dependencies will not occur during Spring context initialization but when actually executing commands for the first time, resulting
    in a {{ javadoc_class_ref("com.opencqrs.framework.CqrsFrameworkException.NonTransientException") }}.
