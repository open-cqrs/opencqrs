{{ custom.framework_name }} encapsulates the details of [command handling](../../extension_points/index.md#command-handling) by
means of the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }}. Hence, developers can focus on defining
_inputs_ in terms of commands, _outputs_ in terms of newly published events, and the corresponding 
[command handling logic](../../extension_points/command_handler/index.md).

While commands and events are typically designed as simple DTO classes, requiring no explicit tests. The command handling logic
can (and should) be unit tested using the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTestFixture") }} instead of using
the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} for integration testing.

## Command Handling Test Fixture

{{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTestFixture") }} provides a 
[given-when-then](https://martinfowler.com/bliki/GivenWhenThen.html) fluent API to write [command handling](../../extension_points/index.md#command-handling) tests.
It mimics the behavior of the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} and therefore needs to
be initialized with the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }} and its associated
{{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s, prior to test execution.

!!! warning "Differences between {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }} and {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTestFixture") }}"
    {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTestFixture") }} mimics {{ javadoc_class_ref("com.opencqrs.framework.command.CommandRouter") }}
    with the following exceptions:

    * No event store or {{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbClient") }} is involved during test execution. All _given_ and _captured_ events are processed __in-memory__.
    * No event upcasting and type resolution is involved, that is the _given_ events are passed as-is to the {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s.
    * Raw {{ javadoc_class_ref("com.opencqrs.esdb.client.Event") }} information is stubbed, as no event store is involved, see the JavaDoc of {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}.
    * Automatic command meta-data propagation to newly published events is ignored.
    * Any state reconstructed prior to command execution won't be cached.

The following [JUnit](https://www.junit.org) test exemplarily shows how to express tests using the
{{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTestFixture") }} for the `PurchaseBookCommand` handler:

```java
@Test
public void bookCanBePurchased() {
    CommandHandlingTestFixture<PurchaseBookCommand> fixture = /* (1)! */

    fixture.givenNothing() /* (2)! */
            .when(  /* (3)! */
                    new PurchaseBookCommand(
                            "4711", 
                            "JRR Tolkien", 
                            "LOTR", 
                            435
                    )
            )
            .expectSuccessfulExecution() /* (4)! */
            .expectSingleEvent(  /* (5)! */
                    new BookPurchasedEvent(
                            "4711", 
                            "JRR Tolkien", 
                            "LOTR", 
                            435
                    )
            );
}
```

1. initialization omitted for brevity
2. initializes the fixture with an empty state, i.e. no prior events published
3. executes `PurchaseBookCommand` command handler, capturing any newly published events
4. verifies successful command handler execution
5. verifies that exactly one `BookPurchasedEvent` was published that equals the given reference event

!!! info "Fluent API"
    {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTestFixture") }} requires a generic {{ javadoc_class_ref("com.opencqrs.framework.command.Command") }}
    subtype identifying the _command under test_. Based on that, it provides a fluent API which can easily be inspected using IDE auto-completion in combination
    with the associated JavaDoc method descriptions.

## Manual Initialization

{{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTestFixture") }} can be manually instantiated using the
{{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }} under test and its associated
{{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s, as shown in the following example:

```java
@Test
public void bookCanBePurchased() {
    CommandHandlingTestFixture<PurchaseBookCommand> fixture =
            CommandHandlingTestFixture
                    .withStateRebuildingHandlerDefinitions(/* (1)! */)
                    .using(/* (2)! */);

    // ...
}
```

1. Variable number of {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }} instances. Could be omitted for this test, since `PurchaseBookCommand` is a creational command.
2. The {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }} to be tested.

## Spring Boot Test Slice

When using Spring [command handler definition](../../extension_points/command_handler/index.md#spring-boot-based-registration) and
[state rebuilding handler definition](../../extension_points/state_rebuilding_handler/index.md#spring-boot-based-registration) beans, test classes
may be annotated using {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTest") }}, providing autoconfigured
{{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTestFixture") }} for _every_ {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}
within the Spring context. This relieves developer from manually initializing the fixture in favor of auto-wiring it, as shown in the following example: 

```java
@CommandHandlingTest
public class BookHandlingTest {

    @Test
    public void bookCanBePurchased(@Autowired CommandHandlingTestFixture<PurchaseBookCommand>/* (1)! */ fixture) {
        fixture.givenNothing()
                .when(
                        new PurchaseBookCommand(
                                "4711",
                                "JRR Tolkien",
                                "LOTR",
                                435
                        )
                )
                .expectSuccessfulExecution()
                .expectSingleEvent(
                        new BookPurchasedEvent(
                                "4711",
                                "JRR Tolkien",
                                "LOTR",
                                435
                        )
                );
    }
}
```

1. It is important to use the unambiguous generic command type, which uniquely identifies the autoconfigured fixture. __Raw usage of {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTestFixture") }} isn't supported.__ 

{{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTest") }} is a so-called [Spring Boot Test Slice](https://docs.spring.io/spring-boot/appendix/test-auto-configuration/slices.html),
which executes the annotated test as Spring Boot test, but with reduced set of beans. The Spring beans (or annotated methods) included in the test context all __must reside__ within
classes annotated with {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerConfiguration") }}.

!!! tip "Mocking"
    As {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlingTest") }} only recognizes Spring beans defined within {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerConfiguration") }}s
    supplementary bean dependencies required by any of the {{ javadoc_class_ref("com.opencqrs.framework.command.CommandHandlerDefinition") }}s or
    {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuildingHandlerDefinition") }}s under test, must be provided manually. As these represent
    dependent beans, this is preferably achieved using Spring's `@MockitoBean` annotation, which injects (or replaces) a mock bean into the context. The mock
    satisfies the bean dependency and may be stubbed and/or verified as part of the test.