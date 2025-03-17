/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.cache;

import java.util.Map;
import java.util.function.Function;

/** {@link StateRebuildingCache} implementation that does not cache anything. */
public final class NoStateRebuildingCache implements StateRebuildingCache {

    @Override
    public <I> CacheValue<I> fetchAndMerge(CacheKey<I> key, Function<CacheValue<I>, CacheValue<I>> mergeFunction) {
        var noCachedValue = new CacheValue<I>(null, null, Map.of());
        return mergeFunction.apply(noCachedValue);
    }
}
