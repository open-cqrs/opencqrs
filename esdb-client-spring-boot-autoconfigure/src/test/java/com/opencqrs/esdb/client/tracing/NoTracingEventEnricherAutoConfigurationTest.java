/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class NoTracingEventEnricherAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NoTracingEventEnricherAutoConfiguration.class));

    @Test
    public void noTracingEventEnricherUsed() {
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(NoTracingEventEnricher.class));
    }

    @Test
    public void otherTracingEventEnricherFoundAndUsed() {
        runner.withBean(TracingEventEnricher.class, Mockito::mock)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(NoTracingEventEnricher.class));
    }
}
