/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.client;

import com.opencqrs.esdb.client.ClientException;
import com.opencqrs.framework.CqrsFrameworkException;

/**
 * Exception class representing thread interruption signalled by {@link ClientException.InterruptedException}.
 *
 * @see ClientRequestErrorMapper
 */
public class ClientInterruptedException extends CqrsFrameworkException.TransientException {

    public ClientInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
