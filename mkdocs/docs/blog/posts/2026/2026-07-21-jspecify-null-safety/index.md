---
draft: false
title: "Null-Safety, Now Included: JSpecify in OpenCQRS"
date: 2026-07-21
authors:
  - sebastian
categories:
  - Engineering
tags:
  - null-safety
  - jspecify
  - opencqrs
  - java
  - error-prone
slug: jspecify-null-safety
---

# Null-Safety, Now Included: JSpecify in OpenCQRS

We all know null is the age-old problem in Java, famously called "the billion dollar
mistake." After decades of attempts to fix it, from JSR-305 annotations to new languages
on the JVM like Kotlin, Java itself still doesn't have a universally adopted standard for
nullability.

As a framework, OpenCQRS should avoid NPE bugs slipping through to production as much as
possible. You rely on it, and it has to work. So we looked at what the Java ecosystem offers today and
picked the best option we could find: **[JSpecify](https://jspecify.org)**. Every module
in the framework is now null-safe by default, enforced at compile time. This hardens not
only our code, it also benefits yours. Let's have a look.

<!-- more -->

## What Is JSpecify and Why We Chose It

Java has had nullability annotations for years. JSR-305 has `@Nullable` and `@Nonnull`,
and it found widespread adoption across libraries like Guava, SpotBugs, and Checkstyle.
But it was never truly finalized through the JCP. Without a formal spec, alternatives
like Jakarta's and JetBrains' own variants cropped up, and the landscape became
fragmented. Every tool and dependency ended up with a different set, if any. You were
stuck with multiple dependencies using different annotations, making it hard to impossible
to properly enforce null-safety.

JSpecify is different. It is a community-driven, tool-agnostic specification backed by a
consortium that includes Google, JetBrains, Uber, Apple, and others. The annotations
work across compilers, IDEs, and static analysis tools.

The key difference to previous annotations is the inversion of defaults. Older libraries
assumed everything was nullable unless you said otherwise. That meant annotating every
field, parameter, and return type that should not be null. Tedious and impractical, so
most code went unannotated.
JSpecify flips this with `@NullMarked` at package level. When a package is
`@NullMarked`, everything in it is non-null by default. Every possible `null` has to be
marked, hence the name. You annotate only the exceptions - the handful of places where
null is valid or unavoidable.

??? tip "Why the inversion matters in practice"
    With previous annotations, most code was not annotated and therefore ambiguous. Tools
    had to guess whether an unannotated parameter was meant to be nullable or not.
    `@NullMarked` removes that ambiguity. Unannotated *always* means non-null. This
    makes the contract explicit and lets tools like NullAway enforce it without guessing.

In addition to its more practical approach to annotating, community backing, and adoption
in tools like NullAway, JSpecify was also added to the Spring Framework, which they wrote
about **[here](https://spring.io/blog/2025/11/12/null-safe-applications-with-spring-boot-4)**.
Since OpenCQRS uses Spring, we now benefit from their annotations while adding our own
null-safety. The more of the ecosystem adopts JSpecify, the more value it adds for
everyone downstream.

## JSpecify in Action - Concrete Examples

Two examples from the OpenCQRS codebase show what the type system now catches or
expresses.

The first is `AtomicReference<@Nullable ExecutorService>` in our **[event handling
processor](../../../../reference/core_components/event_handling_processor/index.md)**.
The `AtomicReference` itself is always non-null. It is a final field, initialized at
construction. But its content is nullable. When the processor is stopped, the reference
holds null. When it is running, it holds a live `ExecutorService`. Older annotation
libraries could not express this. You could say the field is non-null, or you could say
it is nullable, but you could not say "the container is non-null but its content might
be." JSpecify supports type-argument nullability, so you get exactly that.

```java
private final AtomicReference<@Nullable ExecutorService> running = new AtomicReference<>();
```

The second example is `@NullUnmarked` on JPA entity classes. When a package is
`@NullMarked`, every type defaults to non-null. But JPA entities are populated through
reflection. The runtime instantiates the class and sets fields without going through your
constructor. From the type system's perspective, there is no code path that sets the
fields before they could be read. You could annotate every field as `@Nullable`, but that
would be misleading. Most fields are non-null after the entity is loaded. The pragmatic
answer is `@NullUnmarked` on the entity class. It opts the class out of null
checking because the framework's reflection-based lifecycle does not participate in the
type system's guarantees. It is a blunt instrument, it also hides fields that are
genuinely nullable even after loading, but for entities where most fields are non-null
post-load, it is the best pragmatic compromise.

```java
@Entity
@NullUnmarked
public class BookEntity {
    @Id
    public String isbn;
    public Long pages;
    // ...
}
```

These are not edge cases. Building a framework on top of Spring, JPA, and other
reflection-heavy libraries means running into exactly these situations. The type system
adds value where it can, and steps aside where it cannot.

??? info "When you might not want to opt out at entity level"
    `@NullUnmarked` on the whole class is the right call for most JPA entities, but not
    all. If your entity has fields that track optional state transitions, like a
    `cancelledAt` timestamp that is only set when the entity goes through cancellation,
    opting out the entire class hides that nullability. In those cases, you might want to
    annotate individual fields as `@Nullable` instead, or split the entity into a loaded
    part and a nullable part. The trade-off depends on how many fields are genuinely
    nullable in your domain.

## What This Means for You

There is an immediate benefit you get from our adoption of JSpecify without any
configuration. IntelliJ IDEA (CE) 2025.3 and later automatically picks up JSpecify
annotations from your dependencies and shows you nullability hints inline. No extra
dependencies. If you depend on OpenCQRS, your IDE already tells you which parameters are
nullable. It won't prevent you from compiling unsafe code, but it is already a useful
heads-up.

For full compile-time safety, you need three things: the JSpecify annotation library, the
**[Error Prone](https://errorprone.info/docs/installation)** Gradle plugin, and
**[NullAway](https://github.com/uber/NullAway/wiki/JSpecify-Support)** as an Error Prone
checker. Put `@NullMarked` on your packages, configure NullAway with JSpecify mode enabled,
and the moment you pass null where OpenCQRS expects a non-null value, the build fails. You
annotate only the places where null is actually a valid value. Our
**[build scripts](https://github.com/open-cqrs/opencqrs/blob/main/build.gradle.kts)**
show a working example.

??? tip "What is Error Prone and how does NullAway fit in?"
    Error Prone is a static analysis tool that hooks into the Java compiler. It runs
    additional checks during compilation and can report warnings or errors. NullAway is an
    Error Prone plugin that specializes in null-safety. You add both to your build, and
    NullAway becomes one of the checks Error Prone runs. This means NullAway integrates
    directly into your existing compilation pipeline.

Kotlin gets the same compile-time safety for free. Starting with Kotlin 1.8.20,
`@NullMarked` and `@Nullable` are understood natively, and as of 2.1.0 the compiler
emits errors for nullability violations by default. No extra configuration needed.

## Conclusion

JSpecify is tool-agnostic, community-driven, and adopted by Spring Boot 4.x. For
OpenCQRS, it means the framework is more reliable. For you, it means clearer API contracts
and fewer surprises at runtime.

Check out the **[OpenCQRS repository on GitHub](https://github.com/open-cqrs/opencqrs)**
to see how we use it. The `@NullMarked` annotations in `package-info.java` files and the
NullAway configuration in the build scripts are a good place to start.
*[NPE]: NullPointerException - the runtime error thrown when code attempts to use a null reference
*[JCP]: Java Community Process - the body responsible for maintaining Java specifications
