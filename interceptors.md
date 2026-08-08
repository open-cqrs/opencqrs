# OpenCQRS Interceptors — Design Handoff

> **Status:** Design in progress. **No implementation exists yet.** This document captures the agreed
> command-side **and** event-side interceptor interfaces, the reasoning behind them, the decisions
> made, and the work still open (registration/ordering/scoping, and integration into `CommandRouter`
> / `EventHandlingProcessor`).
>
> **Scope:** command-handling and event-handling interceptors only. Dispatch/bus interceptors are
> **out of scope**. Command *enrichment*/transformation and event *filtering*/veto are **deferred**
> to later, separate features.
>
> **Terminology:** we say **instance** / **state**, never "aggregate".
>
> **Companion:** [`tracing.md`](tracing.md) — the OpenTelemetry integration (the primary driver),
> built on this framework.
>
> **Challenge:** [`axon_comparison.md`](axon_comparison.md) — this design challenged against Axon
> Framework 5 (its `ProcessingContext`, the Axon 4 `UnitOfWork` replacement): gaps, extensibility,
> ergonomics, and what is still cheap to change.

---

## Mental model (read this first)

Two concepts carry the whole design; learn them before the reference below.

**`proceed()` and the `Proceeded` token.** Every interceptor is an **around**: do work, call
`continuation.proceed()` to run the wrapped stage, do more work, return. An **observer** continuation
returns an opaque **`Proceeded`** whose *only* source is `proceed()` itself — you cannot fabricate one.
So "return `Proceeded`" is enforced to mean "you actually proceeded": you can neither forget to proceed
nor stall the chain. An observer continuation is **exactly-once** — a *second* `proceed()` throws
`IllegalStateException`. (The **value** continuation used by the command root and `handler` returns a
real `R` instead, so it *may* be skipped — short-circuit — or re-invoked — retry-around-proceed; §3.6.)

**Observer vs. transformer — and what "skip" actually means.** There are two ways to *not* run the
wrapped stage, and they are not the same:

- **Throw** → aborts the whole operation; the exception propagates up and the command/event **fails**.
  Available to **any** hook, observer *or* transformer. This is how a **security deny**, a validation
  failure, or an optimistic-lock reject works — so a security interceptor is perfectly happy as an
  **observer** (throw on deny, `proceed()` on allow).
- **Return a substitute value without proceeding** → skips the stage but reports **success** with a
  different result. **Transformer-only**, because only a `StageTransformer` returns a real `V`. The
  canonical case is **idempotency**: return the cached result, don't run the handler, report success. An
  observer cannot express this — its only return is the `Proceeded` proof that it *did* proceed.

So: **throw to fail (anywhere); return-without-proceeding to succeed *differently* (transformer only).** A
`StageObserver` wraps a stage you must not silently substitute (sourcing, a state-apply, publish, an event
handler); a `StageTransformer` wraps a stage where success-value substitution is the designed behaviour
(the command `handler`, the whole command). Reach for the observer unless success-value substitution is
the point — a check that only ever throws is an observer.

---

## 1. Why / driving use-cases

The interfaces are designed to be challenged against real cross-cutting concerns:

- **OpenTelemetry tracing** — a span per meaningful step, correctly nested. Full integration design
  and worked interceptor in [`tracing.md`](tracing.md).
- **Metrics** — timing each step; e.g. *number of events sourced per instance* as a distribution.
- **User-defined security checks** — including checks that depend on rebuilt state, able to
  short-circuit (deny) by throwing.
- **Optimistic locking** — reject a command whose caller acted on a stale read-model version
  (event-id-as-`@Version`), closing the client-read→command window the write-time preconditions leave
  open.
- Idempotency (short-circuit returning a cached result) as a secondary reference case.

---

## 2. Current pipeline (facts the design is grounded in)

### 2.1 Command side — `CommandRouter.send(Command, Map<String,?> metaData)`

Fully **synchronous**, single method. Per command: resolve `CommandHandlerDefinition` → resolve
matching `StateRebuildingHandlerDefinition`s → **source** events (cache → ESDB read (I/O) → upcast →
convert → replay each to the state-rebuilding handlers, reconstructing the `instance`) → check
`SubjectCondition` → update cache → invoke the `CommandHandler` (which emits events via
`CommandEventPublisher`, each **captured** and **applied** to the state-rebuilding handlers) →
propagate metadata + build preconditions → **atomic append** of all captured events to ESDB.

Key facts:

- No `UnitOfWork`, no envelope, no `Headers` type; metadata is `Map<String,?>` everywhere.
- **"Publish" == the single atomic append** (optimistic concurrency via preconditions; conflicts
  possible).
- **Reading fuses I/O + apply** (rebuild loop reads and applies together); **writing decouples them**
  — events are applied to state *during* the handler, but written to the store as one batch *after*
  the handler returns.

### 2.2 Event side — `EventHandlingProcessor`

Asynchronous: an infinite `observe` loop per group + partition, single worker thread, with
retry/backoff and progress tracking. Per raw event:

```
progressTracker.proceed(group, partition, execution):
    (opens umbrella tx IFF proceedTransactionally=true, else PROPAGATION_NEVER)
    execution:
        - raw partition/sequence relevance                → may SKIP (progress still advances)
        - upcast + deserialize                            → 0..N Java events (fan out / drop to 0)
        - per converted event:
            - converted partition/sequence relevance      → may SKIP
            - per matching EventHandlerDefinition:
                handler.handle(...)                       ← @Transactional wraps HERE (per-handler txAdapter)
    progress UPDATE/INSERT                                 ← own REQUIRED tx (joins umbrella if present)
```

Key facts:

- **Transaction boundary is configurable** (`JdbcProgressTracker.setProceedTransactionally`): default
  `false` → each `@Transactional` handler has its own tx, progress committed separately (not atomic);
  `true` → one umbrella tx wraps all handlers **and** the progress update (atomic + idempotent for
  `DataSource`-managed resources).
- **No return value** — handlers are `void`.
- **Built-in retry** — a failed event isn't progress-advanced, so the observe loop re-delivers it;
  each retry is a **fresh `proceed` attempt**.
- **Two relevance kinds** via `EventSequenceResolver`: `ForRawEvent` (relevance from the raw event,
  known *before* upcast, never `PARTIAL`) and `ForObjectAndMetaDataAndRawEvent` (relevance per
  *converted* event, needs upcast — and this resolver **already upcasts every event on every
  partition** today).

---

## 3. Design principles

1. **Data travels as callback arguments, never as progressive context getters.** A single context
   with `Optional`/`List` getters that are empty "until a phase is reached" is temporal coupling.
   Each phase's data is delivered as **typed, guaranteed-present arguments** to that phase's advice.

2. **Three single-responsibility surfaces**, not one context: an immutable **invocation** (entry
   data), a **lifecycle** (registration only, no getters), and a **continuation** (flow control).

3. **One mechanism — `around` — at every join point.** Execution is a **nested around-tree**: a root
   interceptor is the outermost around; interior join points are registered on the lifecycle, each
   also around. One interceptor class opts into whichever granularities it needs — **no
   phase-specific interceptor interfaces**.

4. **The around body owns the boundary; callbacks own only the interior.** Boundary concerns (pre,
   post/result, `catch`, `finally`, short-circuit, retry) live in the around body; lifecycle hooks
   exist **only** for phases *interior* to the continuation that the body can't reach. **Commit-point
   caveat:** the around model does **not** mark the commit point. On the command side the append *is*
   the commit, so root post-`proceed()`/`finally` code runs *after* events are durable — a throw there
   surfaces as a command failure despite persisted events (committed side effect, reported error), and
   nesting order is **not** commit-relative order. On the event side the root runs *inside* the umbrella
   tx, so post-`proceed()` code runs *before* commit (a true after-commit action is the deferred
   `EventLifecycle.afterCommit`, §8.5). Axon's phase model draws this line explicitly; the around model
   does not — document it, don't rely on it ([`axon_comparison.md`](axon_comparison.md) §3.1/§4).

5. **The closure is the shared data plane.** A join point can only be intercepted from within an
   interceptor, so the enclosing invocation is always reachable. Join points carry **only phase-new
   data**. (Caveat: on state-apply / event join points, `metaData`/`subject` are the **event's**, not
   the command's — not redundant.)

6. **Continuation shape matches intent; enforcement follows from it.**
   - **Observers** wrap for observe/time/count and **must always proceed**. Enforced *structurally*:
     the continuation returns an opaque `Proceeded` token whose only source is `proceed()`, and the
     observer must return it — you cannot forget to proceed (nor stall the chain).
   - **Transformers / value-returning roots** return a real value `V` and **may short-circuit** —
     *return a substitute success value without proceeding* — because that is a designed feature there
     (**idempotency**: return a cached result instead of running the stage; result substitution). They
     are intentionally *not* enforced. **Denial is a *throw*, not a short-circuit:** an
     `AccessDeniedException` / validation failure aborts the operation and can come from *any* hook,
     observer or transformer — so security lives fine on an observer (see §5.3, which sits on `handler`
     only because that is the rebuilt-state/pre-handler point and `handler` is a transformer for
     *result-threading*, not for this).
   - The split is not arbitrary — it encodes **skip-is-dangerous vs. skip-is-a-feature.** Skipping an
     observer stage (`sourcing`, a `…EventInvocation` apply, `publish`, an event handler) would
     silently corrupt state or drop data, so it's forbidden; skipping a value-returning stage (the
     command `handler`, or the whole command) is a legitimate substitution (return a real `R` instead),
     so it's allowed. To act *later* on something seen in an observer stage — e.g. skip the `handler`
     based on a fact observed during sourcing — remember it in **per-invocation closure state** (a
     local in `intercept`, **never** an interceptor field: beans are shared across concurrent commands)
     and short-circuit in the value-returning stage. (If the rebuilt state itself is the fact, it's
     already on `CommandHandlerInvocation.instance()` — no remembering needed. If it should surface as
     an error, just `throw` from the observer.)
   - **Registration is one-shot, guarded at runtime (not type-safe).** Interior hooks must be
     registered on the lifecycle in the `intercept` body **before** `proceed()`. The lifecycle
     **freezes** when interior execution begins (the terminal `proceed()` reaches the framework core);
     any later `register` — the classic *register-after-proceed* mistake, or registering from inside
     another hook's advice — throws `IllegalStateException` rather than silently never firing. This
     *can't* be made type-safe: the only static fix (split registration into a method separate from the
     around) would break the per-invocation closure-state pattern above, forcing unsafe interceptor
     fields. So it's the **inverse of no-stall** — no-stall is structural (the `Proceeded` token),
     register-before-proceed is a fail-fast runtime guard. The observe-then-act case needs no dynamic
     registration: register both hooks up front and gate on closure state.

7. **Two structural pairings on the command side** (informs naming & reasoning):
   - Bulk store I/O: `sourcing` (read-all, before handler) ⟷ `publish` (write-all, after handler).
   - Per-event state-apply: `sourcedEvent` (replayed) ⟷ `publishedEvent` (emitted).
   - Nesting is asymmetric: `sourcedEvent` nests in `sourcing`; `publishedEvent` nests in `handler`
     (not in `publish`), because writing decouples apply from I/O.

8. **Additive-safe by construction.** The surface can grow without breaking user interceptors: new
   lifecycle hooks are non-breaking (users *call* the lifecycle, never implement it) and new
   join-point fields are non-breaking for accessor users (the framework constructs the JPs). Only
   three things are hard to change and are therefore fixed up front: the interceptor **root
   interfaces** (only `default` additions allowed), the **continuation/stage shapes**, and a hook's
   **observer-vs-transformer nature** (need transformation later → add a *new* hook, never flip an
   observer). Authoring rules that preserve the guarantee: don't implement the lifecycle types, and
   read join points via accessors rather than positional record deconstruction. This is why extra
   information/hooks are deliberately *not* front-loaded (see §7).

9. **Join-point naming signals where user code runs.** A **plain-noun** join point (`Sourcing`,
   `Publish`) wraps a **framework stage** — I/O + orchestration the framework performs itself. A
   **`…Invocation`** join point (`SourcedEventInvocation`, `PublishedEventInvocation`,
   `CommandHandlerInvocation`, `EventHandlerInvocation`) wraps a **single call into user-provided
   domain code** (a `StateRebuildingHandler` / `CommandHandler` / `EventHandler`) — the place to
   time/span/guard *someone's* code. The test is "does it wrap a domain-handler call *directly*":
   `Sourcing` *contains* SRH applies but wraps them only via the nested `SourcedEventInvocation`s, so
   it stays a stage. The entry carriers `CommandInvocation`/`EventInvocation` reuse "Invocation" in
   the broader **unit scope** (the whole request the root wraps) — a `CommandHandlerInvocation` nests
   inside a `CommandInvocation`; the `Command` vs `CommandHandler` qualifier keeps them distinct.

---

## 4. Shared mechanism — package `com.opencqrs.framework.interceptor`

Reused by both the command and event sides.

```java
public final class Proceeded {          // opaque proof-of-proceed; framework-only constructor
    Proceeded() {}
}

@FunctionalInterface
public interface Continuation {         // observer continuation — exactly-once; cannot be faked (2nd proceed() throws)
    Proceeded proceed() throws Exception;
}

@FunctionalInterface
public interface ValueContinuation<V> { // value continuation — may be skipped (short-circuit) or re-invoked (retry)
    V proceed() throws Exception;
}

@FunctionalInterface
public interface StageObserver<J> {                       // MUST proceed (returns Proceeded)
    Proceeded around(J joinPoint, Continuation continuation) throws Exception;
}

@FunctionalInterface
public interface StageTransformer<J, V> {                 // MAY short-circuit / substitute value
    V around(J joinPoint, ValueContinuation<V> continuation) throws Exception;
}
```

---

## 5. Command side — package `com.opencqrs.framework.command.interceptor`

```java
public interface CommandInterceptor<C extends Command> {
    Class<C> commandClass();                                              // assignability gate (explicit; Spring-free)

    <R> R intercept(CommandInvocation<C> invocation, CommandLifecycle<R> lifecycle,
                    ValueContinuation<R> continuation) throws Exception;     // root (may short-circuit)
}

public record CommandInvocation<C extends Command>(C command, Map<String, ?> metaData) {
    public String subject() { return command.getSubject(); }
}

public interface CommandLifecycle<R> {
    void sourcing(StageObserver<Sourcing> advice);                          // ONCE — store read + rebuild
    void sourcedEvent(StageObserver<SourcedEventInvocation> advice);        // per replayed event → state
    void publishedEvent(StageObserver<PublishedEventInvocation> advice);    // per emitted event → state
    void handler(StageTransformer<CommandHandlerInvocation, R> advice);     // ONCE — command handler (value)
    void publish(StageTransformer<Publish, Publish> advice);                // ONCE — shape the append request (transform)
}

// join points (only phase-new data; command/metaData come from CommandInvocation)
public record Sourcing(Class<?> instanceClass, SourcingMode sourcingMode) {}

public record SourcedEventInvocation(StateRebuildingHandlerDefinition<?, ?> definition,
                                     @Nullable Object inputInstance, Object event,
                                     Map<String, ?> metaData, String subject, Event rawEvent) {}   // rawEvent non-null

public record PublishedEventInvocation(StateRebuildingHandlerDefinition<?, ?> definition,
                                       @Nullable Object inputInstance, Object event,
                                       Map<String, ?> metaData, String subject) {}                  // no rawEvent

public record CommandHandlerInvocation(CommandHandlerDefinition<?, ?, ?> definition, @Nullable Object instance,
                                       @Nullable String latestSourcedEventId) {}   // head of sourced stream = state version

public record Publish(List<CapturedEvent> events, List<Precondition> additionalPreconditions) {}   // transformer: the append request
```

> **Naming (see §3.9):** plain-noun JPs (`Sourcing`, `Publish`) wrap **framework stages**;
> `…Invocation` JPs wrap **a single domain-handler call**. The entry carrier `CommandInvocation` uses
> "Invocation" in the broader unit sense (the whole command being processed).

### 5.1 Join-point field rationale

| Join point | Fires | Fields | Notes |
|---|---|---|---|
| `Sourcing` | once, before handler | `instanceClass`, `sourcingMode` | from `CommandHandlerDefinition`; wraps whole rebuild incl. store read; fires even under `SourcingMode.NONE` |
| `SourcedEventInvocation` | per replayed event | `definition`, `inputInstance` (nullable), `event`, `metaData` (event's), `subject` (event's), `rawEvent` (**non-null**) | `subject` can differ from command subject under `RECURSIVE` |
| `PublishedEventInvocation` | per emitted event | `definition`, `inputInstance`, `event`, `metaData` (event's), `subject` | **no** `rawEvent` — not persisted yet |
| `CommandHandlerInvocation` | once | `definition`, `instance` (rebuilt state; populated even for `ForCommand`), `latestSourcedEventId` (nullable) | `latestSourcedEventId` = head of the sourced stream **incl. cache-served events** (the state's version, = the `SubjectIsOnEventId` token); `null` when nothing sourced; continuation threads `V = R` |
| `Publish` | once, after handler | `events`, `additionalPreconditions` | **transformer** `V = Publish` (the append request): advice may rewrite events / additional-preconditions or veto by throwing; the core appends the returned request (append not wrapped by advice). Per-event preconditions live on the `CapturedEvent`s; `additionalPreconditions` are the framework guards on top (name matches `ImmediateEventPublisher`) |

### 5.2 Nesting tree

```
intercept (root; may short-circuit)
└─ continuation.proceed()
   ├─ sourcing                          ← store read + rebuild (once)
   │  ├─ sourcedEvent  (event #1)
   │  └─ …
   ├─ handler                           ← command handler (once); threads R
   │  ├─ publishedEvent (emit #1)
   │  └─ …
   └─ publish                           ← shape append request (once; transformer). core appends after the chain
```

### 5.3 Examples

**Tracing** — the primary driver; the full worked interceptor (command + event, continue-only and
originate policy) lives in [`tracing.md`](tracing.md) §6.

**Metrics** (count events sourced per instance):

```java
class SourcingMetricsInterceptor implements CommandInterceptor<Command> {
    public Class<Command> commandClass() { return Command.class; }
    public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont) throws Exception {
        var sourced = new AtomicInteger();
        lc.sourcedEvent((jp, c) -> { sourced.incrementAndGet(); return c.proceed(); });
        try {
            return cont.proceed();
        } finally {
            metrics.distribution("cmd.events.sourced", sourced.get(),
                                 "command", inv.command().getClass().getSimpleName());
        }
    }
}
```

**State-dependent security** (deny by *throwing* — not a value short-circuit; works from any hook, on
`handler` here only because that is the rebuilt-state, pre-handler join point):

```java
class StateBasedSecurityInterceptor implements CommandInterceptor<SecuredCommand> {
    public Class<SecuredCommand> commandClass() { return SecuredCommand.class; }   // sealed base — gates to its permits

    public <R> R intercept(CommandInvocation<SecuredCommand> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont) throws Exception {
        lc.handler((jp, c) -> {
            if (!policy.mayExecute(inv.command(), jp.instance()))   // inv.command() is SecuredCommand — no cast
                throw new AccessDeniedException(inv.command());
            return c.proceed();
        });
        return cont.proceed();
    }
}
```

**Optimistic locking** — **framework-provided** (package `com.opencqrs.framework.command.interceptor.optimisticlocking`;
reject a command whose caller acted on a stale read). Opt-in via the marker interface; the bean is default-registered
(`@ConditionalOnMissingBean`) and inert for any command that doesn't implement it:

```java
interface EventIdExpectingCommand extends Command {         // mix-in: any command may also implement this
    String expectedEventId();   // head event id the caller's read reflected for this subject
}

class OptimisticLockingCommandInterceptor implements CommandInterceptor<EventIdExpectingCommand> {
    public Class<EventIdExpectingCommand> commandClass() { return EventIdExpectingCommand.class; }  // only versioned commands pay

    public <R> R intercept(CommandInvocation<EventIdExpectingCommand> inv, CommandLifecycle<R> lc,
                           ValueContinuation<R> cont) throws Exception {
        lc.handler((jp, c) -> {                              // fail-fast, at the rebuilt-state / pre-handler point
            if (!Objects.equals(jp.latestSourcedEventId(), inv.command().expectedEventId()))
                throw new OptimisticLockingException(
                        inv.command().getSubject(), inv.command().expectedEventId(), jp.latestSourcedEventId());
            return c.proceed();
        });
        return cont.proceed();
    }
}
```

Reads the sourced head id straight off the `handler` join point (`jp.latestSourcedEventId()` — the field the gap
closure added for exactly this). Closes the *client-read → command* window the write-time `SubjectIsOnEventId`
preconditions don't: the command sources the *current* state, but the caller decided on an older version. The exposed
id is **cache-correct** — reconstructing it from a `sourcedEvent` hook (as an earlier draft did) silently misses events
served from the `StateRebuildingCache`, admitting stale commands; that is *the* reason the value is surfaced by the
router rather than recomputed in the interceptor. Caveats: it is **subject/hierarchy-scoped** (the head of the sourced
stream, not a global watermark) — under `RECURSIVE` the id is the hierarchy head (the instance's version); read-model
lag makes rejects conservative (any change rejects — callers refresh + retry, hence the exception is **transient**); and
it requires sourcing (`SourcingMode.NONE` exposes `null`). `OptimisticLockingException` extends the shared
`ConcurrencyException` base (see the exception note in the implementation hand-off), so a single `catch
(ConcurrencyException)` covers both this pre-handler reject and the store's append-time conflict.

---

## 6. Event side — package `com.opencqrs.framework.eventhandler.interceptor`

All hooks are **observers** (handlers are `void`; no transform / no short-circuit — filtering is
deferred). The root sits **inside `progressTracker.proceed`'s execution** (inside the umbrella tx
when enabled), and is re-invoked **once per retry attempt**.

```java
public interface EventInterceptor {
    /** Which events this interceptor wants delivered. Default = quietest (real work only). */
    default Delivery delivery() { return Delivery.ACTIONABLE; }

    Proceeded intercept(EventInvocation invocation, EventLifecycle lifecycle,
                        Continuation continuation) throws Exception;         // root observer (must proceed)
}

public enum Delivery {           // nested filters, finest → coarsest
    ACTIONABLE,   // partition-relevant AND >=1 matching handler   (sees YES / PARTIAL)   [default]
    PARTITIONED,  //  + partition-relevant but no matching handler
    ALL           //  + wrong-partition / dropped-to-0             (can see NO)
}

public enum Relevance { YES, NO, PARTIAL }   // partition relevance across the fan-out

public record EventInvocation(Event rawEvent, String group, long partition, Relevance relevance) {}

public interface EventLifecycle {
    <E> void handler(Class<E> eventClass, StageObserver<EventHandlerInvocation<E>> advice);   // gated by assignability, typed
    default void handler(StageObserver<EventHandlerInvocation<Object>> advice) {             // convenience: all events
        handler(Object.class, advice);
    }
}

public record EventHandlerInvocation<E>(EventHandlerDefinition<?> definition, E event, Map<String, ?> metaData) {}
// rawEvent / group / partition come from EventInvocation (closure)
```

### 6.1 Relevance, delivery, and cost

- **`Relevance`** aggregates partition relevance across the upcast fan-out: all relevant → `YES`,
  none → `NO`, mixed → `PARTIAL`. `PARTIAL` can only arise for `ForObjectAndMetaDataAndRawEvent`
  (per-converted relevance) — which already upcasts everything — so **no extra upcast cost** is
  introduced (**Variant O**): `ForRawEvent` yields `YES`/`NO` from the raw event and upcasts only on
  `YES`; the converted resolver upcasts as it already does.
- **`Delivery`** lets each interceptor choose its own noisiness; the framework evaluates only as deep
  as the **union** of registered levels requires. With the default `ACTIONABLE` (or no interceptors)
  the current fast path is preserved exactly, but two cases must not be conflated:
  - **wrong-partition `ForRawEvent`** events are skipped **pre-upcast** — no upcast, root not fired
    (the real fast path);
  - **no-handler but partition-relevant** events **still upcast** (exactly as today — a matching
    handler is only knowable post-upcast); the interceptor simply isn't fired.

  `NO` reaches only `ALL`-level interceptors, and even then a `ForRawEvent` `NO` fires the root from
  the raw event alone (no upcast; `proceed()` no-ops).
- **Upcast drop-to-0:** representable with no new state — `ForRawEvent` keeps its `YES`/`NO` verdict
  with zero `handler` hooks ("owned but nothing produced"); the converted resolver aggregates the
  empty set to `NO`.

### 6.2 Nesting tree

```
[retry loop → one interception per attempt]
intercept (root; per raw event, delivered per Delivery level)     [EventInvocation: rawEvent, group, partition, relevance]
└─ continuation.proceed()
   └─ per relevant converted event:
      ├─ handler (defn A)     ← wraps handler.handle() incl. its per-handler tx
      └─ handler (defn B)
```

### 6.3 Example — event tracing

The worked `OtelEventTracingInterceptor` lives in [`tracing.md`](tracing.md) §6.4: a `handler`
observer per matching handler plus a root span per event, both nested under the trace resurrected by
`TraceAwareEventReader`. See [`tracing.md`](tracing.md) for the persistence / resurrection / span
split and the continue-only default.

### 6.4 Error & termination semantics

Event interceptors are observers that must `proceed()`, but they participate in the **same
exception-based control flow as event handlers** — this, not a return value, is the channel for
ending the loop:

- throw `CqrsFrameworkException.NonTransientException` → the processing loop **terminates**
  unrecoverably (the "persistent error");
- throw anything else / `TransientException` → **retry** with backoff, then skip on exhaustion.
- a **continuation-contract violation** — a second `proceed()` on an observer `Continuation` (§3.6) — is
  **terminal**: a deterministic bug retry can't fix, so the processor treats the resulting
  `IllegalStateException` as non-transient rather than retrying it forever and blocking the partition (§9.2).

Termination stays exception-based deliberately: exceptions propagate correctly through the *nested*
interceptor chain automatically, whereas a return-based signal would have to be forwarded by every
layer and could be dropped by an observer returning its own `Proceeded`. It is also why `Proceeded`
is **not** a user-constructable / sealed result — a fabricatable termination token would defeat the
no-stall guarantee (§3.6). So `proceed()` stays single-method.

Optional, additive-later: a dedicated, discoverable termination exception (e.g. a subtype of
`NonTransientException`) so the intent reads at the call site — documentation/ergonomics, not a new
return channel.

---

## 7. Reserved / smaller open points

- **Final holistic naming + redundancy review** — reserved for the end of the design pass.
- **`Publish` is a transformer** — `StageTransformer<Publish, Publish>` over the append request
  (`events` + `preconditions`); advice rewrites the request or vetoes by throwing, and the core appends the returned
  request (the append is not wrapped by advice, so there is no post-commit body). This is the reference seam for
  metadata propagation and precondition-contributing interceptors.
- **Wildcards on definitions** (`<?,?,?>`, `<?>`) are deliberate — cross-cutting interceptors don't
  know `I`/`C`/`R`/`E` statically; `R` reappears only as the threaded return value.
- **No `CommandEventPublisher` exposed** on any join point — capturing events is the domain handler's
  job; interceptors see (and may transform) the captured events at `publish`.
- **State-sourcing cache is not an interceptor** — it stays the `StateRebuildingCache` SPI. The
  caching logic (incremental read range from the cached `eventId`, seeding replay with the cached
  instance, and the `sourcedSubjectIds` that feed the `SubjectIsOnEventId` preconditions) is a
  transformation living in the `CommandRouter.send` closure — not something an observer `sourcing`
  hook could do. Cache effectiveness is already observable via `sourcedEvent` counts.
- **Deferred features (all additive-safe per §3.8, so intentionally not front-loaded):** command
  enrichment/transformation; event filtering/veto; retry-/skip-specific event hooks; a handler-match
  indicator on `EventInvocation`; **event retry** (would be
  a `boolean retry()` on `EventInvocation` from `RetryHandler.isRetryExecution()`; attempt-count and
  backoff-interval intentionally excluded — no such state is retained today); **event-side after-commit**
  (an `EventLifecycle.afterCommit(...)` registration the *processor* runs after `progressTracker.proceed`
  returns and the umbrella tx + progress **durably commit** — i.e. *outside* the interceptor around-tree,
  which sits inside the tx; today the ES-native answer is an outbox: append a follow-up event and react to
  it — [`axon_comparison.md`](axon_comparison.md) §3.1); **event dead-lettering** (a *third* interceptor
  outcome distinct from the `SkipEvent` above — skip-and-advance ≠ dead-letter — reserved for if/when a
  `SequencedDeadLetterQueue`-style path is added, so the binary terminate/retry model doesn't calcify —
  [`axon_comparison.md`](axon_comparison.md) §3.2).
- **Exactly-once for observers; at-least-once for value continuations (type-directed, not
  side-directed).** A `StageObserver`'s `Continuation` is **exactly-once**: it must proceed (no-stall),
  and a *second* `proceed()` throws `IllegalStateException` **before** re-running the stage — re-observing
  a side-effecting stage (`sourcing`, a state-apply, `publish`, an event handler) is never legitimate and
  would corrupt state or double-execute. A `ValueContinuation` (the root and `handler`) is
  **at-least-once**: re-invocation stays legal, which is what enables command retry-around-proceed (§8.5,
  Lock-in A). The guard therefore keys off the continuation *type*, not the command/event side. On the
  event side (all observers) a double-`proceed()` is a **terminal** contract violation — a deterministic
  bug retry can't fix, so the processor treats it as non-transient rather than retrying forever (§6.4, §9.2).

---

## 8. Registration, ordering & scoping

### 8.1 Type targeting (assignability gate)

- **Command** — `CommandInterceptor<C extends Command>` declares an explicit abstract
  `Class<C> commandClass()` (no convenience interface). The framework composes an interceptor into a
  command's chain only when `commandClass().isAssignableFrom(command.getClass())`. Generic + explicit
  `Class` gives a type-safe `CommandInvocation<C>` with no reflection — mirroring
  `EventHandlerDefinition<E>(Class<E>, …)`. All-commands interceptors declare
  `CommandInterceptor<Command>` returning `Command.class`. (A generic `default` returning
  `Command.class` is impossible: `Class<Command>` ≠ `Class<C>`.)
- **Event** — targeting lives on the `handler` registration: `<E> void handler(Class<E> eventClass,
  StageObserver<EventHandlerInvocation<E>>)`, callable multiple times, each gated by
  `eventClass.isAssignableFrom(convertedEvent.getClass())` and yielding a typed `E event()`. Richer
  than the command side (per-type advice) and correct — the Java type is only known post-upcast, per
  converted event.
- **Root vs. interior (event).** The root (`intercept`) cannot be gated by these `handler`
  registrations — they run *inside* `intercept`, so they aren't known when the framework decides to
  fire the root (chicken-and-egg). The root fires per `Delivery`/`Relevance`; a type-scoped
  interceptor keeps its root body tolerant of events its `handler` gates don't match. A separate
  declared root-level event-type gate is deferred.
- **Escape hatch.** Non-type predicates: target a broad base (`Command` / `Object.class`) and refine
  with `instanceof` inside.

### 8.2 Ordering

- **Inter-interceptor.** The core receives an **ordered `List`**, **index 0 = outermost**, and has
  **no order hook** (stays Spring-free). The autoconfigure layer relies on Spring sorting collection
  injection (`List<CommandInterceptor>`) by `@Order` / `Ordered` / `@Priority`: **lowest value
  = highest precedence = outermost** (runs first inbound, last outbound). Ties default to discovery
  order (documented, not hash order).
- **Intra-interceptor.** Multiple registrations on the same hook nest in **registration (call) order
  — first registered = outermost**. Same "earlier = outer" rule as the chain.
- **Composite at a join point** = interceptors in chain order, and within each, its registrations in
  call order, all outer→inner — so root and interior nesting never cross.

### 8.3 Bean discovery & wiring

Interceptors are ordinary Spring beans, collected as an `@Order`-sorted `List` and passed into the
core components' constructors — mirroring the existing `List<CommandHandlerDefinition>` /
`List<EventHandlerDefinition>` wiring. **The core stays Spring-free**: it receives an ordered `List`
(index 0 = outermost, §8.2) and never sorts. Spring injects an **empty** list when no interceptor
beans exist, so there is no null/optional handling.

- **Command.** `CommandRouter` gains a `List<CommandInterceptor>` constructor parameter
  (raw-typed, `@SuppressWarnings("rawtypes")`, like its `List<CommandHandlerDefinition>` param).
  `CommandRouterAutoConfiguration.openCqrsCommandRouter(...)` gains a matching method parameter that
  Spring injects **sorted by `@Order`/`Ordered`/`@Priority`**. The router holds the full list and, per
  command, filters by `commandClass().isAssignableFrom(command.getClass())` and composes the applicable
  chain (§9).
- **Event.** `EventHandlingProcessor` gains a `List<EventInterceptor>` constructor parameter.
  Wiring flows through the existing `EventHandlingProcessorRegistrar` (`SmartInitializingSingleton`):
  it injects the `@Order`-sorted `List<EventInterceptor>` and adds it as a constructor arg to
  **every** processor bean definition (one per group×partition). Interceptors are therefore **global
  across all processors** — cross-cutting, not group-scoped. `EventInterceptor` is non-generic,
  so the list is clean (no raw type).
- **Provided (§8.6) & synthesized (§8.4) interceptors need no special path** — they are just beans
  with `@Order`, gated by their own `@ConditionalOn…`, landing in the same sorted list.
- **Backward-compatible + zero-overhead empty path.** Both core constructors keep a convenience
  overload defaulting to `List.of()`, so existing callers (`framework-test`, examples) compile
  unchanged and a no-interceptor app behaves exactly as today. §9 must keep the empty-list path
  allocation-free (no chain object, direct call).

**Still open — finer scoping** (beyond the type gate): per instance-type (command) / per
group or partition (event). Not needed for the driving use-cases; additive later (a declared predicate,
or a group filter on the interceptor).

### 8.4 Annotation convenience (Spring layer only)

Initially **one** convenience annotation: **`@BeforeCommandHandling`** — before-advice on the command
`handler` join point. A `BeanDefinitionRegistryPostProcessor` (mirroring
`CommandHandlingAnnotationProcessingAutoConfiguration`) synthesizes one `CommandInterceptor<C>`
per annotated method:

- **Type gate inferred** from the `Command`-assignable parameter — no `commandClass()` boilerplate.
- **Parameters** (command, rebuilt `instance`/state, `metaData`, `@Autowired` beans) resolved via the
  existing `AutowiredParameterResolver` + parameter-introspection machinery.
- The synthesized interceptor registers a `handler` bracket invoking the method **before**
  `proceed()`; **throwing vetoes** the command (propagates to the caller). `@Order` sets chain
  position.

Lives **only in the autoconfigure module**; the core stays around-based and Spring-free — sugar
projecting onto a before-point (à la Spring AOP `@Before` over `@Around`), not a new phase-specific
core interface.

**Out of scope (use the full interceptor interface):** around / cross-phase state (tracing, timing),
short-circuit-by-return (idempotency), result transformation. **Additive-later, on demand:**
`@AfterCommandHandling`; event-side `@Before/AfterEventHandling` (observation-oriented — a throw there
means retry, not skip); `@AfterAppend`.

### 8.5 Extensibility stress-test

The additive-safety claim (§3.8) was stress-tested against likely future features. Each lands as a new
lifecycle hook, a new `default` method, a new JP field, or a new *sibling* mechanism — none requires
changing a fixed-surface type — **provided the two lock-ins below hold**.

| Future feature | Verdict | Non-breaking path |
|---|---|---|
| Command enrichment / transformation (incl. state-dependent) | additive | new `CommandLifecycle` transform hook (e.g. `enrich` between `sourcing` and `handler`); framework threads the enriched `CommandInvocation` to the handler; add the enriched command to the `CommandHandlerInvocation` JP if needed (additive field) |
| …requiring a `CommandEnvelope` | additive (Lock-in B) | `CommandInvocation<C>` already *is* the envelope; no new type forced |
| Event filtering (skip + advance progress) | additive, **not** via the observer | sibling: a `SkipEvent` exception (loop advances progress) or an `EventFilter` predicate gate — observers still must proceed |
| Event transformation (decrypt / normalise) | additive | new transform hook / sibling; observers stay observers |
| Command optimistic-retry (conflict → re-run) | additive / already expressible (Lock-in A) | around-body loop re-calling `proceed()` |
| Command result transform / wrap | already supported | `handler` is a `StageTransformer`; root returns `R` |
| Metadata on produced events (incl. **existing metadata propagation**) / precondition contribution | **done** | the `publish` stage **is** the transform seam: `publish(StageTransformer<Publish, Publish>)` over the append request (`events` + `additionalPreconditions`). No separate `prepareAppend` hook (an earlier draft's plan) — that would have been a second, confusingly-similar around-the-append hook. **Metadata propagation is now the `MetaDataPropagatingCommandInterceptor` (`framework.metadata`)** — the router no longer propagates; a causation/correlation provider reads `inv.command()` from the closure. |
| Finer scoping (per-tenant / annotation / predicate) | additive | new `default` method on the interceptor interface |
| Dispatch / bus interceptors (distribution) | additive | separate interceptor *family* + chain, layered above; command/event contracts untouched |
| New `Delivery` / `Relevance` levels | additive (low risk) | new enum constant; framework switches carry defaults |
| Async / reactive handling | **not additive — frontier** | changes continuation return types; whole-framework paradigm shift (framework is synchronous by design) → a new async family. Out of scope. |

**Lock-in A — the *value* continuation permits ≥1 `proceed()`.** `ValueContinuation` (the root and
`handler`) is at-least-once: re-invocation must stay legal, or command retry-around-proceed is
permanently foreclosed. Idempotency of the framework operation under re-proceed is the retry author's
concern, not the framework's. This is deliberately **not** true of the observer `Continuation`, which is
**exactly-once** (a second `proceed()` throws — §3.6): re-observing a side-effecting stage is never
legitimate, and on the event side a double-`proceed()` is a terminal contract violation, not a retry.

**Lock-in B — `CommandInvocation` is the frozen entry envelope.** Keep
`CommandInvocation<C>(C command, Map<String,?> metaData)`. If typed `Headers` are ever needed, add a
**derived accessor** `Headers headers()` (a non-canonical record method — additive) viewing the map;
never change the `metaData` component or introduce a parallel `CommandEnvelope` into `intercept`. This
keeps the envelope abstraction additive rather than a root-signature break.

**Lock-in C (reaffirms §3.8) — observers are never retrofitted into transformers.** Every
transformation feature above adds a *new* hook alongside the observer; none flips an existing hook's
return type.

**Lock-in D — no framework-managed shared context; closures are per-interceptor.** The framework
deliberately provides **no** UnitOfWork/`ProcessingContext`-style shared data plane: `intercept` takes no
shared-context parameter, and one `intercept` call's closure is private to it. This is a *chosen*
absence, not an oversight (cf. Axon 5's `ProcessingContext` + `ResourceKey`;
[`axon_comparison.md`](axon_comparison.md) §2). It stays additive because the two channels a user might
eventually want both have homes that need no signature change:

- **interceptor → domain handler** — the deferred command/metadata **enrichment** feature (an
  interceptor wraps the command / adds metadata; the handler reads it via existing parameter resolution).
  Not a new mechanism.
- **interceptor ↔ interceptor** — if ever required, a framework-managed thread-local
  `ProcessingScope.current()` (mirroring OTel `Context.current()`), set around the root and cleared after.
  The synchronous model makes this safe on both sides (command = caller thread; event = the per-processor
  virtual thread, no hop). Additive later — **no change to `intercept`'s signature or to the entry
  carriers**, so it does not disturb Lock-in B.

The flagship use-case already validates the stance: tracing shares nothing through the framework — it
composes via OTel's ambient `Context.current()` ([`tracing.md`](tracing.md) §2). "No shared plane" means
"no *framework-provided* plane; bring (or, later, opt into) a thread-local."

**Command state-apply gating (asymmetry with the event side, intentional).** The command interior
state-apply hooks (`sourcedEvent`/`publishedEvent`) have **no `Class<E>` gate** and non-generic JPs
(`…Invocation` with `Object event`): targeting a specific event type inside the fold is niche, and
`instanceof jp.event()` covers it. The event side's typed gate is justified because event *handlers*
are the type-keyed unit. Additive-safety wrinkle: a **filtering-only** overload
(`sourcedEvent(Class<E>, StageObserver<SourcedEventInvocation>)`, JP unchanged) is additive anytime,
but a **typed** variant needs a generic `SourcedEventInvocation<E>` — and adding a type parameter to a
record is **breaking**, so typed gating is a now-decision, not additive. Current stance:
**non-generic, ungated**.

### 8.6 Provided interceptors (built-ins)

Interceptors the framework ships (or plans to) — auto-configured, OTel-/property-gated as noted —
distinct from the user-written examples in §5.3. All are ordinary interceptor beans (§8.2 ordering);
none extends the core surface.

| Interceptor | Concern | Status | Notes |
|---|---|---|---|
| Metadata propagation | Copy configured command-metadata keys onto produced events | **done** | `MetaDataPropagatingCommandInterceptor` (`framework.metadata`) rewrites event meta-data at the `publish` stage; `PropagationUtil` removed (logic inlined). Property-driven (`MetaDataPropagationProperties`); the bean is registered only when `mode != NONE` **and** keys are configured. Ordered outermost (`CommandInterceptorOrders.META_DATA_PROPAGATION`) so it applies last |
| Optimistic locking | Reject a command built on a stale read-model version | candidate | command-side; ships a `VersionedCommand` mix-in + interceptor type-gated to it; fail-fast pre-handle; needs no new hooks (see §5.3) |
| OTel tracing | Span lifecycle (+ trace resurrection) | candidate | see [`tracing.md`](tracing.md); composes via `Context.current()` with `EsdbClient` persistence + `TraceAwareEventReader` |

### 8.7 Test support (`framework-test`)

`CommandHandlingTestFixture` runs the command through the interceptor chain (reusing the core
chain-composer, §9) around its stubbed sourcing/handler/publish. Because the fixture is a **shared
lazy singleton** under `@CommandHandlingTest`, interceptor selection is **immutable derivation**
(mutation would leak across test methods and race under parallel execution):

- `withAdditionalInterceptors(CommandInterceptor…)` → a **new** fixture = current set + the
  given ones (appended inner-most, in argument order);
- `withoutInterceptors()` → a **new** fixture with an empty set;
- (replace = `withoutInterceptors().withAdditionalInterceptors(…)`.)

Base set:

- **Non-Spring:** empty; `withAdditionalInterceptors(a, b)` yields `[a, b]`.
- **Spring (`@CommandHandlingTest`):** the `@Order`-sorted `List<CommandInterceptor>`
  auto-wired from the **sliced** context. The slice keeps its **default filters** — framework
  auto-config interceptors (tracing, metrics, …) are **not** present and are **never** auto-applied;
  to exercise a framework-provided one (e.g. the versioned-command interceptor), `@Autowired` it and
  `withAdditionalInterceptors(it)`.
- **Class-level opt-out:** `@CommandHandlingTest(withInterceptors = false)` (default `true`) makes the
  pre-configured fixtures' base set **empty** — equivalent to `withoutInterceptors()` pre-applied to
  every injected fixture — for classes focused solely on command handling; per-method
  `withAdditionalInterceptors(…)` still layers on top. Feasible via a `ContextCustomizerFactory`
  reading the annotation attribute (the standard slice-attribute mechanism) and gating the fixture
  builder's base list.

**Why not force framework interceptors into the slice:** it would (a) demand mocking their
collaborators in every focused test, and (b) let a framework upgrade shipping a *new* provided
interceptor silently break existing tests. Opt-in keeps focused tests stable across upgrades and
mock-free by default. (An empty set — the norm for a focused test — runs exactly as today.)

**Verification uses the established assertions — no interceptor-specific DSL.** Effects that matter
to a command test are already observable: deny/reject → `fails().throwing(…)`; idempotency
short-circuit → `succeeds().havingResult(…)` + no events; metadata enrichment →
`EventAsserter.metaData(…)`; state → `havingState(…)`. **Pure-observer** interceptors (tracing,
metrics) have no command-level effect and are tested in their **own** unit tests with the right
harness (in-memory span exporter, `SimpleMeterRegistry`) — the fixture stays uncoupled from
OTel/Micrometer.

### 8.8 Test strategy (for the §9 implementation)

Test each concern at its natural altitude, with **no overlap** — push mechanism *down* so the
already-heavy router/processor tests only verify wiring. The **existing no-interceptor tests stay
green** and are the empty-list / zero-overhead regression.

| Altitude | Coverage | Home |
|---|---|---|
| **Mechanics** | composition order (outer→inner + registration order), `Proceeded`/no-stall, register-after-`proceed()` freeze → `IllegalStateException`, transformer short-circuit, exception unwinding, delivery-union — fake stages + fake terminal op | **`framework.interceptor`** unit tests (load-bearing suite) |
| **Command wiring** | each hook fires at the correct JP with correct JP data; `commandClass()` filtering; ordering; throw/short-circuit propagation — recording fake interceptor over the *real* pipeline (`EventReader`/`ImmediateEventPublisher` already mocked) | **`CommandRouterTest`** (primary) |
| **Event wiring** | root per event (once per retry attempt); `handler` per matching `EventHandlerDefinition`; `Delivery` gating + `Relevance`, incl. **Variant-O: no forced upcast** (assert the upcast callback is *not* invoked for wrong-partition `ForRawEvent`); placement inside `progressTracker.proceed`; throw → retry/terminate | **new `EventHandlingProcessorInterceptorTest`** — own minimal mocks; do **not** bloat `EventHandlingProcessorTest` |
| **E2E smoke** | an interceptor `@Bean` auto-wired into the real component runs end-to-end through ESDB, `@Order` respected | `CommandAndEventHandlingIntegrationTest` / `EventHandlingProcessorIntegrationTest` — **one/few** each; keep their existing focus |
| **Test support** | `withAdditionalInterceptors`/`withoutInterceptors` behaviour + ordering + immutability (DSL); auto-wired base set + `@CommandHandlingTest(withInterceptors=false)` + layering (Spring) | `CommandHandlingTestFixtureTest` (new `@Nested`) / `CommandHandlingTestAutoConfigurationTest` |

Tests for the *interceptor implementations themselves* (optimistic locking, the propagation
migration, `@BeforeCommandHandling`, tracing) ship with those features — out of scope here.

## 9. Not yet designed (integration)

1. **`CommandRouter` integration** — thread the composed interior-advice chains into `send(...)`
   (sourcing lives inside `StateRebuildingCache.fetchAndMerge`; applies inside
   `Util.applyUsingHandlers`; append at `ImmediateEventPublisher.publish`).
2. **`EventHandlingProcessor` integration** — insert the root + `handler` chain inside
   `progressTracker.proceed`'s execution; compute `Relevance` per Variant O; gate invocation by the
   union of registered `Delivery` levels.
3. **Pre-append transform hook + metadata-propagation migration** — introduce the pre-append
   event-transform hook as an **around-shaped `prepareAppend(StageTransformer<PrepareAppend,
   List<CapturedEvent>>)`** on `CommandLifecycle` (nesting between `handler` and `publish`; JP carries
   only the pending events — command + metadata come from the `CommandInvocation` closure, so no separate
   context type). Move `PropagationUtil` (`CommandRouter` line 267, driven by
   `MetaDataPropagationProperties`) behind it as its **first client**, slimming `CommandRouter` to
   source → handle → append + preconditions. Config moves onto a property-driven propagation interceptor
   bean (registered only when keys/mode are configured → default `NONE` behaviour preserved). A
   causation/correlation provider (the natural next client) needs no new hook shape — it reads the
   triggering command from the closure. This settles the pre-append hook's shape.

---

## 10. Decision log

| Fork | Choice |
|---|---|
| Mechanism shape | Around + lifecycle callbacks (hybrid) |
| Interior data delivery | Callback arguments, not progressive context getters |
| Context organization | Split: invocation (data) / lifecycle (registration) / continuation (flow) |
| Command transformation | Read-only + short-circuit; enrichment deferred |
| Command granularity | Nested around-tree; per-`on()`-call state-apply hooks |
| Origin discriminator | Dropped — split `sourcedEvent` (raw event present) vs `publishedEvent` (none) |
| No-stall & once-ness | Observers return opaque `Proceeded` (only from `proceed()`) → cannot stall; **observer `Continuation` is exactly-once** (2nd `proceed()` throws `IllegalStateException` before re-running); **`ValueContinuation` (root/`handler`) is at-least-once** (short-circuit + retry-around-proceed are features). Type-directed, not side-directed; event-side double-proceed = terminal (non-transient) |
| Observer continuation | **Kept** (`Proceeded` token, structural no-stall). Observers don't need `proceed()` for *flow control* (the framework always proceeds); the continuation is kept for **ergonomics** — around `try/finally`, stack-scoped per-invocation state, one uniform shape with transformers (the tracing/metrics drivers). Framework-driven before/after — and a lighter `void around(J, Runnable)` + runtime-guard variant — were considered and rejected |
| Continuations | `Continuation`→`Proceeded` (observers); `ValueContinuation<V>`→`V` (transformers/roots); no separate `Chain` |
| Continuation shape | `StageObserver` for observe-only; `StageTransformer` only where value substitution is designed |
| Whole-replay hook | Kept (`sourcing`) — wraps store read + all applies |
| Join-point fields | Phase-new only; closure supplies command + metaData |
| Command naming | prefix none; `sourcing`/`sourcedEvent`/`publishedEvent`/`handler`/`publish` |
| JP naming convention | plain noun (`Sourcing`/`Publish`) = framework stage; `…Invocation` = wraps a domain-handler call directly; entry carriers (`CommandInvocation`/`EventInvocation`) use the broader unit scope (§3.9) |
| Event value | None — all observers (`void` handlers); event filtering/veto deferred |
| Event root boundary | Per raw event; one interception per retry attempt; inside `progressTracker.proceed` execution (inside umbrella tx when enabled) |
| Event interior hook | Single `handler` per matching `EventHandlerDefinition` (dropped `convertedEvent`) |
| Event termination & continuation | Ends the loop via **exceptions** (uniform with handlers): `NonTransientException` terminates, else retry/skip. Continuation stays single-method `proceed()`; `Proceeded` not user-constructable (a return-based signal breaks chain propagation + no-stall §3.6). Optional discoverable termination-exception subtype, additive-later |
| Partition relevance | `Relevance {YES, NO, PARTIAL}`; **Variant O** — no extra upcast cost |
| Upcast drop-to-0 | Representable: verdict + zero `handler` hooks; converted-resolver empty ⇒ `NO` |
| Delivery filtering | Per-interceptor `Delivery {ACTIONABLE (default), PARTITIONED, ALL}`; framework does minimal work per union of levels |
| Command type targeting | `CommandInterceptor<C extends Command>` + explicit abstract `Class<C> commandClass()` (assignable gate); no convenience interface; all-commands = `<Command>` + `Command.class` |
| Event type targeting | Per-`handler` `Class<E>` gate, multiple registrations, typed `EventHandlerInvocation<E>`; root not gated by interior registrations |
| Ordering | Core receives ordered `List` (index 0 = outermost), no core order hook; Spring `@Order`/`Ordered`/`@Priority` sorts collection injection; intra-interceptor = registration order; ties = discovery order |
| Lifecycle registration | **One-shot**: register interior hooks in `intercept` before `proceed()`; the lifecycle **freezes** at interior-execution start; late `register` → `IllegalStateException` (fail-fast, not a silent no-op). Not type-safe (the static fix would break the closure-state pattern) → runtime guard, the **inverse** of structural no-stall |
| Test strategy | Altitude split, no overlap: mechanics → `framework.interceptor`; command wiring → `CommandRouterTest`; event wiring → new `EventHandlingProcessorInterceptorTest` (don't bloat the existing one); e2e smoke → the integration tests; test-support → fixture tests. Existing no-interceptor tests = empty-path regression. Interceptor-implementation tests deferred with their features |
| Interceptor test support | `CommandHandlingTestFixture` runs the chain (reused composer); immutable `withAdditionalInterceptors(…)` / `withoutInterceptors()` (shared singleton → derive, never mutate); Spring base = `@Order`-sorted **sliced** set (default filters → framework interceptors never auto-applied, no forced mocks, upgrade-safe); `@CommandHandlingTest(withInterceptors=false)` empties the base class-wide (via `ContextCustomizerFactory`); verify via existing assertions — pure observers (tracing/metrics) tested with their own harness |
| Bean discovery & wiring | Interceptors are `@Order`-sorted `List` beans passed to core constructors — `CommandRouter` directly, `EventHandlingProcessor` via `EventHandlingProcessorRegistrar` — mirroring `*Definition` wiring; core stays Spring-free; **event interceptors global** across all processors; provided/synthesized interceptors are just beans in the list; **empty list = today's behaviour** (convenience ctor default) |
| Extra info/hooks (cache / preconditions / retry) | Evaluated & **deferred** — the surface is additive-safe (new hooks + JP fields are non-breaking), so nothing is front-loaded; cache stays the `StateRebuildingCache` SPI |
| Annotation convenience | `@BeforeCommandHandling` only (Spring layer) — synthesizes a `CommandInterceptor` around the command `handler` hook; gate inferred from the command param; before-advice, throw-to-veto; after/event/around/transformation deferred to the full interface |
| Extensibility — no-stall semantics (Lock-in A) | **`ValueContinuation`** (root/`handler`) = **≥1** proceed → enables command retry-around-proceed; observer **`Continuation`** = **exactly-once** (2nd throws). Type-directed |
| Extensibility — command envelope (Lock-in B) | `CommandInvocation<C>(C, Map<String,?>)` frozen as the entry envelope; typed `Headers` (if ever) added as a **derived accessor**, never a component change → enrichment/envelope stays additive |
| Metadata propagation → interceptor | **Done** — `MetaDataPropagatingCommandInterceptor` (`framework.metadata`) rewrites event meta-data via the `publish` transformer; `CommandRouter` no longer propagates (its `propagationMode`/`keys` ctor params were dropped) and `PropagationUtil` was removed. Registered by `MetaDataPropagatingCommandInterceptorAutoConfiguration` only when `mode != NONE && !keys.isEmpty()`; ordered outermost so it applies last. The `CommandHandlingTestFixture` now exposes publish-transformed events so it can be tested via `@CommandHandlingTest` |
| Packages | shared `framework.interceptor`; `command.interceptor`; `eventhandler.interceptor` |
| Dispatch interceptors | Out of scope |
| Data plane (Lock-in D) | **No** framework-managed shared context (no UoW/`ProcessingContext`/`ResourceKey`); closures are per-interceptor. Escape hatches (additive, no signature change): interceptor→handler via deferred enrichment; interceptor↔interceptor via a later thread-local `ProcessingScope.current()` (à la OTel). Tracing validates the stance ([`axon_comparison.md`](axon_comparison.md) §2) |
| Pre-append hook shape | **Resolved: `publish` itself is the transform seam** — `publish(StageTransformer<Publish, Publish>)` over the append request (`events` + `additionalPreconditions`); the core appends the returned request (append not wrapped by advice, removing the post-commit footgun). No separate `prepareAppend` hook (avoids two confusingly-similar around-the-append hooks). Command/metadata/state come from the closure. First client = metadata propagation; next = causation/correlation provider |
| Event-side after-commit | **Out, but shaped**: deferred `EventLifecycle.afterCommit(...)` run by the processor post-commit, outside the around-tree (root is inside the umbrella tx); current answer = outbox ([`axon_comparison.md`](axon_comparison.md) §3.1) |
| Commit-point semantics | **Documented, not built**: the `publish` stage no longer wraps the append (it's a transformer; the core appends after the chain), so *that* footgun is gone. But the **root** `intercept` still spans the append — command post-proceed runs after the durable append (failure surfaces despite persisted events); nesting order ≠ commit-relative order (§3 principle 4, [`axon_comparison.md`](axon_comparison.md) §4) |
| Event dead-lettering | **Reserved**: a third interceptor outcome (≠ `SkipEvent`) for a future `SequencedDeadLetterQueue`-style path; keeps the binary terminate/retry model from calcifying ([`axon_comparison.md`](axon_comparison.md) §3.2) |
```
