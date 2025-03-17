/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.reflection;

import com.opencqrs.framework.CqrsFrameworkException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.ParameterResolutionDelegate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.ReflectionUtils;

/** Base class for reflective {@link AutowiredParameter} resolution. */
public abstract class AutowiredParameterResolver implements ApplicationContextAware {

    private ApplicationContext applicationContext;
    protected final Method method;
    private final Set<AutowiredParameter> autowiredParameters;

    public AutowiredParameterResolver(Method method, Set<AutowiredParameter> autowiredParameters) {
        this.method = method;
        this.autowiredParameters = autowiredParameters;

        ReflectionUtils.makeAccessible(method);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Resolves the configured {@link AutowiredParameter}s, merges them with the given parameter positions and returns
     * them in order.
     *
     * @param params any non-autowired positional parameter values to be included
     * @return an ordered array of the input and autowired params
     */
    protected final Object[] resolveIncludingAutowiredParameters(Map<Integer, Object> params) {
        autowiredParameters.forEach(p -> {
            try {
                if (params.put(
                                p.index(),
                                ParameterResolutionDelegate.resolveDependency(
                                        p.parameter(),
                                        p.index(),
                                        p.containingClass(),
                                        applicationContext.getAutowireCapableBeanFactory()))
                        != null) {
                    throw new IllegalArgumentException(
                            "conflicting parameter positions found for autowired parameter: " + p);
                }
            } catch (BeansException e) {
                throw new CqrsFrameworkException.NonTransientException(
                        "could not resolve required autowired dependency for method: " + method, e);
            }
        });

        return params.keySet().stream().sorted().map(params::get).toArray();
    }
}
