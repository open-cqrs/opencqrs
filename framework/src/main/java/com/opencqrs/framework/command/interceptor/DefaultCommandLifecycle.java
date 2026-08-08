/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import com.opencqrs.framework.interceptor.RegistrationGuard;
import com.opencqrs.framework.interceptor.StageObserver;
import com.opencqrs.framework.interceptor.StageTransformer;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-command-invocation {@link CommandLifecycle} implementation. A single instance is shared across all applicable
 * interceptors so that registrations accumulate in (chain-order, then call-order); it {@linkplain #freeze() freezes}
 * once interior execution begins. Package-private &mdash; driven by {@link CommandInterceptorChain}.
 *
 * @param <R> the command result type
 */
final class DefaultCommandLifecycle<R> implements CommandLifecycle<R> {

    private final RegistrationGuard guard = new RegistrationGuard();

    final List<StageObserver<Sourcing>> sourcing = new ArrayList<>();
    final List<StageObserver<SourcedEventInvocation>> sourcedEvent = new ArrayList<>();
    final List<StageObserver<PublishedEventInvocation>> publishedEvent = new ArrayList<>();
    final List<StageTransformer<CommandHandlerInvocation, R>> handler = new ArrayList<>();
    final List<StageTransformer<Publish, Publish>> publish = new ArrayList<>();

    @Override
    public void sourcing(StageObserver<Sourcing> advice) {
        guard.ensureOpen();
        sourcing.add(advice);
    }

    @Override
    public void sourcedEvent(StageObserver<SourcedEventInvocation> advice) {
        guard.ensureOpen();
        sourcedEvent.add(advice);
    }

    @Override
    public void handler(StageTransformer<CommandHandlerInvocation, R> advice) {
        guard.ensureOpen();
        handler.add(advice);
    }

    @Override
    public void publishedEvent(StageObserver<PublishedEventInvocation> advice) {
        guard.ensureOpen();
        publishedEvent.add(advice);
    }

    @Override
    public void publish(StageTransformer<Publish, Publish> advice) {
        guard.ensureOpen();
        publish.add(advice);
    }

    void freeze() {
        guard.freeze();
    }
}
