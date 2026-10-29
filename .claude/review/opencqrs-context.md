# OpenCQRS review context

Shared foundation for all review agents (`.claude/agents/review-*.md`). Describes what counts as correct, idiomatic and complete in this repository.

## Project

OpenCQRS is a Java framework (Java 21, Gradle Kotlin DSL) for CQRS/Event Sourcing on top of EventSourcingDB. It is a **library**: every public signature is API for third-party code, and every new mandatory dependency ends up with the user.

## Modules and dependency direction

| Module | Role |
|---|---|
| `esdb-client` | HTTP client for EventSourcingDB, marshalling (`Event`, `EventCandidate`, `EsdbClient`) |
| `esdb-client-spring-boot-autoconfigure` / `-starter` | Spring Boot wiring for the client |
| `framework` | Command handling, state rebuilding, event handling, upcasting, persistence |
| `framework-test` | Test fixtures (`CommandHandlingTestFixture` etc.) |
| `framework-spring-boot-autoconfigure` / `-starter` | Spring Boot wiring for the framework |
| `example-application` | Example application, not part of the library |
| `mkdocs/` | Documentation (concepts, howto, reference, tutorials, blog) |

Direction: `esdb-client` ← `framework` ← `framework-test`; autoconfigure modules depend on their core module, never the other way round. `framework` and `esdb-client` are usable without Spring.

## Conventions

- **Null safety:** JSpecify. Packages are `@NullMarked` via `package-info.java`; nullable parameters, fields and return values carry `@Nullable`. New packages need a `package-info.java`.
- **Formatting:** Spotless with palantir-java-format (`./gradlew spotlessCheck`). Formatting is not a finding unless `spotlessCheck` fails.
- **Records** for value types; new record components change the canonical constructor (breaking change).
- **Optional integrations** (e.g. Micrometer, OpenTelemetry, JDBC): `compileOnly` in the core module, wiring in autoconfigure behind a class-level `@ConditionalOnClass` or inside a nested `@Configuration`. Third-party types must not appear in method signatures of an unconditionally loaded configuration.
- **Autoconfigure:** registration in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; default beans with `@ConditionalOnMissingBean` so users can override them; `@ConditionalOnBean` only with matching ordering (`@AutoConfiguration(after = …)`). Tests with `ApplicationContextRunner`, `FilteredClassLoader` for missing classes.
- **Dependency versions:** managed by the Spring Boot BOM. Additional BOMs must not override versions Spring Boot manages.
- **Javadoc** for public types and methods; it is built and linked from the docs (`javadoc_class_ref` in mkdocs).
- **Event compatibility:** stored events are long-lived. New fields must be allowed to be absent when reading old events; changes to serialization need a thought about upcasting.

## Terminology

OpenCQRS has **no aggregates**. Docs, Javadoc and prose use "instance" or "state". A newly introduced "aggregate" in docs or Javadoc is a finding.

## Review vs. CI

Deterministic checks belong to the build and CI, not to the review: compilation, `spotlessCheck` and the test suite run in `.github/workflows/qa.yml` on every PR. Their result reaches you as `CI_STATUS`; a red check is not repeated as a finding. The review is judgement: does the change do what it should, does it fit, what does it affect, what is missing. Where you notice that a whole class of defect could be caught deterministically (e.g. broken docs links, API compatibility), propose that as a Suggestion for the build — once, not per occurrence.

## Severities

- **Error** — must be fixed before merge (wrong behavior, startup failure, broken docs links/build, unintended breaking change)
- **Warning** — should be discussed or improved
- **Suggestion** — could be improved, not a blocker

## Finding rules (all reviewers)

- Every finding needs `file:line` (path relative to the repository root) and evidence: a code citation, call path or docs location. No evidence, no finding.
- Every finding names a concrete scenario: input/state → wrong behavior, or a measurable benefit.
- Only code the diff changes or breaks through the change. No style preferences, no over-engineering.
- Read changed code in context: callers, implementations, tests, neighbouring classes in the same package.
- Do not run Gradle builds or tests. If a claim could only be confirmed by running something, say so in the finding ("Unverified"); the moderator weighs it.
- Reviewers never change code.
