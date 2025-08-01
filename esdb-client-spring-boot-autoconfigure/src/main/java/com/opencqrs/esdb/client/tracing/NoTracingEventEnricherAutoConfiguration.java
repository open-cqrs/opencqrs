/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
public class NoTracingEventEnricherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TracingEventEnricher.class)
    public NoTracingEventEnricher noTracingEventEnricher() {
        return new NoTracingEventEnricher();
    }
}
