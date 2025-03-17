/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;
import java.lang.annotation.*;

/**
 * Annotation to be used in favor of defining {@link StateRebuildingHandlerDefinition}
 * {@link org.springframework.context.annotation.Bean}s.
 *
 * <p>It can be placed on public methods returning a non-primitive {@link Object} representing the
 * {@link StateRebuildingHandlerDefinition#instanceClass()} and with at least one non-primitive parameter extending
 * {@link Object} representing an assignable {@link StateRebuildingHandlerDefinition#eventClass()} Additionally such
 * methods may have any of the following unique parameter types, in any order:
 *
 * <ul>
 *   <li>a type derived from {@link Object} representing the {@link StateRebuildingHandlerDefinition#instanceClass()}
 *   <li>a {@link java.util.Map java.util.Map&lt;String, ?&gt;} for the event meta-data
 *   <li>a {@link String} for the event subject
 *   <li>an {@link Event} for the raw event
 *   <li>any number of {@link org.springframework.beans.factory.annotation.Autowired} annotated parameters, resolving to
 *       single beans within the {@link org.springframework.context.ApplicationContext}
 * </ul>
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
public @interface StateRebuilding {}
