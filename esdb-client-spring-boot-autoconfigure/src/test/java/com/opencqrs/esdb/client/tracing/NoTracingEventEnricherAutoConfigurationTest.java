/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class NoTracingEventEnricherAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    public void noTracingEventEnricherUsed() {
        runner.withConfiguration(AutoConfigurations.of(NoTracingEventEnricherAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(NoTracingEventEnricher.class);
                });
    }

    @Test
    public void otherTracingEventEnricherFoundAndUsed() {
        runner.withConfiguration(AutoConfigurations.of(OpenTelemetryEventEnricherAutoConfiguration.class))
                .withBean(TracingEventEnricher.class, Mockito::mock)
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(NoTracingEventEnricher.class);
                });
    }
}
