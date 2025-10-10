/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = OpenTelemetryAutoConfiguration.class)
@ConditionalOnClass(OpenTelemetry.class)
public class OpenTelemetryEventEnricherAutoConfiguration {

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnMissingBean(TracingEventEnricher.class)
    public OpenTelemetryTracingEventEnricher otelTracingEventEnricher(OpenTelemetry openTelemetry) {
        return new OpenTelemetryTracingEventEnricher(openTelemetry);
    }
}
