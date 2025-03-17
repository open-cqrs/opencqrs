/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.eventhandler.progress.JdbcProgressTracker;
import java.lang.annotation.*;
import org.springframework.core.annotation.AliasFor;

/**
 * Annotation to be used in favor of defining {@link EventHandlerDefinition}
 * {@link org.springframework.context.annotation.Bean}s. It can be placed on public {@code void} methods with at least
 * one of the following unique parameter types, in any order:
 *
 * <ul>
 *   <li>any non-primitive class extending {@link Object} (including {@link Object} itself) representing an assignable
 *       {@link EventHandlerDefinition#eventClass()}
 *   <li>a {@link java.util.Map java.util.Map&lt;String, ?&gt;} for the event meta-data
 *   <li>an {@link Event} for the raw event
 *   <li>any number of {@link org.springframework.beans.factory.annotation.Autowired} annotated parameters, resolving to
 *       single beans within the {@link org.springframework.context.ApplicationContext}
 * </ul>
 *
 * <strong>If placed on a method without any parameter representing {@link EventHandlerDefinition#eventClass()}, i.e.
 * only meta-data and/or raw event, {@link Object} is used as {@link EventHandlerDefinition#eventClass()}. Hence, any
 * such method will consume all available events.</strong>
 *
 * <p>Methods annotated using {@code this} may declare (and throw) any {@link Throwable}. As defined by
 * {@link EventHandlingProcessor#run()} all exceptions except {@link CqrsFrameworkException.NonTransientException} will
 * be retried automatically, if {@linkplain EventHandlingProperties configured} accordingly.
 *
 * <p>The method must be contained within an
 * {@link org.springframework.beans.factory.annotation.AnnotatedBeanDefinition}, for instance within
 * {@link org.springframework.stereotype.Component}s or {@link org.springframework.context.annotation.Configuration}s.
 *
 * <p>{@link EventHandling} annotated methods may optionally be annotated with
 * {@link org.springframework.transaction.annotation.Transactional} to enforce Spring transaction semantics for event
 * handlers. <b>Be aware, that these are applied per single event handling method call.</b>
 *
 * <p>Omitting {@link org.springframework.transaction.annotation.Transactional} annotation <strong>does not</strong>
 * imply, that no transaction will be active while executing the {@link EventHandler}. For instance, using
 * {@link JdbcProgressTracker} will automatically make the handler participate in the same transaction.
 *
 * <p>This annotation may also be used within meta annotations, typically to avoid duplication of the {@link #group()}
 * for multiple methods.
 *
 * @see EventHandler.ForObjectAndMetaDataAndRawEvent
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface EventHandling {

    /**
     * {@linkplain AliasFor Alias for} {@link #group()}.
     *
     * @return the group identifier
     */
    @AliasFor("group")
    String value() default "";

    /**
     * Specifies the {@link EventHandlerDefinition#group()} for the annotated method.
     *
     * @return the group identifier
     */
    @AliasFor("value")
    String group() default "";
}
