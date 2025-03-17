/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.transaction;

/**
 * Internal interface encapsulating {@link org.springframework.transaction.annotation.Transactional} method execution.
 */
public interface TransactionOperationsAdapter {

    /**
     * Executes the given runnable with transactional semantics.
     *
     * @param runnable the runnable to execute
     */
    void execute(Runnable runnable);
}
