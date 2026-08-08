/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.interceptor;

import com.opencqrs.framework.interceptor.InterceptorChains;
import com.opencqrs.framework.interceptor.Proceeded;
import com.opencqrs.framework.interceptor.StageObserver;
import com.opencqrs.framework.interceptor.StageWork;
import java.util.ArrayList;
import java.util.List;

/**
 * Framework-internal driver composing the interceptor around-tree for a single raw event and exposing the interior
 * stage seam to {@link com.opencqrs.framework.eventhandler.EventHandlingProcessor}.
 *
 * <p>{@link #execute(EventInvocation, EventInterior)} composes the given interceptors (index {@code 0} = outermost) as
 * nested observer roots; the innermost root {@linkplain DefaultEventLifecycle#freeze() freezes} the lifecycle and runs
 * the supplied {@link EventInterior}. The interior then wraps each matching handler invocation via {@link #handler},
 * whose composed advice comes from the interceptors' lifecycle registrations.
 */
public final class EventInterceptorChain {

    private final List<EventInterceptor> interceptors;

    private final DefaultEventLifecycle lifecycle = new DefaultEventLifecycle();

    /**
     * @param interceptors the applicable interceptors (already filtered for this event's {@link Relevance} against each
     *     interceptor's {@link Delivery} level) and ordered outermost-first
     */
    public EventInterceptorChain(List<EventInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    /**
     * Composes the interceptor observer roots around {@code interior} and runs the whole chain.
     *
     * @param invocation the raw-event entry data
     * @param interior the framework core's event-processing body
     * @return the {@link Proceeded} token proving the whole chain proceeded
     * @throws Exception any exception propagated from an interceptor or a wrapped stage
     */
    public Proceeded execute(EventInvocation invocation, EventInterior interior) throws Exception {
        List<StageObserver<EventInvocation>> roots = new ArrayList<>(interceptors.size());
        for (EventInterceptor interceptor : interceptors) {
            roots.add((joinPoint, continuation) -> interceptor.intercept(joinPoint, lifecycle, continuation));
        }
        StageWork terminal = () -> {
            lifecycle.freeze();
            interior.execute(this);
        };
        return InterceptorChains.observerChain(roots, invocation, terminal).proceed();
    }

    /**
     * Wraps a single event-handler invocation with the registered {@code handler} advice applicable to the converted
     * event's type.
     *
     * @param joinPoint the handler join point
     * @param work the actual event-handler invocation
     * @param <E> the converted event type
     * @throws Exception any exception propagated from an advice or the handler
     */
    public <E> void handler(EventHandlerInvocation<E> joinPoint, StageWork work) throws Exception {
        InterceptorChains.observerChain(lifecycle.handlerAdvicesFor(joinPoint.event()), joinPoint, work)
                .proceed();
    }
}
