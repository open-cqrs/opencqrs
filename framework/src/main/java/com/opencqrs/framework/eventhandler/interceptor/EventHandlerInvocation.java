/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.interceptor;

import com.opencqrs.framework.eventhandler.EventHandlerDefinition;
import java.util.Map;

/**
 * {@link EventInterceptor} join point wrapping a <strong>single</strong> event-handler invocation &mdash; one
 * {@link com.opencqrs.framework.eventhandler.EventHandler} being invoked for one converted event. Fires once per
 * matching {@link EventHandlerDefinition}, nested inside the root {@link EventInterceptor#intercept intercept}.
 *
 * @param definition the event-handler definition being invoked
 * @param event the deserialized (converted) event being handled
 * @param metaData the event's meta-data, may be empty
 * @param <E> the converted event type
 */
public record EventHandlerInvocation<E>(EventHandlerDefinition<?> definition, E event, Map<String, ?> metaData) {}
