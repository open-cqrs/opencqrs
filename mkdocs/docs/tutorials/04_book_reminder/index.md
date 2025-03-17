---
description: Processing the Event Stream
---

In this tutorial you will learn how to handle events, previously published to the event store, in order
to process them according to your needs. You will build an _event handler_ to remind readers about
rented book due dates.

## Tracking the Book Rental Status

In order to be able to remind reader of their rental due date, both `BookLentEvent` and `BookReturnedEvent` need
to be consumed by a so-called _event handler_. Therefore, the first step is to create a Spring service (bean), by
creating a `BookReminder` as follows:

```java hl_lines="21-29"
package com.example.cqrs;

import com.opencqrs.framework.eventhandler.EventHandling;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class BookReminder {

    private final Map<UUID, Boolean> booksLent = new HashMap<>();

    @EventHandling("reminder")
    public void on(BookLentEvent event) {
        booksLent.put(event.id(), true);
    }

    @EventHandling("reminder")
    public void on(BookReturnedEvent event) {
        booksLent.put(event.id(), false);
    }
}
```

The highlighted lines:

1.  define two _event handlers_ named `reminder` using the `@EventHandling`{ title="com.opencqrs.framework.eventhandler.EventHandling" } annotation
2.  consume any existing or new `BookLentEvent` or `BookReturnedEvent` from the event store in order
3.  track a book copy's rental status, respectively, within a hash map

## Implementing a Book Rental Reminder

With the book rental status tracking in place, a reminder can be triggered, directly after a book was borrowed, as follows:

```java hl_lines="25-28"
package com.example.cqrs;

import com.opencqrs.framework.eventhandler.EventHandling;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class BookReminder {

    private final Map<UUID, Boolean> booksLent = new HashMap<>();

    @EventHandling("reminder")
    public void on(BookLentEvent event) {
        booksLent.put(event.id(), true);

        var delay = Duration.between(Instant.now(), event.returnDueAt());
        if (delay.isPositive()) {
            System.out.println("Remember to return book within " + delay.toDays() + " days: " + event.id());
        }
    }

    @EventHandling("reminder")
    public void on(BookReturnedEvent event) {
        booksLent.put(event.id(), false);
    }
}
```
The reminder (`System.out.println()`) will only be triggered, if the due date is in the future, i.e. the `delay` is positive. This is to make sure, 
no notifications will be issued for older events, in which case the book may already have been returned.

## Implementing a Book Overdue Reminder

An additional notification shall be triggered, once the due date is reached, to inform
the reader that the book is about to become overdue. This can be achieved by scheduling a job to be executed after the
`delay`, which reminds the reader, if the book has not yet been returned, as follows:

```java hl_lines="19 30-38"
package com.example.cqrs;

import com.opencqrs.framework.eventhandler.EventHandling;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class BookReminder {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, Boolean> booksLent = new HashMap<>();

    @EventHandling("reminder")
    public void on(BookLentEvent event) {
        booksLent.put(event.id(), true);

        var delay = Duration.between(Instant.now(), event.returnDueAt());
        if (delay.isPositive()) {
            System.out.println("Remember to return book within " + delay.toDays() + " days: " + event.id());

            scheduler.schedule(
                    () -> {
                        if (booksLent.get(event.id())) {
                            System.out.println("Book is due now: " + event.id());
                        }
                    },
                    delay.get(ChronoUnit.SECONDS),
                    TimeUnit.SECONDS);
        }
    }

    @EventHandling("reminder")
    public void on(BookReturnedEvent event) {
        booksLent.put(event.id(), false);
    }
}
```

## Testing the Application


To test the book reminder, it is recommended to reduce the random rental period, defined within `BookHandling` from 
days to seconds or minutes, e.g. as follows:

```java title="BookHandling.java" hl_lines="5"
@CommandHandling
public Instant handle(Book book, BorrowBookCommand command, CommandEventPublisher<Book> publisher) {
    if (book.lent()) throw new IllegalStateException("book currently lent");

    var dueAt = Instant.now().plus(random.ints(7, 30).findFirst().getAsInt(), ChronoUnit.SECONDS);

    publisher.publish(new BookLentEvent(command.id(), dueAt));

    return dueAt;
}
```

After [starting the Event-Sourcing DB](../01_setup/index.md#running-the-event-sourcing-db) and our
[application](../01_setup/index.md#running-the-application), we can now purchase, borrow, and return books,
as shown in [the previous tutorial](../03_domain_logic/index.md#testing-the-application).

Depending on the duration between borrowing and returning a book copy, we should see different outputs on the
application console, e.g. as follows:

```
Remember to return book within 0 days: ab9c7d71-9a5e-4664-8b75-73f4d04cac5e
Book is due now: ab9c7d71-9a5e-4664-8b75-73f4d04cac5e
```

You have now successfully handled events in order to notify readers about book rental due dates.