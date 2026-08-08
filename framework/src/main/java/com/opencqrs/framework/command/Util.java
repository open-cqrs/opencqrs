/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.command.interceptor.CommandInterceptorChain;
import com.opencqrs.framework.command.interceptor.PublishedEventInvocation;
import com.opencqrs.framework.command.interceptor.SourcedEventInvocation;
import com.opencqrs.framework.interceptor.StageWork;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

class Util {

    /**
     * Applies {@code event} to every {@linkplain StateRebuildingHandlerDefinition#eventClass() matching} state
     * rebuilding handler, folding the result into {@code state}. Each individual handler application is wrapped by the
     * command interceptor chain &mdash; as a {@linkplain CommandInterceptorChain#sourcedEvent sourcedEvent} stage when
     * a {@code rawEvent} is present (a replayed, persisted event), or a
     * {@linkplain CommandInterceptorChain#publishedEvent publishedEvent} stage otherwise (an event just emitted by the
     * command handler). An {@linkplain CommandInterceptorChain empty chain} applies with no advice, so this doubles as
     * the plain state-fold.
     *
     * @return {@code true} if at least one handler matched
     * @throws Exception any exception propagated from an interceptor
     */
    static <I, E> boolean applyUsingHandlers(
            List<StateRebuildingHandlerDefinition<I, E>> stateRebuildingHandlerDefinitions,
            AtomicReference<@Nullable I> state,
            String subject,
            E event,
            Map<String, ?> metaData,
            @Nullable Event rawEvent,
            CommandInterceptorChain<?> chain)
            throws Exception {
        boolean wasApplied = false;
        for (StateRebuildingHandlerDefinition<I, E> srhd : stateRebuildingHandlerDefinitions) {
            if (srhd.eventClass().isAssignableFrom(event.getClass())) {
                I inputInstance = state.get();
                StageWork apply = () -> state.updateAndGet(i -> invokeOn(srhd, i, event, metaData, subject, rawEvent));
                if (rawEvent != null) {
                    chain.sourcedEvent(
                            new SourcedEventInvocation(srhd, inputInstance, event, metaData, subject, rawEvent), apply);
                } else {
                    chain.publishedEvent(
                            new PublishedEventInvocation(srhd, inputInstance, event, metaData, subject), apply);
                }
                wasApplied = true;
            }
        }
        return wasApplied;
    }

    private static <I, E> @Nullable I invokeOn(
            StateRebuildingHandlerDefinition<I, E> srhd,
            @Nullable I instance,
            E event,
            Map<String, ?> metaData,
            String subject,
            @Nullable Event rawEvent) {
        return switch (srhd.handler()) {
            case StateRebuildingHandler.FromObject<I, E> handler -> handler.on(instance, event);
            case StateRebuildingHandler.FromObjectAndRawEvent<I, E> handler -> handler.on(instance, event, rawEvent);
            case StateRebuildingHandler.FromObjectAndMetaData<I, E> handler -> handler.on(instance, event, metaData);
            case StateRebuildingHandler.FromObjectAndMetaDataAndSubject<I, E> handler ->
                handler.on(instance, event, metaData, subject);
            case StateRebuildingHandler.FromObjectAndMetaDataAndSubjectAndRawEvent<I, E> handler ->
                handler.on(instance, event, metaData, subject, rawEvent);
        };
    }
}
