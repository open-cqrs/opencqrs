---
description: How to define state rebuilding logic
---

Within CQRS applications state rebuilding logic is responsible for 
[reconstructing the write model (state)](../../../concepts/event_sourcing/index.md#reconstructing-the-write-model)
in order to be able to [handle commands](../index.md#command-handling), which need to decide if the command
is applicable or not with respect to that state. The [command router](../../core_components/command_router/index.md)
therefore always executes all relevant {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s
based on the [sourced events](../../events/index.md) prior to executing the command.

## Events and Write Models

Events represent facts or state changes within a CQRS/ES application. Accordingly, they can be used to
[reconstruct the state](../../../concepts/event_sourcing/index.md#reconstructing-the-write-model) needed to handle a command.
This is referred to as the _write model_ within CQRS and any Java class (except `java.lang.String`) may be used to represent it,
as shown in the following `Book` class, which represents books within a library, their lending status, and any damaged pages:

```java
public record Book(
        String isbn,
        long numPages, 
        Set<Long> damagedPages, 
        Lending lending
) {
    // ...
}
```

!!! question "Why is there no _Aggregate_ in {{ custom.framework_name }}?"
    The term _aggregate_ was originally coined by DDD to refer to the write model, representing the consistency
    boundary for state changes, in the context of command execution. This, however, does not imply that the aggregate needs to be
    represented by a single class, e.g. `Book` aggregate for all book related commands. {{ custom.framework_name }} lets you
    choose which class is suited best for representing the write model state for a specific command. For instance, one may
    choose to use the `Book` class for purchasing a book and a `Page` model class for maintaining its pages' status.
    
    It is up to the [command handler implementation](../command_handler/index.md) to decide which write model class suits
    its needs best. The only constraint is, that currently only state rebuilding handlers matching this type will be used by
    {{ custom.framework_name }} to reconstruct the state prior to executing the command. Apart from that, any Java class
    (except `java.lang.String`) may be chosen; it does not even have to be serializable, as write model reconstruction
    is an in-memory only operation.


Events may be consumed in two flavors in order to reconstruct the write model:

- as plain Java object representing an [event's payload](../../events/index.md#event-payload-and-metadata)
- as _raw_ {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }} if more information is required, such as its `id` or `hash`

!!! warning "Raw Event Representations"
    Events used to reconstruct the write model will always be obtained from the [event repository](../../core_components/event_repository/index.md).
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

## State Rebuilding Handler Definitions

{{ custom.framework_name }} requires developers to provide {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s,
encapsulating the state reconstruction logic. These need to be registered with a {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }},
in order to be executed prior to the actual commands. {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }} requires 
the following parameters for its construction:

1. A `java.lang.Class` identifying the type of the write model to be reconstructed.
2. A `java.lang.Class` used to identify which type of assignable Java event objects can be applied.
3. A {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler") }} encapsulating the state reconstruction logic.

The {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} upon sourcing the relevant events for the command, takes into account
all registered {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s matching the required write model type
__and__ the assignable event type.

!!! warning "State Reconstruction Constraints"
    Class inheritance may be leveraged to assign sourced events to {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s
    defined using a more generic Java event type, even `java.lang.Object`. If multiple {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s 
    are capable of handling the sourced event, all of them are used to reconstruct the write model state, however, __with no specific order__.

## State Rebuilding Handlers

There are five different types of {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler") }} that may be extended
to define the actual write model reconstruction logic; all of them are functional interfaces:

1. {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler.FromObject") }} if only the previous write model (or `null`) and the Java event object is sufficient.
2. {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler.FromObjectAndMetaData") }} if additional access to the event's meta-data is required.
3. {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler.FromObjectAndMetaDataAndSubject") }} if additional access to the event's meta-data and subject is required.
4. {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent") }} if additional access to the event's meta-data, its subject and the raw {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }} is required.
5. {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler.FromObjectAndRawEvent") }} if additional access to the raw {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }} is required.

!!! warning "Accessing raw {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }}s"
    Special care needs to be taken, when accessing raw {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }}s within {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler") }}s,
    since handlers will not only be called prior to command execution, but also __immediately__ when publishing new events from within {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler") }}s
    using the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandEventPublisher") }}. At this point in time the event has not yet been written to the
    event store, so no _raw_ information, such as the event `id`, is available. Hence, all raw event references will be `null` during that execution phase.

All five types require the developer to specify the following generic types:

1. the type of the write model to be reconstructed
2. the event type

!!! info "Choosing a {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler") }} Type"
The choice of a suitable {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler") }} is for _syntactical_ reasons only, especially
when using Java or Kotlin lambda expressions to implement them. The choice has no effect on the [reconstruction](../index.md#command-handling) itself.

## Registration

{{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s need to be created and registered with the
{{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }}, before commands can be executed.

### Manual Registration

A list of {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }} can be registered with a
[manually configured](../../core_components/command_router/index.md#manual-configuration)
{{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} programmatically as in the following example:

```java
public static CommandRouter commandRouter(EventRepository eventRepository) {
      StateRebuildingHandlerDefinition<Book, BookPurchasedEvent> bookPurchased = new StateRebuildingHandlerDefinition<>(
              Book.class,
              BookPurchasedEvent.class,
              (StateRebuildingHandler.FromObject<Book, BookPurchasedEvent>) (book /* (1)! */, event) ->
                      new Book(
                              event.isbn(),
                              event.numPages(),
                              Set.of(),
                              null
                      )
      );
    
      StateRebuildingHandlerDefinition<Book, BookBorrowedEvent> bookBorrowed = new StateRebuildingHandlerDefinition<>(
              Book.class,
              BookBorrowedEvent.class,
              (StateRebuildingHandler.FromObject<Book, BookBorrowedEvent>) (book, event) ->
                      new Book(
                              book.isbn(),
                              book.numPages(),
                              book.damagedPages(),
                              new Lending(event.dueAt()) /* (2)! */
                      )
      );
    
      return new CommandRouter(
              eventRepository,
              eventRepository,
              List.of(/* (3)! */),
              List.of(bookPurchased, bookBorrowed)
      );
    }
```

1. Previous `book` state isn't used in this example, as `BookPurchasedEvent` represents the initial event, therefore the state is `null`.
2. Previous `book` state is updated by creating a copy including `dueAt` timestamp.
3. {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}s omitted, [see](../command_handler/index.md)


### Spring Boot based Registration

When using the [Spring Boot auto-configured command router](../../core_components/command_router/index.md#spring-boot-auto-configuration)
{{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s can be defined as Spring beans, in order to be
auto-wired with the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} instance created by
the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouterAutoConfiguration") }}.

!!! tip "Command Handling Test Support"
    It is recommended to define {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }} Spring beans
    within classes annotated with {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerConfiguration") }}, which in turn
    is meta-annotated with `org.springframework.context.annotation.Configuration`. This enables
    [command handling tests](../../test_support/command_handling_test_fixture/index.md) to automatically detect and load them without
    starting a full-blown Spring context, instead using the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTest") }} test
    slice.

#### State Rebuilding Handler Definition using @Bean Methods

{{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s can be defined using `org.springframework.context.annotation.Bean`
annotated methods, as shown in the following example.

```java
@CommandHandlerConfiguration
public class BookHandlers {

      @Bean
      public StateRebuildingHandlerDefinition<Book, BookPurchasedEvent> bookPurchased(/* (1)! */) {
            return new StateRebuildingHandlerDefinition<>(
                    Book.class,
                    BookPurchasedEvent.class,
                    (StateRebuildingHandler.FromObject<Book, BookPurchasedEvent>) (book, event) ->
                            new Book(
                                    event.isbn(),
                                    event.numPages(),
                                    Set.of(),
                                    null
                            )
        );
      }
}
```

1. The {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler") }} may access additional auto-wired beans, which can be defined
   as auto-wired dependencies within the `@Bean` annotated method signature, though this is rarely needed.

#### State Rebuilding Handler Definition using Annotations

{{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s may be defined - even in combination with `@Bean` definitions -
using the {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuilding") }} annotation on any suitable state rebuilding method. This greatly
simplifies the definition, since developers are relieved from explicitly instantiating {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}
and choosing a proper {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandler") }} subtype, as shown in the following example:

```java
@CommandHandlerConfiguration
public class BookHandlers {

      @StateRebuilding
      public Book on(BookPurchasedEvent event /* (1)! */) {
            return new Book(
                    event.isbn(), 
                    event.numPages(), 
                    Set.of(), 
                    null
            );
      }
}
```

1. No previous `book` state is injected in this example, as `BookPurchasedEvent` represents the initial event, therefore the state is `null`.

The following rules apply with respect to {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuilding") }} annotated definitions,
enforced by {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingAnnotationProcessingAutoConfiguration") }} during Spring
context initialization:

* The method must be defined within a Spring bean with _singleton_ scope.
* The method name can be chosen arbitrary.
* The method must return a valid write model type inheriting `java.lang.Object`.
* The method arguments can be ordered arbitrarily, as needed.
* The method arguments must be non-repeatable and un-ambiguous.
* One of the parameters must be the Java object event type, which must differ from the returned write model type.
* An optional parameter representing the previous write model state (or `null`) may be defined.
* An optional parameter of type `java.util.Map<String, ?>` may be defined to access the event meta-data.
* An optional parameter of type `java.lang.String` may be defined to access the event subject.
* An optional nullable parameter of type {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }} may be defined to access the raw event. 
* Any number of `@Autowired` annotated parameters may be defined for injection of dependent Spring beans.

!!! warning "Lazy Resolution of Dependencies"
    Method parameters annotated with `@Autowired` are lazily injected upon state reconstruction or event publication. Accordingly, failures to resolve
    those dependencies will not occur during Spring context initialization but when actually executing commands for the first time, resulting
    in a {{ javadoc_class_ref("com.opencqrs.framework.CqrsFrameworkException.NonTransientException") }}.