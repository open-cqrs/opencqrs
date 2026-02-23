---
description: How to run multiple instances in parallel
---

Running multiple instances is easy if a few configuration steps are followed. In this guide,
we will look at these potential stumbling blocks and see which configurations are necessary to ensure
trouble-free operation.

## Distributed Locking

In order for an application based on event sourcing to reliably restore the actual state from all generated events,
it is essential that each event is processed exactly once. As long as only one instance of an OpenCQRS application is
running, this is automatically guaranteed by the framework. However, if the application is running in more than one JVM,
an external mechanism is required to ensure that each event is processed exactly once.

In OpenCQRS, this is made possible by Spring Boot Integration, which provides the
[
`LockRegistry`](https://docs.spring.io/spring-integration/api/org/springframework/integration/support/locks/LockRegistry.html)
interface and also offers various implementations of this interface.
Depending on requirements, you can choose between a Zookeeper, Redis, or JDBC-based implementation.
Depending on the _build script_ used, the appropriate integration must be added next to
`spring-boot-starter-integration`.

<!-- @formatter:off -->
=== ":simple-gradle: Gradle - Kotlin"
    ```kotlin title="build.gradle.kts"
    implementation("org.springframework.boot:spring-boot-starter-integration")
    ```

=== ":simple-gradle: Gradle - Groovy"
    ```groovy title="build.gradle" 
    implementation 'org.springframework.boot:spring-boot-starter-integration'
    ```

=== ":simple-apachemaven: Maven"
    ```xml title="pom.xml" 
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-integration</artifactId>
    </dependency>
    ```
<!-- @formatter:on -->

### Locking with a Database

To use a JDBC compliant database for the locking add the respective dependency to the _build script_. In this example
PostgreSQL is used as database system, but any Spring supported database will work. There are even some schema
definitions for different databases provided in the package `org.springframework.integration.jdbc`.

<!-- @formatter:off -->
=== ":simple-gradle: Gradle - Kotlin"
    ```kotlin title="build.gradle.kts"
    implementation("org.springframework.integration:spring-integration-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    ```

=== ":simple-gradle: Gradle - Groovy"
    ```groovy title="build.gradle" 
    implementation 'org.springframework.integration:spring-integration-jdbc'
    runtimeOnly 'org.postgresql:postgresql'
    ```

=== ":simple-apachemaven: Maven"
    ```xml title="pom.xml" 
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-integration-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    ```
<!-- @formatter:on -->

A Spring data source must be defined in the `application.properties`. To do this, at least the `url`, the `username` and
the `password` must be specified.

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/<database>
spring.datasource.username=<username>
spring.datasource.password=<password>
```

In this database, there must also be a suitable table in which the distributed lock can be stored. The
package `org.springframework.integration.jdbc` from Spring Integration contains SQL scripts for various
database systems. This example for a PostgreSQL is also copied from there. In this case, the name of the
table has been adapted (prefix `EVENTHANDLER_`) so that it is clear for what purpose the lock is used.

<!-- @formatter:off -->
```sql
CREATE TABLE IF NOT EXISTS EVENTHANDLER_LOCK (
        LOCK_KEY CHAR (36) NOT NULL,
        REGION VARCHAR(100) NOT NULL,
        CLIENT_ID CHAR(36),
        CREATED_DATE TIMESTAMP NOT NULL,
        constraint INT_LOCK_PK primary key(LOCK_KEY, REGION)
    );
```
<!-- @formatter:on -->

The following configuration class demonstrates how the two necessary beans `DefaultLockRepository` and
`JdbcLockRegistry`
could be configured for a setup with multiple instances of the same OpenCQRS application.

```java hl_lines="7 13" 

@Configuration
public class CqrsConfiguration {

    @Bean
    public DefaultLockRepository defaultLockRepository(DataSource dataSource) {
        var result = new DefaultLockRepository(dataSource);
        result.setPrefix("EVENTHANDLER_");
        return result;
    }

    @Bean
    public LockRegistry jdbcLockRegistry(DefaultLockRepository lockRepository) {
        lockRepository.setInsertQuery(lockRepository.getInsertQuery() + " ON CONFLICT DO NOTHING");
        return new JdbcLockRegistry(lockRepository);
    }
}
```

*Line 7* defines `EVENTHANDLER_` as a readable prefix for the distributed lock used to prevent more than one instance
from
processing the event stream, matching the schema definition further up.

*Line 13* adds the database specific (PostgreSQL) instruction `ON CONFLICT DO NOTHING` to `InsertQuery` to avoid
exessive
logging on the database, telling that an insert failed, because of duplicate keys.

### Locking with Redis

As already mentioned, Redis can also be used to manage the distributed lock. The following dependencies are needed:

<!-- @formatter:off -->
=== ":simple-gradle: Gradle - Kotlin"
    ```kotlin title="build.gradle.kts"
    implementation("org.springframework.integration:spring-integration-redis")
    runtimeOnly("io.lettuce:lettuce-core")
    ```

=== ":simple-gradle: Gradle - Groovy"
    ```groovy title="build.gradle" 
    implementation 'org.springframework.integration:spring-integration-redis'
    runtimeOnly 'io.lettuce:lettuce-core'
    ```

=== ":simple-apachemaven: Maven"
    ```xml title="pom.xml" 
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-integration-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>io.lettuce</groupId>
        <artifactId>lettuce-core</artifactId>
        <scope>runtime</scope>
    </dependency>
    ```
<!-- @formatter:on -->

With the dependencies in place additional information on how to connect to the Redis server.
Add `host`, `port` and if needed the `password` to the `application.properties`.

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=<secret>
```

In contrast to the JDBC integration, no schema is necessary, as there is no such thing in Redis. The
next step is to define a suitable bean, which then provides a `RedisLockRegistry` for the interface.

```java hl_lines="6" 

@Configuration
public class CqrsConfiguration {

    @Bean
    public LockRegistry jdbcLockRegistry(RedisConnectionFactory connectionFactory) {
        return new RedisLockRegistry(connectionFactory, "EVENTHANDLER");
    }
}
```

*Line 6* defines the `RedisLockRegistry` and the key used in Redis to keep the lock.

### Locking with ZooKeeper

The third option is to used Apache ZooKeeper, a centralized service for maintaining configuration information, naming,
providing distributed synchronization, and providing group services. To use ZooKeeper, add the following dependencies to
the _build script_.

<!-- @formatter:off -->
=== ":simple-gradle: Gradle - Kotlin"
```kotlin title="build.gradle.kts"
implementation("org.springframework.integration:spring-integration-zookeeper")
```

=== ":simple-gradle: Gradle - Groovy"
```groovy title="build.gradle" 
implementation 'org.springframework.integration:spring-integration-zookeeper'
```

=== ":simple-apachemaven: Maven"
```xml title="pom.xml" 
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-integration-zookeeper</artifactId>
</dependency>
```
<!-- @formatter:on -->

Since the `spring-integration-zookeeper` dependency already includes a ZooKeeper client no additional
runtime dependency is needed.

The configuration differs slightly from the other two solutions, as the included ZooKeeper client does not provide
automatic configuration via the `application.properties`, the client bean must be created explicitly.

```java hl_lines="16"

@Configuration
public class CqrsConfiguration {

    @Bean(destroyMethod = "close")
    public CuratorFramework curatorFramework() {
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString("localhost:2181")
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
        return client;
    }

    @Bean
    public LockRegistry jdbcLockRegistry(CuratorFramework client) {
        return new ZookeeperLockRegistry(client, "/EVENTHANDLER");
    }

}

```

*Line 16* creates the `ZookeeperLockRegistry` with a custom path where the information of the lock is stored.

With the usual Spring Boot means, it is of course possible to configure the `CuratorFramework` with regard to `host` and
`port` via the `application.properties`.

## Progress Tracking

So that an OpenCQRS instance that is in stand-by can continue event processing in the event of an error without a
event being forgotten or processed twice, the progress must be saved permanently. In this case, it is necessary
to use a {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.ProgressTracker") }} which saves the
progress permanently and is available to all instances.

```java hl_lines="7"

@Configuration
public class CqrsConfiguration {

    public ProgressTracker jdbcProgressTracker(
            DataSource dataSource, PlatformTransactionManager transactionManager) {
        var result = new JdbcProgressTracker(dataSource, transactionManager);
        result.setProceedTransactionally(true);
        return result;
    }
}
```

OpenCQRS provides a JDBC based {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.ProgressTracker") }}.
If the write model is also created in a JDBC compatible database, this has the additional advantage if, as configured in
*line 7*, participates in the transaction and write model and progress are saved together. This means that no additional
measures need to be taken to prevent duplicate processing of events.


## Summary

In order to run an OpenCQRS application with more than one instance, it is necessary to have a system that allows
to make a leader election for the OpenCQRS application so that events are processed by exactly one instance
and no double processing takes place.
Through Spring Boot integration, there are various ways to ensure this leader election, which
have different advantages and disadvantages. Depending on the environment and requirements profile, one or other
infrastructure component is already available and can be reused. The decision as to which solution is best in a specific
case does not have to be made at the beginning, as it is possible to adapt this implementation
at any time. For example, you can start with a JDBC-based solution, as there may already be a database for
the projections. Later, if the database becomes a bottleneck, you can switch to a Redis-based solution,
which scales better.