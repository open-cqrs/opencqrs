# {{ custom.framework_name }} Documentation

Welcome to the official documentation for **{{ custom.framework_name }}** – an **open-source application framework** for building systems with **CQRS** and **event sourcing** on the **JVM**.

{{ custom.framework_name }} is designed to work seamlessly with **{{ esdb_ref() }}**, making it straightforward to model, implement, and operate event-driven applications. Built with **Java** and **Kotlin** developers in mind, it provides ready-to-use building blocks for **commands, events, aggregates, projections, and testing** – all with a strong focus on **clarity, testability, and maintainability**.

{{ custom.framework_name }} is developed and maintained by **[Digital Frontiers GmbH & Co. KG](https://www.digitalfrontiers.de/)** and released as **open source software** under the **Apache 2.0 license**. It is free to use and backed by professional expertise and consulting services when you need them.

## Learning the Concepts

New to **CQRS** or **event sourcing**, or wondering how {{ custom.framework_name }} can help you build applications? Start here:

- **[Getting Started](tutorials/README.md)**

      Step-by-step tutorials that guide you through building your first applications with {{ custom.framework_name }}.

- **[Guides and How-Tos](howto/README.md)**

      Practical recipes for solving common problems and implementing specific features.

- **[Reference](reference/README.md)**

      Detailed reference documentation for **commands, events, aggregates, projections,** and the **ESDB client SDK**.

- **[Concepts](concepts/README.md)**

      Background explanations of the core ideas behind {{ custom.framework_name }} and its design principles.

!!! tip "Need a refresher on CQRS or Event Sourcing?"
    If you are new to the underlying concepts, visit **[CQRS.com](https://www.cqrs.com)** for an in-depth introduction to **CQRS** and **event sourcing**.

## Platform and Integration

{{ custom.framework_name }} runs on the **Java Virtual Machine** and is fully compatible with JVM-based languages such as **Java** and **Kotlin**. It integrates seamlessly into modern application stacks:

- Available from **Maven Central** for both **Maven** and **Gradle** builds
- Smooth integration with **Spring Boot** for rapid application development and production deployment
- Native support for **{{ esdb_ref() }}** as the underlying event store, with a dedicated **Java client SDK**
- Built-in support for **testing command logic** with fixtures and utilities
- **Modular architecture** for extension and customization

## Need Support?

If you or your team need help **designing, integrating, or scaling** applications with {{ custom.framework_name }}, the team behind the framework is here to assist. Just reach out at **[opencqrs@digitalfrontiers.de](mailto:opencqrs@digitalfrontiers.de)**.
