/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.client;

import com.opencqrs.framework.CqrsFrameworkException;

/**
 * Exception class representing concurrency errors, usually caused by violated preconditions, when publishing events.
 *
 * @see ClientRequestErrorMapper
 */
public class ConcurrencyException extends CqrsFrameworkException.TransientException {

    public ConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
