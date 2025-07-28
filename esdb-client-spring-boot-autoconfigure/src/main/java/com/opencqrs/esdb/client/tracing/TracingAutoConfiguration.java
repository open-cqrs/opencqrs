/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class TracingAutoConfiguration {

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnMissingBean(TracingEventEnricher.class)
    public TracingEventEnricher otelTracingContextualizer(OpenTelemetry openTelemetry) {
        return new OpenTelemetryTracingEventEnricher(openTelemetry);
    }

    @Bean
    @ConditionalOnMissingBean(TracingEventEnricher.class)
    public TracingEventEnricher noOpTracingContextualizer() {
        return new NoTracingEventEnricher();
    }
}
