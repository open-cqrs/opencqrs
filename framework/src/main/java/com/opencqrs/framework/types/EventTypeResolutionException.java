/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.types;

import com.opencqrs.framework.CqrsFrameworkException;

/**
 * {@link CqrsFrameworkException.NonTransientException} exception capturing an {@link EventTypeResolver} resolution
 * error.
 */
public class EventTypeResolutionException extends CqrsFrameworkException.NonTransientException {

    public EventTypeResolutionException(String message) {
        super(message);
    }

    public EventTypeResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
