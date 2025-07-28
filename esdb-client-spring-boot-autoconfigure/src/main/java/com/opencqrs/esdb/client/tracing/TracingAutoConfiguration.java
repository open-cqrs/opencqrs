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
    @ConditionalOnMissingBean(TracingDataEnricher.class)
    public TracingDataEnricher otelTracingContextualizer(OpenTelemetry openTelemetry) { return new OpenTelemetryTracingDataEnricher(openTelemetry); }

    @Bean
    @ConditionalOnMissingBean(TracingDataEnricher.class)
    public TracingDataEnricher noOpTracingContextualizer() { return new NoOpTracingDataEnricher(); }

}
