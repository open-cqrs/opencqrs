/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.context.annotation.Bean;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link OpenTelemetryTracingEventEnricher}. */
@AutoConfiguration(after = OpenTelemetrySdkAutoConfiguration.class)
@ConditionalOnClass(OpenTelemetry.class)
public class OpenTelemetryEventEnricherAutoConfiguration {

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnMissingBean(TracingEventEnricher.class)
    public OpenTelemetryTracingEventEnricher openTelemetryTracingEventEnricher(OpenTelemetry openTelemetry) {
        return new OpenTelemetryTracingEventEnricher(openTelemetry);
    }
}
