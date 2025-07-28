/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.opencqrs.esdb.client.EsdbClient;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class TracingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    public void otelTracingContextualizerUsed() {
        runner.withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class))
                .withBean(EsdbClient.class, Mockito::mock)
                .withBean(OpenTelemetry.class, TracingAutoConfigurationTest::mockOtel)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OpenTelemetryTracingEventEnricher.class);
                });
    }

    @Test
    public void noOpTracingContextualizerUsed() {
        runner.withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class))
                .withBean(EsdbClient.class, Mockito::mock)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(NoTracingEventEnricher.class);
                });
    }

    private static OpenTelemetry mockOtel() {
        return mock(OpenTelemetry.class, Mockito.RETURNS_DEEP_STUBS);
    }
}
