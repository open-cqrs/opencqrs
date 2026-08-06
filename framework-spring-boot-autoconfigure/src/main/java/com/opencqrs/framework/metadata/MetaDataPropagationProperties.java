/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.metadata;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@link ConfigurationProperties} for the {@linkplain MetaDataPropagatingCommandInterceptorAutoConfiguration
 * auto-configured} {@link MetaDataPropagatingCommandInterceptor}.
 *
 * @param mode The propagation mode to use.
 * @param keys The meta-data keys to propagate.
 */
@ConfigurationProperties("opencqrs.metadata.propagation")
public record MetaDataPropagationProperties(
        @DefaultValue("keep_if_present") PropagationMode mode, @DefaultValue Set<String> keys) {}
