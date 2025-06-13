/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.reflection.AutowiredParameter;
import com.opencqrs.framework.reflection.AutowiredParameterResolver;
import com.opencqrs.framework.transaction.NoTransactionOperationsAdapter;
import com.opencqrs.framework.transaction.SpringTransactionOperationsAdapter;
import com.opencqrs.framework.transaction.TransactionOperationsAdapter;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for
 * {@link EventHandlerDefinition}s defined via {@link EventHandling} annotated bean methods.
 */
@AutoConfiguration
public class EventHandlingAnnotationProcessingAutoConfiguration {

    private static final Set<Class<?>> frameworkParameterTypes = Set.of(Map.class, Event.class);

    private static final Set<Class<?>> supportedParameterTypes;

    static {
        Set<Class<?>> classes = new HashSet<>(frameworkParameterTypes);
        classes.add(Object.class);
        supportedParameterTypes = classes;
    }

    @Bean
    public static BeanDefinitionRegistryPostProcessor openCqrsEventHandlingAnnotatedMethodBeanRegistration() {
        return registry -> {
            for (String beanName : registry.getBeanDefinitionNames()) {
                BeanDefinition bd = registry.getBeanDefinition(beanName);
                if (bd instanceof AnnotatedBeanDefinition) {
                    AnnotatedBeanDefinition abd = (AnnotatedBeanDefinition) bd;
                    if (abd.getBeanClassName() != null) {
                        AnnotationMetadata metadata = abd.getMetadata();
                        if (metadata.hasAnnotatedMethods(EventHandling.class.getName())) {
                            if (!bd.isSingleton()) {
                                throw new BeanCreationException(
                                        "annotation based event handlers not allowed in non-singleton beans");
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
                                            "could not load bean class for event handler introspection", e);
                                }
                            }

                            for (MethodMetadata mmd :
                                    standardMetadata.getAnnotatedMethods(EventHandling.class.getName())) {
                                StandardMethodMetadata smmd = (StandardMethodMetadata) mmd;
                                Method introspectedMethod = smmd.getIntrospectedMethod();
                                var groupId = (String) smmd.getAnnotationAttributes(EventHandling.class.getName())
                                        .get("group");
                                if (!StringUtils.hasText(groupId)) {
                                    throw new BeanCreationException(
                                            "event handler must be defined using a valid non-empty group id: " + smmd);
                                }
                                if (!introspectedMethod.getReturnType().isAssignableFrom(Void.TYPE)) {
                                    throw new BeanCreationException(
                                            "event handlers must not be defined with return type other than void, but found: "
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
                                                    "event handler parameter type not supported "
                                                            + parameterType.getSimpleName() + " on: " + smmd);
                                        }
                                        requiredParamPositions.merge(parameterType, i, (a, b) -> {
                                            throw new BeanCreationException(
                                                    "event handlers must be defined with non repeating parameter types, found duplicate "
                                                            + parameterType.getSimpleName() + " on: " + smmd);
                                        });
                                    }
                                }

                                if (requiredParamPositions.isEmpty()) {
                                    throw new BeanCreationException(
                                            "event handler requires at least one (non-autowired) parameter, but found: "
                                                    + smmd);
                                }

                                Set<Class<?>> requiredNonFrameworkTypes =
                                        new HashSet<>(requiredParamPositions.keySet());
                                requiredNonFrameworkTypes.removeAll(frameworkParameterTypes);
                                if (requiredNonFrameworkTypes.size() > 1) {
                                    throw new BeanCreationException(
                                            "event handlers must be defined with at most one Java event object parameter, but found "
                                                    + requiredParamPositions + " on: " + smmd);
                                }
                                Class<?> objectParamType = requiredNonFrameworkTypes.stream()
                                        .findFirst()
                                        .orElse(Object.class);

                                RootBeanDefinition eventHandlerDefinition = new RootBeanDefinition();
                                eventHandlerDefinition.setBeanClass(EventHandlerDefinition.class);
                                eventHandlerDefinition.setTargetType(ResolvableType.forClassWithGenerics(
                                        EventHandlerDefinition.class, objectParamType));
                                ConstructorArgumentValues values = new ConstructorArgumentValues();
                                values.addGenericArgumentValue(
                                        smmd.getAnnotationAttributes(EventHandling.class.getName())
                                                .get("group"));
                                values.addGenericArgumentValue(objectParamType);

                                GenericBeanDefinition eventHandler = new GenericBeanDefinition();
                                eventHandler.setBeanClass(ReflectiveMethodInvocationEventHandler.class);
                                ConstructorArgumentValues ehArgs = new ConstructorArgumentValues();
                                ehArgs.addIndexedArgumentValue(0, new RuntimeBeanReference(beanName));
                                ehArgs.addIndexedArgumentValue(1, introspectedMethod);
                                ehArgs.addIndexedArgumentValue(
                                        2,
                                        new ReflectiveMethodInvocationEventHandler.ParameterPositions(
                                                requiredParamPositions.getOrDefault(objectParamType, -1),
                                                requiredParamPositions.getOrDefault(Map.class, -1),
                                                requiredParamPositions.getOrDefault(Event.class, -1)));
                                ehArgs.addIndexedArgumentValue(3, autowiredParameters);

                                GenericBeanDefinition txAdapter = new GenericBeanDefinition();
                                if (smmd.isAnnotated("org.springframework.transaction.annotation.Transactional")) {
                                    try {
                                        txAdapter.setBeanClass(SpringTransactionOperationsAdapter.class);
                                        ConstructorArgumentValues txArgs = new ConstructorArgumentValues();
                                        txArgs.addGenericArgumentValue(
                                                new RuntimeBeanReference(PlatformTransactionManager.class));
                                        txArgs.addGenericArgumentValue(
                                                new RuntimeBeanReference(TransactionAttributeSource.class));
                                        txArgs.addGenericArgumentValue(introspectedMethod);
                                        txArgs.addGenericArgumentValue(standardMetadata.getIntrospectedClass());
                                        txAdapter.setConstructorArgumentValues(txArgs);
                                    } catch (NoClassDefFoundError e) {
                                        throw new BeanCreationException(
                                                "@Transactional annotated event handler must not be used without spring tx support: "
                                                        + smmd,
                                                e);
                                    }
                                } else {
                                    txAdapter.setBeanClass(NoTransactionOperationsAdapter.class);
                                }
                                ehArgs.addIndexedArgumentValue(4, txAdapter);

                                eventHandler.setConstructorArgumentValues(ehArgs);
                                values.addGenericArgumentValue(eventHandler);

                                eventHandlerDefinition.setConstructorArgumentValues(values);
                                registry.registerBeanDefinition(
                                        introspectedMethod.toGenericString(), eventHandlerDefinition);
                            }
                        }
                    }
                }
            }
        };
    }

    static class ReflectiveMethodInvocationEventHandler extends AutowiredParameterResolver
            implements EventHandler.ForObjectAndMetaDataAndRawEvent<Object> {

        private final Object target;
        private final ParameterPositions parameterPositions;
        private final TransactionOperationsAdapter txAdapter;

        ReflectiveMethodInvocationEventHandler(
                Object target,
                Method method,
                ParameterPositions parameterPositions,
                Set<AutowiredParameter> autowiredParameters,
                TransactionOperationsAdapter txAdapter) {
            super(method, autowiredParameters);
            this.target = target;
            this.parameterPositions = parameterPositions;
            this.txAdapter = txAdapter;
        }

        @Override
        public void handle(Object event, Map<String, ?> metaData, Event rawEvent) {
            var params =
                    resolveIncludingAutowiredParameters(parameterPositions.mapArguments(event, metaData, rawEvent));

            txAdapter.execute(() -> ReflectionUtils.invokeMethod(method, target, params));
        }

        record ParameterPositions(int object, int metaData, int raw) {
            public Map<Integer, Object> mapArguments(Object event, Map<String, ?> metaData, Event rawEvent) {
                Predicate<Integer> present = integer -> integer != -1;

                Map<Integer, Object> result = new HashMap<>();
                if (present.test(object())) result.put(object(), event);
                if (present.test(metaData())) result.put(metaData(), metaData);
                if (present.test(raw())) result.put(raw(), rawEvent);

                return result;
            }
        }
    }
}
