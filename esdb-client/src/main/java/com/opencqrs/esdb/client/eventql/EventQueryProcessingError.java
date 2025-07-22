/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.eventql;

import jakarta.validation.constraints.NotNull;

/**
 * Encapsulates a {@linkplain EventQueryErrorHandler#queryProcessingError(EventQueryProcessingError) query processing
 * error} caused by {@link com.opencqrs.esdb.client.EsdbClient#query(EventQuery, EventQueryRowHandler,
 * EventQueryErrorHandler)}.
 *
 * @param error error descriptions
 * @param startToken optional start token regarding the query input string
 * @param endToken optional end token regarding the query input string
 */
public record EventQueryProcessingError(@NotNull String error, Token startToken, Token endToken) {

    /**
     * Encapsulates a token describing the error position within a malformed or unprocessable query.
     *
     * @param line the line number
     * @param column the column number
     * @param text the encountered query text at that position
     * @param type the type of error
     */
    public record Token(Integer line, Integer column, String text, String type) {}
}
