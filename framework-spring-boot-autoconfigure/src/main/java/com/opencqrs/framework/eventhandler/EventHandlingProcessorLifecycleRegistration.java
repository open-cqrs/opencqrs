/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/** Interface to be implemented for registering {@link EventHandlingProcessorLifecycleController} beans. */
@FunctionalInterface
public interface EventHandlingProcessorLifecycleRegistration {

    /**
     * Implementations are expected to {@linkplain BeanDefinitionRegistry#registerBeanDefinition(String, BeanDefinition)
     * register} an {@link EventHandlingProcessorLifecycleController} within the given {@link BeanDefinitionRegistry},
     * if needed.
     *
     * @param registry the registry to be used for bean registration
     * @param eventHandlingProcessorBeanName the name of the {@link EventHandlingProcessor} bean to refer to for
     *     life-cycle operations
     * @param processorSettings the processor settings
     */
    void registerLifecycleBean(
            BeanDefinitionRegistry registry,
            String eventHandlingProcessorBeanName,
            EventHandlingProperties.ProcessorSettings processorSettings);
}
