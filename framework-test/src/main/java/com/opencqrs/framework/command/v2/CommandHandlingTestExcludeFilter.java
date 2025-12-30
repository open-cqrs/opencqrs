/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command.v2;

import java.util.Set;
import org.springframework.boot.test.autoconfigure.filter.StandardAnnotationCustomizableTypeExcludeFilter;

import com.opencqrs.framework.command.v2.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlerConfiguration;


/**
 * {@link StandardAnnotationCustomizableTypeExcludeFilter} implementation for {@link CommandHandlingTest}, which
 * includes beans defined within {@link CommandHandlerConfiguration}s.
 */
public final class CommandHandlingTestExcludeFilter
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