/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.cache;

import com.opencqrs.framework.command.CommandRouterAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@link ConfigurationProperties} for {@linkplain CommandRouterAutoConfiguration auto-configured}
 * {@link StateRebuildingCache}s.
 *
 * @param type The cache type to use, unless "ref" is specified.
 * @param capacity The cache capacity, if "in_memory" is used.
 * @param ref Custom cache to use.
 */
@ConfigurationProperties("cqrs.command-handling.cache")
public record CommandHandlingCacheProperties(
        @DefaultValue("none") Type type, @DefaultValue("1000") Integer capacity, String ref) {
    /** The pre-defined cache type. */
    public enum Type {
        /**
         * No caching is used.
         *
         * @see NoStateRebuildingCache
         */
        NONE,

        /**
         * In-memory caching is used.
         *
         * @see LruInMemoryStateRebuildingCache
         */
        IN_MEMORY,
    }
}
