
# Tracing

{{ custom.framework_name }} supports EventSourcingDB's [integration of opentelemetry](https://docs.eventsourcingdb.io/deployment-and-operations/integrating-opentelemetry/) and the [W3C Trace Context standard](https://www.w3.org/TR/trace-context/#trace-context-http-headers-format) by providing a series of interfaces as well as a set of default implementations for instrumenting one's app.

## Enriching Events

To enrich fired events with event-tracing data, wether that be from an external service or from an internal source, implement the {{ javadoc_class_ref("com.opencqrs.esdb.client.tracing.TracingEventEnricher") }}-Interface:

```java
public interface TracingEventEnricher {
    
    EventCandidate enrichWithTracingData(EventCandidate candidate);
    
}
```

The ``enrichWithTracingData``-method takes an {{ javadoc_class_ref("com.opencqrs.esdb.client.EventCandidate") }}-record and enriches it with ``traceparent`` and ``tracestate`` header information.

Register an instance of your new class as a bean when using one of {{ custom.framework_name }}'s Spring Boot starters. It will then automatically be used anytime an event is written to the ESDB.

When not using the Spring Boot starter, pass the instance to the {{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbClient") }}'s constructor

## Extract Trace Information 

To extract stored trace information from a given event in the ESDB, implement the {{ javadoc_class_ref("com.opencqrs.framework.tracing.EventTracingContextExtractor") }}-Interface:

```java
public interface EventTracingContextExtractor {

    void extractAndRestoreContextFromEvent(Event event, Runnable runnable);
    
}
```

Here ``event`` contains ``traceparent`` and (possibly) ``tracestate`` headers needed to resurrect the tracing context, while ``runnable`` is a closure containing the event processing logic which is to be executed within the resurrected context.

Register your implementation as a bean when using the Spring Boot starter or pass it as an argument to the {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }}'s constructor.

## Building Spans

In {{ custom.framework_name }}, every interaction can be divided into the following steps:

- Receive a request (e.g. through the web layer)
   - Create and rout a command based on the request
      - Rebuild state based on current event-trail to validate or reject command
      - Handle event(s) fired by successfully validated and routed command in appropriate event handler(s)
        - Create and route additional command(s) from within the event handler(s)

Each of the invoked command/event handlers and state rebuilders corresponds to a span within the interaction's overall trace, identified by the [Parent-ID](https://www.w3.org/TR/trace-context/#parent-id) segment of the ``traceparent``-header, where the nesting hierarchy in the listing above represents the nesting hierarchy of the actual spans.

To use your own span building-logic, implement the {{ javadoc_class_ref("com.opencqrs.framework.tracing.TracingContextSpanBuilder") }}-interface

```java
public interface TracingContextSpanBuilder {

    void executeRunnableWithNewSpan(Map<String, String> spanInfo, Runnable runnable);

    void executeRunnableWithNewSpan(
            Map<String, String> spanInfo, Runnable runnable, UnaryOperator<Map<String, String>> spanInfoPostProcessor);

    <R> R executeSupplierWithNewSpan(Map<String, String> spanInfo, Supplier<R> supplier);

    <R> R executeSupplierWithNewSpan(
            Map<String, String> spanInfo,
            Supplier<R> supplier,
            BiFunction<R, Map<String, String>, Map<String, String>> spanInfoPostProcessor);
}
```

``executeRunnableWithNewSpan`` runs a piece of logic provided as the ``runnable`` within a newly created span of the parent trace (or span) and enriches it with the metadata provided in the ``spanInfo``.
In case the ``runnable`` alters some application state which you also want to reflect in the span metadata, one can use the overload of the method to further enrich the span's metadata by providing a ``spanInfoPostProcessor``-closure which returns updated/additional information in a map *after* the ``runnable`` has been executed.

``executeSupplierWithNewSpan`` works analogously to ``executeRunnableWithNewSpan`` and it's overload, except the spanned logic now returns a value, which the span builder passes up to its caller.

Register your implementation as a bean when using the Spring Boot starter or pass it as an argument to the {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.EventHandlingProcessor") }}'s constructor.

### Enriching Handlers With Span Information

Additionally, you can have one of your custom [Event Handlers](../extension_points/event_handler/index.md), [Command Handlers](../extension_points/command_handler/index.md) or [State Rebuilding Handlers](../extension_points/state_rebuilding_handler/index.md) implement the {{ javadoc_class_ref("com.opencqrs.framework.tracing.TracingSpanInformationSource") }} interface to provide further information to the span builders:

```java
public interface TracingSpanInformationSource {

    String getHandlingClassSimpleName();

    String getHandlingClassFullName();

    String getHandlingMethodSignature();

    default Map<String, String> getEventHandlingSpanInformation() {
        return Map.ofEntries(
                Map.entry("handling.class", getHandlingClassFullName()),
                Map.entry("handling.method", getHandlingMethodSignature()));
    }
}
```
