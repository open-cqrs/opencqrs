/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import com.opencqrs.framework.command.SourcingMode;

/**
 * {@link CommandInterceptor} join point wrapping the whole state-rebuilding stage &mdash; the store read plus replaying
 * every sourced event onto the matching state-rebuilding handlers. Fires <strong>once</strong> per command, before the
 * handler, even under {@link SourcingMode#NONE} (in which case nothing is read). A plain-noun join point: it wraps a
 * framework stage, not a single domain-handler call.
 *
 * @param instanceClass the {@linkplain com.opencqrs.framework.command.CommandHandlerDefinition#instanceClass() instance
 *     type} being rebuilt
 * @param sourcingMode the effective sourcing mode
 */
public record Sourcing(Class<?> instanceClass, SourcingMode sourcingMode) {}
