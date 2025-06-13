/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.framework.command.cache.CommandHandlingCacheProperties;
import com.opencqrs.framework.command.cache.LruInMemoryStateRebuildingCache;
import com.opencqrs.framework.command.cache.NoStateRebuildingCache;
import com.opencqrs.framework.command.cache.StateRebuildingCache;
import com.opencqrs.framework.metadata.MetaDataPropagationProperties;
import com.opencqrs.framework.persistence.EventReader;
import com.opencqrs.framework.persistence.ImmediateEventPublisher;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for
 * {@link CommandRouter} and {@link StateRebuildingCache} default implementations.
 */
@AutoConfiguration
@EnableConfigurationProperties({
    MetaDataPropagationProperties.class,
    CommandHandlingCacheProperties.class,
})
public class CommandRouterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CommandRouter openCqrsCommandRouter(
            EventReader eventReader,
            ImmediateEventPublisher immediateEventPublisher,
            @SuppressWarnings("rawtypes") List<CommandHandlerDefinition> commandHandlerDefinitions,
            @SuppressWarnings("rawtypes") List<StateRebuildingHandlerDefinition> stateRebuildingHandlerDefinitions,
            CommandHandlingCacheProperties cacheProperties,
            MetaDataPropagationProperties metaDataPropagationProperties,
            ApplicationContext applicationContext) {
        String cacheBeanRef =
                switch (cacheProperties.ref()) {
                    case null ->
                        switch (cacheProperties.type()) {
                            case NONE -> "openCqrsNoStateRebuildingCache";
                            case IN_MEMORY -> "openCqrsLruInMemoryStateRebuildingCache";
                        };
                    default -> cacheProperties.ref();
                };

        return new CommandRouter(
                eventReader,
                immediateEventPublisher,
                commandHandlerDefinitions,
                stateRebuildingHandlerDefinitions,
                applicationContext.getBean(cacheBeanRef, StateRebuildingCache.class),
                metaDataPropagationProperties.mode(),
                metaDataPropagationProperties.keys());
    }

    @Bean
    public NoStateRebuildingCache openCqrsNoStateRebuildingCache() {
        return new NoStateRebuildingCache();
    }

    @Bean
    public LruInMemoryStateRebuildingCache openCqrsLruInMemoryStateRebuildingCache(
            CommandHandlingCacheProperties properties) {
        return new LruInMemoryStateRebuildingCache(properties.capacity());
    }
}
