# OpenCQRS Interceptors — Challenge Against Axon Framework 5

> **Status:** Design critique. Companion to [`interceptors.md`](interceptors.md) and
> [`tracing.md`](tracing.md). This document challenges the interceptor design against Axon Framework 5's
> command/event handling — specifically the **`ProcessingContext`** (Axon 5's replacement for the
> Axon 4 `UnitOfWork`). It does **not** restate the design; it records where the two frameworks diverge,
> what that costs, and which decisions are still cheap to change.
>
> **Terminology:** **instance** / **state**, never "aggregate".
>
> **Grounding:** Axon 5 facts below are from the AxonIQ 5.0/5.1 reference + `axon-5/api-changes` migration
> notes; OpenCQRS claims were verified against the code (notably: no after-commit seam on the event side —
> the event root runs inside `JdbcProgressTracker.proceed`'s umbrella tx and control returns straight to
> the observe loop with no `TransactionSynchronization`).

---

## 1. The root divergence: synchronous vs. async

Axon 5's `ProcessingContext` is `CompletableFuture`-based end to end. Interceptors return a
`MessageStream`, the context threads explicitly through every `proceed(message, context)`, and the old
`ThreadLocal` `CurrentUnitOfWork` is **deliberately gone** — there is no "current" UoW; the context is
passed as a parameter through all infrastructure and handlers.

OpenCQRS is deliberately **synchronous** ([`interceptors.md`](interceptors.md) §8.5 names async as "the
frontier"). This is the right call for OpenCQRS's scope — but nearly every ergonomic win in the design is
*purchased by* the synchronous assumption:

- `try/finally` around a phase,
- stack-scoped closure state ([`interceptors.md`](interceptors.md) §3.5),
- the `Proceeded` token (structural no-stall),
- thread-local trace context reaching handlers ([`tracing.md`](tracing.md) §5).

Axon cannot offer these precisely because it went async. Conversely, OpenCQRS can never go async without a
new interceptor *family*. Both positions are coherent; the asymmetry is total in both directions. Every
gap below is downstream of this choice **or** of the "no shared data plane" choice (§2).

| Axis | Axon 5 | OpenCQRS | Verdict |
|---|---|---|---|
| Execution model | async (`CompletableFuture` / `MessageStream`) | synchronous | different ceilings; OpenCQRS simpler in-scope |
| Context object | `ProcessingContext` passed everywhere | none — per-interceptor closure | see §2 |
| Cross-boundary (cmd→event) | persisted correlation metadata + resurrection | persisted trace fields + resurrection | **agreement** (see §5) |

---

## 2. The biggest gap — no framework data plane

Axon's `ProcessingContext` carries typed resources keyed by `ResourceKey<T>`
(`putResource`/`getResource`/`computeResourceIfAbsent`/`updateResource`) that **every interceptor, the
handler, and framework components** read and write. It is the shared data plane.

OpenCQRS §3.5 ("the closure is the shared data plane") is true only **within a single interceptor's
`intercept` call**. There is no channel for:

- interceptor A → interceptor B (they nest, but nothing passes between them);
- interceptor → domain handler (the handler sees command + state + metadata — nothing an interceptor
  computed);
- handler → interceptor (except via emitted events / the return value).

**Two separable concerns were collapsed.** §3.1 rightly rejects *progressive context getters* as temporal
coupling — and on that axis OpenCQRS genuinely **beats** Axon 5, whose `getResource` can return
null-until-populated. But temporal coupling (fixed) and *cross-participant sharing* (also discarded) are
independent. Axon 5 has both: typed resources **and** phased lifecycle actions. Phase-delivered typed
arguments and a typed resource bag can coexist.

**Cases OpenCQRS cannot express:** a security interceptor resolving a `Principal` once for an auditing
interceptor and the handler to reuse; idempotency keyed to something the handler computed; a proper
correlation/causation provider (§6).

**The sharp irony:** the flagship use-case already needs a shared plane and *borrows* one. Tracing does
not use the closure to bridge command→event — it uses OTel's `Context.current()` thread-local (a UoW-like
ambient plane) plus persisted trace fields ([`tracing.md`](tracing.md) §2). So "we don't need a shared
context" actually means **"we provide none; bring your own thread-local."** Defensible — but it should be
stated as such, not as a principled absence.

**Resolved →** Forgone as **Lock-in D** ([`interceptors.md`](interceptors.md) §8.5). Escape via deferred
enrichment (→handler) or a later thread-local `ProcessingScope` (↔interceptor) — no carrier/signature
change. See §9.1 (which corrects the carrier-bag suggestion this doc originally made).

---

## 3. Missing capabilities on the event side

### 3.1 No after-commit seam

**Verified in code.** The event root executes inside `execution.get()`, which runs inside
`proceedTransactionOperations.executeWithoutResult(...)` — the umbrella tx — and `proceed(...)` returns
the instant that block exits. No `TransactionSynchronization`, no post-commit callback; control returns
straight to the ESDB observe loop.

Axon has a dedicated **after-commit phase** (`ProcessingLifecycle`, order 40000;
`onAfterCommit`/`runOnAfterCommit`) for exactly the "do X only once handling + progress/token durably
committed" case: fire a webhook, publish to an external bus, send a notification. In OpenCQRS this is
**structurally unreachable** from an event interceptor — any post-`proceed()` code in the root still runs
*inside* the tx, before durability. An interceptor doing external I/O there acts on an event that may
still roll back and be redelivered.

This is a direct consequence of "no UoW-like structure" (§5): with around-only and no lifecycle object,
there is nothing to hang a post-tx action on.

**Resolved →** Out, but **shaped**: a deferred `EventLifecycle.afterCommit(...)` the processor runs
post-commit, outside the around-tree; outbox is the answer today. See §9.3.

### 3.2 Poison-message / DLQ expressiveness

Axon 5 ships `SequencedDeadLetterQueue` per processing group. OpenCQRS's event error model is **binary**:
`NonTransientException` terminates the loop; everything else retries-forever with backoff. Not an
interceptor flaw — but the interceptor's throw-to-terminate/retry channel is only as expressive as that
model. A transient-looking error on a genuinely poison event retries forever and blocks the partition.

If a DLQ/skip path lands later, interceptors will want a **third** outcome ("dead-letter this"), distinct
from the deferred `SkipEvent` (skip-and-advance, not dead-letter, [`interceptors.md`](interceptors.md)
§8.5). Reserve conceptual room for it.

**Resolved →** Reserved as a future third outcome (≠ `SkipEvent`) in [`interceptors.md`](interceptors.md)
§8.5. See §9.5.

---

## 4. The commit point is invisible to the around model

Axon's phases draw a hard line at commit: prepare-commit/commit run transactionally (failure →
rollback); **after-commit runs post-durability (failure must *not* roll back)**. The around model has no
such line.

Command side: if an interceptor's post-`proceed()`/`finally` throws *after* a successful append, the
events are durable but the caller sees a failure — the classic *committed side effect, reported error*.
Nesting order is **not** commit-relative order, and nothing in the surface lets an author say "this
cleanup runs after the commit point and its failure must not surface." Axon encodes this distinction; the
around model blurs it. It should at least be **documented** on both sides.

Dispatch-side interception being scoped out is fine: `send` is in-process and synchronous, so Axon's
`MessageDispatchInterceptor` (`interceptOnDispatch`) vs `MessageHandlerInterceptor` (`interceptOnHandle`)
split collapses to one call site; a handler interceptor at outermost order covers bean-validation /
dispatch-time metadata.

**Resolved →** Documented both sides ([`interceptors.md`](interceptors.md) §3, principle 4); command
hazard stated, event after-commit deferred; dispatch stays out of scope. See §9.3.

---

## 5. Command/event separation — validated, and more forced than claimed

**The separation is correct — and Axon 5 agrees.**

- Command and event handling sit on opposite sides of an **async, durable ES boundary**. A command
  appends; processors observe *later*, on a different (framework-spawned virtual) thread, possibly a
  different process. There is no instant a single UoW could span both. Axon 4's shared `UnitOfWork` only
  unified them for in-process *subscribing* processors — a mode OpenCQRS does not have. A cross-boundary
  shared UoW is **impossible by construction**, not declined.
- **Axon 5's own answer to the async boundary is not a shared `ProcessingContext`** (that is per-message;
  it does not survive the hop) — it is `CorrelationDataProvider` (`MessageOriginProvider` maintaining
  `correlationId`/`causationId`) stamping metadata onto produced events, resurrected consumer-side. That
  is **exactly** the OpenCQRS tracing design (persist trace fields → `TraceAwareEventReader` resurrects,
  [`tracing.md`](tracing.md) §4–5). Both frameworks independently landed on *"the cross-boundary plane is
  persisted metadata + resurrection, never a shared context."*

**Reframe the question.** The command/event split is *not* the risk. The debatable separation is the one
*within* a single request — no framework plane across interceptors (§2). And §3.1 (no after-commit) and
this section are the same coin: **the price of "no UoW-like structure" is "no lifecycle phases like
after-commit."**

---

## 6. Correlation/causation and metrics

**Correlation/causation chain.** Axon's `MessageOriginProvider` maintains `correlationId` + `causationId`
across the whole chain automatically (5.0 renamed `traceId→correlationId→causationId`). OpenCQRS's
metadata propagation ([`interceptors.md`](interceptors.md) §8.6) is configured-key-copy only — narrower.
Since §9.3 says the propagation migration is what **settles the pre-append hook's shape**, design that
hook as `transform(context) -> List<CapturedEvent>` where `context` carries the triggering command +
metadata — **not** a bare `UnaryOperator<List<CapturedEvent>>`. Otherwise a causation provider (which
needs the trigger's identity) is not expressible, and the hook's shape breaks to add it later.

**Resolved →** The hook is **around-shaped** and carries **only** the pending events; the triggering
command comes from the `CommandInvocation` closure — so no `PreAppendContext` is needed (mirroring it
would be redundant per §3.5). Causation/correlation is expressible from the closure. See §9.2.

**Metrics vs. `MessageMonitor`.** Axon separates monitoring (`MessageMonitor.onMessageIngested` →
`MonitorCallback.reportSuccess/reportFailure/reportIgnored`) from interceptors *specifically because
monitors must see every message including ignored/rejected ones*. OpenCQRS folds metrics into
interceptors, and `Delivery.ALL` is the equivalent of "observe even ignored events." This is **parity** —
just verify an `ALL` observer genuinely fires for drop-to-0 / wrong-partition (the design says a
`ForRawEvent` `NO` fires the root with a no-op `proceed()` — good).

---

## 7. Which style is better understood?

**Verdict: more approachable than Axon 5 at the low end, more novel (steeper) at the high end.**

- **Top-level around — tie.** `intercept(...) { … return continuation.proceed(); }` is the exact model of
  Axon's `interceptOnHandle(msg, ctx, chain)`, servlet filters, and Spring `@Around`.
- **OpenCQRS *easier* for the common case.** Synchronous imperative code beats composing a `MessageStream`
  for a trivial span/timer/veto. `@BeforeCommandHandling` covers the most common case (state-aware veto)
  with zero machinery — a clean analog to Axon's `@CommandInterceptor`, arguably cleaner (no
  "member interceptor only fires if the root has one" quirk).
- **OpenCQRS *harder* at the high end, in two places:**
  1. **`Proceeded`.** No mainstream framework returns an opaque proof token. A newcomer cannot guess what
     to return; "the only way to make one is `proceed()`" must be taught. *Safer* than Axon returning a
     real value, but strictly *less discoverable* — the single highest comprehension cost in the design.
     ([`interceptors.md`](interceptors.md) §10 already considered and rejected `void around + runtime
     guard`; the rejection reasons are sound — the token itself is simply the least-familiar surface.)
  2. **Observer/transformer duality + register-before-proceed/freeze/remember-in-closure-act-later.** Axon
     has *one* interceptor shape everywhere; OpenCQRS has two (`StageObserver`/`StageTransformer`) plus a
     bespoke "register interior hooks that fire during `proceed()` from inside the around body" idiom with
     no analog in Axon. More powerful and compact than "write another interceptor," but genuinely new
     theory to internalize.

**Implication:** the docs carry an unusually heavy load. If `Proceeded` and the observer/transformer split
are not taught prominently, the low-end approachability win is swamped by high-end confusion.

**Credit where due:** `handler` + `CommandHandlerInvocation.instance()` (rebuilt state in scope, veto by
throw) is a clean — arguably cleaner — equivalent of Axon's aggregate `@CommandInterceptor`. Keep
the state-based-security example front-and-center; it is a selling point.

**Resolved →** A `Proceeded` + observer/transformer **reading guide** now leads
[`interceptors.md`](interceptors.md). Separately, the review surfaced a latent flaw the original critique
missed: the shared `Continuation` allowed a second `proceed()` on the event root, re-running handlers in
one tx. Closed **type-directed** — observer `Continuation` exactly-once (2nd `proceed()` throws), value
continuation at-least-once. See §9.4.

---

## 8. Tracing threading holds — keep it explicit

**Confirmed in code:** handling runs synchronously on a per-processor *virtual* thread with no hop between
the ESDB read callback and the handlers, so `TraceAwareEventReader`'s `makeCurrent` reaches them. But that
thread is framework-spawned at `start()`, not the caller's, and commit `ce90554` deliberately avoids
child-context creation. So resurrection must stay **explicit** and must **never** rely on Spring/thread
context inheritance — the "it just works" is contingent on the explicit `makeCurrent` seam
([`tracing.md`](tracing.md) §5), not on thread lineage. Worth reaffirming there.

---

## 9. Resolutions (decided)

The rulings below were made and applied to [`interceptors.md`](interceptors.md) / [`tracing.md`](tracing.md).

1. **Data plane → forgo it (Lock-in D)** (§2). No framework-managed shared context. Escape hatches, both
   additive with **no signature change**: interceptor→handler via the deferred **enrichment** feature;
   interceptor↔interceptor via a later thread-local `ProcessingScope.current()` (à la OTel `Context`).
   **Correction to §2/this doc's earlier draft:** the additive escape is the thread-local scope, *not* a
   `ResourceKey` bag on the invocation carriers — the scope keeps the carriers pure (respecting Lock-in B)
   and needs no carrier change, so naming the lock-in now carries **no** "breaking later" risk. That
   retires the "one decision that's a signature break tomorrow" framing: it isn't one.
2. **Pre-append hook → around-shaped, no context type** (§6). `prepareAppend(StageTransformer<PrepareAppend,
   List<CapturedEvent>>)`; the JP carries only the pending events, and command/metadata come from the
   enclosing `CommandInvocation` closure (§3.5) — so a `PreAppendContext` mirroring them would be
   redundant. Kept around-shaped to preserve the "one mechanism" principle (§3.3).
3. **Commit point → documented, not built** (§3.1, §4). Command side: document the hazard (post-append code
   runs after durable commit; failure surfaces despite persisted events; nesting order ≠ commit-relative
   order). Event side: **after-commit is out, but shaped** — a deferred `EventLifecycle.afterCommit(...)`
   the processor runs post-commit outside the around-tree; the current answer is the ES-native outbox.
4. **Multi-`proceed()` → exactly-once observers, at-least-once value continuations** (§7, augmenting the
   original critique). Type-directed guard: a second observer `proceed()` throws `IllegalStateException`
   before re-running; on the event side that violation is terminal (non-transient), not a retry. This also
   closes the latent re-apply hazard on the command *interior* observers, not just the event root.
5. **Reserved / docs.** Event **dead-lettering** reserved as a future third outcome (≠ `SkipEvent`, §3.2);
   a **`Proceeded` + observer/transformer reading guide** added up front in `interceptors.md`.

Everything else in the design is coherent and, on temporal coupling and synchronous ergonomics, genuinely
*better* than Axon 5. The remaining gaps are the deliberate, now-documented consequences of two choices —
**synchronous** and **no shared plane**.

---

## 10. Summary — parity table

| Concern | Axon 5 | OpenCQRS | Assessment |
|---|---|---|---|
| Around interception | `MessageHandlerInterceptor.interceptOnHandle` + chain | root `intercept` + `Continuation` | parity |
| Result transform / short-circuit (command) | interceptor returns `MessageStream` | `StageTransformer<…, R>` | parity |
| State-aware command veto | `@CommandInterceptor` (entity in scope) | `handler` hook + `instance()` | parity (OpenCQRS cleaner) |
| Interior granularity | separate interceptors / phase actions | lifecycle hooks (`sourcedEvent`, `handler`, …) | OpenCQRS more compact, more novel |
| Shared data plane | `ProcessingContext` + `ResourceKey<T>` | none (per-interceptor closure) | **gap** (§2) |
| Lifecycle phases (after-commit, etc.) | `ProcessingLifecycle` phases 0–40000 | around `try/finally` only | **gap** on event side (§3.1, §4) |
| Cross-boundary propagation | `CorrelationDataProvider` + resurrection | persisted trace fields + resurrection | agreement (§5) |
| Correlation/causation SPI | provider SPI (`MessageOriginProvider`) | configured-key copy | narrower (§6) |
| Metrics | separate `MessageMonitor` SPI | folded into interceptors + `Delivery.ALL` | parity, different placement (§6) |
| Poison-message handling | `SequencedDeadLetterQueue` | terminate-or-retry (binary) | **gap** (§3.2) |
| Dispatch interception | `MessageDispatchInterceptor` | out of scope | acceptable (in-process `send`) (§4) |
| Async | native | out of scope ("frontier") | deliberate divergence (§1) |
