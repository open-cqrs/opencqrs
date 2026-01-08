/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link EsdbHealthIndicator}. */
@AutoConfiguration(after = EsdbClientAutoConfiguration.class)
@ConditionalOnClass({
    HealthIndicator.class,
    EsdbClient.class,
})
@ConditionalOnBean(EsdbClient.class)
@ConditionalOnEnabledHealthIndicator("esdb")
public class EsdbHealthContributorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = {"esdbHealthIndicator", "esdbHealthContributor"})
    public EsdbHealthIndicator esdbHealthContributor(EsdbClient client) {
        return new EsdbHealthIndicator(client);
    }
}
