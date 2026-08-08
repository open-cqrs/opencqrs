/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

/**
 * The framework's actual work at the innermost point of an {@linkplain StageObserver observer} chain &mdash; the store
 * read, the state-apply, the publish, or the event-handler invocation. Supplied by framework core code so that
 * {@link Proceeded} minting stays confined to this package.
 *
 * @see InterceptorChains#observerChain(java.util.List, Object, StageWork)
 */
@FunctionalInterface
public interface StageWork {

    /**
     * Executes the wrapped framework stage.
     *
     * @throws Exception any exception raised by the stage
     */
    void execute() throws Exception;
}
