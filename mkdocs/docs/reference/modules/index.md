---
description: An overview of the CQRS/ES frameworks modules and when to use them
---

The following diagram depicts the CQRS/ES framework modules, their categorization and interdependencies:

``` mermaid
flowchart TD
    subgraph Core
        esdb_client["esdb-client"]
        cqrs_framework["cqrs-framework"]
    end
    subgraph Test Support
        cqrs_framework_test["cqrs-framework-test"]
    end
    subgraph Spring Boot Support
        esdb_client_autoconfigure["esdb-client-spring-boot-autoconfigure"]
        esdb_client_starter["esdb-client-spring-boot-starter"]
        cqrs_framework_autoconfigure["cqrs-framework-spring-boot-autoconfigure"]
        cqrs_framework_starter["cqrs-framework-spring-boot-starter"]
    end
    
    cqrs_framework_test --> cqrs_framework --> esdb_client
    cqrs_framework_starter --> esdb_client_starter --> esdb_client_autoconfigure --> esdb_client
    cqrs_framework_starter --> cqrs_framework_autoconfigure --> cqrs_framework
```

## Core

The framework is made up of two core modules:

1.  The `esdb-client` provides an SDK to communicate with the Event-Sourcing DB, hiding the REST API details from the user.
    Its main focus is on _publishing_ and _observing_ [events](../events/index.md).
2.  The `cqrs-framework` builds on top of the `esdb-client` and provides extension points to implement CQRS/ES applications using
    the Event-Sourcing DB as event store. Its main focus is on enabling application-specific _command_ and _event_ handling.

!!! info "Third-party dependencies"
    Both core modules are solely dependent on the JDK, so no further dependencies aren't necessarily required to use them.
    However, for serialization to and from JSON both contain default marshaller implementations using [Jackson](https://github.com/FasterXML/jackson-databind).
    It is therefore suggested to either include this library or to implement custom marshallers, by extending the appropriate
    interfaces (`Marshaller`{ title="com.opencqrs.eventsourcingdb.client.Marshaller" } and `EventDataMarshaller`{ title="com.opencqrs.framework.serialization.EventDataMarshaller" }),
    respectively.

## Spring Boot Support

Additional [Spring Boot](https://spring.io/projects/spring-boot) modules are offered to simplify the configuration of
the core components:

1.  `esdb-client-spring-boot-autoconfigure` provides the Spring Boot auto-configurations for the `esdb-client`.
2.  `esdb-client-spring-boot-starter` is the Spring Boot starter module for the `esdb-client`. Its main focus is on
    providing a preconfigured client for the Event-Sourcing DB.
3.  `cqrs-framework-spring-boot-autoconfigure` provides Spring Boot auto-configurations for the `cqrs-framework`.
4.  `cqrs-framework-spring-boot-starter` is the Spring Boot starter module for the `cqrs-framework`. Its main focus
    is on providing preconfigured components for _command_ and _event_ handling.

??? note "Spring Module Separation"
    The separation of the modules `esdb-client-spring-boot-autoconfigure` and `esdb-client-spring-boot-starter` and the
    modules `cqrs-framework-spring-boot-autoconfigure` and `cqrs-framework-spring-boot-starter`, respectively, follows best
    practices for creating Spring Boot starters. For end users the starters should be used when building Spring Boot applications.

## Test Support

`cqrs-framework-test` provides test support for the `cqrs-framework`. Its main purpose is to support the testing
of _command_ and _event_ handlers, e.g. within automated JUnit tests. The module can be used with or without Spring Boot,
depending on whether `cqrs-framework-spring-boot-starter` is used or not.