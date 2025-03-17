/* Copyright (C) 2025 OpenCQRS and contributors */
package de.dxfrontiers.cqrs.example.configuration;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.eventhandler.EventHandling;
import com.opencqrs.framework.eventhandler.progress.JdbcProgressTracker;
import com.opencqrs.framework.persistence.EventSource;
import com.opencqrs.framework.types.PreconfiguredAssignableClassEventTypeResolver;
import de.dxfrontiers.cqrs.example.domain.book.api.BookLentEvent;
import de.dxfrontiers.cqrs.example.domain.book.api.BookPageDamagedEvent;
import de.dxfrontiers.cqrs.example.domain.book.api.BookPurchasedEvent;
import de.dxfrontiers.cqrs.example.domain.book.api.BookReturnedEvent;
import de.dxfrontiers.cqrs.example.domain.reader.api.ReaderRegisteredEvent;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.jdbc.lock.LockRepository;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CqrsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CqrsConfiguration.class);

    @Bean
    public DefaultLockRepository defaultLockRepository(DataSource dataSource) {
        var result = new DefaultLockRepository(dataSource);

        result.setPrefix("EVENTHANDLER_");
        return result;
    }

    @Bean
    public JdbcLockRegistry jdbcLockRegistry(LockRepository lockRepository) {
        return new JdbcLockRegistry(lockRepository);
    }

    @Bean
    public JdbcProgressTracker jdbcProgressTracker(
            DataSource dataSource, PlatformTransactionManager transactionManager) {
        return new JdbcProgressTracker(dataSource, transactionManager);
    }

    @Bean
    public PreconfiguredAssignableClassEventTypeResolver eventTypeResolver() {
        return new PreconfiguredAssignableClassEventTypeResolver(Map.of(
                "de.dxfrontiers.cqrs.library.reader.registered.v1", ReaderRegisteredEvent.class,
                "de.dxfrontiers.cqrs.library.book.purchased.v1", BookPurchasedEvent.class,
                "de.dxfrontiers.cqrs.library.book.lent.v1", BookLentEvent.class,
                "de.dxfrontiers.cqrs.library.book.returned.v1", BookReturnedEvent.class,
                "de.dxfrontiers.cqrs.library.book.page.damaged.v1", BookPageDamagedEvent.class));
    }

    @Bean
    public EventSource eventSource() {
        return new EventSource("tag://service-spring-library");
    }

    @EventHandling("logging")
    public void on(Event event) {
        log.info("event published: {}", event);
    }
}
