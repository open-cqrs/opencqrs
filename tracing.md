# OpenCQRS Tracing — Design Handoff

> **Status:** Design in progress. **No implementation exists yet.** Companion to
> [`interceptors.md`](interceptors.md) — tracing is built *on top of* the interceptor framework and
> was its primary motivation. This document owns all tracing-specific design and decisions.
>
> **Scope:** OpenTelemetry / W3C trace-context integration across command and event processing.
>
> **Terminology:** **instance** / **state**, never "aggregate".
>
> **Challenge:** [`axon_comparison.md`](axon_comparison.md) challenges the interceptor + tracing design
> against Axon Framework 5 — including the cross-boundary propagation agreement (§5) and the virtual-thread
> resurrection caveat (§8).

---

## 1. Why (origin story)

Tracing is *the* reason the interceptor framework exists. Span/trace handling had been implemented
inline and separately, and it bloated the processing code. Span lifecycle (start → run → end, with
exceptions recorded, correctly nested) is a textbook **around** concern — so it moved into
interceptors, and everything in [`interceptors.md`](interceptors.md) followed from getting that shape
right.

But tracing is more than spans: trace context must **survive persistence** (be written onto events)
and be **resurrected** on the asynchronous read side so a trace spans the command → event → downstream
boundary. Those two are *not* interceptor concerns — see the architecture below.

---

## 2. Architecture: three concerns, three homes, one seam

| Concern | Home | Operation |
|---|---|---|
| **Span** create / nest / close | **interceptor** (framework, OTel-gated) | the around-tree of `interceptors.md` |
| **Resurrection** (wire → ambient, read side) | **`TraceAwareEventReader`** (decorates `EventReader`, OTel-gated) | extract `traceparent` from the event, `makeCurrent` around handling, close |
| **Persistence** (ambient → wire, write side) | **`EsdbClient`** (OTel-gated) | stamp the current trace onto the outgoing event |

The three **meet only at `Context.current()`** — OpenTelemetry's ambient thread-local context. None
of them references the others' types:

```
                 write path                              read path
 interceptor ──sets current span──▶ EsdbClient      TraceAwareEventReader ──makeCurrent──▶ interceptor
   (span)         Context.current()   (stamp)          (resurrect)   Context.current()      (span, child of resurrected)
```

This decoupling is the whole point: persistence and resurrection are the two **wire↔ambient adapters**;
the interceptor is pure span work that neither knows nor cares how context got into the thread.

It is also *why* tracing composes through OTel's ambient context rather than a framework channel: the
interceptor framework provides **no** shared data plane of its own
([`interceptors.md`](interceptors.md) §8.5, Lock-in D), so tracing brings its own — OTel
`Context.current()`. Tracing is thus the reference validation of Lock-in D. And because handling runs on
a framework-spawned virtual thread with no hop (§5), the resurrection seam must stay **explicit**
`makeCurrent`, never relying on thread/Spring context inheritance.

---

## 3. Trace context on the wire

`EventCandidate` (write) and `Event` (read) carry **nullable** `traceParent` / `traceState` fields —
the W3C trace-context, modeled as CloudEvents distributed-tracing extension attributes. First-class
fields (not buried in `data.metadata`) so they survive marshallers that may drop metadata, and so the
`EsdbClient` — which owns the wire mapping — can populate them without knowing the framework's
payload/metadata convention.

> **Branch caveat:** on some branches these fields are absent (`EventCandidate(source, subject, type,
> data)` / `Event` with 10 non-trace fields). Reinstating the two nullable trace fields on both records
> is a prerequisite for the persistence/resurrection design here.

---

## 4. Persistence (write side) — in the `EsdbClient`

When an event is written and a **valid** trace is current, the client stamps `traceParent`/`traceState`
from `Context.current()` onto the `EventCandidate` — unless already populated. Pluggable via
auto-configuration, active only when OpenTelemetry is present.

Why the client layer:

- **Covers all writers**, framework or not — no trace is lost even when `EsdbClient` is used directly.
- The trace fields live on `EventCandidate`, which the client owns.
- It composes with span management purely through ambient context: the interceptor sets the current
  span; the client reads `Context.current()`. No coupling.

---

## 5. Resurrection (read side) — in a `TraceAwareEventReader`

A decorator over `EventReader` that overrides the single abstract method
`consumeRaw(ClientRequestor, BiConsumer<RawCallback, Event>)`: for each event it extracts
`traceParent`/`traceState`, makes that context current around the per-event consumer callback, and
closes the scope afterwards. Because every read path funnels through `consumeRaw`, one override covers
them all uniformly.

> **Open (parent-child vs. links):** "makes that context current" assumes a **parent-child**
> relationship — the event span becomes a child of the producer's trace. Whether the event side should
> instead emit a **linked** span (a new trace carrying an OTel span link to the producer) is undecided,
> and it changes this very step (`makeCurrent` a parent vs. extract a `SpanContext` for a link). See
> §6.6.

**Why a reader and not the interceptor, and not the `EsdbClient`:**

- **Not the interceptor.** Keeping resurrection out of the span interceptor keeps the interceptor pure
  span management (it spans under `Context.current()`, oblivious to wire trace fields). Resurrection
  also has **standalone value without any tracing interceptor**: the resurrected context means events
  a handler produces get stamped by persistence with the *original* trace → the causal chain continues
  even with no per-phase spans. And it serves direct `EventReader` users.
- **Not the `EsdbClient`.** Only its streaming callbacks (`observe`, `read(…,Consumer)`) can bracket a
  per-event scope; batch methods (`read→List`, `write→List`) cannot. Putting resurrection there would
  be asymmetric across the client's own API. `EventReader.consumeRaw` is *always* the per-event
  streaming funnel, so a decorator is uniform.

**Threading — the enabling fact.** Handling runs **synchronously on the consume thread**
(`EventHandlingProcessor` calls `progressTracker.proceed(...)` directly inside the `consumeRaw`
callback — no executor hop). So the reader's `makeCurrent` scope reaches the handlers on the same
thread, and the interceptor's spans nest under the resurrected context automatically. (An earlier
design hopped handling onto a worker thread; that hop is gone. Had it remained, reader-level
resurrection would have leaked across the thread boundary and this decision would flip.)

**Layering.** Keep the OTel-specific implementation in the **autoconfigure** layer (OTel-gated),
mirroring `ProgressTracker` (core interface) vs. `JdbcProgressTracker` (autoconfigure). If direct-reader
users need the *type*, expose a thin `TraceAwareEventReader` interface/hook in the framework module and
put the OTel scope-management impl in autoconfigure — the core framework must not hard-depend on OTel.

---

## 6. Span management (the interceptor)

Spans are created by tracing interceptors built on the framework of `interceptors.md`. The framework
already exposes everything needed: the raw `Event` (via `EventInvocation.rawEvent()` /
`SourcedEvent.rawEvent`) and the definitions for naming.

### 6.1 Continue-only by default (must be explicit!)

**Trap:** `tracer.spanBuilder(...).startSpan()` with no current context does **not** no-op — it starts
a **new root span with a fresh trace id**. A naive interceptor would therefore manufacture a trace for
every un-traced command/event.

So continue-only is an **explicit check**: if the current `SpanContext` is not valid, skip span
creation and just `proceed()` (still returning `Proceeded`/`R` — the no-stall contract holds). End to
end this yields: no inbound trace → no spans → nothing persisted → nothing to resurrect → event side
no-ops. No unsolicited traces; distributed continuation only.

### 6.2 Enforcing origination — a property, not a separate interceptor

To *originate* a trace you must start a **root span** (a trace can't exist without one). A separate
`TraceCreatingCommandInterceptor` would create a root span and then the detailed interceptor's
`cmd` span would nest under it — a **redundant top span**. The `cmd` span *should be* the trace root.

So origination is a **property on the command span interceptor** (e.g.
`opencqrs.tracing.command.originate`, default `false`), living in the autoconfigure layer:

- `false` (default): orphan command → skip the `cmd` span (continue-only).
- `true`: orphan command → create the `cmd` span **as a new root**.

A standalone originator is warranted **only** for the niche of trace-id continuity *without* the
detailed per-phase spans (register a minimal one-root-span interceptor *instead of* the detailed one).
Additive — build it only if that need appears.

**Event side is continue-only, always.** An event with no inbound trace was produced without
instrumentation; originating at event handling would spawn a disconnected root trace per event (the
noisy "root per event"). Origination is a command-entry / system-boundary concern.

### 6.3 Example — `OtelCommandTracingInterceptor`

Root `cmd` span with interior join points nested beneath. The **conservative default** spans the
`sourcing` phase, `handler`, `publish`, and the (few) `publishedEvent` applies; **per-replayed-event
`sourcedEvent` spans are opt-in** — a cold rebuild can source thousands (see §6.5):

```java
class OtelCommandTracingInterceptor implements CommandInterceptor<Command> {
    private final Tracer tracer;
    private final boolean originate;                         // from opencqrs.tracing.command.originate
    public Class<Command> commandClass() { return Command.class; }   // all commands

    public <R> R intercept(CommandInvocation<Command> inv, CommandLifecycle<R> lc, ValueContinuation<R> cont)
            throws Exception {
        if (!originate && !Span.current().getSpanContext().isValid()) {
            return cont.proceed();                            // continue-only: no inbound trace → no spans
        }
        lc.sourcing((jp, c)       -> span("rebuild " + jp.instanceClass().getSimpleName(), c));
        // opt-in only (rebuild debugging — a cold rebuild sources thousands; see §6.5):
        // lc.sourcedEvent((jp, c) -> span("source " + jp.definition().eventClass().getSimpleName(), c));
        lc.publishedEvent((jp, c) -> span("apply "   + jp.definition().eventClass().getSimpleName(), c));
        lc.handler((jp, c)        -> span("handle "  + jp.definition().commandClass().getSimpleName(), c));
        lc.publish((jp, c)        -> span("append "  + jp.events().size() + " event(s)", c));
        return span("cmd " + inv.command().getClass().getSimpleName(), cont);   // root (originate) or child
    }

    private Proceeded span(String name, Continuation c) throws Exception {
        Span s = tracer.spanBuilder(name).startSpan();
        try (var scope = s.makeCurrent()) { return c.proceed(); }
        catch (Exception e) { s.recordException(e); throw e; } finally { s.end(); }
    }
    private <V> V span(String name, ValueContinuation<V> c) throws Exception {
        Span s = tracer.spanBuilder(name).startSpan();
        try (var scope = s.makeCurrent()) { return c.proceed(); }
        catch (Exception e) { s.recordException(e); throw e; } finally { s.end(); }
    }
}
```

Resulting trace (with an inbound trace or `originate=true`): `cmd → rebuild ; handle → [apply…] ;
append` — the per-replayed-event `source` spans appear only if you opt into `sourcedEvent` (§6.5).

### 6.4 Example — `OtelEventTracingInterceptor`

The event's span becomes a child of whatever `TraceAwareEventReader` resurrected — automatically, via
`Context.current()`. Continue-only, no originate:

```java
class OtelEventTracingInterceptor implements EventInterceptor {
    private final Tracer tracer;
    // delivery() defaults to ACTIONABLE — only events this processor actually handles

    public Proceeded intercept(EventInvocation inv, EventLifecycle lc, Continuation cont) throws Exception {
        if (!Span.current().getSpanContext().isValid()) {
            return cont.proceed();                            // no resurrected trace → no-op
        }
        lc.handler((jp, c) -> span("handle " + jp.definition().eventClass().getSimpleName()
                                   + " [" + inv.group() + "/" + inv.partition() + "]", c));
        return span("event " + inv.rawEvent().type() + " (" + inv.relevance() + ")", cont);
    }

    private Proceeded span(String name, Continuation c) throws Exception {
        Span s = tracer.spanBuilder(name).startSpan();
        try (var scope = s.makeCurrent()) { return c.proceed(); }
        catch (Exception e) { s.recordException(e); throw e; } finally { s.end(); }
    }
}
```

### 6.5 Scope: semantic spans only — not a call graph

The tracing interceptor emits **domain/semantic** spans (`cmd → rebuild → handle → append`; `event`,
per-handler) — the story a human reads to attribute cross-boundary latency. It deliberately does
**not** instrument the framework's own infrastructure (`EsdbClient` HTTP, JDBC for progress / read
models). That transport **call graph** comes from **OTel auto-instrumentation** (the Java agent), and
because everything composes through `Context.current()`, transport spans nest **under** the domain
spans automatically.

This is a boundary, not a gap. Axon's exhaustive framework tracing is its *own* programmatic
`SpanFactory` instrumentation hand-placed through every component (`CommandBus`, processors, sagas,
unit of work, …) — a maintenance burden and hard OTel coupling. OpenCQRS's semantic layer is
necessarily programmatic too (an agent can't name "sourcing" or "command handling"), but is kept to
**one opt-in interceptor + one context seam** (§2) instead of span calls in every class — and the
transport call graph still comes free from the agent.

**Granularity is a knob.** Conservative default: `sourcing` (the rebuild *phase*, one span) + `handler`
+ `publish` + the handful of `publishedEvent` applies. **Per-replayed-event `sourcedEvent` spans are
strictly opt-in** — a cold rebuild can source thousands, turning a semantic trace into a firehose.

### 6.6 Open discussion — parent-child vs. linked event spans

**Undecided; to be discussed before the event-side tracing is implemented.** When the event side
resurrects the producer's trace context, the event-handling span can relate to it two ways:

- **Parent-child** — the event span's *parent* is the resurrected producer context; command → events →
  downstream form **one connected trace** (what §5's "make current + span under it" sketch implies).
- **Linked** — the event span starts its **own** trace (own root, own sampling) and carries an OTel
  **span link** to the producer's span context; causality is preserved without a parent edge.

The choice changes what §5 resurrection does (`makeCurrent` a parent vs. extract a `SpanContext` for a
link) and what downstream-produced events carry (same trace vs. a new linked trace) — so it can't be
left implicit.

Considerations — sampling is the headline, but not the only one:

- **Sampling probability (main).** Parent-based sampling makes the event span **inherit the command's
  sampled flag** — async work is sampled iff the command was. That biases sampling of high-volume
  projection/processor work and yields huge "sampled" traces; you cannot sample async processing on
  its own probability. Links give each async unit an **independent** head-sampling decision.
- **Trace size / unboundedness.** ES fan-out + continuous processing grafted parent-child onto
  producer traces yields very large traces (poor for backend UI / storage / tail-sampling). Links keep
  each unit bounded.
- **Fan-in (sagas / process managers).** A handler correlating **several** events has **multiple**
  causes; parent-child allows only one parent, whereas a span may carry **many links**. Strong pull
  toward links for correlation.
- **Temporal decoupling / duration.** Parent-child keeps the producer trace "open" across async lag,
  distorting command latency and producing absurd durations; links keep durations sane.
- **Replay / reprojection.** Re-handling old events would graft spans onto **ancient/expired** producer
  traces; a fresh trace-per-run **linked** to the historical producer is far cleaner.
- **OTel messaging semantic conventions** lean toward **links** for asynchronous producer→consumer
  (parent-child is for synchronous continuations).
- **Debuggability / UX.** Parent-child gives the single-view "what did this command cause?" story many
  want for CQRS/ES; links require the backend to navigate link edges well.

The discussion should weigh a **links-by-default** stance (bounded, independently sampled, fan-in- and
replay-friendly, convention-aligned) against parent-child's single-trace UX — possibly resolving to a
configurable choice. **Not decided here.**

---

## 7. End-to-end behavior

| Situation | Command span | Persisted on event | Event span |
|---|---|---|---|
| No inbound trace, `originate=false` (default) | none (continue-only) | nothing (null trace fields) | none (nothing resurrected) |
| Inbound trace present | child of inbound | yes | child of resurrected |
| No inbound trace, `originate=true` | new root | yes | child of resurrected |

---

## 8. Layering & OTel-gating

- **Core modules stay OTel-free.** `framework` and `esdb-client` must not hard-depend on OpenTelemetry.
- OTel dependencies and the active integrations live in the **autoconfigure** modules
  (`esdb-client-spring-boot-autoconfigure` for persistence; `framework-spring-boot-autoconfigure` for
  the `TraceAwareEventReader` decorator bean and the tracing interceptor beans), gated on OTel being on
  the classpath.
- The tracing interceptors are ordinary interceptor beans (see `interceptors.md` §8.2 ordering); a
  tracer sits at outermost precedence so its spans wrap everything.

---

## 9. Open / deferred

- **Parent-child vs. linked event spans (§6.6)** — *undecided*; sampling probability (main), trace
  size, fan-in, replay, and OTel messaging conventions all bear on it. Must be settled before
  event-side tracing is implemented.
- Reinstating `traceParent`/`traceState` on `EventCandidate`/`Event` (branch caveat, §3).
- The actual OTel implementation — nothing is built yet.
- Standalone depth-less originator interceptor (trace-id continuity without per-phase spans) — additive.
- Event-side origination property — additive, only if a concrete "ingested events start traces" need appears.
- Whether the two tracing interceptors share a common base/util for the `span(...)` helpers.

---

## 10. Decision log (tracing-specific)

| Decision | Choice |
|---|---|
| Span lifecycle | In **interceptors** (separate inline impl bloated the code) |
| Concern split | **spans** (interceptor) / **resurrection** (`TraceAwareEventReader`) / **persistence** (`EsdbClient`); compose only via `Context.current()` |
| Persistence home | `EsdbClient` — covers all writers; trace fields live on `EventCandidate` |
| Resurrection home | `TraceAwareEventReader` over `consumeRaw` — uniform funnel; works because handling is synchronous on the consume thread; **not** the interceptor (keeps it pure + standalone value), **not** the `EsdbClient` (only some methods stream) |
| Wire representation | First-class nullable `traceParent`/`traceState` (CloudEvents extension attrs), not `data.metadata` |
| Default policy | **Continue-only**, via an explicit valid-`SpanContext` check (else OTel starts a spurious new root) |
| Origination | **Property on the command span interceptor** (`originate`), not a separate `TraceCreatingCommandInterceptor` (avoids a redundant top span); standalone originator only for the depth-less niche |
| Event origination | Never — continue-only always (avoids disconnected per-event traces) |
| OTel deps | Confined to the autoconfigure modules; core `framework` / `esdb-client` stay OTel-free |
| Tracing scope | **Semantic/domain spans only** (interceptor); transport call graph from OTel auto-instrumentation nesting via `Context.current()`; **no** Axon-style framework-class `SpanFactory` instrumentation |
| Span granularity | A knob; conservative default = `sourcing` phase + `handler` + `publish` + `publishedEvent`; **per-replayed-event `sourcedEvent` spans strictly opt-in** (cold-rebuild firehose) |
| Event span relationship | **OPEN** — parent-child vs. linked event spans (§6.6); undecided pending discussion (sampling probability, trace size, fan-in, replay, OTel messaging conventions) |
