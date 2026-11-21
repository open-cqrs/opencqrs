/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * Bridges the {@link CommandHandlingTest#withInterceptors()} slice attribute into the test's application context (a
 * bean cannot read the test class's annotation itself). It registers an {@link InterceptorsEnabled} singleton that
 * {@link CommandHandlingTestAutoConfiguration} reads to either apply or ignore the interceptors found within the slice.
 * Registered via {@code META-INF/spring.factories}.
 */
class CommandHandlingTestContextCustomizerFactory implements ContextCustomizerFactory {

    /** Typed carrier for the {@link CommandHandlingTest#withInterceptors()} flag, registered as a context singleton. */
    record InterceptorsEnabled(boolean value) {}

    @Override
    public @Nullable ContextCustomizer createContextCustomizer(
            Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        CommandHandlingTest annotation =
                TestContextAnnotationUtils.findMergedAnnotation(testClass, CommandHandlingTest.class);
        return annotation == null ? null : new InterceptorsCustomizer(annotation.withInterceptors());
    }

    /**
     * The boolean {@code interceptorsEnabled} component gives value-based {@code equals}/{@code hashCode}, so
     * {@code withInterceptors = true} and {@code false} yield distinct cached application contexts.
     */
    record InterceptorsCustomizer(boolean interceptorsEnabled) implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            context.getBeanFactory()
                    .registerSingleton(
                            "commandHandlingTestInterceptorsEnabled", new InterceptorsEnabled(interceptorsEnabled));
        }
    }
}
