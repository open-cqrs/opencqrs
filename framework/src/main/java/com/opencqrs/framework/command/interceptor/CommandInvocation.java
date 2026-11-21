/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import com.opencqrs.framework.command.Command;
import java.util.Map;

/**
 * Immutable entry data for a command execution, handed to every {@link CommandInterceptor}. It is the frozen entry
 * envelope for the whole unit of work: interior join points carry only their phase-new data, and reach the command /
 * meta-data from this carrier via the enclosing interceptor's closure.
 *
 * @param command the command being executed
 * @param metaData the meta-data passed to {@link com.opencqrs.framework.command.CommandRouter#send(Command, Map)}
 * @param <C> the command type
 */
public record CommandInvocation<C extends Command>(C command, Map<String, ?> metaData) {}
