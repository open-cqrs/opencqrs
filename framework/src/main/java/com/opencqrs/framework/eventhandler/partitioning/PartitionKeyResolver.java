/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.partitioning;

/**
 * Interface for implementations able to consistently derive a numeric partition key from an event sequence identifier.
 *
 * @see EventSequenceResolver
 */
@FunctionalInterface
public interface PartitionKeyResolver {

    /**
     * Deterministically resolves a partition number for the given sequence identifier.
     *
     * @param sequenceId the event sequence identifier, as derived from {@link EventSequenceResolver} implementations
     * @return the partition number
     */
    long resolve(String sequenceId);
}
