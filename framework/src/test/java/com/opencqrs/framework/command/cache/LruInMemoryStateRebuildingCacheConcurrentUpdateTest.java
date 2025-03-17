/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencqrs.framework.Book;
import com.opencqrs.framework.command.SourcingMode;
import com.opencqrs.framework.command.cache.StateRebuildingCache.CacheKey;
import com.opencqrs.framework.command.cache.StateRebuildingCache.CacheValue;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class LruInMemoryStateRebuildingCacheConcurrentUpdateTest {

    private final LruInMemoryStateRebuildingCache subject = new LruInMemoryStateRebuildingCache(5);

    @Test
    public void newestCacheValueBasedOnEventIdWinsIfConcurrentUpdate() {
        var cacheKey = new CacheKey<>("/books/4711", Book.class, SourcingMode.RECURSIVE);

        var expectedNewestCacheValue =
                new CacheValue<>("4", new Book("4711", true), Map.of("/books/4711", "2", "/books/4711/pages/42", "3"));

        subject.fetchAndMerge(cacheKey, ignored1 -> {
            subject.fetchAndMerge(cacheKey, ignored2 -> expectedNewestCacheValue);

            return new CacheValue<>("3", new Book("4711", true), Map.of("/books/4711", "2"));
        });

        assertThat(subject.cache.get(cacheKey)).isEqualTo(expectedNewestCacheValue);
    }
}
