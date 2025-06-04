/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.metadata;

import com.opencqrs.framework.command.CommandRouter;
import com.opencqrs.framework.command.CommandRouterAutoConfiguration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@link ConfigurationProperties} for {@linkplain CommandRouterAutoConfiguration auto-configured} {@link CommandRouter}
 * meta-data propagation.
 *
 * @param mode The propagation mode to use.
 * @param keys The meta-data keys to propagate.
 */
@ConfigurationProperties("opencqrs.metadata.propagation")
public record MetaDataPropagationProperties(
        @DefaultValue("keep_if_present") PropagationMode mode, @DefaultValue Set<String> keys) {}
