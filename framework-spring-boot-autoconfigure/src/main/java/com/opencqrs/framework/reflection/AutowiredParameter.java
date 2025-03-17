/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.reflection;

import java.lang.reflect.Parameter;

/**
 * Represents an {@link org.springframework.beans.factory.annotation.Autowired} parameter within an annotated handler
 * definition.
 *
 * @param parameter the parameter identified as
 *     {@linkplain org.springframework.beans.factory.annotation.ParameterResolutionDelegate#isAutowirable(Parameter,
 *     int) autowirable}
 * @param index the position within its {@link java.lang.reflect.Method}
 * @param containingClass the containing class
 */
public record AutowiredParameter(Parameter parameter, int index, Class<?> containingClass) {}
