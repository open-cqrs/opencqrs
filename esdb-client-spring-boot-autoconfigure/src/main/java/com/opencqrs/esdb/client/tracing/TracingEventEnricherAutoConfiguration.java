/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = OpenTelemetryAutoConfiguration.class)
public class TracingEventEnricherAutoConfiguration {

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnMissingBean(TracingEventEnricher.class)
    public TracingEventEnricher otelTracingEventEnricher(OpenTelemetry openTelemetry) {
        return new OpenTelemetryTracingEventEnricher(openTelemetry);
    }

    @Bean
    @ConditionalOnMissingBean(TracingEventEnricher.class)
    public TracingEventEnricher noTracingEventEnricher() {
        return new NoTracingEventEnricher();
    }
}
