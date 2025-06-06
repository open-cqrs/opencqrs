# OpenCQRS - Java CQRS Framework for the [EventSourcingDB](https://www.eventsourcingdb.io)

<!-- BADGES_START -->
[![Java](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fopen-cqrs%2Fopencqrs%2Frefs%2Fheads%2Fgh-pages%2Fbadges%2Fjdk.json)](https://openjdk.org)
[![EventSourcingDB](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fopen-cqrs%2Fopencqrs%2Frefs%2Fheads%2Fgh-pages%2Fbadges%2Fesdb.json)](https://www.eventsourcingdb.io)
[![Spring Boot](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fopen-cqrs%2Fopencqrs%2Frefs%2Fheads%2Fgh-pages%2Fbadges%2Fspring.json)](https://spring.io/projects/spring-boot)
[![JavaDoc](https://img.shields.io/badge/JavaDoc-click%20here-lightgrey?logo=readthedocs)](https://docs.opencqrs.com/javadoc)
![Documentation](https://img.shields.io/badge/Documentation-coming%20soon-blue?logo=materialformkdocs)
<!-- BADGES_END -->

![OpenCQRS](banner.png)

OpenCQRS is a lightweight open source Java framework for building applications based on the CQRS (Command Query Responsibility Segregation) and Event Sourcing patterns.
It includes built-in support for testing and offers optional Spring Boot integration to simplify configuration and production deployment. 
OpenCQRS is based on [EventSourcingDB](https://www.eventsourcingdb.io), a third-party event store, and provides a Java client SDK for it.

## Installation

OpenCQRS is available directly from [Maven Central](https://www.maven.org). A [running instance](https://docs.eventsourcingdb.io/getting-started/running-eventsourcingdb/) of the
[EventSourcingDB](https://www.eventsourcingdb.io) is required as event store. Spring Boot developers must add the following dependencies to their project in order to start developing
and testing OpenCQRS applications:

### Maven (pom.xml)

```xml
<dependencies>
    <dependency>
        <groupId>com.opencqrs</groupId>
        <artifactId>framework-spring-boot-starter</artifactId>
        <version>{{version}}</version>
    </dependency>
    <dependency>
        <groupId>com.opencqrs</groupId>
        <artifactId>framework-test</artifactId>
        <version>{{version}}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Gradle (build.gradle.kts)

```kotlin
dependencies {
    implementation("com.opencqrs:framework-spring-boot-starter:{{version}}")
    testImplementation("com.opencqrs:framework-test:{{version}}")
}
```

Make sure to configure a proper Spring Boot application name and the connection settings for the
event store, i.e. within your `src/main/resources/application.yml`:

```yaml
spring.application.name: sample-app

esdb:
  server:
    uri: http://localhost:3000
    api-token: <secret>
```

## Usage

OpenCQRS provides the following core capabilities to support the development of robust CQRS/ES-based systems:

### Command Handling

Define annotation based command handlers for implementing business logic and publishing new events:

```java
public record PurchaseBookCommand(String isbn, String author, String title, long numPages) implements Command {
    @Override
    default String getSubject() {
        return "/book/" + isbn();
    }
}

public record BookPurchasedEvent(String isbn, String author, String title, long numPages) {}

@CommandHandlerConfiguration
public class BookHandling {

    @CommandHandling
    public String purchase(PurchaseBookCommand command, CommandEventPublisher<Book> publisher) {
        publisher.publish(
                new BookPurchasedEvent(command.isbn(), command.author(), command.title(), command.numPages()));

        return command.isbn();
    }
}
```

### State Rebuilding

Define annotation based state rebuilding handlers for reconstructing command model state:

```java
public record Book(String isbn, long numPages) {}

@CommandHandlerConfiguration
public class BookHandling {

    @StateRebuilding
    public Book on(BookPurchasedEvent event) {
        return new Book(event.isbn(), event.numPages());
    }
}
```

### Testing Command Logic

Test command handling logic using the built-in test fixture support:

```java
@CommandHandlingTest
public class BookHandlingTest {

    @Test
    public void canBePurchased(@Autowired CommandHandlingTestFixture<Book, PurchaseBookCommand, String> fixture) {
        fixture.givenNothing()
                .when(new PurchaseBookCommand("4711", "JRR Tolkien", "LOTR", 435))
                .expectSuccessfulExecution()
                .expectSingleEvent(new BookPurchasedEvent("4711", "JRR Tolkien", "LOTR", 435));
    }
}
```

### Event Handling

Handle events asynchronously to decouple side effects:
```java
@Component
public class BookCatalogProjector {
    
    @EventHandling("catalog")
    public void on(BookPurchasedEvent event) {
        // ...
    }
}
```

## Project Contents

This is a multi-module project. It contains the following modules:

| module                                                                         | description                                                                     |
|--------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| [esdb-client](esdb-client)                                                     | Client SDK for the [EventSourcingDB](https://www.eventsourcingdb.io)            |
| [esdb-client-spring-boot-autoconfigure](esdb-client-spring-boot-autoconfigure) | Spring Boot auto configurations for the ESDB client SDK                         |
| [esdb-client-spring-boot-starter](esdb-client-spring-boot-starter)             | Spring Boot starter for the ESDB client SDK                                     |
| [framework](framework)                                                         | CQRS/ES core framework (depends on esdb-client)                                 |
| [framework-spring-boot-autoconfigure](framework-spring-boot-autoconfigure)     | Spring Boot auto configurations for the CQRS/ES framework                       |
| [framework-spring-boot-starter](framework-spring-boot-starter)                 | Spring Boot starter for the CQRS/ES framework                                   |
| [framework-test](framework-test)                                               | CQRS/ES framework test support with optional Spring support                     |
| [example-application](example-application)                                     | A complete library domain example application based on OpenCQRS and Spring Boot |


## License

This project is licensed under Apache 2.0 – see the [LICENSE file](LICENSE.txt) for details.

## Copyright

Copyright (C) OpenCQRS and contributors. All rights reserved.

## Contributions

We welcome contributions to OpenCQRS!
To ensure a smooth process and consistency with the project's direction, please contact us before starting work on a feature or submitting a pull request. This helps avoid duplicate efforts and ensures alignment.

Feel free to open issues or reach out via email – we’re happy to collaborate.

## Contact

For questions or feedback, please contact the development team at
`opencqrs (at) digitalfrontiers.de`.