/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.metadata;

import com.opencqrs.framework.command.interceptor.CommandInterceptorOrders;
import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} registering the
 * {@link MetaDataPropagatingCommandInterceptor} from {@link MetaDataPropagationProperties}.
 *
 * <p>The interceptor is only registered when propagation would actually do something &mdash; the
 * {@linkplain MetaDataPropagationProperties#mode() mode} is not {@link PropagationMode#NONE} <strong>and</strong> at
 * least one {@linkplain MetaDataPropagationProperties#keys() key} is configured (empty keys would be a no-op).
 */
@AutoConfiguration
@EnableConfigurationProperties(MetaDataPropagationProperties.class)
public class MetaDataPropagatingCommandInterceptorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Conditional(PropagationEnabledCondition.class)
    @Order(CommandInterceptorOrders.META_DATA_PROPAGATION)
    public MetaDataPropagatingCommandInterceptor openCqrsMetaDataPropagatingCommandInterceptor(
            MetaDataPropagationProperties properties) {
        return new MetaDataPropagatingCommandInterceptor(properties.mode(), properties.keys());
    }

    /** Matches iff propagation is enabled (mode != {@link PropagationMode#NONE}) and at least one key is configured. */
    static class PropagationEnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Binder binder = Binder.get(context.getEnvironment());
            PropagationMode mode = binder.bind("opencqrs.metadata.propagation.mode", PropagationMode.class)
                    .orElse(PropagationMode.KEEP_IF_PRESENT);
            Set<String> keys = binder.bind("opencqrs.metadata.propagation.keys", Bindable.setOf(String.class))
                    .orElseGet(Set::of);
            return mode != PropagationMode.NONE && !keys.isEmpty();
        }
    }
}
