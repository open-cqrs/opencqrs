/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.MethodInvokingFactoryBean;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * {@linkplain org.springframework.boot.autoconfigure.ImportAutoConfiguration Auto-configuration} for
 * {@link CommandHandlingTestFixture}s.
 *
 * @see CommandHandlingTest
 */
@Configuration
@ImportAutoConfiguration({
    CommandHandlingAnnotationProcessingAutoConfiguration.class,
    StateRebuildingAnnotationProcessingAutoConfiguration.class
})
public class CommandHandlingTestAutoConfiguration {

    private static final Logger log = Logger.getLogger(CommandHandlingTestAutoConfiguration.class.getName());

    @Bean
    public CommandHandlingTestFixture.Builder<?> commandHandlingTestFixtureBuilder(
            StateRebuildingHandlerDefinition[] stateRebuildingHandlerDefinitions) {
        return CommandHandlingTestFixture.withStateRebuildingHandlerDefinitions(stateRebuildingHandlerDefinitions);
    }

    @Bean
    public static BeanDefinitionRegistryPostProcessor fixtureBeanDefinitionRegistryPostProcessor() {
        return registry -> {
            for (String beanName : registry.getBeanDefinitionNames()) {
                BeanDefinition bd = registry.getBeanDefinition(beanName);
                if (CommandHandlerDefinition.class.getName().equals(bd.getBeanClassName())) {
                    ResolvableType resolvableType = bd.getResolvableType();
                    Class<?> commandType = resolvableType.resolveGeneric(1);

                    if (commandType != null) {
                        ResolvableType fixtureType =
                                ResolvableType.forClassWithGenerics(CommandHandlingTestFixture.class, commandType);

                        bd.setLazyInit(true);

                        RootBeanDefinition fixture = new RootBeanDefinition();
                        fixture.setBeanClass(MethodInvokingFactoryBean.class);
                        fixture.setTargetType(fixtureType);
                        fixture.setLazyInit(true);
                        fixture.getPropertyValues()
                                .add("targetObject", new RuntimeBeanReference("commandHandlingTestFixtureBuilder"));
                        fixture.getPropertyValues().add("targetMethod", "using");
                        fixture.getPropertyValues().addPropertyValue("arguments", new RuntimeBeanReference(beanName));

                        registry.registerBeanDefinition(beanName + "_fixture", fixture);
                    } else {
                        log.log(
                                Level.WARNING,
                                "Skipping creation of CommandHandlingTestFixture bean for bean {} due to unresolvable generic types for CommandHandlerDefinition",
                                beanName);
                    }
                } else if (bd instanceof AnnotatedBeanDefinition) {
                    MethodMetadata methodMetadata = ((AnnotatedBeanDefinition) bd).getFactoryMethodMetadata();

                    if (methodMetadata != null
                            && methodMetadata
                                    .getReturnTypeName()
                                    .equals(CommandHandlerDefinition.class.getCanonicalName())) {
                        bd.setLazyInit(true);

                        try {
                            AtomicReference<Method> factoryMethod = new AtomicReference<>();
                            ReflectionUtils.doWithMethods(
                                    ClassUtils.forName(
                                            methodMetadata.getDeclaringClassName(),
                                            registry.getClass().getClassLoader()),
                                    factoryMethod::set,
                                    method -> method.getName().equals(methodMetadata.getMethodName()));

                            if (factoryMethod.get() != null) {
                                ResolvableType factoryMethodReturnType =
                                        ResolvableType.forMethodReturnType(factoryMethod.get());
                                ResolvableType commandType = factoryMethodReturnType.getGeneric(1);
                                ResolvableType fixtureType = ResolvableType.forClassWithGenerics(
                                        CommandHandlingTestFixture.class, commandType);

                                RootBeanDefinition fixture = new RootBeanDefinition();
                                fixture.setTargetType(fixtureType);
                                fixture.setLazyInit(true);
                                fixture.setBeanClass(MethodInvokingFactoryBean.class);
                                fixture.getPropertyValues()
                                        .add(
                                                "targetObject",
                                                new RuntimeBeanReference("commandHandlingTestFixtureBuilder"));
                                fixture.getPropertyValues().add("targetMethod", "using");
                                fixture.getPropertyValues()
                                        .addPropertyValue("arguments", new RuntimeBeanReference(beanName));

                                registry.registerBeanDefinition(methodMetadata.getMethodName() + "_fixture", fixture);
                            }
                        } catch (ClassNotFoundException e) {
                            throw new BeanCreationException(
                                    "Could not create test fixture for command handler definition defined within class: "
                                            + methodMetadata.getDeclaringClassName(),
                                    e);
                        }
                    }
                }
            }
        };
    }
}
