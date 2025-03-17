/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.transaction;

import com.opencqrs.framework.eventhandler.EventHandling;

/**
 * Implementation of {@link TransactionOperationsAdapter} used if Spring TX is not available on the class-path or
 * {@link EventHandling} methods have not been annotated using
 * {@link org.springframework.transaction.annotation.Transactional}.
 */
public class NoTransactionOperationsAdapter implements TransactionOperationsAdapter {

    @Override
    public void execute(Runnable runnable) {
        runnable.run();
    }
}
