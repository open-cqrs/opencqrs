---
description: Verifying Command Handling
---

In this tutorial you will learn, how to write unit test for your _command handlers_. The
tests verify the domain logic by focussing on:

1.  sending commands to command handlers for execution
2.  implicitly verifying that the write mode is properly rebuilt
3.  verifying the publication of new events

## Preparing the Test Fixture

In order to test the _command handling_ logic, a new [JUnit](https://junit.org/junit5/) test class needs to be created
in `src/test/java/com/example/cqrs`, as follows:

```java linenums="1"
package com.example.cqrs;

import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@CommandHandlingTest
public class BookHandlingTest {

    @MockitoBean
    private LateChargeCalculator lateChargeCalculator;

    @Test
    public void canBePurchased(@Autowired CommandHandlingTestFixture<Void, PurchaseBookCommand, Void> fixture) {
        // ...
    }
}
```

The test contains the following essential elements:

1.  It is annotated with `@CommandHandlingTest`{ title="com.opencqrs.framework.command.CommandHandlingTest" }, which
    declares the test class as a Spring Boot Test, designated for testing `@CommandHandling`{ title="com.opencqrs.framework.command.CommandHandling" }
    annotated methods (line 17).
2.  It defines a [Mockito](https://site.mockito.org) bean for the `LateChargeCalculator`, since this dependency is
    out of scope of this test class and hence will be mocked (lines 20-21).
3.  It defines an initial test method with an autowired `CommandHandlingTestFixture`{ title="com.opencqrs.framework.command.CommandHandlingTestFixture" }.
    This fixture substitutes the command execution via the `CommandRouter`{ title="com.opencqrs.framework.command.CommandRouter" }.
    The command handler to test is identified by the fixture's generic types in the following order (lines 23-26):
    
    1.  the type of the state rebuilt prior to executing the command (`Void`)
    2.  the type of the command executed (`PurchaseBookCommand`)
    3.  the command handler's return type (`Void`)

For the remainder of this tutorial we will be implementing further tests, by simply adding them to the `BookHandlingTest` class.
All tests can be executed directly from the IDE or as follows:

=== ":simple-gradle: Gradle on :simple-linux: Linux / :simple-apple: MacOS"
    ```shell
    ./gradlew test
    ```

=== ":simple-gradle: Gradle on :fontawesome-brands-windows: Windows"
    ```shell
    gradlew.bat test
    ```

=== ":simple-apachemaven: Maven on :simple-linux: Linux / :simple-apple: MacOS"
    ```shell
    ./mvnw test
    ```

=== ":simple-apachemaven: Maven on :fontawesome-brands-windows: Windows"
    ```shell
    mvnw.bat test
    ```

## Purchasing Books

The first test is going to verify that a new book copy can be purchased successfully, i.e. that
a valid `PurchaseBookCommand` is handled successfully and results in a new `BookPurchasedEvent` being published.
The `CommandHandlingTestFixture`{ title="com.opencqrs.framework.command.CommandHandlingTestFixture" } lets
us express this using its [Given When Then](https://martinfowler.com/bliki/GivenWhenThen.html) fluent API, as follows:

```java
@Test
public void canBePurchased(@Autowired CommandHandlingTestFixture<Void, PurchaseBookCommand, Void> fixture) {
    var id = UUID.randomUUID();
    
    fixture
            // given
            .givenNothing()
            
            // when
            .when(
                    new PurchaseBookCommand(
                            id,
                            "978-0008471286",
                            "Lord of the Rings",
                            "JRR Tolkien",
                            1248L
                    )
            )
            
            // then
            .expectSuccessfulExecution()
            .expectSingleEvent(
                    new BookPurchasedEvent(
                            id,
                            "978-0008471286",
                            "Lord of the Rings",
                            "JRR Tolkien",
                            1248L
                    )
            );
}
```

## Borrowing Books

Secondly, we will test that a book copy can be borrowed. For this, the previously expected `BookPurchasedEvent` is 
__given__ to the test fixture, before the command execution. Upon successful execution a `BookLentEvent` is expected
to be published, as well as the due date being returned from the command handler. The test can be expressed as follows:

```java linenums="1"
@Test
public void canBeBorrowed(@Autowired CommandHandlingTestFixture<Book, BorrowBookCommand, Instant> fixture) {
    var id = UUID.randomUUID();

    fixture
            .given(
                    new BookPurchasedEvent(
                            id,
                            "978-0008471286",
                            "Lord of the Rings",
                            "JRR Tolkien",
                            1248L
                    )
            )
            .when(new BorrowBookCommand(id))
            .expectSuccessfulExecution()
            .expectResultSatisfying(instant -> assertThat(instant).isInTheFuture())
            .expectSingleEventSatisfying((BookLentEvent e) -> {
                assertThat(e.id()).isEqualTo(id);
                assertThat(e.returnDueAt()).isInTheFuture();
            });
}
```
!!! tip
    Since the due date is randomly generated by the command handler, it cannot be verified by equality. Instead,
    [AssertJ](https://joel-costigliola.github.io/assertj/) is used here to verify both the return value from the
    command handler (line 17) and the due date contained within the published event (line 20).

Furthermore, it should be guaranteed that book copies can no longer be borrowed, if currently lent. This can be
verified by another test for the same command handler, which expects no events but an exception instead, as follows:

```java hl_lines="22-23"
@Test
public void cannotBeBorrowedIfAlreadyLent(@Autowired CommandHandlingTestFixture<Book, BorrowBookCommand, Instant> fixture) {
    var id = UUID.randomUUID();

    fixture
            .given(
                    new BookPurchasedEvent(
                            id,
                            "978-0008471286",
                            "Lord of the Rings",
                            "JRR Tolkien",
                            1248L
                    )
            )
            .andGiven(
                    new BookLentEvent(
                            id,
                            Instant.now().plus(3, ChronoUnit.DAYS)
                    )
            )
            .when(new BorrowBookCommand(id))
            .expectException(IllegalStateException.class)
            .expectNoEvents();
}
```

## Returning Books

Upon returning books to the library, we need to distinguish, if the return is overdue or not, i.e. if a late
charge is due or not. The `LateChargeCalculator` is responsible for calculating the fee, while the command handler
decides, if the return is overdue or not. So, for returns without late charge the test may look as follows:

```java
@Test
public void canBeReturnedWithoutLateCharge(@Autowired CommandHandlingTestFixture<Book, ReturnBookCommand, Void> fixture) {
    var id = UUID.randomUUID();
    var now = Instant.now();

    fixture
            .given(
                    new BookPurchasedEvent(
                            id,
                            "978-0008471286",
                            "Lord of the Rings",
                            "JRR Tolkien",
                            1248L
                    )
            )
            .andGiven(
                    new BookLentEvent(
                            id,
                            now.plus(1, ChronoUnit.DAYS)
                    )
            )
            .when(new ReturnBookCommand(id))
            .expectSuccessfulExecution()
            .expectSingleEvent(new BookReturnedEvent(id, 0.0));
}
```

For overdue returns, the `LateChargeCalculator` needs to be stubbed using Mockito to return a defined late charge for
the event publication, as follows:

```java hl_lines="6-8 28"
    @Test
    public void canBeReturnedWithLateChargeCalculated(@Autowired CommandHandlingTestFixture<Book, ReturnBookCommand, Void> fixture) {
        var id = UUID.randomUUID();
        var now = Instant.now();

        doReturn(3.2)
                .when(lateChargeCalculator)
                .calculateLateCharge(any());

        fixture
                .given(
                        new BookPurchasedEvent(
                                id,
                                "978-0008471286",
                                "Lord of the Rings",
                                "JRR Tolkien",
                                1248L
                        )
                )
                .andGiven(
                        new BookLentEvent(
                                id,
                                now.minus(1, ChronoUnit.DAYS)
                        )
                )
                .when(new ReturnBookCommand(id))
                .expectSuccessfulExecution()
                .expectSingleEvent(new BookReturnedEvent(id, 3.2));
    }
```

You have now learned, how to write unit tests for the domain logic contained within command handlers, mocking any
third-party dependencies, if necessary.