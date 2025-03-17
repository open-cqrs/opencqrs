/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.lang.annotation.*;

/**
 * Annotation to be used in favor of defining {@link CommandHandlerDefinition}
 * {@link org.springframework.context.annotation.Bean}s.
 *
 * <p>It can be placed on public methods returning {@code void} or any other {@link CommandHandler} result. Such methods
 * may have any of the following unique parameter types, in any order:
 *
 * <ul>
 *   <li>a mandatory type derived from {@link Command} representing the {@link CommandHandlerDefinition#commandClass()}
 *   <li>an optional meta-data {@link java.util.Map}
 *   <li>a type derived from {@link Object} representing the {@link CommandHandlerDefinition#instanceClass()}
 *   <li>a {@link CommandEventPublisher} with the generic type matching the
 *       {@link CommandHandlerDefinition#instanceClass()}
 *   <li>any number of {@link org.springframework.beans.factory.annotation.Autowired} annotated parameters, resolving to
 *       single beans within the {@link org.springframework.context.ApplicationContext}
 * </ul>
 *
 * <strong>To be able to derive the {@link CommandHandlerDefinition#instanceClass()}, any of the latter two parameters
 * is optional, but not both.</strong>
 *
 * <p>The method must be contained within an
 * {@link org.springframework.beans.factory.annotation.AnnotatedBeanDefinition}, for instance within
 * {@link org.springframework.stereotype.Component}s or {@link org.springframework.context.annotation.Configuration}s.
 *
 * @see StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface CommandHandling {

    /**
     * The {@link SourcingMode} for the {@link CommandHandlerDefinition}.
     *
     * @return the sourcing mode to be used, defaults to {@link SourcingMode#RECURSIVE}
     */
    SourcingMode sourcingMode() default SourcingMode.RECURSIVE;
}
