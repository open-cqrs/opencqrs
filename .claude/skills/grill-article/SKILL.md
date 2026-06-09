---
name: grill-article
description: Critically inspect a written article before enrichment — verify codebase claims with real tool lookups, find internal contradictions, and (for argumentative articles) sparr with the author about over-simplifications, missing counter-cases, and weaknesses a critical reader would catch. The factual + consistency passes run in a sub-agent with a fresh, unbiased context (no brief, no dialogue, no enrichment-notes); only the argumentative dialogue happens in the main session. Writes findings to .article-work/{date}-{slug}/grill-findings.md and appends to the cumulative enrichment-notes.md.
argument-hint: "[path-to-article]"
allowed-tools: Read, Grep, Glob, Bash, AskUserQuestion, Write, Edit, Agent
---

# Article Grilling Skill

Critically inspect a written article before enrichment: $ARGUMENTS

> **Layout reference:** the full artifact layout is specified in `.claude/article-pipeline.md`. This skill reads the published article and (if found) the session folder under `.article-work/{date}-{slug}/`, writes `grill-findings.md` into that session folder, and appends a `## From grill-article ({date})` section to the cumulative `enrichment-notes.md`. The article itself is never edited.

You orchestrate an **adversarial review**. The actual factual and consistency grilling does **not** happen in the main conversation — it is delegated to a sub-agent that runs in a fresh, unbiased context. That sub-agent reads only the article and the source/docs. It does **not** see the brief, the dialogue transcript, or the existing enrichment-notes — those carry the author's framing and would soften the review. Once the sub-agent returns its findings, the main session takes over for the argumentative dialogue with the author (when applicable) and writes the artifacts.

Why the sub-agent split: a session that just helped brainstorm, write, or discuss this article will *empathise* with the choices made. Empathy is the opposite of what grilling needs. A fresh agent walks in cold and asks the questions a stranger would ask.

You do **not** edit the article yourself. The author decides what to fix. Your output is two files (the structured `grill-findings.md` and an append to `enrichment-notes.md`) plus, for argumentative articles, a real dialog where you and the author negotiate which issues are real and which are intentional.

## When to Use This Skill

- **Before `enrich-article`** in the standard article-writing flow.
- For **substantial articles** that are worth defending — typically anything over 1000 words or anything that takes a position.
- **Optional** — not every article needs grilling. Short announcements, release notes, or trivial walkthroughs probably do not.

The skill has two operating modes and asks the user up-front which to run:

- **Argumentative articles** — articles that take a position, defend a concept, or compare approaches. Full grilling with all three modes including the argumentative dialog. Examples: "Why we don't have aggregates", "Testing without an event store", "Dealing with business-process evolution".
- **Implementation walkthroughs** — articles that describe how something works in the codebase, without arguing for or against an approach. Only the factual and consistency modes run; the argumentative dialog is skipped. Examples: most "How We Build OpenCQRS" pieces.

## Workflow

### Step 0: Identify the Article, the Session Folder, and the Mode

This step runs in the main session — the sub-agent is not spawned yet.

1. If `$ARGUMENTS` contains a path to an article, use that. Otherwise list candidates in `mkdocs/docs/blog/posts/` and ask the user which one to grill.
2. Read the article's frontmatter to extract its `slug`. Then locate the matching session folder under `.article-work/` by looking for a folder whose name ends in `-{slug}` (typical pattern `.article-work/{YYYY-MM-DD}-{slug}/`). If multiple match, pick the most recent date prefix. If none match, ask the user via `AskUserQuestion` whether to:
   - point at an existing session folder explicitly,
   - skip session-folder integration entirely (legacy mode — `grill-findings.md` will still be written to `.article-work/{today}-{slug}/`, and `enrichment-notes.md` will be created fresh if it does not exist),
   - abort.
3. Ask the user via `AskUserQuestion` which mode applies:
   - `Argumentative` (full grilling including dialog)
   - `Implementation walkthrough` (factual + consistency only, no argumentative dialog)
   - `Abort` (user changed their mind)

Note the article path, the session folder path, and the mode. **Do not** read the article, the brief, the dialogue, or the existing enrichment-notes in the main session at this point — that would pollute the context. The sub-agent does the reading.

### Step 1: Dispatch the Factual + Consistency Grilling to a Sub-Agent

Spawn a sub-agent with the `Agent` tool, `subagent_type: general-purpose`. The sub-agent runs in a **fresh context** with no knowledge of how the article came to be, no exposure to the brief, the dialogue, or the enrichment-notes, and no empathy for the author's choices. It walks in as a stranger and asks a stranger's questions.

**Prompt template for the sub-agent (adapt to the current run):**

```
You are an adversarial reviewer for a published technical blog article. You have walked in cold — you have no knowledge of how this article came to be, who wrote it, or what the author intended. Your job is to grill the article on two axes:

Mode A (Code-Reference Check) — for every factual claim about code, doc, or filesystem in the article:
- Does the referenced file exist? Use Read / Glob to verify.
- Does the referenced class / method / field / annotation exist? Use Grep.
- Do referenced line numbers contain what the article quotes?
- Do code snippets reproduced in the article match the real source (allowing for explicitly-marked elisions)?
- Do quoted Javadoc lines or doc snippets match the source word-for-word?
- Do internal cross-links to other blog posts or doc pages resolve to existing files?

Severity:
- error — claim is factually wrong or reference is broken
- warn — claim is imprecise or partial (reference is real but the quote misrepresents it)
- info — cosmetic mismatch (e.g., reformatted whitespace)

If you cannot verify a claim because you cannot locate the relevant source, mark it `unverifiable` and explain what you searched for.

Mode B (Internal-Consistency Check) — read the article holistically and look for:
- Code-vs-prose contradictions: a prose statement the shown code immediately undermines. This is the single most common failure pattern — surface it aggressively.
- Self-contradictions across sections.
- Terminology drift: the same concept named differently, or different concepts collapsed under one name.
- Project-rule violations: OpenCQRS articles must not use "aggregate" — use "instance" or "state" instead.
- Introduced-but-unused: a concept, method, or character introduced with fanfare and never referenced again.
- Forward references that never land: "we will see how X solves this" with no follow-through.

ARTICLE PATH: <absolute path to the article file>
ARTICLE TYPE: <argumentative | implementation-walkthrough>

What to do:
1. Read the article completely. That is your starting point and your only narrative input.
2. Use Read, Grep, Glob, and Bash to verify every code, doc, and cross-link claim. Read the actual sources — do not "remember" what a file contains.
3. For Mode B, re-read the article holistically and find the failure patterns above.
4. Do NOT read any of these files even if you find them: the article's session folder under `.article-work/`, any file named brief.md, dialogue.md, or enrichment-notes.md. Those carry the author's framing and would compromise your independence. Verify against the SOURCE CODE and DOCS, not against author notes.
5. If the article type is `argumentative`, in addition to Modes A and B, identify 3-8 candidate Mode C dialogue points — places where you would push back on the argument itself (over-simplifications, missing counter-cases, absolutist statements, one-option-presented-as-the-option, concepts introduced and abandoned). These will be raised with the author in the main session — do NOT engage in dialogue yourself.

Return a structured report with this exact shape (markdown):

```
## Mode A — Code-Reference Findings
### Errors
- <location in article>: <what is wrong> — <pointer to actual source>
### Warns
- ...
### Info
- ...
### Unverifiable
- ...

## Mode B — Internal-Consistency Findings
- <pattern>: <where in article> — <description>
- ...

## Mode C — Candidate Dialogue Points (argumentative articles only)
1. <location in article>: <the tension> — <suggested probing question to ask the author>
2. ...

## Confirmed and Clean
- <short list of substantive claims that were checked and held up, by section>
```

Calibration: a substantial article will surface 5-15 discussion-worthy points total across Modes A, B, and C combined. Surface the load-bearing ones. Do not nitpick commas or stylistic choices.

Project terminology: never use "aggregate" yourself when describing findings — use "instance" or "state". If the article uses "aggregate", flag it as a Mode B project-rule violation.
```

Wait for the sub-agent to return. The main session keeps its hands off the article during this time.

### Step 2: Mode C — Argumentative Dialogue (argumentative mode only)

Modes A and B are done by the sub-agent. Mode C — the dialogue — runs in the main session because the author needs to respond and you need to engage with them.

For argumentative articles, walk through the **Mode C candidate dialogue points** the sub-agent returned. Each one is a question the sub-agent flagged from its cold reading. Now you are running the dialog with the author. The goal is to pre-empt the kind of comments that would otherwise show up in a PR review.

The patterns the sub-agent looked for (so you know what kind of questions to expect in its return):

1. **One option presented as the option.** The article describes one approach as if it were the natural or only choice, when a reasonable practitioner might choose differently. The dialog asks: did you consider X? Why is your approach better here? Should the article acknowledge X exists?
2. **Over-simplified code that misrepresents the concept.** Simplification for readability is legitimate; simplification that makes the example conceptually divergent from how OpenCQRS actually works is a trap, because a knowledgeable reader will trust the example and be misled. The dialog asks: this snippet shows `X`; in real OpenCQRS it would be `Y` — is the simplification still faithful to the *concept*, or has it changed what the concept means?
3. **Critical-reader gaps.** A sharp reader will see a hole in the argument that the article does not address. The dialog asks: a reviewer might say "but what about X?" — do you want to acknowledge it, refute it, or leave it for a follow-up?
4. **Absolutist statements.** "X never happens", "Y is always Z", "this is the only way". Almost always wrong at the margins. The dialog asks: do you want to qualify this, or are you defending the strong form?
5. **Concepts introduced and abandoned** (also a Mode B item, but worth raising in dialog). The dialog asks: this method/concept appears here and never again — is it foundational and you forgot to use it, or should it come out?

For each candidate dialogue point returned by the sub-agent, present it concretely — use the sub-agent's anchor (the exact article quote and the tension it identified) and reframe it as a real question to the author:

> "Aus der Sub-Agent-Lesung: In Section 3 schreibst du [exact quote]. Der gezeigte Code macht [exact mechanic]. Ein Reviewer würde sagen: das zieht in entgegengesetzte Richtungen. Wie willst du damit umgehen?"

Then **wait for the user's response**. Possible user reactions and what to do:

- **"You're right, that's a bug — I'll change it."** Note as `to-fix`, ideally with a one-line suggested edit if you have one. Move to next finding.
- **"That's intentional, I want to keep the strong form and let the reader push back."** Note as `intentional-kept`. Move on.
- **"Let me think about it / discuss further."** Engage the conversation. Offer alternative framings, point at counter-evidence, ask clarifying questions. The dialog *is* the skill's value — do not rush to the next finding.
- **"I disagree with your reading."** Investigate. Re-read the passage in the article, re-read the code, re-read the broader context. If the sub-agent was wrong, say so clearly and move on — do not defend a flawed finding out of loyalty to the sub-agent. If you stand by it, restate it more precisely with the new context.

**Trust the sub-agent's findings as a starting point, not gospel.** It read the article cold, which is the point — but cold also means it may have missed context that a knowledgeable author legitimately relied on. When the author pushes back convincingly, the sub-agent's finding loses. That is part of the design.

**Calibration is critical.** A substantial article will surface 5-15 discussion-worthy points across all three modes, not 50. The sub-agent should already have calibrated; do not pad. If a candidate point feels weak when you actually look at it with the user in the loop, drop it rather than force the discussion.

### Step 3: Present the Summary in Chat AND Write `grill-findings.md`

After all modes are done (and, for argumentative articles, the dialog has run its course), do both of the following:

#### 3a. Present the structured summary to the user

```
## Grilling Summary for {article-slug}

Article type: {argumentative | implementation-walkthrough}
Sections examined: {N}
Codebase references checked: {N}

### Confirmed and Clean
{things that were checked and held up — short list, by section}

### To Fix (errors + accepted dialog outcomes)
{numbered list — each item: location, what is wrong, suggested edit}

### Intentional / Defended
{items the user explicitly chose to keep as-is — useful for later reference}

### Unverifiable
{items where the skill could not access the source to verify — needs human follow-up}

### Open for Reflection
{items the user wanted to think about further — not yet a decision}
```

#### 3b. Write the summary to `grill-findings.md` in the session folder

Use the Write tool to save the exact same summary content to `.article-work/{YYYY-MM-DD-slug}/grill-findings.md` with this frontmatter:

```markdown
---
article: <slug>
date: <YYYY-MM-DD>
article_type: <argumentative | implementation-walkthrough>
sections_examined: <N>
references_checked: <N>
---

# Grilling Findings: <Article Title>

<the same `### Confirmed and Clean` / `### To Fix` / `### Intentional / Defended` / `### Unverifiable` / `### Open for Reflection` sections as the in-chat summary, verbatim>
```

This is the durable artifact for the session: the enricher can read `Intentional / Defended` to know which weaknesses the author chose to keep (and therefore should not be "fixed" by adding cross-links or admonitions that try to compensate). Future revisits to the article can read the file to see what was already grilled.

### Step 4: Append to the cumulative `enrichment-notes.md`

Append a new section `## From grill-article ({YYYY-MM-DD})` to the **end** of `.article-work/{YYYY-MM-DD-slug}/enrichment-notes.md`. **Never overwrite previous sections.** If the file does not exist (legacy mode), create it with the header and frontmatter from the brainstorm contract before appending.

What to put in this section:

```markdown
## From grill-article ({YYYY-MM-DD})

### Open for Reflection (candidates for sidebars / annotations)
<For each "Open for Reflection" item from the grilling: a bullet naming the underlying tension, where in the article it shows, and a suggested handling — typically a `??? tip "Worth Considering"` collapsible admonition or a `{ .annotate }` annotation. The enricher will weigh whether to apply.>

### Intentional / Defended (surfaced as sidebars instead of main-text changes)
<For each "Intentional / Defended" item where the author explicitly chose to defend a strong claim or simplification: a bullet describing what was defended and why, plus a suggested companion admonition (typically `??? info "Why we chose this framing"` or similar) that lets the enricher gently acknowledge the trade-off without weakening the article's voice. Only include items the author wants surfaced — not every defended item belongs in a sidebar.>

### External References (for context)
<Any external sources surfaced during grilling — books, specs, blog posts that the author or you cited while debating a finding. Or omit if none.>

### Code Reference Hints (for content annotations or admonitions)
<Specific file paths and line numbers that came up during the code-reference check and would be valuable as annotation anchors. Or omit if none.>
```

**Do not** copy `To Fix` items into enrichment-notes — those belong in `grill-findings.md` only. The enricher should not be making editorial fixes; it operates on the article as the author chose to leave it.

### Step 5: Confirm Saves and Handoff

Tell the user the saved paths in plain text:

> "Grill findings saved to `.article-work/{folder}/grill-findings.md`. Enrichment notes appended at `.article-work/{folder}/enrichment-notes.md`."

Then ask whether the user wants to:

- Apply the `To Fix` items themselves before continuing.
- Move on to `enrich-article` immediately if the items are minor or already addressed.
- Stop here and revisit later.

Do not invoke other skills automatically — the author drives the next step.

## Critical Rules

- **Never edit the article.** The skill writes only `grill-findings.md` and appends to `enrichment-notes.md` — both in `.article-work/`. The published article is read-only for this skill. The author decides what to change in the article. Suggested edits live as text in the `To Fix` section of `grill-findings.md`, not as file edits to the article.
- **The factual + consistency grilling runs in a sub-agent. Do not bypass this.** Even if the main session "knows" the codebase or the article, dispatch to the sub-agent anyway. The fresh-context guarantee is the entire point of grilling — bypassing it for speed defeats the skill. The only exception is if the user explicitly opts out for an iterative re-run on already-fixed findings.
- **The sub-agent must not read the brief, the dialogue, or the existing enrichment-notes.** Those carry the author's framing. The sub-agent prompt instructs it not to read them; make sure your prompt template explicitly forbids it.
- **Use tools for verification, never memory.** This applies both to the sub-agent (its prompt enforces this) and to any verification you do in the main session if a dialogue exchange sends you back to the source. LLM "knowledge" of what the codebase contains is unreliable and is exactly what this skill exists to compensate for.
- **The dialog in Mode C is the substantive output of the argumentative path.** Do not skip it for argumentative articles, and do not flatten it into a one-shot report. The author defending or revising in conversation is what produces a better article.
- **Calibrate severity honestly.** An `error` is a factual wrong. A `warn` is a real imprecision. An `info` is cosmetic. Do not inflate findings to look thorough — that wastes the author's time and trains them to dismiss your output.
- **OpenCQRS terminology:** never use "aggregate" — use "instance" or "state". This applies to the sub-agent's findings, your dialog questions, and the saved summary.
- **If the sub-agent (or you) was wrong, say so plainly.** When the user pushes back and the codebase confirms their reading, acknowledge it explicitly in the dialog and move on. Defensive doubling-down on a wrong finding destroys the skill's value.
- **Stop when there is nothing substantive left to say.** A skill that finds nothing wrong should report "I checked and found no substantive issues" rather than inventing weak findings to justify having run.
