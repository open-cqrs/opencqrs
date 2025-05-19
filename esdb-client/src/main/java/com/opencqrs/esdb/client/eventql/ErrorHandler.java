/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.eventql;

import com.opencqrs.esdb.client.ClientException;

/**
 * Callback interface that needs to be implement for receiving errors from calls to
 * {@link com.opencqrs.esdb.client.EsdbClient#query(String, RowHandler, ErrorHandler)}.
 */
public interface ErrorHandler {

    /**
     * Signals a query processing error received from the underlying event store, while processing a row for the result
     * set. The error may have been caused both by an invalid query or by a projection error to the result row, e.g.
     * type conversion errors.
     *
     * @param error the error stating that a result row could not be processed successfully
     */
    void queryProcessingError(QueryProcessingError error);

    /**
     * Signals a marshalling error for result rows successfully received from the underlying event store that could not
     * be properly deserialized or transformed to the desired target type by means of the requested {@link RowHandler}.
     *
     * @param exception the marshalling error that occurred
     * @param row the row returned from the event store which could not be unmarshalled
     */
    void marshallingError(ClientException.MarshallingException exception, String row);
}
