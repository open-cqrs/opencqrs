/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.client;

import com.opencqrs.framework.CqrsFrameworkException;

/**
 * {@link ConcurrencyException} signalling that the event store <strong>rejected a write</strong> as conflicting (mapped
 * from an opaque HTTP {@code 409}). The store does not report a cause, so this names only the observable fact &mdash; a
 * concurrent modification or a violated append precondition &mdash; without inferring which.
 *
 * @see ClientRequestErrorMapper
 */
public class ConflictingWriteException extends CqrsFrameworkException.TransientException.ConcurrencyException {

    public ConflictingWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
