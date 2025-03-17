/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.types;

/**
 * {@link EventTypeResolver} implementation that maps {@link Class#getName()} to {@linkplain #getEventType(Class) event
 * type} and vice versa.
 *
 * <p>The use of this {@link EventTypeResolver} implementation is discouraged with respect to interoperability (with
 * non-Java applications operating on the same events) and refactoring.
 *
 * @see PreconfiguredAssignableClassEventTypeResolver
 */
public class ClassNameEventTypeResolver implements EventTypeResolver {

    private final ClassLoader classLoader;

    public ClassNameEventTypeResolver(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public String getEventType(Class<?> clazz) {
        return clazz.getName();
    }

    @Override
    public Class<?> getJavaClass(String eventType) {
        try {
            return classLoader.loadClass(eventType);
        } catch (ClassNotFoundException e) {
            throw new EventTypeResolutionException("failed to resolve java class for event type: " + eventType, e);
        }
    }
}
