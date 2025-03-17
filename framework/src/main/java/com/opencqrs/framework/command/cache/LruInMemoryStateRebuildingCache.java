/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.cache;

import com.opencqrs.esdb.client.IdUtil;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * {@link StateRebuildingCache} implementation backed by a {@code synchronized} {@link LinkedHashMap} with configurable
 * maximum capacity and LRU semantics.
 */
public class LruInMemoryStateRebuildingCache implements StateRebuildingCache {

    private static final Logger log = Logger.getLogger(LruInMemoryStateRebuildingCache.class.getName());
    final Map<CacheKey, CacheValue> cache;

    /**
     * Configures this with a maximum capacity.
     *
     * @param capacity the maximum number of {@link StateRebuildingCache.CacheValue}s to keep, before
     *     {@linkplain LinkedHashMap#removeEldestEntry(Map.Entry) discarding exceed entries}
     */
    public LruInMemoryStateRebuildingCache(int capacity) {
        cache = Collections.synchronizedMap(new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, CacheValue> eldest) {
                if (size() > capacity) {
                    log.fine(() -> "discarding eldest cache element: " + eldest.getKey());
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public <I> CacheValue<I> fetchAndMerge(CacheKey<I> key, Function<CacheValue<I>, CacheValue<I>> mergeFunction) {
        CacheValue updatedValue = mergeFunction.apply(cache.getOrDefault(key, new CacheValue<>(null, null, Map.of())));

        return switch (updatedValue.eventId()) {
            case null -> updatedValue;
            default -> cache.compute(key, (cacheKey, cacheValue) -> switch (cacheValue) {
                case null -> updatedValue;
                default -> isHigherEventId(cacheValue.eventId(), updatedValue.eventId()) ? cacheValue : updatedValue;
            });
        };
    }

    private boolean isHigherEventId(String a, String b) {
        return IdUtil.fromEventId(a) > IdUtil.fromEventId(b);
    }
}
