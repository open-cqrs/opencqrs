/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Mechanics suite for the shared {@link InterceptorChains} composition engine. */
class InterceptorChainsTest {

    /** Marker join point. */
    private record Jp(String name) {}

    private final Jp jp = new Jp("jp");

    @Nested
    class ObserverChains {

        @Test
        void runsAdvicesOuterToInnerAroundWork() throws Exception {
            var trace = new ArrayList<String>();
            StageObserver<Jp> outer = (j, c) -> {
                trace.add("outer-before");
                var p = c.proceed();
                trace.add("outer-after");
                return p;
            };
            StageObserver<Jp> inner = (j, c) -> {
                trace.add("inner-before");
                var p = c.proceed();
                trace.add("inner-after");
                return p;
            };

            Continuation chain = InterceptorChains.observerChain(List.of(outer, inner), jp, () -> trace.add("work"));
            Proceeded proceeded = chain.proceed();

            assertThat(proceeded).isNotNull();
            assertThat(trace).containsExactly("outer-before", "inner-before", "work", "inner-after", "outer-after");
        }

        @Test
        void emptyAdvicesRunsWorkAndReturnsProceeded() throws Exception {
            var count = new AtomicInteger();

            Continuation chain =
                    InterceptorChains.observerChain(List.<StageObserver<Jp>>of(), jp, count::incrementAndGet);
            Proceeded proceeded = chain.proceed();

            assertThat(proceeded).isNotNull();
            assertThat(count).hasValue(1);
        }

        @Test
        void passesTheSameJoinPointToEveryAdvice() throws Exception {
            var seen = new ArrayList<Jp>();
            StageObserver<Jp> a = (j, c) -> {
                seen.add(j);
                return c.proceed();
            };
            StageObserver<Jp> b = (j, c) -> {
                seen.add(j);
                return c.proceed();
            };

            InterceptorChains.observerChain(List.of(a, b), jp, () -> {}).proceed();

            assertThat(seen).containsExactly(jp, jp);
        }

        @Test
        void duplicateProceedPreventedThrowingIllegalStateException() {
            var workRuns = new AtomicInteger();
            StageObserver<Jp> doubleProceed = (j, c) -> {
                c.proceed();
                return c.proceed(); // illegal second proceed
            };

            Continuation chain = InterceptorChains.observerChain(List.of(doubleProceed), jp, workRuns::incrementAndGet);

            assertThatThrownBy(chain::proceed)
                    .isInstanceOf(InterceptorContractViolation.class)
                    .hasMessageContaining("already proceeded");
            assertThat(workRuns).as("work must run only once").hasValue(1);
        }

        @Test
        void terminalWorkRunsExactlyOnceAcrossNestedObservers() throws Exception {
            var workRuns = new AtomicInteger();
            StageObserver<Jp> outer = (j, c) -> c.proceed();
            StageObserver<Jp> inner = (j, c) -> c.proceed();

            InterceptorChains.observerChain(List.of(outer, inner), jp, workRuns::incrementAndGet)
                    .proceed();

            assertThat(workRuns).hasValue(1);
        }

        @Test
        void exceptionFromWorkUnwindsThroughAdviceFinally() {
            var trace = new ArrayList<String>();
            StageObserver<Jp> outer = (j, c) -> {
                try {
                    return c.proceed();
                } finally {
                    trace.add("outer-finally");
                }
            };
            var boom = new RuntimeException("boom");

            Continuation chain = InterceptorChains.observerChain(List.of(outer), jp, () -> {
                throw boom;
            });

            assertThatThrownBy(chain::proceed).isSameAs(boom);
            assertThat(trace).containsExactly("outer-finally");
        }

        @Test
        void throwingAdviceThatNeverProceedsSkipsWork() {
            var workRuns = new AtomicInteger();
            var denied = new RuntimeException("denied");
            StageObserver<Jp> denying = (j, c) -> {
                throw denied;
            };

            Continuation chain = InterceptorChains.observerChain(List.of(denying), jp, workRuns::incrementAndGet);

            assertThatThrownBy(chain::proceed).isSameAs(denied);
            assertThat(workRuns).hasValue(0);
        }
    }

    @Nested
    class TransformerChains {

        @Test
        void runsAdvicesOuterToInnerAndThreadsResult() throws Exception {
            var trace = new ArrayList<String>();
            StageTransformer<Jp, String> outer = (j, c) -> {
                trace.add("outer-before");
                String r = c.proceed();
                trace.add("outer-after");
                return r + "-outer";
            };
            StageTransformer<Jp, String> inner = (j, c) -> {
                trace.add("inner-before");
                String r = c.proceed();
                trace.add("inner-after");
                return r + "-inner";
            };

            ValueContinuation<String> chain = InterceptorChains.transformerChain(List.of(outer, inner), jp, () -> {
                trace.add("work");
                return "R";
            });
            String result = chain.proceed();

            assertThat(result).isEqualTo("R-inner-outer");
            assertThat(trace).containsExactly("outer-before", "inner-before", "work", "inner-after", "outer-after");
        }

        @Test
        void shortCircuitSubstitutesValueWithoutRunningWork() throws Exception {
            var workRuns = new AtomicInteger();
            StageTransformer<Jp, String> shortCircuit = (j, c) -> "cached";

            String result = InterceptorChains.transformerChain(List.of(shortCircuit), jp, () -> {
                        workRuns.incrementAndGet();
                        return "R";
                    })
                    .proceed();

            assertThat(result).isEqualTo("cached");
            assertThat(workRuns).hasValue(0);
        }

        @Test
        void mayProceedMoreThanOnce() throws Exception {
            var workRuns = new AtomicInteger();
            StageTransformer<Jp, Integer> retry = (j, c) -> {
                c.proceed();
                return c.proceed();
            };

            Integer result = InterceptorChains.transformerChain(List.of(retry), jp, workRuns::incrementAndGet)
                    .proceed();

            assertThat(workRuns).hasValue(2);
            assertThat(result).isEqualTo(2);
        }

        @Test
        void emptyAdvicesReturnsTerminalValue() throws Exception {
            String result = InterceptorChains.transformerChain(List.<StageTransformer<Jp, String>>of(), jp, () -> "R")
                    .proceed();

            assertThat(result).isEqualTo("R");
        }

        @Test
        void allowsNullResult() throws Exception {
            String result = InterceptorChains.transformerChain(
                            List.<StageTransformer<Jp, String>>of((j, c) -> c.proceed()), jp, () -> null)
                    .proceed();

            assertThat(result).isNull();
        }

        @Test
        void exceptionFromWorkPropagatesThroughAdvice() {
            var boom = new RuntimeException("boom");
            StageTransformer<Jp, String> passthrough = (j, c) -> c.proceed();

            ValueContinuation<String> chain = InterceptorChains.transformerChain(List.of(passthrough), jp, () -> {
                throw boom;
            });

            assertThatThrownBy(chain::proceed).isSameAs(boom);
        }
    }
}
