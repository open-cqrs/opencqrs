/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.interceptor;

import com.opencqrs.framework.interceptor.RegistrationGuard;
import com.opencqrs.framework.interceptor.StageObserver;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-raw-event {@link EventLifecycle} implementation. A single instance is shared across all applicable interceptors
 * so that registrations accumulate in (chain-order, then call-order); it {@linkplain #freeze() freezes} once interior
 * execution begins. Package-private &mdash; driven by {@link EventInterceptorChain}.
 */
final class DefaultEventLifecycle implements EventLifecycle {

    private final RegistrationGuard guard = new RegistrationGuard();

    private record Registration(Class<?> eventClass, StageObserver<?> advice) {}

    private final List<Registration> handlers = new ArrayList<>();

    @Override
    public <E> void handler(Class<E> eventClass, StageObserver<EventHandlerInvocation<E>> advice) {
        guard.ensureOpen();
        handlers.add(new Registration(eventClass, advice));
    }

    /**
     * Selects the registered {@code handler} advice applicable to {@code event}, i.e. those whose registered event
     * class {@linkplain Class#isInstance(Object) is assignable from} the converted event's runtime type, preserving
     * registration order (outermost first).
     */
    @SuppressWarnings("unchecked")
    <E> List<StageObserver<EventHandlerInvocation<E>>> handlerAdvicesFor(E event) {
        List<StageObserver<EventHandlerInvocation<E>>> applicable = new ArrayList<>();
        for (Registration registration : handlers) {
            if (registration.eventClass().isInstance(event)) {
                applicable.add((StageObserver<EventHandlerInvocation<E>>) registration.advice());
            }
        }
        return applicable;
    }

    void freeze() {
        guard.freeze();
    }
}
