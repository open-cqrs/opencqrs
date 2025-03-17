/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.cache;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.Precondition;
import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandHandler;
import com.opencqrs.framework.command.CommandHandlerDefinition;
import com.opencqrs.framework.command.SourcingMode;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import com.opencqrs.framework.persistence.EventReader;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Interface specifying operations to cache {@linkplain StateRebuildingHandlerDefinition#instanceClass() event-sourced
 * instance state} to reduce the number of {@linkplain EventReader#consumeRaw(EventReader.ClientRequestor, BiConsumer)
 * fetched events} prior to executing a {@link CommandHandler}.
 *
 * @see #fetchAndMerge(CacheKey, Function)
 */
@FunctionalInterface
public interface StateRebuildingCache {

    /**
     * Method supposed to fetch a {@link CacheValue} from the underlying cache based on the given {@link CacheKey}. The
     * cached value is applied to the given {@code mergeFunction}, the underlying cache updated with the merged
     * {@link CacheValue}, before it is returned to the caller. Implementations may choose to return an even
     * {@linkplain CacheValue#eventId() newer} {@link CacheValue}, if concurrent merges occur.
     *
     * @param key the cache key
     * @param mergeFunction a merge function updating a {@link CacheValue} previously cached, typically by fetching
     *     newer events, applying them to the instance and returning the merged value, which will then be applied to the
     *     underlying cache (if no newer value has already been applied concurrently, depending on the concurrency
     *     guarantees of the implementation). <strong>In case of a cache miss, a {@link CacheValue} with {@code null}
     *     contents will be passed to the merge function, as annotated.</strong>
     * @return the fetched and merged cache value
     * @param <I> the generic instance type being cached
     */
    <I> CacheValue<I> fetchAndMerge(CacheKey<I> key, Function<CacheValue<I>, CacheValue<I>> mergeFunction);

    /**
     * Represents the cache key, whose {@link Object#equals(Object) equality} determines, which {@link CacheValue} to
     * {@linkplain #fetchAndMerge(CacheKey, Function) fetch}.
     *
     * @param subject the corresponding {@link Command#getSubject()}
     * @param instanceClass the corresponding {@link CommandHandlerDefinition#instanceClass()} and those of its
     *     {@link StateRebuildingHandlerDefinition}s.
     * @param sourcingMode the corresponding {@link CommandHandlerDefinition#sourcingMode()}
     * @param <I> the generic instance type being cached
     */
    record CacheKey<I>(@Nonnull String subject, @Nonnull Class<I> instanceClass, @Nonnull SourcingMode sourcingMode) {}

    /**
     * Represents the cache value.
     *
     * @param eventId the newest {@link Event#id()} this value represents
     * @param instance the cached object instance
     * @param sourcedSubjectIds the sourced subjects and their corresponding {@link Event#id()} to reconstruct
     *     {@link Precondition.SubjectIsOnEventId}s before applying new events. <strong>The {@code mergeFunction} is
     *     supposed to merge this map with any previous ids.</strong>
     * @param <I> the generic instance type being cached
     */
    record CacheValue<I>(
            @Nullable String eventId, @Nullable I instance, @Nonnull Map<String, String> sourcedSubjectIds) {}
}
