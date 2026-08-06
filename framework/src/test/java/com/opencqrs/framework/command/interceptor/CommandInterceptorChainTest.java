/* Copyright (C) 2026 OpenCQRS and contributors */
package com.opencqrs.framework.command.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencqrs.esdb.client.Event;
import com.opencqrs.framework.CqrsFrameworkException;
import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandHandler;
import com.opencqrs.framework.command.CommandHandlerDefinition;
import com.opencqrs.framework.command.SourcingMode;
import com.opencqrs.framework.command.StateRebuildingHandler;
import com.opencqrs.framework.command.StateRebuildingHandlerDefinition;
import com.opencqrs.framework.interceptor.InterceptorContractViolation;
import com.opencqrs.framework.interceptor.Proceeded;
import com.opencqrs.framework.interceptor.ValueContinuation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Mechanics of the command interceptor driver: root/stage composition, freeze, and result substitution. */
class CommandInterceptorChainTest {

    private record TestCommand(String subject) implements Command {
        @Override
        public String getSubject() {
            return subject;
        }
    }

    private static final CommandHandlerDefinition<?, ?, ?> HANDLER_DEFINITION = new CommandHandlerDefinition<>(
            String.class,
            TestCommand.class,
            (CommandHandler.ForCommand<String, TestCommand, Object>) (command, publisher) -> null,
            SourcingMode.NONE);

    private static final StateRebuildingHandlerDefinition<?, ?> SRH_DEFINITION = new StateRebuildingHandlerDefinition<>(
            String.class, Object.class, (StateRebuildingHandler.FromObject<String, Object>)
                    (instance, event) -> instance);

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

    private final CommandInvocation<TestCommand> invocation =
            new CommandInvocation<>(new TestCommand("subject"), Map.of());

    @SuppressWarnings("rawtypes")
    private static CommandInterceptorChain<String> chain(CommandInterceptor... interceptors) {
        return new CommandInterceptorChain<>(List.of(interceptors));
    }

    /**
     * Drives the full stage tree so a test can observe every hook and its nesting: {@code sourcing} (wrapping a
     * {@code sourcedEvent} apply) → {@code handler} (wrapping a {@code publishedEvent} apply, returning "R") →
     * {@code publish}.
     */
    private static CommandInterior<String> fullInterior(List<String> trace) {
        return c -> {
            // mirror the CommandRouter nesting: the sourced-event replay happens inside the sourcing stage, and the
            // emitted-event apply happens inside the handler stage
            c.sourcing(new Sourcing(String.class, SourcingMode.NONE), () -> {
                trace.add("sourcing-work");
                c.sourcedEvent(
                        new SourcedEventInvocation(SRH_DEFINITION, null, "e", Map.of(), "subject", RAW_EVENT),
                        () -> trace.add("sourced-work"));
            });
            String result = c.handler(new CommandHandlerInvocation(HANDLER_DEFINITION, null, RAW_EVENT.id()), () -> {
                trace.add("handler-work");
                c.publishedEvent(
                        new PublishedEventInvocation(SRH_DEFINITION, null, "e", Map.of(), "subject"),
                        () -> trace.add("published-work"));
                return "R";
            });
            Publish publishJoinPoint = new Publish(List.of(), List.of());
            c.publish(publishJoinPoint, () -> {
                trace.add("publish-work");
                return publishJoinPoint;
            });
            return result;
        };
    }

    @Test
    void emptyChainRunsInteriorAndReturnsResult() throws Exception {
        var trace = new ArrayList<String>();

        String result = chain().execute(invocation, fullInterior(trace));

        assertThat(result).isEqualTo("R");
        assertThat(trace)
                .containsExactly("sourcing-work", "sourced-work", "handler-work", "published-work", "publish-work");
    }

    @Test
    void composesRootsAndStagesOuterToInner() throws Exception {
        var trace = new ArrayList<String>();

        String result = chain(recording("A", trace), recording("B", trace)).execute(invocation, fullInterior(trace));

        assertThat(result).isEqualTo("R");
        // every stage nests A-outer-B-inner: inbound A→B, outbound (inside-out) B→A. sourcedEvent nests inside
        // sourcing and publishedEvent inside handler (mirroring CommandRouter); each stage unwinds inside-out —
        // including the post-handler publishedEvent / publish stages.
        assertThat(trace)
                .containsExactly(
                        "A-root-before",
                        "B-root-before",
                        "A-sourcing-before",
                        "B-sourcing-before",
                        "sourcing-work",
                        "A-sourcedEvent-before",
                        "B-sourcedEvent-before",
                        "sourced-work",
                        "B-sourcedEvent-after",
                        "A-sourcedEvent-after",
                        "B-sourcing-after",
                        "A-sourcing-after",
                        "A-handler-before",
                        "B-handler-before",
                        "handler-work",
                        "A-publishedEvent-before",
                        "B-publishedEvent-before",
                        "published-work",
                        "B-publishedEvent-after",
                        "A-publishedEvent-after",
                        "B-handler-after",
                        "A-handler-after",
                        "A-publish-before",
                        "B-publish-before",
                        "publish-work",
                        "B-publish-after",
                        "A-publish-after",
                        "B-root-after",
                        "A-root-after");
    }

    @Test
    void multipleRegistrationsOnSameHookNestInRegistrationOrder() throws Exception {
        var trace = new ArrayList<String>();
        CommandInterceptor<Command> twoSourcings = new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                lc.sourcing((jp, c) -> {
                    trace.add("first");
                    return c.proceed();
                });
                lc.sourcing((jp, c) -> {
                    trace.add("second");
                    return c.proceed();
                });
                return cont.proceed();
            }
        };

        chain(twoSourcings).execute(invocation, c -> {
            c.sourcing(new Sourcing(String.class, SourcingMode.NONE), () -> trace.add("work"));
            return "R";
        });

        assertThat(trace).containsExactly("first", "second", "work");
    }

    @Test
    void handlerAdviceMaySubstituteResultWithoutProceeding() throws Exception {
        var handlerWork = new AtomicInteger();
        CommandInterceptor<Command> handlerCaching = new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                lc.handler((jp, c) -> {
                    @SuppressWarnings("unchecked")
                    R cached = (R) "cached";
                    return cached;
                });
                return cont.proceed();
            }
        };

        String result = chain(handlerCaching)
                .execute(
                        invocation,
                        c -> c.handler(new CommandHandlerInvocation(HANDLER_DEFINITION, null, null), () -> {
                            handlerWork.incrementAndGet();
                            return "R";
                        }));

        assertThat(result).isEqualTo("cached");
        assertThat(handlerWork).as("handler must not run when short-circuited").hasValue(0);
    }

    @Test
    void publishedEventHookFiresPerInvocation() throws Exception {
        var fired = new AtomicInteger();
        CommandInterceptor<Command> counting = new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                lc.publishedEvent((jp, c) -> {
                    fired.incrementAndGet();
                    return c.proceed();
                });
                return cont.proceed();
            }
        };

        chain(counting).execute(invocation, c -> {
            for (int i = 0; i < 3; i++) {
                c.publishedEvent(
                        new PublishedEventInvocation(SRH_DEFINITION, null, "e", Map.of(), "subject"), () -> {});
            }
            return "R";
        });

        assertThat(fired).hasValue(3);
    }

    @Test
    void publishAdviceReturningNullFailsAsNonTransientException() {
        CommandInterceptor<Command> nullPublish = new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                lc.publish((jp, c) -> null); // violates the contract: must return a (possibly rewritten) request
                return cont.proceed();
            }
        };

        assertThatThrownBy(() -> chain(nullPublish).execute(invocation, c -> {
                    c.publish(new Publish(List.of(), List.of()), () -> new Publish(List.of(), List.of()));
                    return "R";
                }))
                .isInstanceOf(CqrsFrameworkException.NonTransientException.class)
                .hasMessageContaining("null append request");
    }

    @Test
    void sourcedEventHookFiresPerInvocationAndDeliversJoinPoint() throws Exception {
        var work = new AtomicInteger();
        var fired = new HashMap<String, SourcedEventInvocation>();
        CommandInterceptor<Command> counting = new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                lc.sourcedEvent((jp, c) -> {
                    fired.put(jp.subject(), jp);
                    return c.proceed();
                });
                return cont.proceed();
            }
        };

        chain(counting).execute(invocation, c -> {
            for (int i = 0; i < 2; i++) {
                c.sourcedEvent(
                        new SourcedEventInvocation(
                                SRH_DEFINITION, "state", "e", Map.of("k", "v"), "child/subject/" + i, RAW_EVENT),
                        work::incrementAndGet);
            }
            return "R";
        });

        assertThat(fired).containsOnlyKeys("child/subject/0", "child/subject/1").allSatisfy((s, jp) -> {
            assertThat(jp.rawEvent()).isSameAs(RAW_EVENT);
            assertThat(jp.inputInstance()).isEqualTo("state");
            assertThat(jp.metaData().get("k")).isEqualTo("v");
        });
    }

    @Test
    void registeringAfterProceedThrows() {
        CommandInterceptor<Command> lateRegistration = new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                R result = cont.proceed();
                lc.sourcing((jp, c) -> c.proceed()); // illegal: lifecycle already frozen
                return result;
            }
        };

        assertThatThrownBy(() -> chain(lateRegistration).execute(invocation, c -> "R"))
                .isInstanceOf(InterceptorContractViolation.class)
                .hasMessageContaining("frozen");
    }

    @Test
    void throwingStageAdviceAbortsAndSkipsWork() {
        var work = new AtomicInteger();
        var boom = new RuntimeException("boom");
        CommandInterceptor<Command> denying = new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                lc.sourcing((jp, c) -> {
                    throw boom;
                });
                return cont.proceed();
            }
        };

        assertThatThrownBy(() -> chain(denying).execute(invocation, c -> {
                    c.sourcing(new Sourcing(String.class, SourcingMode.NONE), work::incrementAndGet);
                    return "R";
                }))
                .isSameAs(boom);
        assertThat(work).hasValue(0);
    }

    private static CommandInterceptor<Command> recording(String name, List<String> trace) {
        return new CommandInterceptor<>() {
            @Override
            public Class<Command> commandClass() {
                return Command.class;
            }

            @Override
            public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
                    throws Exception {
                trace.add(name + "-root-before");
                lc.sourcing((jp, c) -> {
                    trace.add(name + "-sourcing-before");
                    Proceeded p = c.proceed();
                    trace.add(name + "-sourcing-after");
                    return p;
                });
                lc.sourcedEvent((jp, c) -> {
                    trace.add(name + "-sourcedEvent-before");
                    Proceeded p = c.proceed();
                    trace.add(name + "-sourcedEvent-after");
                    return p;
                });
                lc.handler((jp, c) -> {
                    trace.add(name + "-handler-before");
                    R r = c.proceed();
                    trace.add(name + "-handler-after");
                    return r;
                });
                lc.publishedEvent((jp, c) -> {
                    trace.add(name + "-publishedEvent-before");
                    Proceeded p = c.proceed();
                    trace.add(name + "-publishedEvent-after");
                    return p;
                });
                lc.publish((jp, c) -> {
                    trace.add(name + "-publish-before");
                    Publish p = c.proceed();
                    trace.add(name + "-publish-after");
                    return p;
                });
                R result = cont.proceed();
                trace.add(name + "-root-after");
                return result;
            }
        };
    }
}
