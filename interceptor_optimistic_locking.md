# OpenCQRS — Optimistic Locking Interceptor — Implementation Hand-off

The **first framework-provided command interceptor**, built on the command interceptor framework
(`interceptors.md`, `interceptors_implementation.md`). This document is self-contained: an implementer should be able
to pick it up without re-deriving the design. It corresponds to **Stage 9** in `interceptors_implementation.md`.

---

## 0. What it is / why it exists

An **optimistic-locking** interceptor that rejects a command whose caller acted on a **stale read**, *before* the command
handler runs. A command opts in by implementing `EventIdExpectingCommand`, declaring an `EventIdExpectation` over the
head of its sourced event stream; the interceptor evaluates that expectation against the **sourced head event id** of the
rebuilt state and throws `OptimisticLockingException` if it is violated.

### Event ids are globally monotonic — so compare by order, not equality

`IdUtil.fromEventId(String) → Long.valueOf(id)`: event ids are a **globally monotonic `long`** sequence — any event
appended later, on *any* subject, gets a strictly higher id. Two consequences drive the design:

- **Exact equality is the wrong default.** The same subject can be targeted by commands with different `SourcingMode`
  (e.g. in `BookHandling`, `purchase`/`returnBook` are `LOCAL` while `borrow` is `RECURSIVE`). A caller who read through a
  broader (`RECURSIVE`) scope may hold a *higher* id than a narrower (`LOCAL`) command sources — so `sourcedHead == expected`
  yields a **false conflict** even though nothing in the command's own scope changed.
- **Order comparison fixes it.** Because ids are ordered, the check can be `sourcedHead ≤ expected` ("nothing newer than
  the caller saw"), which tolerates a narrower-scope (older) head and rejects only genuine concurrent modifications.

Hence the expectation is a **sealed `EventIdExpectation`** with three variants (§1): `None` (skip), `AtMost` (`≤`, the
scope-robust default lock), `Exactly` (`==`, strict). This assumes the store assigns ids in a single global order
(confirmed by the maintainer).

### Not redundant with the store's write-time preconditions

The event store already does *server-side* optimistic locking: at append time `CommandRouter` emits
`Precondition.SubjectIsOnEventId(subject, sourcedHeadId)` for every sourced subject
(`framework/.../command/CommandRouter.java:322-324`), and a violation surfaces as `ConcurrencyException`. That guards the
**router's own** source→append window — it locks against what the *router* sourced.

It does **not** catch a stale *client* read:

```
client GET → sees head E1, issues command with expectedEventId = E1
       concurrent writer appends E2
router sources subject → head is now E2
       router appends with SubjectIsOnEventId(subject, E2) → SUCCEEDS (locked against E2, not E1)
       ⇒ the lost update is NOT caught
```

The interceptor closes exactly this **client-read → command** window by comparing the client's `E1` against the sourced
head `E2` **before the handler**, and it fires even when the handler would emit nothing (no append ⇒ no precondition
runs). This is the reason it must be a *pre-handler* check rather than an extra append precondition.

---

## 1. Decisions (settled with the maintainer)

| # | Question | Decision | Notes |
|---|---|---|---|
| 1 | Expose the sourced head event id to interceptors? | **Yes — `@Nullable String latestSourcedEventId` on `CommandHandlerInvocation`** | **DONE** (§2). = `CacheValue.eventId()`; **cache-correct** (a `sourcedEvent`-hook reconstruction would miss cache-served events — the reason it is surfaced by the router, not recomputed). |
| 2 | Marker interface | **`EventIdExpectingCommand extends Command`**, `EventIdExpectation expectedEventId()` (non-null) | Extends `Command` ⇒ mix-in via multiple-interface inheritance (diamond-on-`Command` harmless). Interceptor gates as `CommandInterceptor<EventIdExpectingCommand>` — no swallow-all, no `instanceof`. Returns a sealed expectation (row 2b), not a bare id. |
| 2b | Locking semantic | **Sealed `EventIdExpectation` = `None` \| `AtMost(eventId)` \| `Exactly(eventId)`** | `None` = skip (opt out per invocation). `AtMost` (`sourcedHead ≤ eventId`) = the scope-robust default lock. `Exactly` (`==`) = strict. `AtLeast` (`≥`, a freshness/read-your-writes guard on a different axis) **deferred**. Sealed (not enum) because id-presence differs per variant, and it makes the switch exhaustive. |
| 3 | Naming: "version" vs "event id" | **Event id** | "Event id" over "version"; but note the id **is** orderable — a globally monotonic `long` (`IdUtil`), which is what enables `AtMost`. |
| 4 | Interceptor name | **`OptimisticLockingCommandInterceptor`** | Keeps the `…CommandInterceptor` suffix; states intent. |
| 5 | Exception | **`OptimisticLockingException`** | See §4 for the hierarchy refactor. |
| 6 | Exception hierarchy | **Promote `ConcurrencyException` to a shared base**; rename today's store-side subtype to **`ConflictingWriteException`** (store rejected the write with an opaque HTTP 409); add `OptimisticLockingException` as the second subtype (framework-detected) | Single `catch (ConcurrencyException)` covers store conflict **and** pre-handler reject; both **transient**. The store-side name reflects the *observable fact* (a write conflicted), not an inferred cause — ESDB returns a bare 409 with no detail. |
| 7 | Transient or non-transient? | **`TransientException`** (via the `ConcurrencyException` base) | Recoverable: caller refreshes + retries. Contrast `CommandSubjectConditionViolatedException` (non-transient, structural). `CqrsFrameworkException` is `sealed` ⇒ must extend `TransientException`/`NonTransientException`. |
| 8 | Package | **`com.opencqrs.framework.command.interceptor.optimisticlocking`** | Feature sub-package; keeps the SPI package (`command.interceptor`) free of provided impls; scales to sibling provided interceptors. |
| 9 | Auto-configuration | **One `OptimisticLockingCommandInterceptorAutoConfiguration`** (not a shared bucket) | Independently `spring.autoconfigure.exclude`-able, matching Boot idiom. |
| 10 | Register by default? | **Yes**, guarded `@ConditionalOnMissingBean` | The marker interface *is* the opt-in; the bean is inert (assignability gate never matches) for non-versioned commands. |
| 11 | Order — where & how | **Applied by the autoconfigure module, never the core.** The `framework` interceptor stays Spring-free — it does **not** implement `org.springframework.core.Ordered` and carries no order constant. The `@Bean` factory method carries `@Order(CommandInterceptorOrders.OPTIMISTIC_LOCKING)`. | Spring sorts the autowired `List<CommandInterceptor>` by `@Order` on the bean method (`AnnotationAwareOrderComparator`); the router consumes that pre-sorted list (index 0 = outermost) and never re-sorts. Lower = further outside. |
| 12 | Centralized order constants? | **Yes — `CommandInterceptorOrders`** (autoconfigure module): one named, spaced constant per provided interceptor; `OPTIMISTIC_LOCKING = 0` | Single source of truth for relative nesting. Convention: lower = outer; unordered beans sort at `LOWEST_PRECEDENCE` (innermost). Negative range reserved for future outermost provided interceptors (observability/security); gaps let user interceptors interleave. Event side gets a sibling `EventInterceptorOrders` when Stage 3/5 land. |

**Resolved:** the earlier `null`-means-pristine question is **moot** under the sealed `EventIdExpectation` — `None`
is the explicit opt-out (no nullable id overloading meaning), and asserting a *new* subject is left entirely to
`Command.SubjectCondition.PRISTINE` (which also adds a store-side append precondition a pre-handler check cannot).

---

## 2. Prerequisite — DONE

Exposing the sourced head id on the pre-handler join point (Stage 9.0, already merged on this branch):

```java
// framework/.../command/interceptor/CommandHandlerInvocation.java
public record CommandHandlerInvocation(
        CommandHandlerDefinition<?, ?, ?> definition,
        @Nullable Object instance,
        @Nullable String latestSourcedEventId) {}   // head of the sourced stream (incl. cache-served events)
```

- **Wired** in `CommandRouter` (`:279-283`): passes `fromCacheMerged.eventId()`.
- **Fixture** (`framework-test/.../CommandHandlingTestFixture.java`) passes `null` — no event-id model for given events
  yet (added in Stage 9.4).
- **Coverage** in `CommandRouterInterceptorTest`: the real-pipeline `Recording` traces `jp.latestSourcedEventId()` at the
  `handler` hook (both full-trace tests assert the sourced head `event-id-1` reaches the join point); a focused non-null
  test; a null-path (pristine ⇒ `null`) test; and — crucially — a test that sources a **trailing event with no matching
  state-rebuilding handler** and asserts the head still advances to it. That last case can *only* live here: the fixture
  rejects given events without an SRHD, so it can never exercise the SRHD-less-head scenario, which is precisely what
  distinguishes the router-sourced value from a `sourcedEvent`-hook reconstruction.

Semantics of the value (document these on the interceptor too):
- Head of the **whole sourced stream** = the rebuilt instance's *version*.
- **`LOCAL`**: equals the command-subject head. **`RECURSIVE`**: the hierarchy head (changes if any descendant subject
  gets a newer event) — the instance's true version.
- **`null`** when nothing was sourced (pristine subject, or `SourcingMode.NONE`).

---

## 3. Staged plan (command side only — no event side)

Each stage compiles, is independently testable, and keeps existing tests green. Build note: the Gradle wrapper needs the
sandbox disabled (writes a lock under `~/.gradle`); after touching package-private types in `framework`, verify
`framework-test` with `--rerun-tasks` (incremental compiles emit phantom NullAway errors in `CommandHandlingTestFixture`).

> **Implementation status:** 9.1–9.4 are **implemented and green**. 9.5 (example-app showcase) is **deferred** at the
> maintainer's request — not needed for now.

### Stage 9.1 — Marker interface + exception hierarchy

**Semantic type** — `framework/.../command/interceptor/optimisticlocking/EventIdExpectation.java`:

```java
public sealed interface EventIdExpectation
        permits EventIdExpectation.None, EventIdExpectation.AtMost, EventIdExpectation.Exactly {
    record None() implements EventIdExpectation {}          // skip
    record AtMost(String eventId) implements EventIdExpectation {}   // sourcedHead <= eventId  (default lock)
    record Exactly(String eventId) implements EventIdExpectation {}  // sourcedHead == eventId  (strict)
}
```

**Marker** — `framework/.../command/interceptor/optimisticlocking/EventIdExpectingCommand.java`:

```java
@NullMarked
public interface EventIdExpectingCommand extends Command {
    /** The optimistic-locking expectation over the sourced head; {@code None} to opt out. */
    EventIdExpectation expectedEventId();   // non-null
}
```

(Package needs a `package-info.java` with `@NullMarked`, mirroring the other interceptor packages.)

**Exception refactor** (see §4 for the rationale and blast radius):
1. `com.opencqrs.framework.CqrsFrameworkException.TransientException.ConcurrencyException` becomes the **base** — add a `(String message)` constructor
   alongside the existing `(String message, Throwable cause)`; keep it concrete and `extends
   CqrsFrameworkException.TransientException`.
2. Add `com.opencqrs.framework.client.ConflictingWriteException extends ConcurrencyException`; move the
   `ClientRequestErrorMapper` throw site to it (opaque store 409 — no cause detail available).
3. Add `com.opencqrs.framework.optimisticlocking.OptimisticLockingException extends
   ConcurrencyException` with fields `subject`, the **violated `EventIdExpectation`** (never `None`), and `actualEventId`
   (nullable), plus a message-building constructor. Capturing the whole expectation lets a handler tell *which* semantic
   failed (`Exactly` vs `AtMost`), not just the id.

### Stage 9.2 — The interceptor

`framework/.../command/interceptor/optimisticlocking/OptimisticLockingCommandInterceptor.java`:

```java
// framework module — Spring-free: NO `implements Ordered`, NO order constant. Ordering is the autoconfigure's job (9.3).
public class OptimisticLockingCommandInterceptor implements CommandInterceptor<EventIdExpectingCommand> {

    @Override public Class<EventIdExpectingCommand> commandClass() { return EventIdExpectingCommand.class; }

    @Override
    public <R> @Nullable R intercept(CommandInvocation<EventIdExpectingCommand> inv,
                                     CommandLifecycle<R> lc,
                                     ValueContinuation<R> cont) throws Exception {
        lc.handler((jp, c) -> {                         // rebuilt-state / pre-handler point; fail-fast
            String actual = jp.latestSourcedEventId();
            String subject = inv.command().getSubject();
            switch (inv.command().expectedEventId()) {
                case EventIdExpectation.None ignored -> {}
                case EventIdExpectation.Exactly exactly -> {
                    if (!Objects.equals(actual, exactly.eventId()))
                        throw new OptimisticLockingException(subject, exactly.eventId(), actual);
                }
                case EventIdExpectation.AtMost atMost -> {          // globally monotonic ids: reject only if strictly newer
                    if (actual != null && IdUtil.fromEventId(actual) > IdUtil.fromEventId(atMost.eventId()))
                        throw new OptimisticLockingException(subject, atMost.eventId(), actual);
                }
            }
            return c.proceed();
        });
        return cont.proceed();
    }
}
```

Notes:
- Registers a **single `handler` transformer**; the root just proceeds. Registration happens before the root proceeds
  (respecting the register-before-proceed freeze).
- Throwing from the `handler` advice vetoes the command and skips the append (the framework unwinds; no events published).
- Do **not** read `latestSourcedEventId` in a `sourcedEvent` hook — that misses cache-served events (decision #1).
- **No `Ordered`.** The core must not depend on Spring; ordering is applied by the `@Bean` in 9.3.

### Stage 9.3 — Auto-configuration (default-on) + order constants

**Order constants** — `framework-spring-boot-autoconfigure/.../command/CommandInterceptorOrders.java` (public API; the
single source of truth for provided-interceptor nesting):

```java
package com.opencqrs.framework.command;

/**
 * Well-known {@code @Order} values for the framework-provided command interceptors, so their relative nesting lives in
 * one place and user interceptors can position themselves around them. Convention: <strong>lower = further outside</strong>
 * (wraps more); beans without an explicit order sort at {@link org.springframework.core.Ordered#LOWEST_PRECEDENCE}
 * (innermost). The negative range is reserved for future outermost provided interceptors (observability, security);
 * values are spaced to leave integer room for user interceptors to interleave.
 */
public final class CommandInterceptorOrders {
    private CommandInterceptorOrders() {}

    /** Optimistic-locking guard ({@code OptimisticLockingCommandInterceptor}). */
    public static final int OPTIMISTIC_LOCKING = 0;
}
```

**Auto-configuration** — `framework-spring-boot-autoconfigure/.../command/OptimisticLockingCommandInterceptorAutoConfiguration.java`:

```java
@AutoConfiguration
public class OptimisticLockingCommandInterceptorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @Order(CommandInterceptorOrders.OPTIMISTIC_LOCKING)   // ordering applied HERE, not in the core interceptor
    public OptimisticLockingCommandInterceptor openCqrsOptimisticLockingCommandInterceptor() {
        return new OptimisticLockingCommandInterceptor();
    }
}
```

- Register the auto-configuration in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- `CommandRouterAutoConfiguration.openCqrsCommandRouter` already injects `List<CommandInterceptor>`; Spring sorts it by the
  `@Order` on each bean method (`AnnotationAwareOrderComparator`), so **no change there**. User interceptors declared as
  beans position themselves with their own `@Order` / `Ordered`, e.g. `@Order(CommandInterceptorOrders.OPTIMISTIC_LOCKING
  - 10)` to sit just outside optimistic locking.
- Inert for any command that doesn't implement `EventIdExpectingCommand` (the router's assignability gate never matches),
  so default registration adds zero behaviour to existing apps.

### Stage 9.4 — Fixture support + unit/slice tests

- **Fixture:** give `CommandHandlingTestFixture` an event-id model for *given* events so it can populate
  `latestSourcedEventId` on the `CommandHandlerInvocation` it constructs (currently `null`, see §2). The stubbed raw
  `Event.id()` is already random-but-overridable per event (`EventSpecifierDsl.id(String)` — see the fixture's raw-event
  stubbing table), so the fixture already *has* per-given-event ids; thread the **last** given event's id (for the command
  subject) into the handler join point instead of `null`. This enables fixture-level optimistic-locking tests.
- **Interceptor test** (`OptimisticLockingCommandInterceptorTest`): a **`@CommandHandlingTest` slice** with
  `@Import(OptimisticLockingCommandInterceptor.class)` and a nested `@TestConfiguration` holding a `@CommandHandling`
  method + `@StateRebuilding`; drives the auto-wired `CommandHandlingTestFixture` with **numeric** given-event ids (so
  `IdUtil.fromEventId` can order them). Parameterized over all variants: `None` (skip), `Exactly` (equal ⇒ ok; different
  or pristine ⇒ conflict), `AtMost` (equal/older-scope/pristine ⇒ ok; **strictly newer ⇒ conflict**) — the older-scope
  case being the one exact-equality got wrong. Lives in the `framework` module (its test scope now depends on
  `framework-test`); dogfoods the `@Import` inclusion path documented on `@CommandHandlingTest`.
- **Fixture-threading test** (generic, driver-level): two cases added to `CommandHandlingTestFixtureTest.Interceptors`
  using a plain capturing interceptor (no optimistic-locking reference) — asserts the handler join point sees the given
  event's id, and `null` when nothing is sourced.
- **Auto-config test:** bean present by default; `@ConditionalOnMissingBean` honoured; correctly ordered relative to
  user interceptors via `CommandInterceptorOrders`.

### Stage 9.5 — Example-application showcase — DEFERRED (not needed for now)

If revisited: a library command implements `EventIdExpectingCommand`; the REST layer passes the client's expected id;
demonstrate the conflict (409-style) path. Note `ExceptionControllerAdvice` already maps `TransientException` → 409, so
`OptimisticLockingException` surfaces as 409 with no advice change.

- **Read-model dependency (main work):** the example must be able to hand the client the current head event id. Extend a
  projection / the book read model to record the last event id per book (or expose it), and return it (e.g. an `ETag` on
  the `GET`).
- Make a mutating command (e.g. `BorrowBookCommand` / `ReturnBookCommand`) implement `EventIdExpectingCommand`; the
  controller reads an `If-Match`-style header (or body field) into `expectedEventId()`.
- Map `OptimisticLockingException` to HTTP 409 in `ExceptionControllerAdvice`.
- Add an integration test proving the stale-read reject over the real ESDB testcontainer.

---

## 4. Exception hierarchy refactor — detail & blast radius

Target hierarchy (all under the sealed framework tree; `ConcurrencyException` stays `TransientException`):

```
CqrsFrameworkException                      (sealed)
└─ TransientException                        (non-sealed)
   └─ ConcurrencyException                   ← base (com.opencqrs.framework.client)
      ├─ ConflictingWriteException           ← store rejected the write (opaque HTTP 409) — client pkg
      └─ OptimisticLockingException          ← pre-handler client-expectation mismatch — optimisticlocking pkg
```

Why transient (not mirroring `CommandSubjectConditionViolatedException`, which is *non*-transient): a version conflict
heals on refresh + retry; a `SubjectCondition` (EXISTS/PRISTINE) violation is a structural fact that never heals.

**Touch points (grounded in current code):**
- `framework/.../client/ConcurrencyException.java` — becomes the base; add `(String)` ctor.
- New `framework/.../client/ConflictingWriteException.java extends ConcurrencyException`.
- `framework/.../client/ClientRequestErrorMapper.java:56` — change `new ConcurrencyException("concurrency error", e)`
  to `new ConflictingWriteException(...)`; update the `@throws` javadoc at `:23`.
- `framework/.../client/ClientRequestErrorMapperTest.java:80` — asserts the mapped exception type for HTTP 409. **If it
  asserts `isInstanceOf(ConcurrencyException.class)`** the subclass keeps it green; **if it asserts exact type**, update
  the expected class to `ConflictingWriteException`.
- `framework/.../integration/CommandAndEventHandlingIntegrationTest.java` (`:329,333,355,384,388,408`) — asserts
  `isInstanceOf(ConcurrencyException.class)`; **stays green** because `ConflictingWriteException` *is* a
  `ConcurrencyException`. (Nice regression property: keeping the base name means these do not change.)
- New `OptimisticLockingException` in the `optimisticlocking` package.

---

## 5. Runtime behaviour & caveats (document on the public types)

- **Fail-fast** at the rebuilt-state / pre-handler point (`CommandHandlerInvocation`). Note "fail-fast" is *before the
  handler*, **not** before sourcing — the check needs the sourced head id, so the sourcing cost is inherent.
- **Scope-robust via global order:** `AtMost` (`sourcedHead ≤ expected`) tolerates a narrower-scope (older) head, so a
  `LOCAL` command against a subject last read through a broader `RECURSIVE` scope is not falsely rejected. `Exactly`
  (`==`) does *not* tolerate this and is the strict niche. Under `RECURSIVE` the head is the hierarchy head.
- **Requires sourcing:** under `SourcingMode.NONE` the head is always absent (`null`) — `AtMost` never rejects and
  `Exactly` always rejects; neither is meaningful.
- **No `null`/pristine overloading:** the sealed `EventIdExpectation` removes the old nullable-id ambiguity — `None` is
  the explicit opt-out; asserting a *new* subject is `Command.SubjectCondition.PRISTINE`'s job (which also adds a
  store-side append precondition). A pristine (`null`) head is handled by order: `AtMost` treats it as "not newer".
- **Conservative rejects:** read-model lag means any newer event rejects; callers refresh + retry (hence *transient*).
- **Composes with the write precondition:** even if a concurrent write slips in *after* this check, the router's
  append-time `SubjectIsOnEventId` still guards the source→append window; the two together close both windows.
- **Ordering interactions:** at `CommandInterceptorOrders.OPTIMISTIC_LOCKING` (`0`), observability interceptors
  (tracing/metrics) should sit at negative orders so they record the reject; unordered user interceptors (default
  `LOWEST_PRECEDENCE`) nest inside, so a stale command is rejected before their handler-level work runs. The order is
  applied by the autoconfigure `@Bean`, not the core interceptor.

---

## 6. Cross-cutting invariants to preserve

- **Empty applicable list = today's behaviour.** The interceptor only joins a command's chain when its `commandClass()`
  gate matches; non-versioned commands take the unchanged path.
- **Core stays Spring-free.** The interceptor lives in `framework` (no Spring) and must **not** implement `Ordered` or
  reference any Spring type. Everything Spring — the `@AutoConfiguration`, the `@Order`, and `CommandInterceptorOrders` —
  lives in `framework-spring-boot-autoconfigure`.
- **Register-before-proceed** and **observer/transformer** contracts of the shared mechanism are respected (single
  `handler` transformer, registered before the root proceeds).
- Existing no-interceptor and `ConcurrencyException` tests are the regression suite; the base-class rename must keep them
  green.

---

## 7. Definition of done

- [x] `EventIdExpectingCommand`, `OptimisticLockingCommandInterceptor`, `OptimisticLockingException` in the
  `optimisticlocking` package; `ConcurrencyException` promoted to base + `ConflictingWriteException` sibling;
  `ClientRequestErrorMapper` updated.
- [x] `CommandInterceptorOrders` added; auto-config registered and default-on; `@Order(...)` on the bean; excludable;
  `@ConditionalOnMissingBean`. Core interceptor free of any Spring dependency (no `Ordered`).
- [x] Fixture populates `latestSourcedEventId` (generic threading test in `CommandHandlingTestFixtureTest`); interceptor
  driven via a `@CommandHandlingTest` + `@Import` slice test; auto-config test — all green; NullAway clean.
- [ ] ~~Example app showcases a stale-read 409 over ESDB.~~ **Deferred (not needed for now.)**
- [x] `interceptors.md` §5.3 (final names) stays consistent; `interceptors_implementation.md` Stage 9 status advanced.
