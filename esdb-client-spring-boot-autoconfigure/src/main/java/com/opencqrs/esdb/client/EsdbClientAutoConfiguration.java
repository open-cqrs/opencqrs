/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.esdb.client;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link EsdbClient}. */
@AutoConfiguration
@ConditionalOnClass(EsdbClient.class)
@EnableConfigurationProperties(EsdbClientProperties.class)
public class EsdbClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Client.class)
    public Client esdbClient(
            EsdbClientProperties properties, Marshaller marshaller, HttpClient.Builder httpClientBuilder) {
        return new EsdbClient(
                properties.server().uri(),
                properties.server().accessToken(),
                marshaller,
                httpClientBuilder.connectTimeout(properties.connectionTimeout()));
    }

    @Bean
    @ConditionalOnMissingBean(HttpClient.Builder.class)
    public HttpClient.Builder esdbHttpClientBuilder() {
        return HttpClient.newBuilder();
    }
}
