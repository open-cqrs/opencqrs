/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class OpenTelemetryEventEnricherAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    public void noOtelInClasspathFound() {
        runner.withConfiguration(AutoConfigurations.of(OpenTelemetryEventEnricherAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(OpenTelemetry.class, OpenTelemetryAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(OpenTelemetryTracingEventEnricher.class);
                });
    }

    @Test
    void noOtelBeanInApplicationContextFound() {
        runner.withConfiguration(AutoConfigurations.of(OpenTelemetryEventEnricherAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(OpenTelemetryTracingEventEnricher.class);
                });
    }

    @Test
    public void otherTracingEventEnricherFoundAndUsed() {
        runner.withConfiguration(AutoConfigurations.of(OpenTelemetryEventEnricherAutoConfiguration.class))
                .withBean(OpenTelemetry.class, Mockito::mock)
                .withBean(TracingEventEnricher.class, Mockito::mock)
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(OpenTelemetryTracingEventEnricher.class);
                    assertThat(context).hasNotFailed().hasSingleBean(TracingEventEnricher.class);
                });
    }

    @Test
    public void otelTracingEventEnricherUsed() {
        runner.withConfiguration(AutoConfigurations.of(OpenTelemetryEventEnricherAutoConfiguration.class))
                .withBean(OpenTelemetry.class, () -> Mockito.mock(OpenTelemetry.class, Mockito.RETURNS_DEEP_STUBS))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OpenTelemetryTracingEventEnricher.class);
                });
    }
}
