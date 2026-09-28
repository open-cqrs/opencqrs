/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opencqrs.framework.command.CommandHandlingTestContextCustomizerFactory.InterceptorsCustomizer;
import com.opencqrs.framework.command.CommandHandlingTestContextCustomizerFactory.InterceptorsEnabled;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.MergedContextConfiguration;

class CommandHandlingTestContextCustomizerFactoryTest {

    private final CommandHandlingTestContextCustomizerFactory factory =
            new CommandHandlingTestContextCustomizerFactory();

    @Test
    void returnsNoCustomizerForNonAnnotatedClass() {
        assertThat(factory.createContextCustomizer(Object.class, List.of())).isNull();
    }

    @Test
    void interceptorsEnabledByDefault() {
        assertThat(factory.createContextCustomizer(DefaultSlice.class, List.of()))
                .isEqualTo(new InterceptorsCustomizer(true));
    }

    @Test
    void interceptorsDisabledWhenAttributeFalse() {
        assertThat(factory.createContextCustomizer(InterceptorsDisabledSlice.class, List.of()))
                .isEqualTo(new InterceptorsCustomizer(false));
    }

    @Test
    void customizerRegistersFlagAsContextSingleton() {
        var context = new GenericApplicationContext();
        context.refresh();

        new InterceptorsCustomizer(false).customizeContext(context, mock(MergedContextConfiguration.class));

        assertThat(context.getBean(InterceptorsEnabled.class)).isEqualTo(new InterceptorsEnabled(false));
    }

    @CommandHandlingTest
    static class DefaultSlice {}

    @CommandHandlingTest(withInterceptors = false)
    static class InterceptorsDisabledSlice {}
}
