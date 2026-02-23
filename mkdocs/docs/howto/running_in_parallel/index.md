---
description: Reliable Event Handling in a Distributed Environment
---

When deploying an {{ custom.framework_name }} application as multiple instances for example, to achieve high
availability or horizontal scalability
the [Event Handling Processor](../../reference/core_components/event_handling_processor/index.md)
addresses two issues that are typical for distributed event processing. First, we need to determine which instance is
responsible for processing the events to prevent parallel processing of the same event. This is addressed by **Leader
Election**. Second, it must be ensured that no events are lost or processed repeatedly when the processing instance
changes or the system is restarted. This is addressed by **Progress Tracking**.

**Leader Election** is solved in {{ custom.framework_name }} using a distributed locking: only the instance holding the
lock processes events, while all other instances remain on standby.

**Progress Tracking** is solved in {{ custom.framework_name }} using a shared
{{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.ProgressTracker") }}.

This guide describes how to configure both mechanisms in order to achieve at-least-once delivery semantics in a
distributed (cloud) environment. The necessary configuration steps are straightforward, but it is important to
understand which infrastructure components are involved in each case.

## Leader Election through Distributed Locking

In {{ custom.framework_name }}, distributed locking is supported via Spring Integration out of the box. It is based on
Spring
[
`LockRegistry`](https://docs.spring.io/spring-integration/api/org/springframework/integration/support/locks/LockRegistry.html)
interface and its ready-to-use implementations.
Depending on your requirements, you can choose between a Database, Redis or Zookeeper based implementation.

In all cases, the base dependency `spring-boot-starter-integration` must be added to the _build script_:

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

### Distributed Locking using a Database

To use a JDBC compliant database for the locking add the respective dependency to the _build script_. In this example
PostgreSQL is used as database system, but any Spring supported database will work. There are even some schema
definitions for different databases provided in the package [
`org.springframework.integration.jdbc`](https://github.com/spring-projects/spring-integration/tree/main/spring-integration-jdbc/src/main/resources/org/springframework/integration/jdbc).

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
package [
`org.springframework.integration.jdbc`](https://github.com/spring-projects/spring-integration/tree/main/spring-integration-jdbc/src/main/resources/org/springframework/integration/jdbc).
from Spring Integration contains SQL scripts for various database systems. This example for a PostgreSQL is also copied
from there. In this case, the name of the table has been adapted (prefix `EVENTHANDLER_`) so that it is clear for what
purpose the lock is used.

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
could be configured for a setup with multiple instances of the same {{ custom.framework_name }} application.

```java hl_lines="7 13" 

@Configuration
public class CqrsConfiguration {

    @Bean
    public DefaultLockRepository defaultLockRepository(DataSource dataSource) {
        var result = new DefaultLockRepository(dataSource);
        result.setPrefix("EVENTHANDLER_"); /* (1)! */
        return result;
    }

    @Bean
    public LockRegistry lockRegistry(DefaultLockRepository lockRepository) {
        lockRepository.setInsertQuery(lockRepository.getInsertQuery() + " ON CONFLICT DO NOTHING"); /* (2)! */
        return new JdbcLockRegistry(lockRepository);
    }
}
``` 

1. Configures `EVENTHANDLER_` as a readable prefix for the table containing the distributed lock.
2. Suppresses excessive logging of constraint violations during lock acquisition by adding the database specific (
   PostgreSQL) instruction `ON CONFLICT DO NOTHING`.

### Distributed Locking using  Redis

To use Redis for the locking add the respective dependencies to the _build script_.

<!-- @formatter:off -->
=== ":simple-gradle: Gradle - Kotlin"

    ```kotlin title="build.gradle.kts"
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.integration:spring-integration-redis") 
    runtimeOnly("io.lettuce:lettuce-core")
    ```

=== ":simple-gradle: Gradle - Groovy"
    ```groovy title="build.gradle" 
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.integration:spring-integration-redis'
    runtimeOnly 'io.lettuce:lettuce-core'
    ```

=== ":simple-apachemaven: Maven"
    ```xml title="pom.xml" 
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
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

The dependency on spring-boot-starter-data-redis was added for convenience. This allows the connection to Redis to be 
configured via `application.properties` by specifying the `host`, `port`, and, if necessary, the `password`.

```properties title="application.properties"
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=<secret>
```

Define a suitable `RedisLockRegistry` bean as follows:

```java hl_lines="6" 

@Configuration
public class CqrsConfiguration {

    @Bean
    public LockRegistry lockRegistry(RedisConnectionFactory connectionFactory) {
        return new RedisLockRegistry(connectionFactory, "EVENTHANDLER"); /* (1)! */
    }
}
```

1. Configure the `RedisLockRegistry` to use `EVENTHANDLER` as key for the lock.

### Distributed Locking using ZooKeeper

To use ZooKeeper for the locking add the following dependency to the _build script_.

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

Since Spring Boot does not provide an autoconfigured ZooKeeper client the following beans need to be defined:

```java hl_lines="16"

@Configuration
public class CqrsConfiguration {

    @Bean(destroyMethod = "close")
    public CuratorFramework curatorFramework() {
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString("localhost:2181") /* (1)! */
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
        return client;
    }

    @Bean
    public LockRegistry lockRegistry(CuratorFramework client) {
        return new ZookeeperLockRegistry(client, "/EVENTHANDLER"); /* (2)! */
    }
}

```

1. Configure the Zookeeper connection string.
2. Configure the `ZookeeperLockRegistry` with a custom path where the information of the lock is stored.

## Progress Tracking

While distributed locking ensures that only one instance processes events at a time, it does not by itself
guarantee that a newly elected leader knows where to continue. When a standby instance takes over after the
previously active instance has stopped it must resume event processing from exactly the position where the
previous instance left off. To achieve this, the processing progress must be persisted in a location shared
across all instances.

Any durable storage can be used to track progress. {{custom.framework_name}} provides out of the box support for
Progress Tracking using a JDBC database, but supports custom implementations.
For [EventHandler](../../reference/extension_points/event_handler/index.md) providing durable
read models it is suggested to use the same storage for progress tracking, to
leverage [Event Handler Idempotency](../../reference/core_components/event_handling_processor/index.md#event-processing-loop).

### Progress Tracking using JDBC

{{ custom.framework_name }} provides a JDBC-based {{ javadoc_class_ref("
com.opencqrs.framework.eventhandler.progress.ProgressTracker") }}
for this purpose. It is necessary to configure it explicitly so that progress is stored persistently
and is available to all instances.

```java hl_lines="7"

@Configuration
public class CqrsConfiguration {

    @Bean
    public ProgressTracker jdbcProgressTracker(
            DataSource dataSource, PlatformTransactionManager transactionManager) {
        var result = new JdbcProgressTracker(dataSource, transactionManager);
        result.setProceedTransactionally(true); /* (1)! */
        return result;
    }
}
```

1. Enables EventHandlers to participate in the same transaction as the progress tracker. This means that read model
   updates and the progress will be committed atomically, effectively guaranteeing exactly-once-delivery semantics.

### Implementing a custom Progress Tracker

Implementing your own {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.ProgressTracker") }} is easy,
as demonstrated by the following example for Redis.

```java
public class RedisProgressTracker implements ProgressTracker {
    private static final Logger LOG = LoggerFactory.getLogger(RedisProgressTracker.class);

    private final StringRedisTemplate template;

    public RedisProgressTracker(StringRedisTemplate template) {
        this.template = template;
    }

    private static @NonNull String createKey(String group, long partition) {
        return "group-%s-partition-%s".formatted(group, partition);
    }

    @Override
    public Progress current(String group, long partition) {
        return Optional.ofNullable(template.opsForValue().get(createKey(group, partition)))
                .map(it -> (Progress) new Progress.Success(it))
                .orElseGet(Progress.None::new); /* (1)! */
    }

    @Override
    public void proceed(String group, long partition, Supplier<Progress> execution) {
        template.execute(new SessionCallback<List<Object>>() { /* (2)! */
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                try {
                    operations.multi(); /* (3)! */
                    switch (execution.get()) {
                        case Progress.None ignored -> {
                        }
                        case Progress.Success success -> {
                            operations.opsForValue().set(createKey(group, partition), success.id());
                        }
                    }
                    return operations.exec(); /* (4)! */
                } catch (RuntimeException e) {
                    LOG.error(e.getMessage(), e);
                    operations.discard(); /* (5)! */
                    throw e;
                }
            }
        });
    }
}
```

1. You can retrieve the current progress marker directly by querying the corresponding key in Redis. If there is no
   value yet, a new one is created.
2. To ensure that all subsequent commands are executed on the same connection, it is necessary to create a session
   callback. The actual grouping of commands into a session is handled using `MULTI` and `EXEC`.
3. `MULTI` starts a queue in which all subsequent commands are placed.
4. `EXEC` ensures that the queue is processed in one go.
5. If an error occurs, `DISCARD` ensures that the queue is cleared and none of the submitted commands are executed.

Now that we have implemented our own `ProgressTracker`, we need to make it available to the Spring context so that
{{custom.framework_name}} uses it instead of the built-in {{ javadoc_class_ref("com.opencqrs.framework.eventhandler.progress.JdbcProgressTracker") }}.

```java hl_lines="7"
@Configuration
public class CqrsConfiguration {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.setEnableTransactionSupport(true); /*(1)!*/
        return redisTemplate;
    }

    @Bean
    public ProgressTracker redisProgressTracker(StringRedisTemplate template) {
        return new RedisProgressTracker(template); /*(2)!*/
    }
}
```

1. Enables that this template participates in ongoing transactions using MULTI...EXEC|DISCARD to keep track of
   operations.
2. Instantiate a `RedisProgressTracker` and make it available to the Spring context.

Within the event handler itself, you can also use the `StringRedisTemplate` to place additional commands in the
connection-specific queue. This way, changes are saved to Redis along with the progress.

```java

@EventHandling("book-verifier")
public void on(BookPurchasedEvent event, @Autowired StringRedisTemplate template) {
    template.opsForValue().set(event.isbn(), event.author()); /*(1)!*/
}
```

1. In this case, the book's author is stored under the ISBN.

Last but not least, the Progress Tracker must be defined in `application.yaml`. This is done by referencing the bean
that implements the ProgressTracker. In the example, this applies to all event handlers.

```yaml
opencqrs:
  event-handling:
    standard:
      progress.tracker-ref: "redisProgressTracker"
```