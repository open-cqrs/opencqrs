/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.client;

import com.opencqrs.esdb.client.Client;
import com.opencqrs.esdb.client.ClientException;
import com.opencqrs.framework.CqrsFrameworkException;
import java.util.function.Supplier;

/**
 * Helper class to map errors from {@link Client} operations to {@link CqrsFrameworkException}.
 *
 * @see #handleMappingExceptionsIfNecessary(Supplier)
 */
public final class ClientRequestErrorMapper {

    /**
     * Handles and maps suitable {@link ClientException}s thrown from the given handler to suitable subclasses of
     * {@link CqrsFrameworkException}.
     *
     * @param handler encapsulates calls to {@link Client} operations
     * @return the response from the handler if successful
     * @param <T> generic response type for the handler
     * @throws ConcurrencyException in case of {@link ClientException.HttpException.HttpClientException} with status
     *     code {@code 409}
     * @throws ClientInterruptedException in case of {@link ClientException.InterruptedException}
     * @throws CqrsFrameworkException.TransientException in case of
     *     {@link ClientException.HttpException.HttpClientException} with status code {@code 408}
     * @throws CqrsFrameworkException.NonTransientException in case of any other
     *     {@link ClientException.HttpException.HttpClientException}
     * @throws CqrsFrameworkException.TransientException in case of
     *     {@link ClientException.HttpException.HttpServerException}
     * @throws CqrsFrameworkException.NonTransientException in case of any other {@link ClientException.HttpException}
     * @throws CqrsFrameworkException.TransientException in case of {@link ClientException.TransportException}
     * @throws CqrsFrameworkException.NonTransientException in case of {@link ClientException.MarshallingException}
     * @throws CqrsFrameworkException.NonTransientException in case of {@link ClientException.InvalidUsageException}
     * @throws ClientException if none of the aforementioned (for internal use only)
     */
    public <T> T handleMappingExceptionsIfNecessary(Supplier<T> handler) throws CqrsFrameworkException {
        try {
            return handler.get();
        } catch (ClientException.InvalidUsageException e) {
            throw new CqrsFrameworkException.NonTransientException("invalid usage of client api", e);
        } catch (ClientException.MarshallingException e) {
            throw new CqrsFrameworkException.NonTransientException("marshalling error", e);
        } catch (ClientException.TransportException e) {
            throw new CqrsFrameworkException.TransientException("communication error", e);
        } catch (ClientException.InterruptedException e) {
            throw new ClientInterruptedException("client interrupted", e);
        } catch (ClientException.HttpException.HttpServerException e) {
            throw new CqrsFrameworkException.TransientException("http server error", e);
        } catch (ClientException.HttpException.HttpClientException e) {
            switch (e.getStatusCode()) {
                case 408:
                    throw new CqrsFrameworkException.TransientException("http request timeout", e);
                case 409:
                    throw new ConcurrencyException("concurrency error", e);
                default:
                    throw new CqrsFrameworkException.NonTransientException("http client error", e);
            }
        } catch (ClientException.HttpException e) {
            throw new CqrsFrameworkException.NonTransientException("unhandled http status", e);
        }
    }
}
