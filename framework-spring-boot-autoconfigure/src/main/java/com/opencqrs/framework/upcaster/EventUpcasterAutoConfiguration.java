/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.upcaster;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for
 * {@link EventUpcasters}.
 */
@AutoConfiguration
public class EventUpcasterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventUpcasters eventUpcasterChain(List<EventUpcaster> eventUpcasters) {
        return new EventUpcasters(eventUpcasters);
    }
}
