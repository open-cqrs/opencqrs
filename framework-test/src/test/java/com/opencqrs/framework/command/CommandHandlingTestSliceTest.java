/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.esdb.client.EsdbClient;
import com.opencqrs.esdb.client.EsdbClientAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

@CommandHandlingTest
public class CommandHandlingTestSliceTest {

    @Autowired
    private ApplicationContext context;

    @Test
    public void fixtureAutoCreatedWithProperGenericTypes_beanNoDependency(
            @Autowired ObjectProvider<CommandHandlingTestFixture<MyCommand1>> fixture) {
        assertThat(fixture.getIfAvailable()).isNotNull();
    }

    @Test
    public void fixtureAutoCreatedWithProperGenericTypes_beanUnresolvableDependency(
            @Autowired ObjectProvider<CommandHandlingTestFixture<MyCommand2>> fixture) {
        assertThatThrownBy(fixture::getIfAvailable)
                .hasCauseInstanceOf(UnsatisfiedDependencyException.class)
                .hasMessageContaining("chdUnresolvableDependency");
    }

    @Test
    public void fixtureAutoCreatedWithProperGenericTypes_beanCommandHandling(
            @Autowired ObjectProvider<CommandHandlingTestFixture<MyCommand3>> fixture) {
        assertThat(fixture.getIfAvailable()).isNotNull();
    }

    @Test
    public void fixtureAutoCreatedWithProperGenericTypes_programmaticBeanRegistration(
            @Autowired ObjectProvider<CommandHandlingTestFixture<MyCommand4>> fixture) {
        assertThat(fixture.getIfAvailable()).isNotNull();
    }

    @Test
    public void fixtureNotCreated(@Autowired ObjectProvider<CommandHandlingTestFixture<Command>> fixture) {
        assertThat(fixture.getIfAvailable()).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                CommandRouterAutoConfiguration.class,
                EsdbClientAutoConfiguration.class,
                CommandRouter.class,
                EsdbClient.class,
                ObjectMapper.class
            })
    public void unnecessaryBeansIgnored(Class<?> bean) {
        assertThatThrownBy(() -> context.getBean(bean)).isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}
