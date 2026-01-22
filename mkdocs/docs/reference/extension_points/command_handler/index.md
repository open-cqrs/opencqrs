---
description: How to define custom command handling logic
---

Within CQRS applications command handling is responsible for validating business rules, before applying changes
to the application's state by means of new events. In order to execute command handling logic, it is necessary
[to reconstruct the write model](../../../concepts/event_sourcing/index.md#reconstructing-the-write-model), in order
to be able to decide, whether the command is applicable or not. This is why command handlers may not be executed
directly, but will be executed by the [command router](../../core_components/command_router/index.md), instead,
as shown in [command handling](../index.md#command-handling).

## Commands

Commands express the intent and any information required to execute the associated command handling logic. Commands must
implement {{ javadoc_class_ref("com.opencqrs.framework.command.Command") }}, which:

1. identifies the command subject used to [source the events to reconstruct the write model](../../../concepts/event_sourcing/index.md#reconstructing-the-write-model)
2. an optional {{ javadoc_class_ref("com.opencqrs.framework.command.Command.SubjectCondition") }} specifying conditions with respect to the subject's existence.

An example of a _pristine subject_ command used to purchase new (not yet existing) books within a library, may look as follows:

```java
public record PurchaseBookCommand(
        String isbn, 
        String author,  
        String title,  
        long numPages
) implements Command {

    @Override
    public String getSubject() {
        return "/books/" + isbn();
    }

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.PRISTINE;
    }
}
```

## Command Handler Definitions

{{ custom.framework_name }} requires developers to provide {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}s,
encapsulating the command handling logic. These need to be registered with a {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }},
in order to execute commands. {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }} requires the following parameters
for its construction:

1. A `java.lang.Class` identifying the type of the write model to be reconstructed prior to the actual command execution. This class is used to identify the [state rebuilding handlers](../state_rebuilding_handler/index.md) used to reconstruct the write model, i.e. those with the same instance type.
2. A command class implementing {{ javadoc_class_ref("com.opencqrs.framework.command.Command") }} expressing the intent and any information required to execute the command.
3. A {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler") }} encapsulating the command handling logic, which will be executed with the given command, once the write model has been reconstructed.
4. An optional {{ javadoc_class_ref("com.opencqrs.framework.command.SourcingMode") }} specifying which events need to be sourced in order to reconstruct the write model.

## Command Handlers

There are three different types of {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler") }} that may be extended
to define the actual command handling logic; all of them are functional interfaces:

1. {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler.ForCommand") }} if accessing the {{ javadoc_class_ref("com.opencqrs.framework.command.Command") }} is sufficient. This is typically used for creational commands in combination with {{ javadoc_class_ref("com.opencqrs.framework.command.Command.SubjectCondition") }} `PRISTINE`.
2. {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler.ForInstanceAndCommand") }} if additional access to the write model is required.
3. {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler.ForInstanceAndCommandAndMetaData") }} if additional access to any command meta-data is required.

All three types accept an additional {{ javadoc_class_ref("com.opencqrs.framework.command.CommandEventPublisher") }} for publishing new events
as part of the command handling logic. In addition, all types require the developer to specify the following generic types:

1. the type of the write model to be sourced
2. the command type to be handled
3. the return type, or `java.lang.Void` for command handlers returning `null`

!!! info "Choosing a {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler") }} Type"
    The choice of a suitable {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler") }} is for _syntactical_ reasons only, especially
    when using Java or Kotlin lambda expressions to implement them. The choice has no effect on the [command handling workflow](../index.md#command-handling) itself,
    nor the presence of meta-data or the reconstructed write model.

## Consistency and Atomicity

The {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} ensures consistency while executing a {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler") }}, 
preventing malicious event publication, if necessary. Inconsistencies typically occur due to parallel command execution, for instance two customers trying to borrow the same book copy concurrently.
There are different types of inconsistencies that need to be prevented:

* partial event publication, in case a command handler publishes more than one event
* duplicate publication of an initial, _creational_ event for a specific subject
* publication of new events, if the events sourced prior to command execution have been superseded by newer events
* publication of new events, if custom pre-conditions applied by the developer are violated

Partial event publication is prevented by capturing all events published via the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandEventPublisher") }}
in-memory, _atomically_ publishing them all at once together with a set of suitable [pre-conditions](https://docs.eventsourcingdb.io/fundamentals/preconditions/) 
__after__ successful command execution using the [event repository](../../core_components/event_repository/index.md), or none of them in case of any inconsistency.

!!! warning "Avoiding Side Effects in Command Handlers"
    The {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} isn't aware of any side effects caused by the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler") }}
    executed. Accordingly, these cannot be rolled back automatically, in case the event publication fails due to inconsistencies. Command execution logic
    should therefore avoid any kind of side effect, in favor of defering those to an appropriate [event handler](../event_handler/index.md) instead.

To prevent all further inconsistencies, the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} applies the following checks and preconditions, throwing
a suitable exception if needed:

1. Once all events have been sourced to [reconstruct the write model](../state_rebuilding_handler/index.md) the {{ javadoc_class_ref("com.opencqrs.framework.command.Command.SubjectCondition") }} specified for the command is evaluated:
    1. If `PRISTINE`, it is ensured that no event has been sourced exactly matching the {{ javadoc_class_ref("com.opencqrs.framework.command.Command") }} subject, otherwise failing with a {{ javadoc_class_ref("com.opencqrs.framework.command.CommandSubjectAlreadyExistsException") }} __before__ the actual command execution.
    2. If `EXISTS`, it is ensured that at least one event has been sourced exactly matching the {{ javadoc_class_ref("com.opencqrs.framework.command.Command") }} subject, otherwise failing with a {{ javadoc_class_ref("com.opencqrs.framework.command.CommandSubjectDoesNotExistsException") }} __before__ the actual command execution.
2. After successful command execution all captured events (if any) will be published using the following preconditions, causing {{ javadoc_class_ref("com.opencqrs.framework.client.ConcurrencyException") }} if any of them is violated:
    1. {{ javadoc_class_ref("com.opencqrs.esdb.client.Precondition.SubjectIsOnEventId") }} is applied for any unique subject previously sourced, using the latest event id seen for that subject.
    2. {{ javadoc_class_ref("com.opencqrs.esdb.client.Precondition.SubjectIsPristine") }} is applied for any subject not sourced and for the command subject if {{ javadoc_class_ref("com.opencqrs.framework.command.Command.SubjectCondition") }} `PRISTINE` was specified.
    3. {{ javadoc_class_ref("com.opencqrs.esdb.client.Precondition.SubjectIsPopulated") }} is applied for the command subject if {{ javadoc_class_ref("com.opencqrs.framework.command.Command.SubjectCondition") }} `EXISTS` was specified.
    4. any custom {{ javadoc_class_ref("com.opencqrs.esdb.client.Precondition") }} provided by the developer via the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandEventPublisher") }} publishing methods

The {{ javadoc_class_ref("com.opencqrs.framework.command.SourcingMode") }} configured for the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}
additionally affects, when and to which level of detail concurrent modifications can be detected, as follows:

* `RECURSIVE` sources all events for the command's subject and all recursive events and subjects. Hence, the command's consistency boundary covers all events and their respective ids.
* `LOCAL` sources only those events assigned to the command subject, but no recursive events and subjects. Accordingly, the command's consistency boundary is limited to events and their respective ids belonging to the command subject only.
* `NONE` sources no events at all. Accordingly, the command's consistency boundary is limited to the rules enforced by {{ javadoc_class_ref("com.opencqrs.framework.command.Command.SubjectCondition") }}.

!!! warning "Consistency Boundary Limitations"
    The command consistency boundary currently cannot be constrained to a specific event id __recursively__, for instance using a pre-condition stating that a subject is on a specific id recursively,
    i.e. including all child subjects and their respective events. Accordingly, concurrent event publication to relative subjects, e.g. `/books/4711/copies/1` and `/books/4711/copies/2` cannot be
    prevented. This needs to be taken into considerations, whenever there is a need for limiting the number of relative subjects, e.g. the number of book copies in this example.

    Moreover, the consistency rules applied during command handling focus solely on avoiding concurrent modification due to parallel command handler execution. They explicitly cannot
    prevent any inconsistencies resulting from out-dated read model data due to the inherent eventual consistency between write and read models. For instance, updating a book's title and
    release year (more or less) concurrently based on the same read model state, may result in the last change overriding the first one. It is the responsibility of the command handler to
    prevent updates on out-dated data explicitly. This can be achieved, for instance, by publishing the latest event id as part of the read model. This _reference_ id may then be included
    as part of the updating command, for the command handler to be able to verify it is still up to date.

## Registration

{{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}s need to be created and registered with the
{{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }}, before commands can be executed.

!!! tip "Command Ambiguity and Inheritance"
    All {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}s registered with the same
    {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} instance must be non-ambiguous with respect
    to their {{ javadoc_class_ref("com.opencqrs.framework.command.Command") }} for the router to know, where commands
    need to be routed to. Command inheritance is ignored for that matter, i.e. commands `A` and `B` extending `A` will
    be treated as distinct commands.

### Manual Registration

A list of {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }} can be registered with a 
[manually configured](../../core_components/command_router/index.md#manual-configuration)
{{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} programmatically as in the following example:

```java
public static CommandRouter commandRouter(EventRepository eventRepository) {
    CommandHandlerDefinition<Book, PurchaseBookCommand, Void> purchaseCmdDef = new CommandHandlerDefinition<>(
            Book.class,
            PurchaseBookCommand.class,
            (CommandHandler.ForCommand<Book, PurchaseBookCommand, Void>) (command, publisher) -> {
                if (command.numPages <= 0) {
                    throw new IllegalArgumentException("Number of pages must be greater than 0.");
                }

                publisher.publish(
                        new BookPurchasedEvent(
                                command.isbn(),
                                command.author(),
                                command.title(),
                                command.numPages()
                        )
                );
                return null;
            }
    );

    return new CommandRouter(
            eventRepository,
            eventRepository,
            List.of(purchaseCmdDef),
            List.of(/* (1)! */) 
    );
}
```

1.  {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s omitted, [see](../state_rebuilding_handler/index.md)

### Spring Boot based Registration

When using the [Spring Boot auto-configured command router](../../core_components/command_router/index.md#spring-boot-auto-configuration)
{{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}s can be defined as Spring beans, in order to be
auto-wired with the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} instance created by
the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouterAutoConfiguration") }}.

!!! tip "Command Handling Test Support"
    It is recommended to define {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }} Spring beans
    within classes annotated with {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerConfiguration") }}, which in turn
    is meta-annotated with `org.springframework.context.annotation.Configuration`. This enables 
    [command handling tests](../../test_support/command_handling_test_fixture/index.md) to automatically detect and load them without
    starting a full-blown Spring context, instead using the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTest") }} test
    slice.

#### Command Handler Definition using @Bean Methods

{{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}s can be defined using `org.springframework.context.annotation.Bean`
annotated methods, as shown in the following example.

```java
@CommandHandlerConfiguration
public class BookHandlers {
    
    @Bean
    public CommandHandlerDefinition<Book, PurchaseBookCommand, Void> purchaseBookCommandVoidCommandHandlerDefinition(BookValidator bookValidator) {
        return new CommandHandlerDefinition<>(
                Book.class,
                MyBookCommand.class,
                (CommandHandler.ForCommand<Book, PurchaseBookCommand, Void>) (command, publisher) -> {
                    bookValidator.checkValidPurchaseRequest(command);
                    publisher.publish(
                            new BookPurchasedEvent(
                                    command.isbn(),
                                    command.author(),
                                    command.title(),
                                    command.numPages()
                            )
                    );
                    return null;
                }
        );
    }
}
```

The {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler") }} hence may access additional auto-wired beans (e.g. `BookValidator`), which are defined
as auto-wired dependencies within the `@Bean` annotated method signature.

#### Command Handler Definition using Annotations

{{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}s may be defined - even in combination with `@Bean` definitions -
using the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandling") }} annotation on any suitable command handling method. This greatly
simplifies the definition, since developers are relieved from explicitly instantiating {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}
and choosing a proper {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandler") }} subtype, as shown in the following example:

```java
@CommandHandlerConfiguration
public class BookHandlers {

    @CommandHandling
    public void purchase(
            PurchaseBookCommand command, 
            CommandEventPublisher<Book> publisher,
            @Autowired BookValidator bookValidator
    ) {
        bookValidator.checkValidPurchaseRequest(command);
        publisher.publish(
                new BookPurchasedEvent(
                        command.isbn(), 
                        command.author(), 
                        command.title(), 
                        command.numPages()
                )
        );
    }
}
```

The following rules apply with respect to {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandling") }} annotated definitions, 
enforced by {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingAnnotationProcessingAutoConfiguration") }} during Spring
context initialization:

* The method must be defined within a Spring bean with _singleton_ scope.
* The method name can be chosen arbitrary.
* The method arguments can be ordered arbitrarily, as needed.
* The method arguments must be non-repeatable and un-ambiguous.
* One of the parameters must be of type {{ javadoc_class_ref("com.opencqrs.framework.command.Command") }}.
* At least one of the following two parameters needs to be defined:
    * {{ javadoc_class_ref("com.opencqrs.framework.command.CommandEventPublisher") }} identifying the write model instance type via its generic type
    * a Java object representing the write model instance
* An optional parameter of type `java.util.Map<String, ?>` may be defined to access the command meta-data.
* Any number of `@Autowired` annotated parameters may be defined for injection of dependent Spring beans.

!!! warning "Lazy Resolution of Dependencies"
    Method parameters annotated with `@Autowired` are lazily injected upon command execution. Accordingly, failures to resolve
    those dependencies will not occur during Spring context initialization but when actually executing commands for the first time, resulting
    in a {{ javadoc_class_ref("com.opencqrs.framework.CqrsFrameworkException.NonTransientException") }}.