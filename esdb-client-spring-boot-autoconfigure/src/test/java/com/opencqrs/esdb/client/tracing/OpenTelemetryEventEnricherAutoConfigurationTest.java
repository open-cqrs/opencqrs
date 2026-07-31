/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class OpenTelemetryEventEnricherAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenTelemetryEventEnricherAutoConfiguration.class));

    @Test
    public void noOtelInClasspathFound() {
        runner.withClassLoader(new FilteredClassLoader(OpenTelemetry.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TracingEventEnricher.class));
    }

    @Test
    void noOtelBeanInApplicationContextFound() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TracingEventEnricher.class));
    }

    @Test
    public void otherTracingEventEnricherFoundAndUsed() {
        runner.withBean(OpenTelemetry.class, Mockito::mock)
                .withBean(TracingEventEnricher.class, Mockito::mock)
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(OpenTelemetryTracingEventEnricher.class);
                    assertThat(context).hasNotFailed().hasSingleBean(TracingEventEnricher.class);
                });
    }

    @Test
    public void otelTracingEventEnricherPreferredOverNoTracingEnricher() {
        runner.withConfiguration(AutoConfigurations.of(NoTracingEventEnricherAutoConfiguration.class))
                .withBean(OpenTelemetry.class, () -> Mockito.mock(OpenTelemetry.class, Mockito.RETURNS_DEEP_STUBS))
                .run(context ->
                        assertThat(context).hasNotFailed().hasSingleBean(OpenTelemetryTracingEventEnricher.class));
    }
}
