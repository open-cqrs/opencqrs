/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.optimisticlocking;

import com.opencqrs.framework.command.interceptor.CommandInterceptorOrders;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} registering the
 * framework-provided {@link OptimisticLockingCommandInterceptor} by default.
 */
@AutoConfiguration
public class OptimisticLockingCommandInterceptorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Order(CommandInterceptorOrders.OPTIMISTIC_LOCKING)
    public OptimisticLockingCommandInterceptor openCqrsOptimisticLockingCommandInterceptor() {
        return new OptimisticLockingCommandInterceptor();
    }
}
