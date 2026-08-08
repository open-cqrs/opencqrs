/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

import com.opencqrs.framework.CqrsFrameworkException;

/**
 * Thrown when an interceptor's advice raises a <em>checked</em> exception that cannot propagate as-is &mdash; e.g. from
 * a state-apply hook whose surrounding boundary (the command router's cache merge function, or
 * {@link com.opencqrs.framework.command.interceptor.CommandLifecycle#publishedEvent publishedEvent} application) does
 * not declare {@code throws}. The original exception is preserved as the {@linkplain #getCause() cause}.
 *
 * <p><strong>Unchecked</strong> advice exceptions (a security denial, an optimistic-lock reject, a validation failure)
 * are <em>not</em> wrapped &mdash; they propagate unchanged so callers can catch their own types.
 */
public class InterceptorExecutionException extends CqrsFrameworkException.NonTransientException {

    public InterceptorExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
