/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ResolvableType;

public class CommandHandlingTestAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CommandHandlingTestAutoConfiguration.class,
                    CommandHandlingAnnotationProcessingAutoConfiguration.class))
            .withUserConfiguration(CommandHandlingConfiguration.class);

    @Test
    public void initialized_CommandHandlingTestFixture_Builder() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CommandHandlingTestFixture.Builder.class);
            assertThat(context)
                    .getBean(CommandHandlingTestFixture.Builder.class)
                    .satisfies(builder -> {
                        assertThat(builder.stateRebuildingHandlerDefinitions)
                                .singleElement()
                                .isSameAs(context.getBean("myStateRebuildingHandlerDefinition"));
                    });
        });
    }

    @Test
    public void initialized_CommandHandlingTestFixture_beanMethodNoDependency() {
        contextRunner.run(context -> {
            assertThat(context.getBeanNamesForType(
                            ResolvableType.forClassWithGenerics(CommandHandlingTestFixture.class, MyCommand1.class)))
                    .singleElement()
                    .satisfies(beanName -> {
                        assertThat(context).getBean(beanName).isInstanceOf(CommandHandlingTestFixture.class);
                    });
        });
    }

    @Test
    public void lazyInitialized_CommandHandlingTestFixture_beanMethodWithUnresolvableDependency() {
        contextRunner.run(context -> {
            assertThat(context.getBeanNamesForType(
                            ResolvableType.forClassWithGenerics(CommandHandlingTestFixture.class, MyCommand2.class)))
                    .singleElement()
                    .satisfies(beanName -> {
                        assertThatThrownBy(() -> context.getBean(beanName))
                                .hasCauseInstanceOf(UnsatisfiedDependencyException.class)
                                .hasMessageContaining("chdUnresolvableDependency");
                    });
        });
    }

    @Test
    public void initialized_CommandHandlingTestFixture_for_CommandHandlingAnnotation() {
        contextRunner.run(context -> {
            assertThat(context.getBeanNamesForType(
                            ResolvableType.forClassWithGenerics(CommandHandlingTestFixture.class, MyCommand3.class)))
                    .singleElement()
                    .satisfies(beanName -> {
                        assertThat(context).getBean(beanName).isInstanceOf(CommandHandlingTestFixture.class);
                    });
        });
    }

    @Test
    public void initialized_CommandHandlingTestFixture_programmaticBeanRegistration() {
        contextRunner.run(context -> {
            assertThat(context.getBeanNamesForType(
                            ResolvableType.forClassWithGenerics(CommandHandlingTestFixture.class, MyCommand4.class)))
                    .singleElement()
                    .satisfies(beanName -> {
                        assertThat(context).getBean(beanName).isInstanceOf(CommandHandlingTestFixture.class);
                    });
        });
    }
}
