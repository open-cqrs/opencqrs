/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;
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
            @Nullable Event rawEvent) {
        var wasApplied = new AtomicReference<>(false);
        stateRebuildingHandlerDefinitions.stream()
                .filter(srhd -> srhd.eventClass().isAssignableFrom(event.getClass()))
                .forEach(srhd -> {
                    state.updateAndGet(i -> switch (srhd.handler()) {
                        case StateRebuildingHandler.FromObject<I, E> handler -> handler.on(i, event);
                        case StateRebuildingHandler.FromObjectAndRawEvent<I, E> handler ->
                            handler.on(i, event, rawEvent);
                        case StateRebuildingHandler.FromObjectAndMetaData<I, E> handler ->
                            handler.on(i, event, metaData);
                        case StateRebuildingHandler.FromObjectAndMetaDataAndSubject<I, E> handler ->
                            handler.on(i, event, metaData, subject);
                        case StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<I, E> handler ->
                            handler.on(i, event, metaData, subject, rawEvent);
                    });
                    wasApplied.set(true);
                });

        return wasApplied.get();
    }
}
