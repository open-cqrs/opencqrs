/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.progress;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.eventhandler.EventHandler;
import com.opencqrs.framework.eventhandler.EventHandling;
import com.opencqrs.framework.eventhandler.EventHandlingProcessor;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link ProgressTracker} implementation using Spring {@link JdbcOperations} and {@link TransactionOperations} to
 * persist {@link Progress}.
 */
public class JdbcProgressTracker implements ProgressTracker, InitializingBean, SmartLifecycle {

    public static final String DEFAULT_TABLE_PREFIX = "EVENTHANDLER_";

    private final DataSource dataSource;
    private final PlatformTransactionManager platformTransactionManager;

    private JdbcOperations jdbcOperations;
    private TransactionOperations defaultTransactionOperations;
    private TransactionOperations proceedTransactionOperations;

    /**
     * Initializes {@code this} progress tracker given a {@link DataSource} and a compliant
     * {@link PlatformTransactionManager}.
     *
     * @param dataSource the data source to use for persisting {@link Progress}
     * @param platformTransactionManager the transaction manager used to persist and for {@linkplain #proceed(String,
     *     long, Supplier) proceed execution}
     */
    public JdbcProgressTracker(DataSource dataSource, PlatformTransactionManager platformTransactionManager) {
        this.dataSource = dataSource;
        this.platformTransactionManager = platformTransactionManager;
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean checkDatabaseOnStart = true;
    private boolean proceedTransactionally = false;
    private String tablePrefix = DEFAULT_TABLE_PREFIX;

    private String findQuery =
            """
            SELECT EVENT_ID
            FROM %sPROGRESS
            WHERE GROUP_KEY = ? AND PARTITION_ID = ?
            """;

    private String updateQuery =
            """
            UPDATE %sPROGRESS
            SET EVENT_ID = ?
            WHERE GROUP_KEY = ? AND PARTITION_ID = ?
            """;

    private String insertQuery =
            """
            INSERT INTO %sPROGRESS(GROUP_KEY, PARTITION_ID, EVENT_ID)
            VALUES(?, ?, ?)
            """;

    private String countAllQuery = """
            SELECT COUNT(EVENT_ID)
            FROM %sPROGRESS
            """;

    /**
     * Configures, if the SQL database should be checked on startup, in order to be sure the tables have been created.
     *
     * @param checkDatabaseOnStart {@code false} if check shall be disabled (defaults to {@code true})
     */
    public void setCheckDatabaseOnStart(boolean checkDatabaseOnStart) {
        this.checkDatabaseOnStart = checkDatabaseOnStart;
    }

    /**
     * Configures whether the {@linkplain Supplier execution} passed to {@link #proceed(String, long, Supplier)} will be
     * executed transactionally. If set to {@code true} all {@link EventHandler}s or {@link EventHandling} annotated
     * methods executed within the {@linkplain EventHandlingProcessor#run() event processing loop} (for the same raw
     * {@link Event}) will be executed transactionally within the same transaction, that is used to retrieve and update
     * the event processing progress. <strong>This guarantees atomicity and idempotency, if and only if all
     * participating event handlers operate only on resources managed by the configured
     * {@link PlatformTransactionManager}, i.e. the {@link DataSource} resource.</strong>
     *
     * @param proceedTransactionally {@code true} if event handlers shall participate in the same transaction, defaults
     *     to {@code false} otherwise
     */
    public void setProceedTransactionally(boolean proceedTransactionally) {
        this.proceedTransactionally = proceedTransactionally;
    }

    /**
     * Configures the SQL table prefix.
     *
     * @param tablePrefix the table prefix, defaults to {@value #DEFAULT_TABLE_PREFIX}
     */
    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    /**
     * Configures the SQL {@code SELECT} statement to query the {@linkplain #current(String, long) current}
     * {@link Progress}. The statement may contain a single {@literal %s} placeholder for the
     * {@linkplain #setTablePrefix(String) table prefix}.
     *
     * @param statement the statement
     */
    public void setFindQuery(String statement) {
        this.findQuery = statement;
    }

    /**
     * Configures the SQL {@code UPDATE} statement to update an existing {@link Progress} during {@link #proceed(String,
     * long, Supplier)}. The statement may contain a single {@literal %s} placeholder for the
     * {@linkplain #setTablePrefix(String) table prefix}.
     *
     * @param statement the statement
     */
    public void setUpdateQuery(String statement) {
        this.updateQuery = statement;
    }

    /**
     * Configures the SQL {@code INSERT} statement to insert a new {@link Progress} during {@link #proceed(String, long,
     * Supplier)}. The statement may contain a single {@literal %s} placeholder for the
     * {@linkplain #setTablePrefix(String) table prefix}.
     *
     * @param statement the statement
     */
    public void setInsertQuery(String statement) {
        this.insertQuery = statement;
    }

    /**
     * Configures the SQL {code SELECT} statement used to {@linkplain #setCheckDatabaseOnStart(boolean) check the
     * database on start-up}. The statement may contain a single {@literal %s} placeholder for the
     * {@linkplain #setTablePrefix(String) table prefix}.
     *
     * @param countAllQuery the statement
     */
    public void setCountAllQuery(String countAllQuery) {
        this.countAllQuery = countAllQuery;
    }

    @Override
    public void afterPropertiesSet() {
        this.jdbcOperations = new JdbcTemplate(dataSource);
        this.defaultTransactionOperations = new TransactionTemplate(
                platformTransactionManager,
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED));
        this.proceedTransactionOperations = new TransactionTemplate(
                platformTransactionManager,
                new DefaultTransactionDefinition(
                        proceedTransactionally
                                ? TransactionDefinition.PROPAGATION_REQUIRED
                                : TransactionDefinition.PROPAGATION_NEVER));

        this.findQuery = String.format(findQuery, tablePrefix);
        this.updateQuery = String.format(updateQuery, tablePrefix);
        this.insertQuery = String.format(insertQuery, tablePrefix);
        this.countAllQuery = String.format(countAllQuery, tablePrefix);
    }

    @Override
    public boolean isAutoStartup() {
        return checkDatabaseOnStart;
    }

    @Override
    public void start() {
        if (this.running.compareAndSet(false, true)) {
            this.jdbcOperations.queryForObject(this.countAllQuery, Long.class);
        }
    }

    @Override
    public void stop() {
        this.running.set(false);
    }

    @Override
    public boolean isRunning() {
        return this.running.get();
    }

    @Override
    public Progress current(String group, long partition) {
        return defaultTransactionOperations.execute(status -> {
            Stream<String> query =
                    jdbcOperations.queryForStream(findQuery, (rs, rowNum) -> rs.getString(1), group, partition);
            return Optional.ofNullable(DataAccessUtils.singleResult(query))
                    .map(it -> (Progress) new Progress.Success(it))
                    .orElseGet(Progress.None::new);
        });
    }

    @Override
    public void proceed(String group, long partition, Supplier<Progress> execution) {
        proceedTransactionOperations.executeWithoutResult(status -> {
            switch (execution.get()) {
                case Progress.None ignored -> {}
                case Progress.Success success ->
                    defaultTransactionOperations.executeWithoutResult(transactionStatus -> {
                        int updated = jdbcOperations.update(updateQuery, success.id(), group, partition);
                        if (updated == 0) {
                            jdbcOperations.update(insertQuery, group, partition, success.id());
                        }
                    });
            }
        });
    }
}
