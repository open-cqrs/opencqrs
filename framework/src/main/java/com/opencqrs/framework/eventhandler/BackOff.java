/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

/** Interface to be implemented by classes providing back off semantics, e.g. fixed or exponential back off. */
@FunctionalInterface
public interface BackOff {

    /**
     * Starts a back off {@link Execution}.
     *
     * @return a newly started back off
     */
    Execution start();

    /** Encapsulates back off execution state. */
    @FunctionalInterface
    interface Execution {

        /**
         * Yields the number of milliseconds to back off.
         *
         * @return number of milliseconds to back off, or {@code -1L} if back off is exhausted
         */
        long next();
    }
}
