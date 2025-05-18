/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.example.projection.statistics;

import com.opencqrs.framework.eventhandler.EventHandling;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@EventHandling("statistics")
public @interface StatisticsHandling {}
