/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.reflection.AutowiredParameter;
import com.opencqrs.framework.reflection.AutowiredParameterResolver;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.ParameterResolutionDelegate;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for
 * {@link StateRebuildingHandlerDefinition}s defined via {@link StateRebuilding} annotated bean methods.
 */
@AutoConfiguration
public class StateRebuildingAnnotationProcessingAutoConfiguration {

    private static final Set<Class<?>> frameworkParameterTypes = Set.of(Map.class, String.class, Event.class);

    private static final Set<Class<?>> supportedParameterTypes;

    static {
        Set<Class<?>> classes = new HashSet<>(frameworkParameterTypes);
        classes.add(Object.class);
        supportedParameterTypes = classes;
    }

    @Bean
    public static BeanDefinitionRegistryPostProcessor openCqrsStateRebuildingAnnotatedMethodBeanRegistration() {
        return registry -> {
            for (String beanName : registry.getBeanDefinitionNames()) {
                BeanDefinition bd = registry.getBeanDefinition(beanName);
                if (bd instanceof AnnotatedBeanDefinition) {
                    AnnotatedBeanDefinition abd = (AnnotatedBeanDefinition) bd;
                    if (abd.getBeanClassName() != null) {
                        AnnotationMetadata metadata = abd.getMetadata();
                        if (metadata.hasAnnotatedMethods(StateRebuilding.class.getName())) {
                            if (!bd.isSingleton()) {
                                throw new BeanCreationException(
                                        "annotation based state rebuilding handlers not allowed in non-singleton beans");
                            }

                            StandardAnnotationMetadata standardMetadata;
                            if (metadata instanceof StandardAnnotationMetadata) {
                                standardMetadata = (StandardAnnotationMetadata) metadata;
                            } else {
                                try {
                                    Class<?> beanClass = ClassUtils.forName(
                                            bd.getBeanClassName(),
                                            registry.getClass().getClassLoader());
                                    standardMetadata =
                                            (StandardAnnotationMetadata) AnnotationMetadata.introspect(beanClass);
                                } catch (ClassNotFoundException e) {
                                    throw new BeanCreationException(
                                            "could not load bean class for state rebuilding handler introspection", e);
                                }
                            }

                            for (MethodMetadata mmd :
                                    standardMetadata.getAnnotatedMethods(StateRebuilding.class.getName())) {
                                StandardMethodMetadata smmd = (StandardMethodMetadata) mmd;
                                Method introspectedMethod = smmd.getIntrospectedMethod();
                                Class<?> instanceType = introspectedMethod.getReturnType();
                                if (!Object.class.isAssignableFrom(instanceType) || Object.class.equals(instanceType)) {
                                    throw new BeanCreationException(
                                            "state rebuilding handlers must return an instance type inheriting object, but found: "
                                                    + smmd);
                                }

                                Map<Class<?>, Integer> requiredParamPositions = new HashMap<>();
                                Set<AutowiredParameter> autowiredParameters = new HashSet<>();
                                for (int i = 0; i < introspectedMethod.getParameterCount(); i++) {
                                    var param = introspectedMethod.getParameters()[i];
                                    if (ParameterResolutionDelegate.isAutowirable(param, i)) {
                                        autowiredParameters.add(new AutowiredParameter(
                                                param, i, standardMetadata.getIntrospectedClass()));
                                    } else {
                                        Class<?> parameterType = param.getType();
                                        if (supportedParameterTypes.stream()
                                                .noneMatch(supported -> supported.isAssignableFrom(parameterType))) {
                                            throw new BeanCreationException(
                                                    "state rebuilding handler parameter type not supported "
                                                            + parameterType.getSimpleName() + " on: " + smmd);
                                        }
                                        requiredParamPositions.merge(parameterType, i, (a, b) -> {
                                            throw new BeanCreationException(
                                                    "state rebuilding handlers must be defined with non repeating parameter types, found duplicate "
                                                            + parameterType.getSimpleName() + " on: " + smmd);
                                        });
                                    }
                                }

                                Set<Class<?>> requiredNonFrameworkTypes =
                                        new HashSet<>(requiredParamPositions.keySet());
                                requiredNonFrameworkTypes.removeAll(frameworkParameterTypes);
                                if (requiredNonFrameworkTypes.size() > 2
                                        || requiredNonFrameworkTypes.stream()
                                                        .filter(it -> !it.equals(instanceType))
                                                        .count()
                                                > 1) {
                                    throw new BeanCreationException(
                                            "state rebuilding handlers must be defined with at most two Java object parameters for current instance state and event, but found "
                                                    + requiredParamPositions + " on: " + smmd);
                                }

                                Class<?> eventParamType = requiredNonFrameworkTypes.stream()
                                        .filter(it -> !it.equals(instanceType))
                                        .findFirst()
                                        .orElseThrow(() -> new BeanCreationException(
                                                "state rebuilding handler must be defined with an event payload parameter inheriting object, but found: "
                                                        + smmd));

                                RootBeanDefinition stateRebuildingHandlerDefinition = new RootBeanDefinition();
                                stateRebuildingHandlerDefinition.setBeanClass(StateRebuildingHandlerDefinition.class);
                                stateRebuildingHandlerDefinition.setTargetType(ResolvableType.forClassWithGenerics(
                                        StateRebuildingHandlerDefinition.class, instanceType, eventParamType));
                                ConstructorArgumentValues values = new ConstructorArgumentValues();
                                values.addGenericArgumentValue(instanceType);
                                values.addGenericArgumentValue(eventParamType);

                                GenericBeanDefinition stateRebuildingHandler = new GenericBeanDefinition();
                                stateRebuildingHandler.setBeanClass(
                                        ReflectiveMethodInvocationStateRebuildingHandler.class);
                                ConstructorArgumentValues srhArgs = new ConstructorArgumentValues();
                                srhArgs.addIndexedArgumentValue(0, new RuntimeBeanReference(beanName));
                                srhArgs.addIndexedArgumentValue(1, introspectedMethod);
                                srhArgs.addIndexedArgumentValue(
                                        2,
                                        new ReflectiveMethodInvocationStateRebuildingHandler.ParameterPositions(
                                                requiredParamPositions.getOrDefault(instanceType, -1),
                                                requiredParamPositions.get(eventParamType),
                                                requiredParamPositions.getOrDefault(Map.class, -1),
                                                requiredParamPositions.getOrDefault(String.class, -1),
                                                requiredParamPositions.getOrDefault(Event.class, -1)));
                                srhArgs.addIndexedArgumentValue(3, autowiredParameters);

                                stateRebuildingHandler.setConstructorArgumentValues(srhArgs);
                                values.addGenericArgumentValue(stateRebuildingHandler);

                                stateRebuildingHandlerDefinition.setConstructorArgumentValues(values);
                                registry.registerBeanDefinition(
                                        introspectedMethod.toGenericString(), stateRebuildingHandlerDefinition);
                            }
                        }
                    }
                }
            }
        };
    }

    static class ReflectiveMethodInvocationStateRebuildingHandler extends AutowiredParameterResolver
            implements StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<Object, Object> {

        private final Object target;
        private final ParameterPositions parameterPositions;

        ReflectiveMethodInvocationStateRebuildingHandler(
                Object target,
                Method method,
                ParameterPositions parameterPositions,
                Set<AutowiredParameter> autowiredParameters) {
            super(method, autowiredParameters);
            this.target = target;
            this.parameterPositions = parameterPositions;
        }

        @Override
        public Object on(Object instance, Object event, Map<String, ?> metaData, String subject, Event rawEvent) {
            var requiredParams = parameterPositions.mapArguments(instance, event, metaData, subject, rawEvent);

            return ReflectionUtils.invokeMethod(method, target, resolveIncludingAutowiredParameters(requiredParams));
        }

        record ParameterPositions(int instance, int event, int metaData, int subject, int raw) {
            public Map<Integer, Object> mapArguments(
                    Object instance, Object event, Map<String, ?> metaData, String subject, Event rawEvent) {
                Predicate<Integer> present = integer -> integer != -1;

                Map<Integer, Object> result = new HashMap<>();
                if (present.test(instance())) result.put(instance(), instance);
                if (present.test(event())) result.put(event(), event);
                if (present.test(metaData())) result.put(metaData(), metaData);
                if (present.test(subject())) result.put(subject(), subject);
                if (present.test(raw())) result.put(raw(), rawEvent);

                return result;
            }
        }
    }
}
