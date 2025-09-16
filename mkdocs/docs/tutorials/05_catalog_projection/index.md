---
description: Projecting Events for Querying
---

In this tutorial you will learn, how to aggregate events to persist them as read models, by
building a book catalog. This catalog may be _queried_ by interested readers to search for available books
by ISBN, title, or author, thus focusing on the __Q__ in CQRS.

## Persisting Books within the Catalog

For building a persistent book catalog, the following requirements need to be met:

1.  Book copies need to be aggregated by ISBN, title, and author to enable search. This can
    be derived from `BookPurchasedEvent`.
2.  Book copy ids need to be stored to deduce the total number of copies per book. This can
    be derived from `BookPurchasedEvent`, as well.
3.  Book rentals and returns need to be reflected within a book's availability. This can
    be derived from `BookLentEvent` and `BookReturnedEvent`, respectively.

The following JPA entity needs to be created in `src/main/java/com/example/cqrs` to persist
the required information:

```java
package com.example.cqrs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "BOOK_CATALOG")
public class BookCatalogEntity {

    @Id
    private String isbn;
    private String title;
    private String author;

    private Set<UUID> copies = new HashSet<>();
    private Set<UUID> available = new HashSet<>();

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @JsonIgnore
    public Set<UUID> getCopies() {
        return copies;
    }

    public Set<UUID> getAvailable() {
        return available;
    }

    public int getTotal() {
        return copies.size();
    }
}
```

In order to store, update, and read `BookCatalogEntity` instances, a [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
repository needs to be defined, as follows:
```java
package com.example.cqrs;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface BookCatalogRepository extends CrudRepository<BookCatalogEntity, String> {

    @Query(nativeQuery = true, value = "SELECT * FROM BOOK_CATALOG WHERE ARRAY_CONTAINS(copies, :id)")
    Optional<BookCatalogEntity> findByCopiesContaining(UUID id);

    Iterable<BookCatalogEntity> findAllByIsbnContainingIgnoreCaseAndTitleIsContainingIgnoreCaseAndAuthorContainingIgnoreCase(String isbn, String title, String author);
}
```

It defines two additional query methods:

1.  one for looking up a `BookCatalogEntity` for a given book copy id
2.  one for searching the book catalog with any combination of ISBN, title, or author

## Populating the Catalog

The `BOOK_CATALOG` table needs to be filled driven by the events stored within the {{ esdb_ref() }}. Accordingly,
_event handlers_ need to be defined, mapping the different domain events to `BookCatalogEntity`. Create a
`BookCatalogProjector` as follows:

```java linenums="1"
package com.example.cqrs;

import com.opencqrs.framework.eventhandler.EventHandling;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class BookCatalogProjector {

    private final BookCatalogRepository repository;

    public BookCatalogProjector(BookCatalogRepository repository) {
        this.repository = repository;
    }

    @EventHandling("catalog")
    public void on(BookPurchasedEvent event) {
        var entity = repository
                .findById(event.isbn())
                .orElseGet(() -> {
                    var e = new BookCatalogEntity();
                    e.setIsbn(event.isbn());
                    e.setTitle(event.title());
                    e.setAuthor(event.author());
                    return e;
                });
        entity.getAvailable().add(event.id());
        entity.getCopies().add(event.id());
        repository.save(entity);
    }

    @EventHandling("catalog")
    public void on(BookLentEvent event) {
        repository
                .findByCopiesContaining(event.id())
                .ifPresent(entity -> {
                    entity.getAvailable().remove(event.id());
                });
    }

    @EventHandling("catalog")
    public void on(BookReturnedEvent event) {
        repository
                .findByCopiesContaining(event.id())
                .ifPresent(entity -> {
                    entity.getAvailable().add(event.id());
                });
    }
}
```

This ensures, that:

1.  `BookPurchasedEvent` creates a new `BookCatalogEntity` if necessary, populating its ISBN, title, and author, then
    updates the book copies and availability (lines 17-31).
2.  `BookLentEvent` marks the book copy as currently not available (lines 33-40).
3.  `BookReturnedEvent` marks the book copy as available, again (lines 42-49).


## Querying the Catalog

With the event handlers in place, the `BookCatalogRepository` can be used within a new REST controller to
expose the data to REST clients. For this, create the following `BookCatalogController`:

```java
package com.example.cqrs;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/books/catalog")
public class BookCatalogController {

    private final BookCatalogRepository repository;

    public BookCatalogController(BookCatalogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Iterable<BookCatalogEntity> fetch(
            @RequestParam(required = false, defaultValue = "") String isbn,
            @RequestParam(required = false, defaultValue = "") String title,
            @RequestParam(required = false, defaultValue = "") String author
    ) {
        return repository.findAllByIsbnContainingIgnoreCaseAndTitleIsContainingIgnoreCaseAndAuthorContainingIgnoreCase(isbn, title, author);
    }
}
```

## Testing the Application

Finally, after [starting the {{ esdb_name() }}](../01_setup/index.md#running-the-event-sourcing-db) and our
[application](../01_setup/index.md#running-the-application), you can query the book catalog, for instance
matching the book title, using:

```shell
curl --request GET --url "http://localhost:8080/books/catalog?title=ring"
```

The output for a book with two copies, which is partially available, may look as follows:
```json
[
  {
    "isbn": "978-0008471286",
    "title": "Lord of the Rings",
    "author": "JRR Tolkien",
    "available": [
      "fb9c7d71-9a5e-4664-8b75-73f4d04cac5e"
    ],
    "total": 2
  }
]
```

You have now successfully handled events, aggregated, and projected them to a SQL database to be able to query them
via an additional REST API.
