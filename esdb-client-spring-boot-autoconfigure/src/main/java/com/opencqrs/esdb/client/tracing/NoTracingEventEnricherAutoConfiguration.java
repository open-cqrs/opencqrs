/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = OpenTelemetryEventEnricherAutoConfiguration.class)
public class NoTracingEventEnricherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TracingEventEnricher.class)
    public NoTracingEventEnricher noTracingEventEnricher() {
        return new NoTracingEventEnricher();
    }
}
