/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.persistence;

import com.opencqrs.esdb.client.Event;

/**
 * Encapsulates a configurable {@link Event#source()} for event publication.
 *
 * @param source the source identifier to be used when publishing events
 */
public record EventSource(String source) {}
