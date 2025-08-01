/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class NoTracingEventEnricherAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    public void otelTracingEventEnricherUsed() {
        runner.withConfiguration(AutoConfigurations.of(OpenTelemetryEventEnricherAutoConfiguration.class))
                .withBean(OpenTelemetry.class, NoTracingEventEnricherAutoConfigurationTest::mockOtel)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OpenTelemetryTracingEventEnricher.class);
                });
    }

    @Test
    public void noTracingEventEnricherUsed() {
        runner.withConfiguration(AutoConfigurations.of(NoTracingEventEnricherAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(OpenTelemetry.class, OpenTelemetryAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(NoTracingEventEnricher.class);
                });
    }

    private static OpenTelemetry mockOtel() {
        return mock(OpenTelemetry.class, Mockito.RETURNS_DEEP_STUBS);
    }
}
