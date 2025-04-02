/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Sealed interface for options used by {@link Client} implementations to read or observe events from the underlying
 * event store.
 */
public sealed interface Option {

    /**
     * Specifies that events shall be fetched recursively, that is including the requested subject (events) and its
     * hierarchical subject (events).
     *
     * @see Client#read(String, Set)
     * @see Client#read(String, Set, Consumer)
     * @see Client#observe(String, Set, Consumer)
     */
    record Recursive() implements Option {}

    /**
     * Encapsulates the type of ordering of the fetched events.
     *
     * @param type the requested event order type
     * @see Client#read(String, Set)
     * @see Client#read(String, Set, Consumer)
     */
    record Order(@NotNull Type type) implements Option {

        /** Specifies the event order. */
        public enum Type {
            /** Specifies chronological event order. */
            CHRONOLOGICAL,
            /** Specifies antichronological event order. */
            ANTICHRONOLOGICAL;
        }
    }

    /**
     * Specifies the lowest inclusive event id to fetch from.
     *
     * @param id the lower bound event id (inclusive)
     * @see Client#read(String, Set)
     * @see Client#read(String, Set, Consumer)
     * @see Client#observe(String, Set, Consumer)
     */
    record LowerBoundInclusive(@NotBlank String id) implements Option {}

    /**
     * Specifies the lowest exclusive event id to fetch from.
     *
     * @param id the lower bound event id (inclusive)
     * @see Client#read(String, Set)
     * @see Client#read(String, Set, Consumer)
     * @see Client#observe(String, Set, Consumer)
     */
    record LowerBoundExclusive(@NotBlank String id) implements Option {}

    /**
     * Specifies the highest inclusive event id to fetch to.
     *
     * @param id the upper bound event id (inclusive)
     * @see Client#read(String, Set)
     * @see Client#read(String, Set, Consumer)
     */
    record UpperBoundInclusive(@NotBlank String id) implements Option {}

    /**
     * Specifies the highest exclusive event id to fetch to.
     *
     * @param id the upper bound event id (inclusive)
     * @see Client#read(String, Set)
     * @see Client#read(String, Set, Consumer)
     */
    record UpperBoundExclusive(@NotBlank String id) implements Option {}

    /**
     * Specifies that the list of events is optimized by <i>omitting</i> any event prior to the latest event available
     * for the subject specified as part of {@code this}.
     *
     * <p>This is typically used to read so-called snapshot events from the given subject location in favor of consuming
     * a much longer event stream for the actual subject being fetched.
     *
     * @param subject the subject to read the latest event from
     * @param type the {@link Event#type()} that the latest event must match
     * @param ifEventIsMissing specifies the fall-back fetch strategy, in case there is no such event
     */
    record FromLatestEvent(@NotBlank String subject, @NotBlank String type, @NotNull IfEventIsMissing ifEventIsMissing)
            implements Option {

        /** Specifies the fall-back fetch strategy. */
        public enum IfEventIsMissing {
            /** Specifies that all events shall be fetched for the actual subject. */
            READ_EVERYTHING,

            /** Specifies that no events shall be fetched for the actual subject. */
            READ_NOTHING;
        }
    }
}
