/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import java.lang.annotation.*;
import org.springframework.context.annotation.Configuration;

/**
 * Annotation to be used for {@link Configuration}s containing {@link CommandHandling} annotated methods,
 * {@link CommandHandlerDefinition} {@link org.springframework.context.annotation.Bean}s, {@link StateRebuilding}
 * annotated methods, and {@link StateRebuildingHandlerDefinition} {@link org.springframework.context.annotation.Bean}s
 * in order to be able to test them using {@link com.opencqrs.framework.command.CommandHandlingTest}.
 *
 * <p>{@link com.opencqrs.framework.command.interceptor.CommandInterceptor}
 * {@link org.springframework.context.annotation.Bean}s may be declared within as well, in which case they are loaded
 * into the {@link com.opencqrs.framework.command.CommandHandlingTest} slice together with the handlers and form the
 * base interceptor set of the auto-wired fixtures.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Configuration
public @interface CommandHandlerConfiguration {}
