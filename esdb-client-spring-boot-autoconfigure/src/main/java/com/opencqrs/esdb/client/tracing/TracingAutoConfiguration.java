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
    @ConditionalOnMissingBean(TracingContextualizer.class)
    public TracingContextualizer tracingContextualizer(OpenTelemetry openTelemetry) { return new OpenTelemetryTracingContextualizer(openTelemetry); }

    @Bean
    @ConditionalOnMissingBean({OpenTelemetry.class, TracingContextualizer.class})
    public TracingContextualizer tracingContextualizer() { return new NoOpTracingContextualizer(); }

}
