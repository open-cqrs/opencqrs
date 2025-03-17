/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.transaction;

import com.opencqrs.framework.eventhandler.EventHandling;
import java.lang.reflect.Method;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link TransactionOperationsAdapter} implementation that uses a {@link TransactionOperations} delegate for
 * {@linkplain #execute(Runnable) transactional execution} of an {@link EventHandling} annotated method according to the
 * supplied {@link org.springframework.transaction.annotation.Transactional} configuration.
 */
public class SpringTransactionOperationsAdapter implements TransactionOperationsAdapter {

    private final TransactionOperations delegate;

    public SpringTransactionOperationsAdapter(
            PlatformTransactionManager platformTransactionManager,
            TransactionAttributeSource transactionAttributeSource,
            Method method,
            Class<?> clazz) {
        this.delegate = new TransactionTemplate(
                platformTransactionManager, transactionAttributeSource.getTransactionAttribute(method, clazz));
    }

    @Override
    public void execute(Runnable runnable) {
        delegate.executeWithoutResult(transactionStatus -> runnable.run());
    }
}
