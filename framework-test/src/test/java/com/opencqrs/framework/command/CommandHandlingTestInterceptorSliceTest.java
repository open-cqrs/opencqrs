/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencqrs.framework.command.CommandHandlingTestContextCustomizerFactory.InterceptorsEnabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * End-to-end check that the {@link CommandHandlingTestContextCustomizerFactory} is discovered via
 * {@code META-INF/spring.factories} and bridges {@link CommandHandlingTest#withInterceptors()} into the context (the
 * builder wiring itself is covered by {@link CommandHandlingTestAutoConfigurationTest}).
 */
@CommandHandlingTest
public class CommandHandlingTestInterceptorSliceTest {

    @Test
    public void withInterceptorsFlagBridgedIntoContext(@Autowired InterceptorsEnabled interceptorsEnabled) {
        assertThat(interceptorsEnabled.value()).isTrue();
    }
}
