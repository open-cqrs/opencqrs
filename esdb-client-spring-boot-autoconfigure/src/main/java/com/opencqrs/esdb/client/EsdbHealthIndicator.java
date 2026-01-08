/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;

/**
 * {@link org.springframework.boot.health.contributor.HealthContributor} implementation based on
 * {@link EsdbClient#health()}.
 */
public class EsdbHealthIndicator extends AbstractHealthIndicator {

    private final EsdbClient client;

    public EsdbHealthIndicator(EsdbClient client) {
        this.client = client;
    }

    @Override
    protected void doHealthCheck(org.springframework.boot.health.contributor.Health.Builder builder) {
        Health health = client.health();
        builder.withDetail("status", health.status()).withDetail("checks", health.checks());

        switch (health.status()) {
            case pass, warn -> builder.up();
            case fail -> builder.down();
        }
    }
}
