/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.metadata;

import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.interceptor.CommandInterceptor;
import com.opencqrs.framework.command.interceptor.CommandInvocation;
import com.opencqrs.framework.command.interceptor.CommandLifecycle;
import com.opencqrs.framework.command.interceptor.Publish;
import com.opencqrs.framework.interceptor.ValueContinuation;
import com.opencqrs.framework.persistence.CapturedEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Framework-provided {@link CommandInterceptor} that propagates configured
 * {@linkplain #MetaDataPropagatingCommandInterceptor(PropagationMode, Set) keys} of the command's meta-data onto every
 * event a command publishes, just before the atomic append. It applies to <strong>all</strong> commands and rewrites
 * the {@link Publish append request} at the {@code publish} stage.
 *
 * @see PropagationMode
 */
public class MetaDataPropagatingCommandInterceptor implements CommandInterceptor<Command> {

    private final PropagationMode propagationMode;
    private final Set<String> propagationKeys;

    /**
     * @param propagationMode how command meta-data keys are merged onto event meta-data
     * @param propagationKeys the command meta-data keys to propagate
     */
    public MetaDataPropagatingCommandInterceptor(PropagationMode propagationMode, Set<String> propagationKeys) {
        this.propagationMode = propagationMode;
        this.propagationKeys = propagationKeys;
    }

    @Override
    public Class<Command> commandClass() {
        return Command.class;
    }

    @Override
    public <R> @Nullable R intercept(
            CommandInvocation<Command> invocation, CommandLifecycle<R> lifecycle, ValueContinuation<R> continuation)
            throws Exception {
        lifecycle.publish((joinPoint, publishContinuation) -> {
            var request = publishContinuation.proceed();
            if (request == null) {
                return null;
            }
            Map<String, ?> propagationData = propagationData(invocation.metaData());
            if (propagationData.isEmpty()) {
                return request;
            }
            return request.withEvents(request.events().stream()
                    .map(event -> new CapturedEvent(
                            event.subject(),
                            event.event(),
                            propagate(event.metaData(), propagationData),
                            event.preconditions()))
                    .toList());
        });
        return continuation.proceed();
    }

    private Map<String, ?> propagationData(Map<String, ?> commandMetaData) {
        if (propagationMode == PropagationMode.NONE || propagationKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, ?> result = new HashMap<>(commandMetaData);
        result.keySet().retainAll(propagationKeys);
        return result;
    }

    private Map<String, ?> propagate(Map<String, ?> eventMetaData, Map<String, ?> propagationData) {
        Map<String, Object> result = new HashMap<>(eventMetaData);
        propagationData.forEach(
                (key, value) -> result.merge(key, value, (existing, incoming) -> switch (propagationMode) {
                    case KEEP_IF_PRESENT -> existing;
                    case OVERRIDE_IF_PRESENT -> incoming;
                    case NONE -> throw new IllegalStateException("propagation must not be invoked with mode NONE");
                }));
        return result;
    }
}
