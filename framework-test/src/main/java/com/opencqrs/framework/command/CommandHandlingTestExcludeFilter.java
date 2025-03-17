/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.util.Set;
import org.springframework.boot.test.autoconfigure.filter.StandardAnnotationCustomizableTypeExcludeFilter;

/**
 * {@link StandardAnnotationCustomizableTypeExcludeFilter} implementation for {@link CommandHandlingTest}, which
 * includes beans defined within {@link CommandHandlerConfiguration}s.
 */
final class CommandHandlingTestExcludeFilter
        extends StandardAnnotationCustomizableTypeExcludeFilter<CommandHandlingTest> {

    CommandHandlingTestExcludeFilter(Class<?> testClass) {
        super(testClass);
    }

    @Override
    protected boolean isUseDefaultFilters() {
        return true;
    }

    @Override
    protected Set<Class<?>> getDefaultIncludes() {
        return Set.of(CommandHandlerConfiguration.class);
    }
}
