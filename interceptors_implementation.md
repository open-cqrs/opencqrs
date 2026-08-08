# OpenCQRS Interceptors — Implementation Hand-off

> **Companion to** [`interceptors.md`](interceptors.md) (the design spec). This document is the **implementation
> plan and progress tracker** for building the interceptor framework. Read `interceptors.md` first for the *why*;
> this file is the *what to build, in what order, and where it plugs into the existing code*.
>
> **Terminology:** **instance** / **state**, never "aggregate".

---

## 0. Scope (this cut)

**In scope — the core interceptor framework only:**

- the shared stage mechanism (`com.opencqrs.framework.interceptor`);
- the command-side interceptor surface + `CommandRouter` integration;
- the event-side interceptor surface + `EventHandlingProcessor` integration;
- Spring autoconfigure wiring (`@Order`-sorted bean lists into the core components);
- `framework-test` fixture integration;
- tests at every altitude.

**Explicitly OUT of scope here** (all additive-safe per `interceptors.md` §3.8, deferred to later cuts):

- **Tracing** entirely — the OTel interceptor, `TraceAwareEventReader`, and the `traceParent`/`traceState`
  wire fields on `Event`/`EventCandidate`. (`tracing.md` is parked.)
- **Dedicated / provided interceptors** — metrics, state-based security. (**Optimistic locking** is now **in scope** as
  the *first* framework-provided interceptor — see **Stage 9**. Its prerequisite — exposing the sourced head event id on
  `CommandHandlerInvocation` — is **done**.)
- The **`prepareAppend` pre-append transform hook** and the **metadata-propagation migration** (§9.3 of the spec).
- **`@BeforeCommandHandling`** annotation sugar (§8.4).
- **`EventLifecycle.afterCommit`** (§8.5).

Nothing above needs a fixed-surface change later; each lands as a new hook / bean / annotation.

---

## 1. Resolved design forks (decided with the maintainer)

| # | Question | Decision | Consequence for implementation |
|---|---|---|---|
| 1 | `sourcedEvent`/`publishedEvent` granularity: per event, or per matching state-rebuilding handler? | **Per `.on()` call** (per event × matching SRH definition) | The JP keeps its **singular** `definition`. Thread the advice chain **inside** the fold in `Util.applyUsingHandlers`, not around the whole per-event apply. `inputInstance` = state *before* that handler's `.on()`. |
| 2 | Does `publish` fire when the handler emitted **zero** events? | **Only on an actual append** | `publish` observer / `Publish` JP fire exactly where `immediateEventPublisher.publish(...)` runs today (guarded by non-empty). Preserves today's no-append fast path. |
| 3 | Wire the chain into `CommandHandlingTestFixture` now, or defer? | **Include now** | Otherwise the fixture runs a *different* path than production. Reuse the core chain-composer. |
| 4 | Expose the sourced head event id to interceptors? | **Yes — new `@Nullable String latestSourcedEventId` on `CommandHandlerInvocation`** (= `CacheValue.eventId()`) | Prerequisite for optimistic locking. Router passes `fromCacheMerged.eventId()`; **cache-correct** — a `sourcedEvent`-reconstruction would miss cache-served events. **DONE** (Stage 9 prereq). |
| 5 | Marker interface name / shape | **`EventIdExpectingCommand extends Command`**, `String expectedEventId()` | Extends `Command` ⇒ mix-in via multiple-interface inheritance (diamond-on-`Command` harmless); interceptor gates as `CommandInterceptor<EventIdExpectingCommand>` (no swallow-all). "Event id", not "version" — the token is an opaque `Event.id()`, not an orderable number. |
| 6 | Interceptor name | **`OptimisticLockingCommandInterceptor`** | — |
| 7 | Exception name + hierarchy | **`OptimisticLockingException`**; promote **`ConcurrencyException` to the shared base**, rename today's store-side subtype to **`ConflictingWriteException`** (opaque HTTP 409 — no cause detail) | Single `catch (ConcurrencyException)` covers pre-handler reject + store conflict; both stay **transient** (sealed hierarchy ⇒ must extend `TransientException`). Store-side name reflects the observable fact, not an inferred precondition. Contrast `CommandSubjectConditionViolatedException` (non-transient, structural). |
| 8 | Package | **`com.opencqrs.framework.command.interceptor.optimisticlocking`** | Feature sub-package; keeps the SPI package free of provided impls; scales to sibling provided interceptors. |
| 9 | Autoconfigure | **One `OptimisticLockingCommandInterceptorAutoConfiguration`** (not a shared bucket) | Independently `spring.autoconfigure.exclude`-able, matching Boot idiom. |
| 10 | Register by default? | **Yes**, guarded by `@ConditionalOnMissingBean` | The marker interface *is* the opt-in; the bean is inert (assignability gate never matches) for non-versioned commands. |
| 11 | Interceptor order — where | **Applied by the autoconfigure `@Bean` (`@Order`), not the core.** The `framework` interceptor does **not** implement `Ordered`. A central **`CommandInterceptorOrders`** constants class (autoconfigure) holds `OPTIMISTIC_LOCKING = 0`. | Keeps core Spring-free. Lower = outer; unordered beans sort innermost (`LOWEST_PRECEDENCE`); negative range reserved for outermost provided interceptors; gaps let user interceptors interleave. See `interceptor_optimistic_locking.md` §1/§3. |

---

## 2. Integration facts (grounded in the current code)

Verified by reading the sources on branch `interceptors`. Line numbers are approximate anchors, not contracts.

### 2.1 Command side — `framework/.../command/CommandRouter.java`

- `send(Command, Map<String,?>)` is fully synchronous (`CommandRouter.java:167`).
- **Constructor** takes `List<CommandHandlerDefinition>` + `List<StateRebuildingHandlerDefinition>` (both raw,
  `@SuppressWarnings`), a `StateRebuildingCache`, `PropagationMode`, `Set<String>` keys
  (`CommandRouter.java:52`). A convenience ctor defaults cache/propagation (`:89`).
- **Sourcing** happens inside `stateRebuildingCache.fetchAndMerge(key, closure)` (`:177`–`:244`). The closure
  reads newer-than-cached events and, per sourced event, calls
  `Util.applyUsingHandlers(srhds, instance, subject, event, metaData, raw)` (`:239`). **On a warm cache the
  closure may source zero events** → `sourcedEvent` legitimately fires zero times; `sourcing` still wraps the
  whole `fetchAndMerge` regardless.
- **Handler** invocation is a `switch` over the three `CommandHandler` variants (`:249`–`:257`);
  `fromCacheMerged.instance()` is the rebuilt state (available even for `ForCommand`).
- **Emitted events** are captured + applied by `CommandEventCapturer.publish/publishRelative`
  (`command/CommandEventCapturer.java`), each calling `Util.applyUsingHandlers(..., rawEvent=null)`.
- **Append** is `immediateEventPublisher.publish(events, preconditions)` (`:291`), **only when
  `!eventCapturer.getEvents().isEmpty()`** (`:259`). Metadata propagation + precondition assembly happen
  just before (`:260`–`:290`) — left untouched this cut.
- `Util.applyUsingHandlers` (`command/Util.java:12`) folds ONE event over ALL matching SRH definitions
  (`.filter(...).forEach(...)`), calling `handler.on(...)` per matching definition. **This is the fold to
  thread the per-`.on()` advice chain through** (decision #1). It returns `boolean wasApplied`.

### 2.2 Event side — `framework/.../eventhandler/EventHandlingProcessor.java`

- Async observe loop on a per-processor **virtual thread** (`start()` at `:334`); handling runs synchronously on
  that thread inside the `consumeRaw` callback — no executor hop.
- Per raw event: `progressTracker.proceed(group, partition, executionSupplier)` (`:214`). The supplier
  (`:214`–`:282`) is where the **root interceptor must sit** (inside the umbrella tx when enabled).
- **`skipEvent` gate** (`:215`): after retry exhaustion an event is skipped (progress still advances). Interceptors
  do **not** fire on that abandonment path.
- **Relevance** today: `ForRawEvent` → `rawEventRelevant` computed pre-upcast (`:216`); if not relevant the whole
  `upcast` is skipped (`:224`) → the real fast path. `ForObjectAndMetaDataAndRawEvent` → `rawEventRelevant` is
  always `true` (`:221`) and relevance is checked per **converted** event post-upcast (`:227`).
- **Handlers**: for each matching `EventHandlerDefinition` (assignability filter `:241`–`:246`), a `switch` over
  the three `EventHandler` variants (`:247`–`:262`). **This is where the `handler` advice chain wraps.**
- **Error classification** (`:287`–`:310`): `NonTransientException` → terminate; everything else →
  `retryHandler.handle(...)` → retry/backoff, skip on exhaustion. ⚠️ A bare `IllegalStateException` (e.g. a
  double-`proceed()` contract violation) would currently be classified **transient → retried forever**. The
  framework must therefore raise the double-proceed violation as a **`NonTransientException`** so it terminates
  (spec §6.4/§9.2). See task E-4.
- **Progress tx** — `framework-spring-boot-autoconfigure/.../progress/JdbcProgressTracker.java:215`:
  `proceed` runs `execution.get()` inside `proceedTransactionOperations.executeWithoutResult(...)`
  (`PROPAGATION_REQUIRED` when `proceedTransactionally=true`, else `PROPAGATION_NEVER`, `:168`–`:173`); the
  progress UPDATE/INSERT runs in a nested `defaultTransactionOperations` (`PROPAGATION_REQUIRED`).

### 2.3 Definitions (verbatim)

- `record CommandHandlerDefinition<I, C extends Command, R>(Class<I> instanceClass, Class<C> commandClass, CommandHandler<I,C,R> handler, SourcingMode sourcingMode)`
- `record StateRebuildingHandlerDefinition<I, E>(Class<I> instanceClass, Class<E> eventClass, StateRebuildingHandler<I,E> handler)`
- `record EventHandlerDefinition<E>(String group, Class<E> eventClass, EventHandler<E> handler)`

### 2.4 Wiring

- **Command:** `framework-spring-boot-autoconfigure/.../command/CommandRouterAutoConfiguration.java:31`
  `openCqrsCommandRouter(...)` @Bean — Spring injects `List<CommandHandlerDefinition>` /
  `List<StateRebuildingHandlerDefinition>` as collection beans. Add an `@Order`-sorted
  `List<CommandInterceptor>` param here.
- **Event:** `framework-spring-boot-autoconfigure/.../eventhandler/EventHandlingProcessorAutoConfiguration.java`.
  The nested `EventHandlingProcessorRegistrar` (a `BeanFactoryAware` + `SmartInitializingSingleton`, `:172`)
  builds one `EventHandlingProcessor` bean def **per group × partition** via `BeanDefinitionBuilder ...
  addConstructorArgValue(...)` (`:299`–`:311`). Add an `@Order`-sorted `List<EventInterceptor>` to the
  registrar's `@Bean` factory method (`:164`) and `.addConstructorArgValue(interceptors)` on each processor def.
  Interceptors are therefore **global across all processors**.

### 2.5 Test support — `framework-test/.../command/`

`CommandHandlingTestFixture` + `@CommandHandlingTest`. To be inspected in Stage 7; the chain-composer is reused
around its stubbed sourcing/handler/publish. Fixture is a shared lazy singleton under the slice → interceptor
selection must be **immutable derivation** (`withAdditionalInterceptors` / `withoutInterceptors`), never mutation.

---

## 3. Package / module layout

```
framework/ (core, Spring-free, OTel-free)
  com.opencqrs.framework.interceptor                 ← shared mechanism  [Stage 1 ✅]
  com.opencqrs.framework.command.interceptor         ← command surface   [Stage 2]
  com.opencqrs.framework.command.interceptor.optimisticlocking  ← provided: optimistic locking [Stage 9]
  com.opencqrs.framework.eventhandler.interceptor     ← event surface     [Stage 3]
framework-spring-boot-autoconfigure/                  ← @Order-sorted wiring [Stage 6]
    …command.CommandInterceptorOrders                              ← central @Order constants for provided interceptors [Stage 9]
    …command.OptimisticLockingCommandInterceptorAutoConfiguration  ← default-on, @ConditionalOnMissingBean, @Order [Stage 9]
framework-test/                                        ← fixture integration  [Stage 7]
```

Packages are `@NullMarked` (package-info) with jspecify `@Nullable` + NullAway on `compileJava`. Tests: JUnit 5 +
AssertJ. Build: `./gradlew :framework:test` (the Gradle wrapper needs the sandbox disabled — it writes a lock
under `~/.gradle`).

---

## 4. Staged plan

Each stage compiles, is independently testable, and keeps the **existing no-interceptor tests green** as the
empty-list / zero-overhead regression.

### Stage 1 — Shared mechanism + composition engine ✅ DONE

Package `com.opencqrs.framework.interceptor`:

| Type | Role |
|---|---|
| `Proceeded` | opaque proof-of-proceed; `private` ctor + package-confined `INSTANCE` (only mintable at a real proceed) |
| `Continuation` | observer continuation, exactly-once, returns `Proceeded` |
| `ValueContinuation<V extends @Nullable Object>` | value continuation, at-least-once, returns real `V` |
| `StageObserver<J>` | around returning `Proceeded` (must proceed) |
| `StageTransformer<J, V extends @Nullable Object>` | around returning `V` (may short-circuit / retry) |
| `StageWork` | throwing-runnable for the innermost framework work (keeps `Proceeded` minting in-package) |
| `InterceptorChains` | `observerChain(advices, jp, work)` (every level once-guarded) + `transformerChain(advices, jp, terminal)` (unguarded) |
| `RegistrationGuard` | `ensureOpen()` / `freeze()` fail-fast backing register-before-proceed |

> **Naming note:** the innermost-work type is `StageWork`, **not** `InterceptorWork` — at the stage altitude
> "Interceptor" would collide with the command/event interceptors one level up.

Tests (`InterceptorChainsTest`, `RegistrationGuardTest`, **16 green**): outer→inner order, no-stall (structural),
observer exactly-once (2nd `proceed()` throws before re-running work), work-runs-once across nesting, throw-skips-work,
exception unwind through advice `finally`; transformer thread-result / short-circuit / multi-proceed / null result /
exception propagation; guard open-then-frozen + idempotent freeze.

### Stage 2 — Command interceptor surface

Package `com.opencqrs.framework.command.interceptor`. Public surface per spec §5:

- `CommandInterceptor<C extends Command>` — `Class<C> commandClass()` + `<R> R intercept(CommandInvocation<C>, CommandLifecycle<R>, ValueContinuation<R>)`.
- `record CommandInvocation<C extends Command>(C command, Map<String,?> metaData)` with `subject()`.
- `interface CommandLifecycle<R>` — `sourcing`, `sourcedEvent`, `publishedEvent`, `handler` (transformer, threads `R`), `publish`.
- JP records: `Sourcing`, `SourcedEventInvocation` (rawEvent **non-null**), `PublishedEventInvocation` (no rawEvent), `CommandHandlerInvocation`, `Publish`.
- **Internal**: a `CommandLifecycle` impl backed by `InterceptorChains` + `RegistrationGuard`. One shared lifecycle
  instance collects advices per JP in (chain-order, then call-order); it **freezes** when the root terminal is
  reached; interior stages then compose outer→inner. A `CommandInterceptorChain<R>` driver exposes terminal-op
  seams for `CommandRouter` (Stage 4).

Registration/freeze model: interceptor[0].`intercept` registers hooks then calls `continuation.proceed()`, which
runs interceptor[1].`intercept`, …; the innermost `proceed()` reaches the framework terminal → `freeze()`. This
yields chain-order-then-call-order naturally.

**Nullability of the command result.** The command result is nullable (`send`/`CommandHandler.handle` return
`@Nullable R`), so every result-returning method here does too: `CommandInterceptor.intercept`, `CommandInterior.execute`,
and `CommandInterceptorChain.execute`/`handler` all return `@Nullable R`. The nullability lives on `ValueContinuation`
itself — declared `<V>` (unbounded) with `@Nullable V proceed()` — so callers use bare `ValueContinuation<R>` and still
get a nullable result, with no need for `ValueContinuation<@Nullable R>` type arguments (which this project's NullAway,
not in JSpecify-generics mode, would reject anyway). `StageTransformer<J, V>` is likewise unbounded, and its `around`
returns `@Nullable V` (symmetric with `proceed()`), so a passthrough `(jp, c) -> c.proceed()` transformer typechecks and
a transformer may substitute `null`. Because `proceed()`/`around` are `@Nullable`, no NullAway suppression is needed for
the result flow; `CommandInterceptorChain.execute` keeps only `@SuppressWarnings({"rawtypes","unchecked"})` for the raw
interceptor bridge.

**Delivered internal driver** (`command.interceptor`): `DefaultCommandLifecycle<R>` (registration + freeze),
`CommandInterceptorChain<R>` (public, framework-internal — composes roots, exposes `sourcing`/`sourcedEvent`/
`publishedEvent`/`handler`/`publish` stage seams), and `CommandInterior<R>` (the router-supplied body run at the
innermost root). Stage 4 wires `CommandRouter` to build a chain only when ≥1 interceptor applies and calls these seams;
`sourcedEvent`/`publishedEvent` are threaded down into `Util.applyUsingHandlers` per decision #1.

Tested here (`CommandInterceptorChainTest`, 8): empty-chain passthrough, root+stage outer→inner order, intra-hook
call-order nesting, handler short-circuit, per-invocation firing of both state-apply hooks (`publishedEvent` count +
`sourcedEvent` count with `SourcedEventInvocation` join-point data delivered), register-after-proceed freeze, throwing
advice aborts. Full `commandClass` filtering/ordering coverage lands in Stage 4 over the real router.

### Stage 3 — Event interceptor surface ✅ DONE

Package `com.opencqrs.framework.eventhandler.interceptor`. Per spec §6. Delivered public surface:

- `EventInterceptor` — `default Delivery delivery()` (=`ACTIONABLE`) + `Proceeded intercept(EventInvocation, EventLifecycle, Continuation)`.
  Note the two deliberate divergences from the *command* surface: the root is an **observer** (returns `Proceeded` via a
  `Continuation`, not a threaded `@Nullable R`), and there is **no whole-interceptor type gate** (no `commandClass()`
  analogue) — type targeting is per-`handler`-registration instead. Termination is exception-based (spec §6.4); the
  Javadoc states the terminate-vs-retry contract, but the processor's *enforcement* of it is Stage 5.
- `enum Delivery { ACTIONABLE, PARTITIONED, ALL }`, `enum Relevance { YES, NO, PARTIAL }` (surface only — consumed by
  the processor's per-event delivery gating in Stage 5; the driver runs whatever pre-filtered interceptor list it is given).
- `record EventInvocation(Event rawEvent, String group, long partition, Relevance relevance)`.
- `interface EventLifecycle` — `<E> void handler(Class<E> eventClass, StageObserver<EventHandlerInvocation<E>>)` + all-events
  `default handler(StageObserver<EventHandlerInvocation<Object>>)` → `handler(Object.class, …)`.
- `record EventHandlerInvocation<E>(EventHandlerDefinition<?> definition, E event, Map<String,?> metaData)` (raw event /
  group / partition come from the enclosing `EventInvocation`).

**Internal driver** (analogous to the command side): `DefaultEventLifecycle` (package-private; registration + freeze;
stores typed `handler` registrations and selects applicable advice by `eventClass.isInstance(event)` — the per-`.on()`
assignability gate), `EventInterior` (functional interface; the processor-supplied body run at the innermost root, returns
`void` since the terminal is an observer), and `EventInterceptorChain` (public, framework-internal). The chain composes the
observer roots by **reusing `InterceptorChains.observerChain`** via adapter lambdas — so roots, the interior terminal, and
each `handler` are all **exactly-once** (a second `proceed()` throws `IllegalStateException`, the terminal double-proceed
that Stage 5's E-4 will classify as non-transient). This is the intended contrast with the command driver, whose
transformer roots are at-least-once and hand-composed.

Tests (`EventInterceptorChainTest`, **10 green**, NullAway clean): empty-chain passthrough, root+handler outer→inner
nesting, intra-hook registration-order nesting, `handler` fires once per invocation + delivers join-point data, **type
gating** (typed `handler(Class<E>, …)` fires only for assignable converted events) + all-events convenience fires for all,
register-after-proceed freeze, throwing advice aborts + skips work, **observer exactly-once** on both the root and a
`handler` continuation. `commandClass`-analogue / delivery / relevance gating are the processor's job → covered in Stage 5.

### Stage 4 — `CommandRouter` integration ✅ DONE (built before Stage 3 — order flipped at maintainer's request)

Delivered:

- **New 8-arg ctor** takes a raw `List<CommandInterceptor>` (index 0 = outermost); a 7-arg overload delegates with
  `List.of()`, so existing callers (autoconfigure, `framework-test`, examples) compile unchanged. `send`'s existing
  `@Nullable R` signature is unchanged.
- **Empty applicable list → today's direct path** (`doSend(..., chain=null)`): no `CommandInterceptorChain`, no
  `CommandInvocation`, no join-point allocation, `Util.applyUsingHandlers` uses the original (non-throwing) overload.
  The 26 existing `CommandRouterTest` cases stay green as the zero-overhead regression.
- **Non-empty path:** `send` filters by `commandClass().isAssignableFrom(...)`, builds a `CommandInterceptorChain<R>`,
  and calls `chain.execute(new CommandInvocation<>(command, metaData), c -> doSend(..., c))`. Stage seams: `sourcing`
  wraps the `fetchAndMerge` call (via a holder, since it's a `StageObserver`); `handler` wraps the handler `switch`
  (threading `R`); `publish` wraps `immediateEventPublisher.publish` **only when ≥1 event was captured** (decision #2).
- **Per-`.on()` seams (decision #1):** `Util.applyUsingHandlers` is a **single** method taking a
  `CommandInterceptorChain<?>`; it wraps each individual handler application and dispatches on `rawEvent` presence —
  `rawEvent != null` (a replayed persisted event) → `chain.sourcedEvent(new SourcedEventInvocation(...))`, else (an
  event just emitted by the handler) → `chain.publishedEvent(new PublishedEventInvocation(...))`. An **empty chain is
  the no-op** (empty advice list → the stage just runs the apply), so this one method also serves the plain state-fold.
  `CommandEventCapturer` holds a non-null `CommandInterceptorChain<?>` (its 3-arg ctor supplies `new
  CommandInterceptorChain<>(List.of())`); `CommandRouter` passes its real chain via the 4-arg ctor. *(Earlier this used
  a `Util.ApplyInterceptor` callback + a `PublishedEventInterceptor` SPI + a nullable-conditional in the capturer; all
  removed — the empty chain replaces the no-op branch, and `rawEvent` presence replaces the sourced/published
  discriminator.)*
- **Checked-exception tunneling → `InterceptorExecutionException`.** `sourcedEvent`/`publishedEvent` advice fire
  behind non-throwing boundaries (the cache `Function`, `CommandEventPublisher.publish`), so a *checked* exception from
  that advice is caught there and re-thrown as **`com.opencqrs.framework.interceptor.InterceptorExecutionException`** (a
  shared `CqrsFrameworkException.NonTransientException` subtype — see below); **unchecked** advice exceptions (the norm
  — deny/veto) propagate unchanged. Checked exceptions from the root/`sourcing`/`handler`/`publish` advice surface via
  `send`'s outer `catch (Exception)` → same wrapping; unchecked rethrown as-is.
- **`InterceptorExecutionException`** lives in the shared `interceptor` package so the event side (Stage 5) reuses it:
  wrapping a checked advice failure to a `NonTransientException` means the processor *terminates* rather than
  retry-forever (consistent with the double-`proceed()` decision). Checked-only; message names the stage where known.
- Tests (`command.interceptor.CommandRouterInterceptorTest`, 6, recording interceptor over the real pipeline): all
  hooks at correct JPs with correct data; list-order nesting (outermost first); `commandClass()` filtering; throw-veto
  skips publish; handler short-circuit substitutes result + skips handler + publish; publish fires only on append.

> **Gotcha for the next implementer:** a single incremental `:framework-test:compileJava` right after changing the
> package-private `Util` can emit *phantom* NullAway override-nullability errors in `CommandHandlingTestFixture`
> (`framework-test` shares the split package `com.opencqrs.framework.command` and uses `Util`). They vanish on a full
> recompile (`--rerun-tasks`) — verify with a clean compile, don't chase them.

> **Still open for the command side:** autoconfigure does **not** yet inject interceptor beans (Stage 6) — the 7-arg
> ctor path is still used, so command interceptors only run when passed via the 8-arg ctor directly.

### Stage 5 — `EventHandlingProcessor` integration ✅ DONE

Delivered (per maintainer: **no convenience overload** — the `List<EventInterceptor>` param was added to the *existing*
public and package-private ctors, so the two `EventHandlingProcessorTest` call sites now pass `List.of()`; no third ctor):

- **Ctor param** `List<EventInterceptor> eventInterceptors` (index 0 = outermost) inserted after `eventHandlerDefinitions`.
- **`run()`** delegates the per-raw-event body to a **single** `dispatch(raw, rawCallback)` — **no `isEmpty()` branch**;
  the chain is *always* built and an empty applicable list is a passthrough (handlers run, no root/handler advice fires),
  mirroring `CommandRouter`'s empty-chain seam. All existing `EventHandlingProcessorTest` cases stay green.
- **`dispatch` / Variant O:** a wrong-partition `ForRawEvent` is decided **pre-upcast** and is **not upcast** — it fires
  the root only for `ALL`-level interceptors (`Relevance.NO`, no-op handler stage), else nothing. Otherwise the upcast
  fan-out is **buffered once** (no *extra* upcast) into `Converted(javaClass, event, metadata, relevant)` records so the
  aggregate `Relevance` (`aggregateRelevance`: `ForRawEvent`→`YES` even on drop-to-0; converted resolver folds
  `YES`/`NO`/`PARTIAL`, empty→`NO`) and **actionability** (≥1 relevant converted event with a matching handler) are known
  before the root fires. `executeChain` filters the **applicable** interceptors via **`admits(delivery, relevance,
  actionable)`** — an exhaustive `switch` over `Delivery` (`ACTIONABLE`: relevant ∧ actionable; `PARTITIONED`: relevant;
  `ALL`: always) that encodes the semantics **without relying on enum ordinal/declaration order**. Handlers are wrapped by
  `chain.handler(EventHandlerInvocation, work)` per matching def.
- **Error classification:** `executeChain` catches advice exceptions — **unchecked** propagate into the existing
  `EventProcessingFailure` classification (NonTransient→terminate, else retry); **checked** are wrapped as
  `InterceptorExecutionException` (NonTransient→terminate), mirroring the command side.
- **E-4 (double-proceed / register-after-freeze → terminal):** `InterceptorChains.onceGuarded` and `RegistrationGuard`
  now throw **`InterceptorContractViolation extends CqrsFrameworkException.NonTransientException`** (shared `interceptor`
  pkg). It extends `NonTransientException` — not `IllegalStateException` — because a contract violation *is*
  non-recoverable, matching the framework's canonical don't-retry signal and the sibling `InterceptorExecutionException`;
  so the processor's **existing** `case NonTransientException -> throw` terminates it with **no special-case** (nothing in
  main source catches `IllegalStateException` for control flow, so this is a consistency win, not a behavioural change). A
  *deliberate* `IllegalStateException` from a handler/interceptor still falls to `default` → retry, so the two are never
  conflated — the distinct discoverable type (spec §6.4) is what keeps them apart. The ~6 mechanism/driver-test assertions
  and the `@throws`/prose javadocs across Stages 1–3 were updated from `IllegalStateException` to
  `InterceptorContractViolation`.
- `skipEvent` (retry-exhausted) path is unchanged and does **not** fire interceptors.
- Tests: **new** `EventHandlingProcessorInterceptorTest` (10, mock-driven harness; existing test untouched bar the two
  `List.of()` ctor edits): root+handler wrap-order over an actionable event; **root once per retry attempt**; `ACTIONABLE`
  not fired for a partition-relevant no-handler event vs `PARTITIONED` fired (sees `YES`); `ACTIONABLE` not fired **and no
  upcast** for wrong-partition `ForRawEvent` (Variant-O regression) vs `ALL` fired with `Relevance.NO` and still no upcast;
  **`PARTIAL`** over a mixed converted fan-out (handler only for the relevant one); interceptor throw → retry vs
  `NonTransientException` → terminate; **double-`proceed()` → terminate** (E-4).

### Stage 6 — Autoconfigure wiring

- **Command side ✅ DONE.** `CommandRouterAutoConfiguration.openCqrsCommandRouter` gained a
  `@SuppressWarnings("rawtypes") List<CommandInterceptor> commandInterceptors` param (Spring
  `@Order`/`Ordered`/`@Priority` sorts collection injection) passed to the `CommandRouter` ctor (interceptors are the
  5th ctor arg, after the two definition lists). Empty list when no beans exist → today's behaviour preserved. Verified
  end-to-end by the Stage 8 integration test (no dedicated `CommandRouterAutoConfigurationTest` exists).
- **Event side ✅ DONE.** `EventHandlingProcessorAutoConfiguration.eventHandlingProcessorRegistrar(...)` gained a
  `List<EventInterceptor> eventInterceptors` param (Spring `@Order`-sorted collection injection; empty when no beans),
  stored on the registrar and `.addConstructorArgValue(eventInterceptors)` on **every** processor bean def (after `ehds`,
  before backoff) — global across all processors. `EventInterceptor` is non-generic, so the list is raw-type-free.
  Covered by `EventHandlingProcessorAutoConfigurationTest.eventInterceptorsInjectedIntoEveryProcessorInOrder` (slice test
  — two `@Order`-ed `EventInterceptor` beans asserted present, in outermost-first order, on every processor's
  `eventInterceptors`; auto-start disabled so no threads spawn).

### Stage 7 — `framework-test` fixture integration ✅ DONE (non-Spring DSL); Spring auto-wiring deferred

**Settled decision (fixture sourcing fidelity):** the fixture's `given` events **must** fire `sourcing`/`sourcedEvent`,
because in production the given-history *is* the sourced history. Delivered:

- **Immutable interceptor DSL** on `CommandHandlingTestFixture`: `withAdditionalInterceptors(CommandInterceptor…)` (append,
  arg order) and `withoutInterceptors()` — each returns a **new** fixture (the base is unaffected); an `interceptors`
  field + `sourcingMode` (captured from the `CommandHandlerDefinition`, else `RECURSIVE` for the bare-handler ctor) were
  added to the private ctor.
- **`when()` runs the command through `chain.execute(new CommandInvocation<>(command, metaData), c -> …)`**, filtering by
  `commandClass()`. The interior fires all stages: `sourcing` wraps the given-event fold (so **given events fire
  `sourcedEvent`**), `handler` wraps the handler switch, `publish` wraps a no-op (fixture doesn't append) when ≥1 event
  was captured. The `CommandHandlerInvocation`/`Sourcing` JPs use a synthesized `CommandHandlerDefinition`
  (`commandClass = command.getClass()`).
- **Exception mapping:** the whole `chain.execute` is inside the existing `try { … } catch (Throwable)` → a veto/handler
  throw becomes a `Failing` outcome (`.fails().throwing(…)`), unchecked propagating unchanged.
- **Setup validation is eager:** "given event has no matching state-rebuilding handler" is checked **before**
  `chain.execute` and thrown as a programming error (`IllegalArgumentException`), *not* captured as a command failure —
  otherwise the pre-existing `throwsWhenNoStateRebuildingHandlerMatchesEventType` test would flip.
- **Empty-interceptor path is byte-identical** — all 185 pre-existing fixture tests stay green.
- **New tests** (`CommandHandlingTestFixtureTest` → `@Nested WithInterceptors`, 6): veto→`fails().throwing`, handler
  short-circuit→`succeeds().havingResult().withoutEvents()`, `handler` hook sees state rebuilt from `given` events,
  **given events fire `sourcedEvent`** (the fidelity win), `withAdditionalInterceptors` immutability, `commandClass`
  filtering. Verified via the existing DSL — no interceptor-specific assertions. **191 framework-test tests green.**

**Spring auto-wiring — ✅ DONE.** `@CommandHandlingTest` now applies the slice's interceptors to every auto-wired
fixture:

- `CommandHandlingTestFixture.Builder` gained a package-private `interceptors` field + `withInterceptors(List)`; its
  `using(…)` seeds each fixture with them.
- `CommandHandlingTestAutoConfiguration.commandHandlingTestFixtureBuilder` injects the `@Order`-sorted
  `List<CommandInterceptor>` (collection injection over the sliced context — user interceptors defined in a
  `@CommandHandlerConfiguration` are included; framework/provided auto-config interceptors are **not**, per the slice's
  default filters) and sets them as the base set.
- **`@CommandHandlingTest(withInterceptors = false)`** (default `true`) empties the base set. A bean can't read the
  test-class annotation, so a **`ContextCustomizerFactory`** (registered in `META-INF/spring.factories`) reads the
  attribute and registers a typed `InterceptorsEnabled(boolean)` **context singleton** (not an ambient property — the
  factory hands it over as a first-class context object); the builder reads it via `ObjectProvider` (absent outside a
  slice ⇒ enabled). The customizer is a `record`, so `true`/`false` yield distinct cached contexts.
- Tests: `CommandHandlingTestAutoConfigurationTest` (+2, `ApplicationContextRunner`: base set includes sliced
  interceptors / empty when disabled), `CommandHandlingTestContextCustomizerFactoryTest` (+4: annotation→customizer flag,
  null for non-annotated, singleton registration), `CommandHandlingTestInterceptorSliceTest` (real `@CommandHandlingTest`
  slice → factory discovered via `spring.factories`, flag bridged). **199 framework-test tests green.**

### Stage 8 — E2E smoke

- **Command side ✅ DONE.** `CommandAndEventHandlingIntegrationTest` gained an `InterceptedCommandHandling`
  `@TestConfiguration` with two `@Order`-ed `CommandInterceptor` `@Bean`s (gated to a dedicated `InterceptedCommand`, so
  they can't perturb other tests' shared async counters) and a test that sends the command through the **real
  ESDB-backed** auto-wired `CommandRouter` and asserts `containsExactly("outer:before", "inner:before", "inner:after",
  "outer:after")` — proving auto-wiring + `@Order` (lowest = outermost) + inside-out unwinding end-to-end. The suite runs
  12 tests against the ESDB testcontainer, all green.
- **Event side ⬜ pending** (with Stage 3/5): one interceptor `@Bean` through `EventHandlingProcessorIntegrationTest`,
  `@Order` respected.

---

### Stage 9 — Optimistic locking (first framework-provided interceptor)

Decisions in §1 rows 4–11. Built on the command surface only (no event side).

- **9.0 Prerequisite — expose the sourced head id. ✅ DONE.** `CommandHandlerInvocation` gained
  `@Nullable String latestSourcedEventId` (= the head of the sourced stream, incl. cache-served events). `CommandRouter`
  passes `fromCacheMerged.eventId()`; the fixture passes `null` (no event-id model for given events yet — added in 9.4).
  The two driver-test call sites were updated. **Router-level coverage added** in `CommandRouterInterceptorTest`: the
  real-pipeline `Recording` now traces `jp.latestSourcedEventId()` at the `handler` hook (both full-trace tests assert
  the sourced head `event-id-1` reaches the join point), a focused non-null test, a null-path test (pristine subject ⇒
  `null`), and a **no-SRHD-head** test (a trailing sourced event without a matching state-rebuilding handler still
  advances the head — the case only the router can cover, since the fixture rejects given events lacking an SRHD).
  Compiles clean (full `--rerun-tasks`); all `CommandRouterInterceptorTest` + interceptor + fixture unit tests green.
- **9.1 Marker + exception ✅.** `EventIdExpectingCommand extends Command` (+ `package-info`). Exception refactor:
  `ConcurrencyException` (`client` pkg) is now the shared base (added `(String)` ctor); `ClientRequestErrorMapper` throws
  the new `ConflictingWriteException extends ConcurrencyException`; `OptimisticLockingException extends
  ConcurrencyException` added in the `optimisticlocking` package. The integration test's
  `isInstanceOf(ConcurrencyException.class)` assertions stay green (subclass).
- **9.2 Interceptor ✅.** `OptimisticLockingCommandInterceptor implements CommandInterceptor<EventIdExpectingCommand>`
  (Spring-free — no `Ordered`). Single `handler` transformer: compare `jp.latestSourcedEventId()` against
  `inv.command().expectedEventId()`; mismatch ⇒ throw `OptimisticLockingException`, else `c.proceed()`.
- **9.3 Autoconfigure + order constants ✅.** `CommandInterceptorOrders` (autoconfigure; `OPTIMISTIC_LOCKING = 0`) and
  `OptimisticLockingCommandInterceptorAutoConfiguration` (`@AutoConfiguration`, `@ConditionalOnMissingBean`,
  `@Order(CommandInterceptorOrders.OPTIMISTIC_LOCKING)` on the `@Bean`), registered in `…AutoConfiguration.imports`.
  Default-on. Ordering is Spring's job here, not the core's.
- **9.4 Fixture support + tests ✅.** The fixture threads the last given-event's id into
  `CommandHandlerInvocation.latestSourcedEventId` (router parity), verified generically in
  `CommandHandlingTestFixtureTest.Interceptors` (plain capturing interceptor, no optimistic-locking reference). The
  interceptor itself is tested via a `@CommandHandlingTest` + `@Import` slice (`OptimisticLockingCommandInterceptorTest`,
  driven through the auto-wired fixture) plus `OptimisticLockingCommandInterceptorAutoConfigurationTest` — all green.
- **9.5 Example-application showcase ⬜ DEFERRED** (not needed for now). `ExceptionControllerAdvice` already maps
  `TransientException` → 409, so `OptimisticLockingException` would surface as 409 with no advice change if revisited.

---

### Stage 10 — Event-side `Error` classification policy ⬜ OPEN (decision pending)

**Question:** should a `java.lang.Error` thrown during event *handling* (by an `EventHandler` **or** an `EventInterceptor`) **terminate** the processor, or be **retried**?

**Current behaviour (as built):** it is **retried**. `EventHandlingProcessor.run()` wraps `catch (Error | RuntimeException)` around the `progressTracker.proceed(...)` supplier into an `EventProcessingFailure`; the classification switch terminates only on `NonTransientException`, so any other cause — including `Error` — falls to the retry branch. This is a **pre-existing, tested policy** (`EventHandlingProcessorTest.eventRetriedAndRecoveredSuccessfullyForTransientErrors` includes `Error.class` in its retry set), and interceptors are **consistent** with handlers under it. Note: `executeChain`'s `catch (RuntimeException e) { throw e; } catch (Exception e) { → InterceptorExecutionException }` does **not** affect this — `Error` is not an `Exception`, so it is never wrapped there and propagates straight to the loop's `Error | RuntimeException` catch. (An earlier `catch (RuntimeException | Error e)` clause in `executeChain` was **redundant** and its removal is behaviourally neutral.)

**The open call:** whether unrecoverable `Error`s (e.g. `OutOfMemoryError`, `StackOverflowError`) should instead be **terminal** rather than retried forever. That is a change to the loop's error **classification** (the `EventProcessingFailure` switch), *not* to any interceptor code, and — for consistency — it would apply to handler-thrown `Error`s too (so it would revise `eventRetriedAndRecoveredSuccessfullyForTransientErrors`). Deferred pending a maintainer decision; **do not** change interceptor code for this. If pursued, decide the handler/interceptor symmetry first, then adjust the switch + the affected regression test together.

---

## 5. Cross-cutting invariants to preserve

- **Empty applicable list = today's behaviour, allocation-free.** Both core components keep a convenience ctor
  defaulting to `List.of()`; the intercepted path is only taken when ≥1 interceptor applies.
- **Core stays Spring-free.** Components receive an already-ordered `List` (index 0 = outermost) and never sort.
- **Observer exactly-once / value at-least-once** is **type-directed** (keyed off continuation type), not
  side-directed. Event-side double-proceed is terminal.
- **`Proceeded` is never user-constructable.** Termination is exception-based, uniform with handlers.
- Existing no-interceptor tests are the regression suite; they must stay green throughout.

---

## 6. Status

> **Note:** Stages 3 and 4 were **flipped** at the maintainer's request — `CommandRouter` integration (Stage 4) was
> built before the event interceptor surface (Stage 3).

| Stage | State |
|---|---|
| 1 — Shared mechanism | ✅ **Done** — 16 tests green, NullAway clean |
| 2 — Command surface | ✅ **Done** — 8 driver tests green, NullAway clean |
| 4 — `CommandRouter` integration | ✅ **Done** — 6 wiring tests green; 26 existing tests green (empty-path regression) |
| 3 — Event surface | ✅ **Done** — 10 driver tests green, NullAway clean |
| 5 — `EventHandlingProcessor` integration | ✅ **Done** — 10 new interceptor tests green; existing processor tests green (empty-path regression); E-4 terminal |
| 6 — Autoconfigure wiring | ✅ **Done** — command + event sides wired (`List<EventInterceptor>` into the processor registrar) |
| 7 — `framework-test` fixture | ✅ **Done** — DSL + `@CommandHandlingTest` auto-wiring (+ `withInterceptors`); 199 tests green |
| 8 — E2E smoke | ✅ **Command side done** — `CommandAndEventHandlingIntegrationTest` (12 tests green on ESDB); **event side ⬜ pending** (one `@Order`-ed `EventInterceptor` `@Bean` through `EventHandlingProcessorIntegrationTest`) |
| 9 — Optimistic locking (provided) | ✅ **9.0–9.4 done** — marker + `ConcurrencyException`/`ConflictingWriteException`/`OptimisticLockingException`, interceptor, `CommandInterceptorOrders` + default-on auto-config, fixture parity; interceptor/fixture/auto-config tests green. 9.5 (example showcase) deferred |
| 10 — Event-side `Error` classification policy | ⬜ **Open** — `Error` during handling currently **retries** (consistent between handlers & interceptors, tested); decision pending on whether unrecoverable `Error`s should terminate instead (loop classification change, not interceptor code) |
