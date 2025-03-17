/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.partitioning;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Default implementation of {@link PartitionKeyResolver} which uses {@link CRC32} checksums and modulo operation to
 * derive partition numbers from event sequence identifiers.
 *
 * <p><strong>This implementation is guaranteed to always yield the same partition number for the same event sequence
 * identifier, as long as the number of {@link #activePartitions} is constant.</strong>
 */
public final class DefaultPartitionKeyResolver implements PartitionKeyResolver {

    private final long activePartitions;

    public DefaultPartitionKeyResolver(long activePartitions) {
        if (activePartitions <= 0) {
            throw new IllegalArgumentException("partition num must be greater than zero");
        }
        this.activePartitions = activePartitions;
    }

    @Override
    public long resolve(String sequenceId) {
        CRC32 checksum = new CRC32();
        checksum.update(sequenceId.getBytes(StandardCharsets.UTF_8));
        return checksum.getValue() % activePartitions;
    }
}
