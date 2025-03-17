/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework;

import com.opencqrs.framework.client.ClientRequestErrorMapper;
import java.util.function.Supplier;

/**
 * Base class for exceptions handled within the framework. This class is {@code sealed} to distinguish between
 * {@linkplain TransientException transient} (potentially recoverable) and {@linkplain NonTransientException
 * non-transient} (non-recoverable) errors.
 *
 * @see ClientRequestErrorMapper#handleMappingExceptionsIfNecessary(Supplier)
 */
public sealed class CqrsFrameworkException extends RuntimeException {
    public CqrsFrameworkException(String message) {
        super(message);
    }

    public CqrsFrameworkException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Exception class representing potentially recoverable errors, such as communication or concurrency errors. */
    public static non-sealed class TransientException extends CqrsFrameworkException {
        public TransientException(String message) {
            super(message);
        }

        public TransientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Exception class representing non-recoveralbe errors. */
    public static non-sealed class NonTransientException extends CqrsFrameworkException {
        public NonTransientException(String message) {
            super(message);
        }

        public NonTransientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
