---
description: Implementing Business Validations for the Command Handlers
---

In this tutorial you will extend the command handling and learn how to implement validation logic
with CQRS. For this, you will have to leverage _event sourcing_ to rebuild the state of write models from
previously published events.

## Preparing the Write Model

Before we can decide whether a book can be borrowed or returned by a reader or not, or if it even exists, we need to
rebuild our _write model_ for the command execution from any previously published events. Three informations
are needed to decide, whether a book can be borrowed and whether it can be returned with or without a late charge:

1.  whether the book actually exisits within the book inventory, which can be derived from the `BookPurchasedEvent`
2.  whether the book is currently lent and this not available, for which a new `BookLentEvent` will be used
3.  the _due date_ of the book rental, if not yet returned

These informations can be expressed within a new `Book` Java record, which serves as write model for the command handlers. 
Create the record within `src/main/java/com/example/cqrs` as follows:

```java
package com.example.cqrs;

import java.time.Instant;

public record Book(
        boolean lent,
        Instant returnDueAt
) {}
```

## Borrowing Books

The intent to borrow books from the library requires a new `BorrowBookCommand` to be created as follows:

```java
package com.example.cqrs;

import com.opencqrs.framework.command.Command;

import java.util.UUID;

public record BorrowBookCommand(
        UUID id
) implements Command {

    @Override
    public String getSubject() {
        return "/books/" + id();
    }
}
```

A successful book rental including its _due date_ is represented by a new `BookLentEvent` to be created as follows:

```java
package com.example.cqrs;

import java.time.Instant;
import java.util.UUID;

public record BookLentEvent(
        UUID id,
        Instant returnDueAt
) {}
```

In order to be able to handle the `BorrowBookCommand`, the following extensions need to be made:

1.  The `Book` write model needs to be rebuilt from the `BookPurchasedEvent`. An additional {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuilding") }}
    annotated handler is required for this.
2.  The rebuilt `Book` instance needs to be passed to a new command handler for the `BorrowBookCommand`, in order to be able to decide, whether the borrowing request is
    eligible, i.e. non-exceptional. Furthermore, the `BookLentEvent`, containing a random _due date_ between 7 and 30 days, will be published, if so. Finally, the _due date_ will be returned 
    from the command handler, so it can be exposed as result from the REST API call later.
3.  Another {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuilding") }} handler is needed to update the `Book` write model
    based on the `BookLentEvent` just published, to prevent further rental requests.

The required additions to the `BookHandling` class are highlighted in the following code block:

```java hl_lines="12 27-30 32-41 43-46"
package com.example.cqrs;

import com.opencqrs.framework.command.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

@CommandHandlerConfiguration
public class BookHandling {

    private static final Random random = new Random();

    @CommandHandling
    public void handle(PurchaseBookCommand command, CommandEventPublisher<Void> publisher) {
        publisher.publish(
                new BookPurchasedEvent(
                        command.id(),
                        command.isbn(),
                        command.title(),
                        command.author(),
                        command.numPages()
                )
        );
    }

    @StateRebuilding
    public Book on(BookPurchasedEvent event) {
        return new Book(false, null);
    }

    @CommandHandling
    public Instant handle(Book book, BorrowBookCommand command, CommandEventPublisher<Book> publisher) {
        if (book.lent()) throw new IllegalStateException("book currently lent");

        var dueAt = Instant.now().plus(random.ints(7, 30).findFirst().getAsInt(), ChronoUnit.DAYS);

        publisher.publish(new BookLentEvent(command.id(), dueAt));

        return dueAt;
    }

    @StateRebuilding
    public Book on(BookLentEvent event) {
        return new Book(true, event.returnDueAt());
    }
}
```

## Returning Books

The intent to return books can be expressed similarly using the `ReturnBookCommand` as follows:

```java
package com.example.cqrs;

import com.opencqrs.framework.command.Command;

import java.util.UUID;

public record ReturnBookCommand(
        UUID id
) implements Command {

    @Override
    public String getSubject() {
        return "/books/" + id();
    }
}
```

A book may be returned in-time or past its due date, which results in a late charge for the reader. A `BookReturnedEvent`
represents the successful return including the late charge amount (may be zero), as follows:
```java
package com.example.cqrs;

import java.util.UUID;

public record BookReturnedEvent(
        UUID id,
        Double lateCharge
) {}
```

In order to be able to handle the `ReturnBookCommand`, the following extensions need to be made:

1.  A new command handler for the `ReturnBookCommand` is needed, in order to be able to decide, whether the return request is
    eligible, i.e. non-exceptional. 
2.  In case of an overdue return a _late charge_ needs to be calculated. The calculation logic can be out-sourced to a separate
    service, which is `@Autowired` into the command handler additionally.
3.  Furthermore, the `BookReturnedEvent`, containing the calculated late charge, will be published.
4.  Another {{ javadoc_class_ref("com.opencqrs.framework.command.StateRebuilding") }} handler is needed to update the `Book` write model
    based on the `BookReturnedEvent` just published, to mark it as available for further borrow requests.

The required additions to the `BookHandling` class are highlighted in the following code block:

```java hl_lines="50-65 67-70"
package com.example.cqrs;

import com.opencqrs.framework.command.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

@CommandHandlerConfiguration
public class BookHandling {

    private static final Random random = new Random();

    @CommandHandling
    public void handle(PurchaseBookCommand command, CommandEventPublisher<Void> publisher) {
        publisher.publish(
                new BookPurchasedEvent(
                        command.id(),
                        command.isbn(),
                        command.title(),
                        command.author(),
                        command.numPages()
                )
        );
    }

    @StateRebuilding
    public Book on(BookPurchasedEvent event) {
        return new Book(false, null);
    }

    @CommandHandling
    public Instant handle(Book book, BorrowBookCommand command, CommandEventPublisher<Book> publisher) {
        if (book.lent()) throw new IllegalStateException("book currently lent");

        var dueAt = Instant.now().plus(random.ints(7, 30).findFirst().getAsInt(), ChronoUnit.DAYS);

        publisher.publish(new BookLentEvent(command.id(), dueAt));

        return dueAt;
    }

    @StateRebuilding
    public Book on(BookLentEvent event) {
        return new Book(true, event.returnDueAt());
    }

    @CommandHandling
    public void handle(
            Book book,
            ReturnBookCommand command,
            CommandEventPublisher<Book> publisher,
            @Autowired LateChargeCalculator calculator
    ) {
        if (!book.lent()) throw new IllegalStateException("book currently not lent");

        if (Instant.now().isBefore(book.returnDueAt())) {
            publisher.publish(new BookReturnedEvent(command.id(), 0.0));
        } else {
            var lateCharge = calculator.calculateLateCharge(Duration.between(Instant.now(), book.returnDueAt()));
            publisher.publish(new BookReturnedEvent(command.id(), lateCharge));
        }
    }

    @StateRebuilding
    public Book on(BookReturnedEvent event) {
        return new Book(false, null);
    }
}
```

The `LateChargeCalculator`, encapsulating the late charge calculation, needs to be created separately as follows, so it can be 
auto-wired into the command handler by the Spring Framework:

```java
package com.example.cqrs;

import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LateChargeCalculator {

    public Double calculateLateCharge(Duration overdue) {
        return 0.5 * overdue.toDays();
    }
}
```

## Extending the REST API

Both `BorrowBookCommand` and `ReturnBookCommand` can be exposed as separate REST endpoints by adding the
highlighted lines to the `BookController`:

```java hl_lines="23-26 28-31"
package com.example.cqrs;

import com.opencqrs.framework.command.CommandRouter;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/books")
public class BookController {

    private final CommandRouter commandRouter;

    public BookController(CommandRouter commandRouter) {
        this.commandRouter = commandRouter;
    }

    @PostMapping("/purchase")
    public void purchase(@RequestBody PurchaseBookCommand command) {
        commandRouter.send(command);
    }

    @PostMapping("/borrow")
    public Instant borrow(@RequestBody BorrowBookCommand command) {
        return commandRouter.send(command);
    }

    @PostMapping("/return")
    public void returnBook(@RequestBody ReturnBookCommand command) {
        commandRouter.send(command);
    }
}
```

The `/books/borrow` endpoint returns the due date from the command handler as HTTP response
to its clients.

## Testing the Application

Finally, after [starting the {{ esdb_name() }}](../01_setup/index.md#running-the-event-sourcing-db) and our
[application](../01_setup/index.md#running-the-application), [previously purchased books](../02_command_handling/index.md#testing-the-application)
can be borrowed using the REST API, for instance as follows:

=== ":simple-linux: Linux / :simple-apple: MacOS"
    ```shell
    curl --request POST \
         --url "http://localhost:8080/books/borrow" \
         --header "Content-Type: application/json" \
         --data '{ "id": "ab9c7d71-9a5e-4664-8b75-73f4d04cac5e" }'
    ```

=== " :fontawesome-brands-windows: Windows"
    ```shell
    curl --request POST \
         --url "http://localhost:8080/books/borrow" ^
         --header "Content-Type: application/json" ^
         --data '{ "id": "ab9c7d71-9a5e-4664-8b75-73f4d04cac5e" }'
    ```

Returning books via the REST API can be achieved using the following command:

=== ":simple-linux: Linux / :simple-apple: MacOS"
    ```shell
    curl --request POST \
         --url "http://localhost:8080/books/return" \
         --header "Content-Type: application/json" \
         --data '{ "id": "ab9c7d71-9a5e-4664-8b75-73f4d04cac5e" }'
    ```

=== " :fontawesome-brands-windows: Windows"
    ```shell
    curl --request POST \
         --url "http://localhost:8080/books/return" ^
         --header "Content-Type: application/json" ^
         --data '{ "id": "ab9c7d71-9a5e-4664-8b75-73f4d04cac5e" }'
    ```

You have now successfully implemented the domain logic of your domain, exposed new commands via REST,
and published new events. Moreover, the command handlers assure that the write model remains valid, 
by throwing exceptions, if necessary.