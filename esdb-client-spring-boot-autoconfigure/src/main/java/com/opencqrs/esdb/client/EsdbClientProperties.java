/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@link ConfigurationProperties} for {@linkplain EsdbClientAutoConfiguration auto-configured} {@link EsdbClient}.
 *
 * @param server server configuration
 * @param connectionTimeout maximum duration to establish connection with the server
 */
@ConfigurationProperties("esdb")
public record EsdbClientProperties(@NotNull Server server, @NotNull @DefaultValue("PT5S") Duration connectionTimeout) {

    /**
     * Server configuration settings.
     *
     * @param uri Server connection URI.
     * @param accessToken Bearer access token.
     */
    public record Server(@NotNull URI uri, @NotBlank String accessToken) {}
}
