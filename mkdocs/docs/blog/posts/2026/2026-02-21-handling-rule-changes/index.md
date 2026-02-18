---
draft: true
title: Handling Rule and Data Changes
date: 2026-02-21
authors:
  - kersten
categories:
  - Event Sourcing
tags:
  - command interceptors
  - aggregate versioning
  - business rules
  - lazy migration
slug: handling-rule-changes-in-running-processes
---

# Handling Rule and Data Changes in Running Processes

In the [previous article](../evolving-event-sourced-systems.md), I showed you how to evolve an event-sourced system across three dimensions: event schemas, process workflows, and API contracts. Those patterns handle structural changes — the shape of your data, the steps in your workflow, the version of your interface. But I carefully avoided a harder question. **What happens when a business rule changes, and you need running processes to adopt that change?**

Consider a concrete scenario from our loan application system. Applications requesting less than $10,000 were auto-approved — no manual review required. The business decides to lower that threshold to $5,000. Every new application must follow the new rule immediately, but there are hundreds of in-flight applications between $5,000 and $10,000 that were auto-approved under the old rule. **Are they still valid? Should the system re-evaluate them? Should it leave them alone?**

<!-- more -->

This article explores two complementary patterns that address this challenge. First, I will introduce the **command interceptor** as the mechanism that makes everything else possible. Then, I will walk you through **automatic aggregate upgrades** that detect and migrate stale aggregates on the fly. Finally, I will show you how a **version-bumped rule change** propagates through your system and forces re-evaluation of running processes. The key insight connects back to the [previous article](../evolving-event-sourced-systems.md): you grandfather your workflows, but you re-evaluate your rules.

## The Command Interceptor: Cross-Cutting Logic Before Every Command

Before I dive into the patterns themselves, you need to understand the mechanism that enables them. A **command interceptor** is a hook that runs before every command reaches its handler on an aggregate. It sits between the command bus and your business logic, with full access to the incoming command, the aggregate's current state, and the unit of work that wraps the operation.

Think of it as middleware for your command bus, but with a crucial difference: **it runs inside the aggregate's context**. This means it can inspect the aggregate's version, check the caller's permissions, enrich the command with metadata, and even trigger additional commands before the original one is processed. It is the perfect place for cross-cutting concerns that must be enforced consistently across every operation on an aggregate.

Here is a minimal skeleton that shows the structure. The interceptor receives every command message, performs its checks, and then either lets the command proceed or intervenes:

```kotlin
class LoanApplicationAggregate {

    var currentVersion: Int = 0

    @CommandHandlerInterceptor
    fun intercept(
        command: CommandMessage<*>,
        interceptorChain: InterceptorChain,
    ) {
        // Runs before EVERY command handler on this aggregate.
        // You can inspect the command, check aggregate state,
        // trigger side effects, or block execution entirely.

        interceptorChain.proceed()
    }
}
```

**The interceptor is not optional infrastructure — it is the foundation for the patterns that follow.** In the next two sections, you will see how this simple hook enables automatic version detection, lazy migration, and rule propagation. Every command that enters your aggregate passes through this gate, which means every interaction becomes an opportunity to enforce consistency.

## Auto-Upgrade: Lazy Migration of Stale Aggregates

Imagine your loan application aggregate was created six months ago at version 5. Since then, you have shipped versions 6 and 7 — adding new fields to the application data, restructuring validation rules, and tightening eligibility constraints. **Your event store holds thousands of aggregates at various versions, and there is no "UPDATE table SET version = 7" in event sourcing.** You cannot batch-migrate aggregates because their state is reconstructed from events, not stored in a mutable row.

The naive approach would be to ignore the version mismatch and hope that old aggregates work with new logic. **This breaks in subtle and dangerous ways.** A version 5 aggregate might lack fields that a version 7 command handler expects. Validation rules might evaluate against data structures that have been restructured. Constraints that were added in version 6 would never be checked against version 5 data. You need a way to bring old aggregates up to date — but only when someone actually interacts with them.

This is where the command interceptor becomes your migration engine. Before processing any command, the interceptor compares the aggregate's current version against the latest known version. If the aggregate is behind, it triggers an `UpgradeCommand` that runs before the original command is processed. The upgrade emits an `ApplicationUpgradedEvent` carrying the migrated data — restructured application data, re-validated participant states, and the new version number. Here is what this looks like in practice:

```kotlin
@CommandHandlerInterceptor
fun checkVersionAndUpgrade(
    command: CommandMessage<*>,
    latestVersion: Int,
) {
    if (command.payload is UpgradeApplicationCommand) return

    if (currentVersion < latestVersion) {
        handle(
            UpgradeApplicationCommand(
                applicationId = id,
                targetVersion = latestVersion,
            )
        )
    }
}

data class ApplicationUpgradedEvent(
    val applicationId: ApplicationId,
    val fromVersion: Int,
    val toVersion: Int,
    val upgradedValidationStates: Map<ParticipantId, ValidationState>,
)
```

The upgrade handler extracts the current application data from the aggregate, re-computes the validation states using the latest rules, and emits the `ApplicationUpgradedEvent`. **The event sourcing handler then applies the upgrade, updating the version and replacing the stale validation states with the freshly computed ones.** From this point forward, the aggregate behaves as if it had always been at the latest version — its event history still contains the full trail of how it got there, but its current state reflects the latest expectations.

**You do not need a batch migration — every command becomes an opportunity to bring an aggregate up to date.** This is lazy migration in the truest sense. Aggregates that nobody touches stay at their old version forever, which is perfectly fine because nobody is interacting with them. The moment someone sends a command, the interceptor detects the staleness and transparently upgrades before proceeding. The user never sees the migration happen — they just see a system that always behaves according to the latest rules.

## Rule Changes: When a Threshold Shifts

Now let me connect everything with the scenario from the introduction. Your loan application system auto-approved applications under $10,000 without manual review. **The business lowers this threshold to $5,000.** New applications must follow the new rule immediately, and existing applications must be re-evaluated. How do you implement this?

The answer involves three coordinated steps. First, you change the threshold in your validation code — this is a straightforward code change, not an event migration. Second, you bump the aggregate version constant from, say, version 6 to version 7. Third, you let the command interceptor do the rest. **The threshold is a validation rule, not event data.** It does not live in the event store, so there is no upcaster involved. It lives in your current code, and it applies the moment the aggregate is re-validated.

Here is what the version bump and the validation constraint look like:

```kotlin
object Versions {
    const val LOAN_APPLICATION_VERSION = 7  // bumped from 6
}

fun validateReviewType(amount: BigDecimal): Validation<ReviewType> =
    validation {
        addConstraint(
            "Automatic approval is only available for applications under $5,000"
        ) { reviewType ->
            if (amount > BigDecimal(5_000)) {
                reviewType != ReviewType.Automatic
            } else {
                true
            }
        }
    }
```

Here is what happens when a loan officer opens an existing application that was automatically approved at $7,000 under the old rule. The aggregate replays its events and reconstructs the state: version 6, amount $7,000, review type `Automatic`. **The replay itself is completely safe — event sourcing handlers reconstruct state without re-validating against business rules.** The threshold does not interfere with the replay because it is not part of the event data. The aggregate comes back exactly as it was when the last event was stored.

Then the loan officer's command arrives, and the interceptor kicks in. It detects that the aggregate is at version 6 while the system is at version 7. **It triggers the automatic upgrade before the original command is processed.** The upgrade handler extracts the current application data, re-validates it using the current rules — which now include the $5,000 threshold — and emits an `ApplicationUpgradedEvent`. The result: the $7,000 application with review type `Automatic` now carries a constraint violation. The loan officer sees the error and must switch the review type to `ManualReview`.

**A rule change is not just a code change — it is a version event.** By bumping the version, you create a clear boundary between "before" and "after." The interceptor uses this boundary to detect which aggregates are stale, and the upgrade mechanism ensures that every stale aggregate is re-validated against the current rules upon first interaction. You do not need to find and fix every affected aggregate yourself — the system does it for you, one command at a time.

## Three Strategies for One System

At this point, you might wonder: should every change trigger a re-evaluation? The answer is no — and the real answer is more nuanced than a simple yes or no. There are actually **three distinct strategies** at play, and they apply at different stages of an aggregate's lifecycle. Understanding when each one kicks in is the key to building a system that evolves without breaking running processes.

The first strategy is **re-evaluation during data capture**. While an application is still being filled out — before the applicant has submitted it for processing — no significant events have been generated yet. The data is still mutable. When the aggregate version bumps, the interceptor triggers an upgrade, and **the system re-validates all captured data against the current rules**. If the auto-approval threshold changed from $10,000 to $5,000, a $7,000 application that has not yet been submitted will immediately see a constraint violation. The applicant must correct the data before they can proceed. This is the safest and most straightforward case: the rules changed, the data has not been committed to any process step, so you simply apply the new rules.

The second strategy is **freeze on submission**. The moment an applicant submits their application and the system generates the first processing event, the captured data becomes immutable in a meaningful sense. From this point forward, **an aggregate version upgrade only bumps the version number — it does not re-validate the frozen data**. This is a deliberate design choice. The data was valid at the time of submission, a processing event was generated based on that data, and subsequent process steps may already depend on it. Re-evaluating it now could invalidate decisions that downstream steps have already acted upon. The event marks the boundary: everything before it is mutable and subject to current rules, everything after it is frozen.

The third strategy is **workflow grandfathering**, which I covered in the [previous article](../evolving-event-sourced-systems.md). The state model version is pinned at process start and never changes. An in-flight application keeps the state machine it was started with, even if new processes follow a completely different workflow. **You do not retroactively insert a compliance check into a process that has already passed the review stage.** The workflow version protects the structural integrity of the process, while the freeze-on-submission boundary protects the data integrity of committed decisions.

These three strategies compose into a clear decision framework. When you need to make a change, ask yourself three questions. **Does it affect the process flow?** Create a new state model version — new processes get the new flow, existing processes keep theirs. **Does it affect business rules for data that has not yet been submitted?** Bump the aggregate version — the interceptor will re-validate on the next interaction. **Does it affect data that has already been committed to a process?** Leave it alone — the submission event marks the point of no return. The architecture handles all three cases because the versioning mechanisms operate on orthogonal concerns, and the submission event acts as the boundary between mutable and immutable.

## Bringing It All Together

Over these two articles, you have seen how to evolve an event-sourced system across every dimension that matters. **Event upcasting** transforms old event schemas during replay. **State model versioning** pins workflows at process start. **Interface version blocking** enforces client-server compatibility. **Auto-upgrade via command interceptor** lazily migrates stale aggregates. **Version-bumped rule changes** propagate new business rules through re-validation — but only for data that has not yet been committed to a process.

These patterns are not alternatives — they are layers that compose. A single change to your system might require an upcaster for the event schema, a version bump for the aggregate, and an interceptor-driven upgrade to re-validate uncommitted data. **The version numbers and event boundaries tell you exactly what happened: the state model version tells you which workflow governs the process, the aggregate version tells you which rules apply, and the submission event marks where re-evaluation stops.** Together, they give you full traceability of how your system evolved and what each aggregate experienced along the way.

The immutability of event sourcing is not a limitation — it is a feature. Every schema migration, every workflow change, every rule update is captured as an explicit event in your aggregate's history. **The real power of event sourcing is not that you can replay the past — it is that you can evolve the future while keeping the past intact.** Build versioning into your architecture from day one, treat your first processing event as the point of no return, and your system will grow with your business instead of against it.
