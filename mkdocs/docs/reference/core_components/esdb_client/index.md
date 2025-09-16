---
description: Accessing the {{ esdb_name() }} using the Java Client SDK
---

The `esdb-client` [module](../../modules/index.md) provides helper classes, encapsulating the
communication with the {{ esdb_ref() }}. This relieves the user from dealing with the database's
HTTP-based API directly. It provides the following key functionalities:

* Publishing events using [`EventCandidate`](../../events/index.md#events-and-eventcandidates) instances
* Reading of published events as [`Event`](../../events/index.md#events-and-eventcandidates) instances
* Querying published events using [EventQL](https://docs.eventsourcingdb.io/reference/eventql/)
* Continuously observing published events
* Monitoring the ESDB health status

All these operations provide suitable [client exceptions](../../exceptions/index.md#client-exceptions) in case of an error.

!!! info
    The key functionalities of the client SDK are provided using core JDK classes, e.g.
    using `java.net.http.HttpClient` for HTTP communication. However, the JDK does not provide
    suitable support for JSON marshalling. The {{ javadoc_class_ref("com.opencqrs.esdb.client.Marshaller") }} defines the
    necessary JSON transformation operations. {{ javadoc_class_ref("com.opencqrs.esdb.client.jackson.JacksonMarshaller") }}
    provides an implementation of this interface, requiring an additional Jackson dependency.

## Configuration

The ESDB client functionality is implemented within the {{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbClient") }} Java class. An instance 
of this class can be obtained, either by manually instantiating it or using Spring Boot autoconfiguration.

!!! tip
    In either case it is required to make sure [Jackson Databind](https://github.com/FasterXML/jackson-databind) is
    included as dependency, unless a custom {{ javadoc_class_ref("com.opencqrs.esdb.client.Marshaller") }} implementation is
    used.

### Manual Configuration

An instance of {{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbClient") }} can be created providing the necessary
configuration properties:

* a `java.net.URI` pointing to the {{ esdb_ref() }} instance to connect to
* an API token to authenticate
* a {{ javadoc_class_ref("com.opencqrs.esdb.client.Marshaller") }} instance responsible for serializing and deserializing events
* a `java.net.http.HttpClient.Builder` instance

The following example shows, how to instantiate an {{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbClient") }} using
the built-in {{ javadoc_class_ref("com.opencqrs.esdb.client.jackson.JacksonMarshaller") }}.

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.esdb.client.jackson.JacksonMarshaller;

import java.net.URI;
import java.net.http.HttpClient;

public class EsdbClientConfiguration {

    public static EsdbClient esdbClient() {
        return new EsdbClient(
                URI.create("http://localhost:3000"),
                "<api token>",
                new JacksonMarshaller(new ObjectMapper()),
                HttpClient.newBuilder()
        );
    }
}
```

The correct configuration can be confirmed, by calling `authenticate()`, e.g. as follows:

```java
public static void main(String[] args){
    EsdbClientConfiguration.esdbClient().authenticate();
}
```

### Spring Boot Auto-Configuration

For Spring Boot applications using the `esdb-client-spring-boot-starter` [module](../../modules/index.md)
and [Jackson Databind](https://github.com/FasterXML/jackson-databind)
{{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbClientAutoConfiguration") }} provides
a fully configured {{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbClient") }} Spring bean. The following
Spring Boot configuration properties must be provided, e.g. via a suitable `application.properties` file:

```properties
esdb.server.uri=http://localhost:3000
esdb.server.api-token=<api token>
```
With that configuration in place the auto-configured {{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbClient") }} instance
can be auto-wired within any other Spring bean, if needed. The configuration can be further customized by:

* overriding the {{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbClient") }} Spring bean with an application-defined one
* by providing a custom {{ javadoc_class_ref("com.opencqrs.esdb.client.Marshaller") }} Spring bean
* by providing a custom `java.net.http.HttpClient.Builder` Spring bean


!!! tip
    In order to make sure the {{ esdb_ref() }} connection is configured properly, it is recommended to include
    [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html) in the dependencies.
    {{ javadoc_class_ref("com.opencqrs.esdb.client.EsdbHealthContributorAutoConfiguration") }} will then make sure
    the Spring Boot application regularly checks the ESDB health and will expose this information via the context path
    `/actuator/health/esdb`.