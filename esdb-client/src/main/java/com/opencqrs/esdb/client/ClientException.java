/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

/** Base class for an exception thrown from {@link EsdbClient}. */
public abstract class ClientException extends RuntimeException {
    public ClientException(String message) {
        super(message);
    }

    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClientException(Throwable cause) {
        super(cause);
    }

    /**
     * Exception class representing errors due to invalid {@link EsdbClient client API} usage. This is typically caused
     * by an invalid combination of {@link Option}s or {@link Precondition}s.
     */
    public static class InvalidUsageException extends ClientException {

        public InvalidUsageException(String message) {
            super(message);
        }
    }

    /** Exception class representing network and/or connection errors. */
    public static class TransportException extends ClientException {

        public TransportException(String message) {
            super(message);
        }

        public TransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Exception class representing thread interruption. */
    public static class InterruptedException extends ClientException {

        public InterruptedException(String message) {
            super(message);
        }

        public InterruptedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Exception class thrown by {@link Marshaller} implementations due to serialization or deserialization errors. */
    public static class MarshallingException extends ClientException {

        public MarshallingException(String message) {
            super(message);
        }

        public MarshallingException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Exception class thrown when event validation fails, such as when {@link Event#verifyHash()} detects a hash
     * mismatch.
     */
    public static class ValidationException extends ClientException {

        public ValidationException(String message) {
            super(message);
        }

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Base class for exceptions related to HTTP status codes returned from an event store. */
    public static class HttpException extends ClientException {

        private final int statusCode;

        public HttpException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }

        /** Exception class thrown for HTTP status codes {@code 4xx}. */
        public static class HttpClientException extends HttpException {
            public HttpClientException(String message, int statusCode) {
                super(message, statusCode);
            }
        }

        /** Exception class thrown for HTTP status codes {@code 5xx}. */
        public static class HttpServerException extends HttpException {

            public HttpServerException(String message, int statusCode) {
                super(message, statusCode);
            }
        }
    }
}
