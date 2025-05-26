/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.types;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for
 * {@link ClassNameEventTypeResolver}.
 */
@AutoConfiguration
public class ClassNameEventTypeResolverAutoConfiguration implements BeanClassLoaderAware {

    private ClassLoader beanClassLoader;

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    @Bean
    @ConditionalOnMissingBean
    public EventTypeResolver openCqrsClassNameEventTypeResolver() {
        return new ClassNameEventTypeResolver(beanClassLoader);
    }
}
