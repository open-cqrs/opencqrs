/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.framework.reflection.AutowiredParameter;
import com.opencqrs.framework.reflection.AutowiredParameterResolver;
import java.lang.reflect.Method;
import java.util.*;
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
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * {@linkplain org.springframework.boot.autoconfigure.EnableAutoConfiguration Auto-configuration} for
 * {@link CommandHandlerDefinition}s defined via {@link CommandHandling} annotated bean methods.
 */
@AutoConfiguration
public class CommandHandlingAnnotationProcessingAutoConfiguration {

    private static final Set<Class<?>> frameworkParameterTypes = Set.of(CommandEventPublisher.class, Map.class);

    private static final Set<Class<?>> supportedParameterTypes;

    static {
        Set<Class<?>> classes = new HashSet<>(frameworkParameterTypes);
        classes.add(Object.class);
        supportedParameterTypes = classes;
    }

    @Bean
    public static BeanDefinitionRegistryPostProcessor openCqrsCommandHandlingAnnotatedMethodBeanRegistration() {
        return registry -> {
            for (String beanName : registry.getBeanDefinitionNames()) {
                BeanDefinition bd = registry.getBeanDefinition(beanName);
                if (bd instanceof AnnotatedBeanDefinition) {
                    AnnotatedBeanDefinition abd = (AnnotatedBeanDefinition) bd;
                    if (abd.getBeanClassName() != null) {
                        AnnotationMetadata metadata = abd.getMetadata();
                        if (metadata.hasAnnotatedMethods(CommandHandling.class.getName())) {
                            if (!bd.isSingleton()) {
                                throw new BeanCreationException(
                                        "annotation based command handlers not allowed in non-singleton beans");
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
                                            "could not load bean class for command handler introspection", e);
                                }
                            }

                            for (MethodMetadata mmd :
                                    standardMetadata.getAnnotatedMethods(CommandHandling.class.getName())) {
                                StandardMethodMetadata smmd = (StandardMethodMetadata) mmd;
                                Method introspectedMethod = smmd.getIntrospectedMethod();
                                CommandHandling annotation =
                                        AnnotationUtils.findAnnotation(introspectedMethod, CommandHandling.class);

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
                                                    "command handler parameter type not supported "
                                                            + parameterType.getSimpleName() + " on: " + smmd);
                                        }
                                        requiredParamPositions.merge(parameterType, i, (a, b) -> {
                                            throw new BeanCreationException(
                                                    "command handlers must be defined with non repeating parameter types, found duplicate "
                                                            + parameterType.getSimpleName() + " on: " + smmd);
                                        });
                                    }
                                }

                                Set<Class<?>> requiredNonFrameworkTypes =
                                        new HashSet<>(requiredParamPositions.keySet());
                                requiredNonFrameworkTypes.removeAll(frameworkParameterTypes);
                                if (requiredNonFrameworkTypes.size() > 2
                                        || requiredNonFrameworkTypes.stream()
                                                        .filter(Command.class::isAssignableFrom)
                                                        .count()
                                                != 1) {
                                    throw new BeanCreationException(
                                            "command handlers must be defined with at most two (non @Autowired) Java object parameters for current instance state and command, but found "
                                                    + requiredParamPositions + " on: " + smmd);
                                }

                                Class<?> returnType = introspectedMethod.getReturnType();
                                if (void.class.equals(returnType)) {
                                    returnType = Void.class;
                                }
                                Class<?> commandType = requiredNonFrameworkTypes.stream()
                                        .filter(Command.class::isAssignableFrom)
                                        .findFirst()
                                        .orElseThrow(() -> new BeanCreationException(
                                                "command handler must be defined with a command parameter inheriting Command, but none found: "
                                                        + smmd));
                                Class<?> instanceType = requiredNonFrameworkTypes.stream()
                                        .filter(it -> !Command.class.isAssignableFrom(it))
                                        .findFirst()
                                        .or(() -> Optional.ofNullable(
                                                        requiredParamPositions.get(CommandEventPublisher.class))
                                                .map(index -> ResolvableType.forMethodParameter(
                                                                introspectedMethod, index)
                                                        .getGeneric(0)
                                                        .resolve()))
                                        .orElseThrow(() -> new BeanCreationException(
                                                "command handler instance type could not be derived from: " + smmd));

                                RootBeanDefinition commandHandlerDefinition = new RootBeanDefinition();
                                commandHandlerDefinition.setBeanClass(CommandHandlerDefinition.class);
                                commandHandlerDefinition.setTargetType(ResolvableType.forClassWithGenerics(
                                        CommandHandlerDefinition.class, instanceType, commandType, returnType));
                                ConstructorArgumentValues values = new ConstructorArgumentValues();
                                values.addGenericArgumentValue(instanceType);
                                values.addGenericArgumentValue(commandType);
                                values.addGenericArgumentValue(annotation.sourcingMode());

                                GenericBeanDefinition commandHandler = new GenericBeanDefinition();
                                commandHandler.setBeanClass(ReflectiveMethodInvocationCommandHandler.class);
                                ConstructorArgumentValues chArgs = new ConstructorArgumentValues();
                                chArgs.addIndexedArgumentValue(0, new RuntimeBeanReference(beanName));
                                chArgs.addIndexedArgumentValue(1, introspectedMethod);
                                chArgs.addIndexedArgumentValue(
                                        2,
                                        new ReflectiveMethodInvocationCommandHandler.ParameterPositions(
                                                requiredParamPositions.getOrDefault(instanceType, -1),
                                                requiredParamPositions.get(commandType),
                                                requiredParamPositions.getOrDefault(Map.class, -1),
                                                requiredParamPositions.getOrDefault(CommandEventPublisher.class, -1)));
                                chArgs.addIndexedArgumentValue(3, autowiredParameters);

                                commandHandler.setConstructorArgumentValues(chArgs);
                                values.addGenericArgumentValue(commandHandler);

                                commandHandlerDefinition.setConstructorArgumentValues(values);
                                registry.registerBeanDefinition(
                                        introspectedMethod.toGenericString(), commandHandlerDefinition);
                            }
                        }
                    }
                }
            }
        };
    }

    static class ReflectiveMethodInvocationCommandHandler extends AutowiredParameterResolver
            implements CommandHandler.ForInstanceAndCommandAndMetaData<Object, Command, Object> {

        private final Object target;
        private final ParameterPositions parameterPositions;

        public ReflectiveMethodInvocationCommandHandler(
                Object target,
                Method method,
                ParameterPositions parameterPositions,
                Set<AutowiredParameter> autowiredParameters) {
            super(method, autowiredParameters);
            this.target = target;
            this.parameterPositions = parameterPositions;
        }

        @Override
        public Object handle(
                Object instance,
                Command command,
                Map<String, ?> metaData,
                CommandEventPublisher<Object> commandEventPublisher) {
            var requiredParams = parameterPositions.mapArguments(instance, command, metaData, commandEventPublisher);

            return ReflectionUtils.invokeMethod(method, target, resolveIncludingAutowiredParameters(requiredParams));
        }

        record ParameterPositions(int instance, int command, int metaData, int commandEventPublisher) {
            public Map<Integer, Object> mapArguments(
                    Object instance,
                    Command command,
                    Map<String, ?> metaData,
                    CommandEventPublisher<Object> commandEventPublisher) {
                Predicate<Integer> present = integer -> integer != -1;

                Map<Integer, Object> result = new HashMap<>();
                if (present.test(instance())) result.put(instance(), instance);
                if (present.test(command())) result.put(command(), command);
                if (present.test(metaData())) result.put(metaData(), metaData);
                if (present.test(commandEventPublisher())) result.put(commandEventPublisher(), commandEventPublisher);

                return result;
            }
        }
    }
}
