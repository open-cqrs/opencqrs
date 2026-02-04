/* Copyright (C) 2025 OpenCQRS and contributors */
package com.opencqrs.framework.command;

import com.opencqrs.esdb.client.Precondition;
import com.opencqrs.framework.persistence.CapturedEvent;
import com.opencqrs.framework.persistence.EventCapturer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link CommandEventPublisher} used by {@link CommandRouter} to apply events to the
 * {@link StateRebuildingHandler}s relevant for the {@link Command} being executed.
 *
 * @param <I> the instance as defined by the {@link CommandHandlerDefinition} being executed
 */
public class CommandEventCapturer<I> extends EventCapturer implements CommandEventPublisher<I> {

    private final List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions;
    private final String subject;

    final AtomicReference<I> previousInstance;

    public CommandEventCapturer(
            I initialInstance,
            String subject,
            List<StateRebuildingHandlerDefinition<I, Object>> stateRebuildingHandlerDefinitions) {
        this.stateRebuildingHandlerDefinitions = stateRebuildingHandlerDefinitions;
        this.previousInstance = new AtomicReference<>(initialInstance);
        this.subject = subject;
    }

    @Override
    public <E> I publish(E event, Map<String, ?> metaData, List<Precondition> preconditions) {
        getEvents().add(new CapturedEvent(subject, event, metaData, preconditions));

        Util.applyUsingHandlers(stateRebuildingHandlerDefinitions, previousInstance, subject, event, metaData, null);

        return previousInstance.get();
    }

    @Override
    public <E> I publishRelative(
            String subjectSuffix, E event, Map<String, ?> metaData, List<Precondition> preconditions) {
        String s = subject + "/" + subjectSuffix;
        getEvents().add(new CapturedEvent(s, event, metaData, preconditions));

        Util.applyUsingHandlers(stateRebuildingHandlerDefinitions, previousInstance, s, event, metaData, null);

        return previousInstance.get();
    }
}
