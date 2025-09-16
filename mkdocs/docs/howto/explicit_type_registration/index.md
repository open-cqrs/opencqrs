---
description: How to configure interoperable event type identifiers
---

[Events](../../concepts/events/index.md) require a __unique type__ to identify their corresponding
class when loading them from the {{ esdb_ref() }}. This type is assigned once, when publishing the event.

The {{ javadoc_class_ref("com.opencqrs.framework.persistence.EventRepository") }} is responsible for
resolving the type identifier using a configurable {{ javadoc_class_ref("com.opencqrs.framework.types.EventTypeResolver") }},
both for __reading__ and __writing__ events.

## Derived Class-Based Type Identifiers

By default, when using the framework's Spring Boot Starter, {{ javadoc_class_ref("com.opencqrs.framework.types.ClassNameEventTypeResolver") }} is registered
automatically. This implementation uses the fully-qualified Java classname as type identifier.

!!! warning
    The use of this type resolver is strongly discouraged, especially with respect to
    interoperability with other programming languages. Also package or classname refactoring using
    this type resolver, requires additional [upcasting](../../concepts/upcasting/index.md).

## Explicit Type Registration

For explicit type registration a {{ javadoc_class_ref("com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolver") }}
needs to be defined as Spring Bean within the application context, for instance within a dedicated 
`OpenCqrsFrameworkConfiguration`, as follows:
```java hl_lines="17-20"
package com.opencqrs.example.configuration;

import com.opencqrs.example.domain.book.events.*;
import com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class OpenCqrsFrameworkConfiguration {

    @Bean
    public PreconfiguredAssignableClassEventTypeResolver eventTypeResolver() {
        return new PreconfiguredAssignableClassEventTypeResolver(
                Map.of(
                        "com.opencqrs.library.book.purchased.v1", BookPurchasedEvent.class,
                        "com.opencqrs.library.book.lent.v1", BookLentEvent.class,
                        "com.opencqrs.library.book.returned.v1", BookReturnedEvent.class,
                        "com.opencqrs.library.book.page.damaged.v1", BookPageDamagedEvent.class
                )
        );
    }
}
```
This bean maps type identifiers to Java classes and is used when:

1. writing the event object to persist the type identifier within the raw event
2. reading the event to hint the {{ javadoc_class_ref("com.opencqrs.framework.serialization.EventDataMarshaller") }} how to deserialize the event

As shown in the highlighted lines, the type identifier may include a _version_ to be able
to evolve events using [upcasters](../../concepts/upcasting/index.md).
!!! tip
    Make sure to register __all__ of your event classes, as any manually defined
{{ javadoc_class_ref("com.opencqrs.framework.types.EventTypeResolver") }} bean supersedes the auto-configured {{ javadoc_class_ref("com.opencqrs.framework.types.ClassNameEventTypeResolver") }}.
    In other words, there is (for obvious reasons) no fallback to class-based types,
    once the bean has defined explicitly.
