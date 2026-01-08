/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.progress;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@DataJdbcTest
@Transactional(propagation = Propagation.NEVER)
public class JdbcProgressTrackerTest {

    @TestConfiguration
    public static class JdbcProgressTrackerConfig {

        @Bean
        public JdbcProgressTracker jdbcProgressTracker(
                DataSource dataSource, PlatformTransactionManager transactionManager) {
            var result = new JdbcProgressTracker(dataSource, transactionManager);
            result.setTablePrefix("TEST_");
            return result;
        }

        @Bean
        public JdbcProgressTracker transactionalJdbcProgressTracker(
                DataSource dataSource, PlatformTransactionManager transactionManager) {
            var result = new JdbcProgressTracker(dataSource, transactionManager);
            result.setTablePrefix("TEST_");
            result.setProceedTransactionally(true);
            return result;
        }
    }

    @Autowired
    private JdbcProgressTracker jdbcProgressTracker;

    @Autowired
    private JdbcProgressTracker transactionalJdbcProgressTracker;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    public void nonExistingGroupAndPartitionHasProgressNone() {
        assertThat(jdbcProgressTracker.current("no-such-group", 0)).isInstanceOf(Progress.None.class);
    }

    @Test
    public void progressSuccessProperlyPersistedInitially() {
        var groupId = UUID.randomUUID().toString();
        jdbcProgressTracker.proceed(groupId, 0, () -> new Progress.Success("4711"));

        assertThat(jdbcProgressTracker.current(groupId, 0)).isEqualTo(new Progress.Success("4711"));
        assertThat(jdbcProgressTracker.current(groupId, 1)).isInstanceOf(Progress.None.class);
    }

    @Test
    public void progressSuccessProperlyUpdated() {
        var groupId = UUID.randomUUID().toString();
        jdbcProgressTracker.proceed(groupId, 0, () -> new Progress.Success("4711"));

        jdbcProgressTracker.proceed(groupId, 0, () -> new Progress.Success("4712"));

        assertThat(jdbcProgressTracker.current(groupId, 0)).isEqualTo(new Progress.Success("4712"));
    }

    @Test
    public void progressNoneDiscarded() {
        var groupId = UUID.randomUUID().toString();
        jdbcProgressTracker.proceed(groupId, 0, () -> new Progress.Success("4711"));

        jdbcProgressTracker.proceed(groupId, 0, Progress.None::new);

        assertThat(jdbcProgressTracker.current(groupId, 0)).isEqualTo(new Progress.Success("4711"));
    }

    @Test
    public void progressExecutionFailureNotPersisted() {
        var groupId = UUID.randomUUID().toString();
        jdbcProgressTracker.proceed(groupId, 0, () -> new Progress.Success("4711"));

        var exception = new RuntimeException();

        assertThatThrownBy(() -> jdbcProgressTracker.proceed(groupId, 0, () -> {
                    throw exception;
                }))
                .isSameAs(exception);

        assertThat(jdbcProgressTracker.current(groupId, 0)).isEqualTo(new Progress.Success("4711"));
    }

    @Test
    public void proceedCalledNonTransactionallyByDefault() {
        assertThatCode(() -> jdbcProgressTracker.proceed(UUID.randomUUID().toString(), 0, () -> new TransactionTemplate(
                                transactionManager,
                                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NEVER))
                        .execute(status -> new Progress.Success("42"))))
                .doesNotThrowAnyException();
    }

    @Test
    public void proceedCalledTransactionallyIfConfigured() {
        assertThatCode(() -> transactionalJdbcProgressTracker.proceed(
                        UUID.randomUUID().toString(), 0, () -> new TransactionTemplate(
                                        transactionManager,
                                        new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_MANDATORY))
                                .execute(status -> new Progress.Success("42"))))
                .doesNotThrowAnyException();
    }
}
