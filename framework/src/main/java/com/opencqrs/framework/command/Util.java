/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.tracing.TracingContextSpanBuilder;
import com.opencqrs.framework.tracing.TracingSpanInformationSource;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

class Util {

    static <I, E> boolean applyUsingHandlers(
            List<StateRebuildingHandlerDefinition<I, E>> stateRebuildingHandlerDefinitions,
            AtomicReference<@Nullable I> state,
            String subject,
            E event,
            Map<String, ?> metaData,
            @Nullable Event rawEvent,
            TracingContextSpanBuilder spanBuilder) {
        var wasApplied = new AtomicReference<>(false);
        stateRebuildingHandlerDefinitions.stream()
                .filter(srhd -> srhd.eventClass().isAssignableFrom(event.getClass()))
                .forEach(srhd -> {
                    state.updateAndGet(i -> Optional.ofNullable(spanBuilder.executeSupplierWithNewSpan(
                                    createStateRebuildingSpanInformation(i, rawEvent, srhd),
                                    () -> switch (srhd.handler()) {
                                        case StateRebuildingHandler.FromObject<I, E> handler -> handler.on(i, event);
                                        case StateRebuildingHandler.FromObjectAndRawEvent<I, E> handler ->
                                            handler.on(i, event, rawEvent);
                                        case StateRebuildingHandler.FromObjectAndMetaData<I, E> handler ->
                                            handler.on(i, event, metaData);
                                        case StateRebuildingHandler.FromObjectAndMetaDataAndSubject<I, E> handler ->
                                            handler.on(i, event, metaData, subject);
                                        case StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<I, E>
                                                handler -> handler.on(i, event, metaData, subject, rawEvent);
                                    }))
                            .orElseThrow(() -> new CqrsFrameworkException.NonTransientException(
                                    "state rebuilding handler returned 'null' instance for event: "
                                            + event.getClass().getName())));
                    wasApplied.set(true);
                });

        return wasApplied.get();
    }

    private static <I> Map<String, String> createStateRebuildingSpanInformation(
            @Nullable I instance, @Nullable Event sourcedEvent, StateRebuildingHandlerDefinition<?, ?> srhd) {

        var srhdInfo = Map.ofEntries(
                Map.entry("span.name", "state " + ((sourcedEvent != null) ? "sourcing" : "update")),
                Map.entry("event.class", srhd.eventClass().getName()));

        Map<String, String> rawEventInfo = (sourcedEvent != null)
                ? Map.ofEntries(
                        Map.entry("event.id", sourcedEvent.id()),
                        Map.entry("event.type", sourcedEvent.type()),
                        Map.entry("event.subject", sourcedEvent.subject()),
                        Map.entry("event.timestamp", sourcedEvent.time().toString()),
                        Map.entry("event.source", sourcedEvent.source()))
                : Collections.emptyMap();

        Map<String, String> instanceInfo = (instance != null)
                ? Map.ofEntries(Map.entry("instance.class", instance.getClass().getName()))
                : Collections.emptyMap();

        var result = new HashMap<>(srhdInfo);
        result.putAll(rawEventInfo);
        result.putAll(instanceInfo);

        switch (srhd.handler()) {
            case TracingSpanInformationSource srhInfoSource:
                StringBuilder spanNameBuilder = new StringBuilder();
                spanNameBuilder.append(result.get("span.name"));
                spanNameBuilder.append(" ");
                spanNameBuilder.append(srhInfoSource.getHandlingClassSimpleName());
                spanNameBuilder.append(".");
                spanNameBuilder.append(srhInfoSource.getHandlingMethodSignature());

                result.put("span.name", spanNameBuilder.toString());
                result.put("handling.class", srhInfoSource.getHandlingClassFullName());
                result.put("handling.method", srhInfoSource.getHandlingMethodSignature());
            default:
                return result;
        }
    }
}
