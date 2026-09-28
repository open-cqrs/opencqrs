/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Framework-internal composition engine reused by the command- and event-handling interceptor frameworks. Given an
 * ordered list of advices (index {@code 0} = outermost) and the terminal stage they wrap, it composes the nested
 * around-tree.
 *
 * <p>For {@linkplain #observerChain(List, Object, StageWork) observer chains} every level &mdash; including the
 * terminal &mdash; is guarded to run <strong>exactly once</strong>: a second {@link Continuation#proceed()} throws
 * {@link InterceptorContractViolation} before re-running the wrapped stage. For {@linkplain #transformerChain(List,
 * Object, ValueContinuation) transformer chains} no such guard applies: a transformer may skip its continuation
 * (short-circuit) or invoke it more than once (retry).
 *
 * <p>Exceptions propagate naturally through the composed lambdas, so each advice's {@code try/catch/finally} observes
 * failures from the stages it wraps without any explicit forwarding.
 */
public final class InterceptorChains {

    private InterceptorChains() {}

    /**
     * Composes {@code advices} (outermost first) around {@code work}, minting the single {@link Proceeded} token at the
     * innermost point.
     *
     * @param advices the observer advices, index {@code 0} outermost; may be empty
     * @param joinPoint the join point passed to every advice
     * @param work the framework stage to run at the center of the chain
     * @param <J> the join-point type
     * @return the outermost {@link Continuation}; calling {@link Continuation#proceed()} runs the whole chain
     */
    public static <J> Continuation observerChain(
            List<? extends StageObserver<J>> advices, J joinPoint, StageWork work) {
        Continuation chain = onceGuarded(() -> {
            work.execute();
            return Proceeded.INSTANCE;
        });
        for (int i = advices.size() - 1; i >= 0; i--) {
            StageObserver<J> advice = advices.get(i);
            Continuation inner = chain;
            chain = onceGuarded(() -> advice.around(joinPoint, inner));
        }
        return chain;
    }

    /**
     * Composes {@code advices} (outermost first) around {@code terminal}, which yields the real stage result.
     *
     * @param advices the transformer advices, index {@code 0} outermost; may be empty
     * @param joinPoint the join point passed to every advice
     * @param terminal the framework stage producing the real result
     * @param <J> the join-point type
     * @param <V> the result type
     * @return the outermost {@link ValueContinuation}; calling {@link ValueContinuation#proceed()} runs the whole chain
     */
    public static <J, V> ValueContinuation<V> transformerChain(
            List<? extends StageTransformer<J, V>> advices, J joinPoint, ValueContinuation<V> terminal) {
        ValueContinuation<V> chain = terminal;
        for (int i = advices.size() - 1; i >= 0; i--) {
            StageTransformer<J, V> advice = advices.get(i);
            ValueContinuation<V> inner = chain;
            chain = () -> advice.around(joinPoint, inner);
        }
        return chain;
    }

    private static Continuation onceGuarded(Continuation delegate) {
        AtomicBoolean proceeded = new AtomicBoolean(false);
        return () -> {
            if (!proceeded.compareAndSet(false, true)) {
                throw new InterceptorContractViolation(
                        "observer continuation already proceeded; an observer must call proceed() exactly once");
            }
            return delegate.proceed();
        };
    }
}
