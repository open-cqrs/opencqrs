/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.eventhandler.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.eventhandler.EventHandler;
import com.opencqrs.framework.eventhandler.EventHandlerDefinition;
import com.opencqrs.framework.interceptor.InterceptorContractViolation;
import com.opencqrs.framework.interceptor.Proceeded;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Mechanics of the event interceptor driver: root/handler composition, freeze, type gating, and exactly-once. */
public class EventInterceptorChainTest {

    private sealed interface DomainEvent permits BookAdded, BookRemoved {}

    private record BookAdded(String isbn) implements DomainEvent {}

    private record BookRemoved(String isbn) implements DomainEvent {}

    private static final EventHandlerDefinition<?> HANDLER_DEFINITION =
            new EventHandlerDefinition<>("group", Object.class, (EventHandler.ForObject<Object>) event -> {});

    private static final Event RAW_EVENT = new Event(
            "source",
            "subject",
            "type",
            Map.of(),
            "1.0",
            "event-id-1",
            Instant.EPOCH,
            "application/json",
            "hash",
            "predecessorHash");

    private final EventInvocation invocation = new EventInvocation(RAW_EVENT, "group", 0L, Relevance.YES);

    private static EventInterceptorChain chain(EventInterceptor... interceptors) {
        return new EventInterceptorChain(List.of(interceptors));
    }

    private static <E> EventHandlerInvocation<E> handlerInvocation(E event) {
        return new EventHandlerInvocation<>(HANDLER_DEFINITION, event, Map.of());
    }

    /**
     * Drives the full interior stage tree so a test can observe every hook and its nesting. The event side currently
     * has a single interior stage — {@code handler}, wrapping one converted-event handler invocation; extend this in
     * one place as future stages add interior seams.
     */
    private static EventInterior fullInterior(List<String> trace) {
        return c -> c.handler(handlerInvocation(new BookAdded("4711")), () -> trace.add("handler-work"));
    }

    @Test
    void emptyChainRunsInteriorAndFiresHandlerWork() throws Exception {
        var trace = new ArrayList<String>();

        Proceeded proceeded = chain().execute(invocation, fullInterior(trace));

        assertThat(proceeded).isNotNull();
        assertThat(trace).containsExactly("handler-work");
    }

    @Test
    void composesRootsAndHandlerOuterToInner() throws Exception {
        var trace = new ArrayList<String>();

        chain(recording("A", trace), recording("B", trace)).execute(invocation, fullInterior(trace));

        // every stage nests A-outer-B-inner: inbound A→B, outbound (inside-out) B→A
        assertThat(trace)
                .containsExactly(
                        "A-root-before",
                        "B-root-before",
                        "A-handler-before",
                        "B-handler-before",
                        "handler-work",
                        "B-handler-after",
                        "A-handler-after",
                        "B-root-after",
                        "A-root-after");
    }

    @Test
    void multipleHandlerRegistrationsNestInRegistrationOrder() throws Exception {
        var trace = new ArrayList<String>();
        EventInterceptor twoHandlers = (inv, lc, cont) -> {
            lc.handler((jp, c) -> {
                trace.add("first");
                return c.proceed();
            });
            lc.handler((jp, c) -> {
                trace.add("second");
                return c.proceed();
            });
            return cont.proceed();
        };

        chain(twoHandlers)
                .execute(invocation, c -> c.handler(handlerInvocation(new BookAdded("4711")), () -> trace.add("work")));

        assertThat(trace).containsExactly("first", "second", "work");
    }

    @Test
    void handlerHookFiresOncePerInvocationAndDeliversJoinPoint() throws Exception {
        var work = new AtomicInteger();
        var fired = new HashMap<String, EventHandlerInvocation<Object>>();
        EventInterceptor counting = (inv, lc, cont) -> {
            lc.handler((jp, c) -> {
                fired.put(((BookAdded) jp.event()).isbn(), jp);
                return c.proceed();
            });
            return cont.proceed();
        };

        chain(counting).execute(invocation, c -> {
            for (int i = 0; i < 2; i++) {
                c.handler(
                        new EventHandlerInvocation<>(HANDLER_DEFINITION, new BookAdded("isbn-" + i), Map.of("k", "v")),
                        work::incrementAndGet);
            }
        });

        assertThat(work).hasValue(2);
        assertThat(fired).containsOnlyKeys("isbn-0", "isbn-1").allSatisfy((isbn, jp) -> {
            assertThat(jp.definition()).isSameAs(HANDLER_DEFINITION);
            assertThat(jp.metaData().get("k")).isEqualTo("v");
        });
    }

    @Test
    void handlerAdviceIsGatedByConvertedEventType() throws Exception {
        var seen = new ArrayList<String>();
        EventInterceptor typed = (inv, lc, cont) -> {
            lc.handler(BookAdded.class, (jp, c) -> {
                seen.add("added:" + jp.event().isbn());
                return c.proceed();
            });
            lc.handler(BookRemoved.class, (jp, c) -> {
                seen.add("removed:" + jp.event().isbn());
                return c.proceed();
            });
            return cont.proceed();
        };

        chain(typed).execute(invocation, c -> {
            c.handler(handlerInvocation(new BookAdded("4711")), () -> {});
            c.handler(handlerInvocation(new BookRemoved("4711")), () -> {});
        });

        // each typed advice fires only for its assignable converted event
        assertThat(seen).containsExactly("added:4711", "removed:4711");
    }

    @Test
    void allEventsConvenienceAdviceFiresForEveryConvertedEvent() throws Exception {
        var seen = new AtomicInteger();
        EventInterceptor allEvents = (inv, lc, cont) -> {
            lc.handler((jp, c) -> {
                seen.incrementAndGet();
                return c.proceed();
            });
            return cont.proceed();
        };

        chain(allEvents).execute(invocation, c -> {
            c.handler(handlerInvocation(new BookAdded("4711")), () -> {});
            c.handler(handlerInvocation(new BookRemoved("4711")), () -> {});
        });

        assertThat(seen).hasValue(2);
    }

    @Test
    void registeringAfterProceedThrows() {
        EventInterceptor lateRegistration = (inv, lc, cont) -> {
            Proceeded proceeded = cont.proceed();
            lc.handler((jp, c) -> c.proceed()); // illegal: lifecycle already frozen
            return proceeded;
        };

        assertThatThrownBy(() -> chain(lateRegistration).execute(invocation, c -> {}))
                .isInstanceOf(InterceptorContractViolation.class)
                .hasMessageContaining("frozen");
    }

    @Test
    void throwingHandlerAdviceAbortsAndSkipsWork() {
        var work = new AtomicInteger();
        var boom = new RuntimeException("boom");
        EventInterceptor denying = (inv, lc, cont) -> {
            lc.handler((jp, c) -> {
                throw boom;
            });
            return cont.proceed();
        };

        assertThatThrownBy(() -> chain(denying)
                        .execute(
                                invocation,
                                c -> c.handler(handlerInvocation(new BookAdded("4711")), work::incrementAndGet)))
                .isSameAs(boom);
        assertThat(work).hasValue(0);
    }

    @Test
    void rootProceedingTwiceIsRejected() {
        EventInterceptor doubleProceed = (inv, lc, cont) -> {
            cont.proceed();
            return cont.proceed(); // illegal: observer continuation is exactly-once
        };

        assertThatThrownBy(() -> chain(doubleProceed).execute(invocation, c -> {}))
                .isInstanceOf(InterceptorContractViolation.class)
                .hasMessageContaining("already proceeded");
    }

    @Test
    void handlerProceedingTwiceIsRejected() {
        EventInterceptor doubleProceed = (inv, lc, cont) -> {
            lc.handler((jp, c) -> {
                c.proceed();
                return c.proceed(); // illegal: observer continuation is exactly-once
            });
            return cont.proceed();
        };

        assertThatThrownBy(() -> chain(doubleProceed)
                        .execute(invocation, c -> c.handler(handlerInvocation(new BookAdded("4711")), () -> {})))
                .isInstanceOf(InterceptorContractViolation.class)
                .hasMessageContaining("already proceeded");
    }

    private static EventInterceptor recording(String name, List<String> trace) {
        return (inv, lc, cont) -> {
            trace.add(name + "-root-before");
            lc.handler((jp, c) -> {
                trace.add(name + "-handler-before");
                Proceeded p = c.proceed();
                trace.add(name + "-handler-after");
                return p;
            });
            Proceeded result = cont.proceed();
            trace.add(name + "-root-after");
            return result;
        };
    }
}
