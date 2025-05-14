/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = EsdbClientAutoConfiguration.class)
@ConditionalOnClass({
    HealthContributor.class,
    EsdbClient.class,
})
@ConditionalOnBean(EsdbClient.class)
@ConditionalOnEnabledHealthIndicator("esdb")
public class EsdbHealthContributorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = {"esdbHealthIndicator", "esdbHealthContributor"})
    public HealthContributor esdbHealthContributor(EsdbClient client) {
        return new EsdbHealthIndicator(client);
    }
}
