/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class EsdbHealthContributorAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    public void healthContributorCreated() {
        runner.withConfiguration(AutoConfigurations.of(EsdbHealthContributorAutoConfiguration.class))
                .withBean(EsdbClient.class, Mockito::mock)
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(EsdbHealthContributorAutoConfiguration.class)
                            .hasSingleBean(EsdbHealthIndicator.class);
                });
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                HealthIndicator.class,
                EsdbClient.class,
            })
    public void conditionallyDisabledByMissingClass(Class<?> clazz) {
        runner.withConfiguration(AutoConfigurations.of(EsdbHealthContributorAutoConfiguration.class))
                .withBean(EsdbClient.class, Mockito::mock)
                .withClassLoader(new FilteredClassLoader(clazz))
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(EsdbHealthContributorAutoConfiguration.class);
                });
    }

    @Test
    public void conditionallyDisabledByMissingClientBean() {
        runner.withConfiguration(AutoConfigurations.of(EsdbHealthContributorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(EsdbHealthContributorAutoConfiguration.class);
                });
    }

    @Test
    public void conditionallyDisabledByProperty() {
        runner.withConfiguration(AutoConfigurations.of(EsdbHealthContributorAutoConfiguration.class))
                .withBean(EsdbClient.class, Mockito::mock)
                .withPropertyValues("management.health.esdb.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(EsdbHealthContributorAutoConfiguration.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"esdbHealthIndicator", "esdbHealthContributor"})
    public void conditionallyDisabledHealthContributorByExistingBean(String beanName) {
        runner.withConfiguration(AutoConfigurations.of(EsdbHealthContributorAutoConfiguration.class))
                .withBean(EsdbClient.class, Mockito::mock)
                .withBean(beanName, HealthIndicator.class, Mockito::mock)
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(EsdbHealthContributorAutoConfiguration.class)
                            .doesNotHaveBean(EsdbHealthIndicator.class);
                });
    }
}
