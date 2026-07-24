/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;

/** Interface to be implemented for controlling the life-cycle of {@link EventHandlingProcessor} beans. */
@FunctionalInterface
public interface EventHandlingProcessorLifecycleControllerFactory {

    /**
     * Provides a {@link BeanDefinition} controlling the life-cycle of the given {@link EventHandlingProcessor} bean
     * reference, that is {@linkplain EventHandlingProcessor#start() starting} and
     * {@linkplain EventHandlingProcessor#stop() stopping} it accordingly.
     *
     * @param eventHandlingProcessorReference bean reference to {@link EventHandlingProcessor}
     * @param lifeCycleSettings the life-cycle settings
     * @return an additional bean definition to be registered for controlling the life-cycle.
     */
    BeanDefinition createLifecycleBeanDefinition(
            BeanReference eventHandlingProcessorReference,
            EventHandlingProperties.ProcessorSettings.LifeCycle lifeCycleSettings);
}
