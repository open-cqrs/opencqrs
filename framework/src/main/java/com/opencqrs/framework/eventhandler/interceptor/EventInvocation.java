/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.interceptor;

import com.opencqrs.esdb.client.Event;

/**
 * Immutable entry data for {@link EventInterceptor#intercept}, wrapping the processing of a <strong>single raw
 * {@link Event}</strong> by one event-handling processor. The root interceptor is (re-)invoked once per (retry)
 * attempt.
 *
 * @param rawEvent the raw event being processed, before upcasting/conversion
 * @param group the processing group the owning processor belongs to
 * @param partition the partition the owning processor is responsible for
 * @param relevance the raw event's relevance with respect to the partition and upcast fan-out
 */
public record EventInvocation(Event rawEvent, String group, long partition, Relevance relevance) {}
