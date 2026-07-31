
# Setup Tracing with OpenTelemetry

In order to quickly set up the [OpenTelemetry-based tracing](../../reference/tracing/index.md) of your app using {{ custom.framework_name }}'s defaults, do the following steps:

## 1. Add OpenTelemetry-Starter

Add the official [Spring Boot OpenTelemetry-starter](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot) to your dependencies:

```kotlin
runtimeOnly("org.springframework.boot:spring-boot-starter-opentelemetry")
```

## 2. Configure application properties

Add the following to your app's ``application.yml`` (or ``application.properites``):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "beans"
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: "http://{otel.backend.url}/v1/traces"
  tracing:
    sampling:
      probability: 1.0 
```
where ``otel.backend.url`` is the url (including the port, if any) of your running OpenTelemetry backend.

The sampling rate dictates what percentage of traces will actually be marked for further processing (such as sending them to the backend), i.e. end with the [trace flag](https://www.w3.org/TR/trace-context/#sampled-flag) `01`.

This only pertains to traces started in your app itself, not ones who started 'outside' your application where the `traceparent` (and `tracestate`) where sent as a request-header.

## 3. Start an OpenTelemetry backend

Start an OpenTelemetry-compliant backend to which your app can send its tracing data to.

One option would be the official [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/), which can be done via docker-compose like this:

```yaml
otel:
    image: otel/opentelemetry-collector
    volumes:
        - ./otel-collector-config.yaml:/etc/otelcol/config.yaml
    ports:
        - 4318:4318
```

where the `config.yaml` could look like this:

```yaml
receivers:
  otlp:
    protocols:
      http:
        endpoint: "{otel.backend.url}

exporters:
  debug:
    verbosity: detailed
  # Additional exporters such as Zipkin, Jaeger, etc.

service:
  pipelines:
    traces:
      receivers: [otlp]
      exporters: [debug] # And addition exporters defined above
```

## 4. Visualize (optional)

In order to visualize the generated tracing information, set up your backend to send it to a visualization tool like [Zipkin](https://zipkin.io/).

First, boot up an instance (docker-compose):

```yaml
zipkin:
    image: ghcr.io/openzipkin/zipkin:latest
    container_name: zipkin
    environment:
        - STORAGE_TYPE=mem
    ports:
        - 9411:9411
```

Edit your Backend's `config.yaml` like so:

```yaml
exporters:
  # Other exporters
  zipkin:
    endpoint: "http://zipkin:9411/api/v2/spans"

service:
  pipelines:
    traces:
      receivers: [otlp]
      exporters: [zipkin] # And other exporters defined above
```
