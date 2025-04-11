# openCQRS - CQRS/ES Framework for Event Sourcing DB

<!-- BADGES_START -->
![Release](https://img.shields.io/badge/Release-1.0.0-blue.svg)
![JDK](https://img.shields.io/badge/JDK-21-green.svg)
![ESDB](https://img.shields.io/badge/ESDB-0.79.1-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.2-brightgreen.svg)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
<!-- BADGES_END -->

## What is openCQRS?

openCQRS is an open-source CQRS framework written in Java with fast adaptation for Spring Boot applications, including a client for Event Sourcing DB. This framework provides comprehensive tools to develop robust, scalable applications using modern architectural patterns.

## What can you do with it?

- Enable Command Query Responsibility Segregation (CQRS) in your applications
- Implement Event Sourcing with seamless integration to Event Sourcing DB
- Separate write and read concerns for better scalability and maintenance
- Leverage comprehensive testing support for reliable development

## Core Features

- **Command Handling**: Process commands that express intent to change system state
- **Event Processing**: Capture and consume events to build various projections
- **State Rebuilding**: Reconstruct domain objects from event streams
- **Spring Boot Integration**: Auto-configuration modules for simplified setup
- **ESDB Client**: Native client for working with Event Sourcing DB
- **Full Test Support**: Specialized tools for testing command and event handlers

## Documentation and Resources

- **Example Application**: A complete library management system demonstrating framework capabilities
- **How-To Guides**: Step-by-step instructions for common implementation tasks
- **Tutorials**: Comprehensive guides from setup to advanced features
- **Reference Documentation**: Detailed information about all components and APIs

## Getting Started

The best way to start is by exploring:
1. The example application in `/example-application`
2. The tutorials in the documentation
3. Our comprehensive how-to guides

## Development Setup

### Required Tools
- JDK 21
- Gradle (no installation needed - wrapper script included in repository)

### IDE Setup
For IntelliJ IDEA:
1. Install plugin "palantir-java-format"
2. Enable plugin in options after restart

## Development Commands

1. Build the project: `./gradlew clean build`
2. Apply formatter: `./gradlew spotlessApply`
3. Publish locally / with explicit version: `./gradlew publishToMavenLocal` / `./gradlew -Pversion=<version> publishToMavenLocal`
4. Start documentation server: `env -C mkdocs mkdocs serve -f mkdocs.yml`
5. Documentation available at: http://localhost:8000

## License

openCQRS is licensed under the [Apache License 2.0](LICENSE.txt) - see the LICENSE.txt file for details.