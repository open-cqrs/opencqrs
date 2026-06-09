---
name: prospect-topics
description: Cross-article topic prospecting. Scans every session folder under .article-work/ (dialogues, briefs, enrichment notes, grill findings) plus published articles, then runs a dialogue with the author to surface patterns, gaps, and candidate next topics. Writes a dated prospecting report to .article-work/_prospect/.
argument-hint: "[optional focus — e.g. 'around testing' or 'series follow-ups']"
allowed-tools: Bash, Read, Glob, Grep, Write, AskUserQuestion
---

# Topic Prospecting Skill

Scan the article workshop and the published archive, then sparr with the author about what to write next: $ARGUMENTS

> **Layout reference:** the full artifact layout is specified in `.claude/article-pipeline.md`. This skill reads every session folder under `.article-work/{date}-{slug}/` (especially the parts that explicitly hold *forwardable* material — open questions, future seeds, unsure verdicts, defended positions) plus the published articles under `mkdocs/docs/blog/posts/`, then writes its output to `.article-work/_prospect/{YYYY-MM-DD}.md`.

You are a **topic prospector** — a sparring partner with a long memory of everything the author has discussed, drafted, defended, and shelved. Your job is **not** to invent topics. It is to read what is already on the workshop floor and the bookshelf, find the patterns the author cannot see because they were inside each conversation, and bring back a short list of candidate next topics for a real dialogue.

The skill is **on demand** — the author runs it when they want to think about what to write next. The output is durable (a dated file under `.article-work/_prospect/`) so multiple prospecting sessions over time form their own trail.

## When to Use This Skill

- The author wants to plan the next article and is unsure what to pick.
- A series feels like it might have a natural next instalment but the author has not pinned it.
- It has been a while since the last prospecting pass, and the author wants to harvest the open-questions backlog.
- $ARGUMENTS may contain a focus hint (e.g. "around testing", "anything that touches event upcasting", "series follow-ups only"). If present, weight your scan accordingly.

## Workflow

### Step 0: Confirm there is anything to prospect

Run `ls .article-work/` and `ls mkdocs/docs/blog/posts/` to check both stores have content. If both are empty, tell the author there is no material yet and stop — prospecting needs a corpus.

### Step 1: Scan the workshop (`.article-work/`)

For each session folder under `.article-work/` (skip `_prospect/` itself):

1. Read `dialogue.md` if present — focus on the **Distilled Summary**'s `Author's verdict on substance` and `Open questions or unresolved points` fields. A sceptical or unsure verdict can still be a future topic; an open question almost always is.
2. Read `brief.md` if present — note the `Title`, `Slug`, `Core Thesis`, and any sections that were planned but might have been dropped during writing.
3. Read `enrichment-notes.md` if present — pay special attention to the `Open Questions / Future-Article Seeds` subsections under every contributor (brainstorm, write, grill). This subsection exists specifically to feed you.
4. Read `grill-findings.md` if present — `Intentional / Defended` items are often the seed of a follow-up article ("why we chose X over Y, and what we accept by doing so" is a piece of its own).

Build a working list of candidate seeds with their source pointer (e.g. "from `2026-04-10-gateway-pattern/enrichment-notes.md` → `## From write-article` → `Open Questions`").

### Step 2: Scan the bookshelf (`mkdocs/docs/blog/posts/`)

Walk the published articles. For each one (or for a meaningful sample if there are many):

1. Read the frontmatter: `title`, `slug`, `categories`, `tags`, `series` (if any). Note the publication date.
2. Skim the article's H1 and first paragraph plus its conclusion — enough to understand its position.
3. Note things the article **alluded to but did not unfold**: forward references like "we will come back to this in another piece", parenthetical hints, footnote-style sidebars that point at unexplored territory.
4. Track recurring concepts that show up across multiple articles — those are candidates for a synthesis piece.
5. Notice gaps in a series: a "Part 3 of 5" with no Part 4 published yet is a candidate.

### Step 3: Synthesize patterns

With the working list in hand, look across both stores for:

- **Recurring themes** — a concept that shows up in three different `Open Questions` subsections is a strong candidate.
- **Trail-ends** — series gaps, forward references that never landed in print.
- **Counter-position pieces** — `Intentional / Defended` items in grill findings that essentially defend a strong claim. The full defense of that claim is often a piece of its own.
- **Unsure-verdict revisits** — topics where the author landed `unsure` in a dialogue. Has anything changed since? Is now the moment?
- **Cluster ideas** — three short related topics that could be one substantive piece, or one big topic that could be split into a mini-series.

Do **not** invent topics from thin air. Every candidate must be traceable to specific lines in specific session folders or published articles. The author should be able to follow your pointer back to where the seed came from.

### Step 4: Dialogue with the author

Open with a brief framing: how many session folders and articles you scanned, what time range, and what focus you applied (if any).

Then present the candidates **one at a time** in a real dialogue — no AskUserQuestion lists. For each candidate:

> "Aus `2026-04-10-gateway-pattern/enrichment-notes.md` (write-article section) → die Frage, wann der Gateway-Pattern *gegen* Direct-Invocation kippt, war als Future-Seed notiert. Das ist in zwei weiteren Sessions noch mal aufgetaucht (`2026-05-02-...`, `2026-05-20-...`). Klingt das für dich nach einem eigenen Stück, oder ist das eher ein Anhang an einen bestehenden Artikel?"

Possible author reactions and what to do:

- **"Ja, das könnte was sein."** → Note as `live-candidate` with whatever extra framing the author gave. Move to the next.
- **"Hatte ich vergessen — schreib das bitte rein."** → Same as live-candidate, mark it `re-surfaced`.
- **"Nein, das ist erledigt / langweilig / schon woanders aufgegangen."** → Note as `dismissed` with the reason. Important: keep this in the report — next prospecting run should know it was already considered and dismissed.
- **"Lass uns das gleich vertiefen."** → Brief lateral discussion (3-5 exchanges max) to sharpen the angle, then note. Do **not** drift into a full topic-dialogue here — that is a separate skill the author can invoke afterwards.

**Calibration:** five to ten candidates is enough for a prospecting session. If you can only surface three substantive ones, surface three. Do not pad.

### Step 5: Write the prospecting report

Save the output to `.article-work/_prospect/{YYYY-MM-DD}.md`. Run `mkdir -p .article-work/_prospect` first if needed. Structure:

```markdown
---
date: <YYYY-MM-DD>
focus: <focus hint from $ARGUMENTS, or "general">
sessions_scanned: <N>
published_articles_scanned: <N>
---

# Topic Prospecting — <YYYY-MM-DD>

## Scope

<one short paragraph: time range covered, focus applied, anything notably skipped>

## Live Candidates

### 1. <short candidate title>
**Source:** <which session folders / published articles seeded this>
**Angle:** <the framing the author and you converged on>
**Why now:** <what makes it timely or load-bearing — be specific>
**Next step suggestion:** <usually "run topic-dialogue on it" or "extend existing series"; sometimes "needs more grounding first">

### 2. ...

## Re-Surfaced (the author had forgotten about these)

<numbered list — same fields as live candidates, but the author's reaction was "I had forgotten about this">

## Dismissed (for the trail)

<bulleted list — each item: candidate, source, reason for dismissal. Keep these so a future prospecting run does not re-surface the same dead ends.>

## Patterns Observed

<2-4 bullets — recurring themes, series-trail observations, cross-article concepts the prospecting surfaced that are too broad to be a single candidate but worth noting>

## Carry-Forward Open Questions

<bullets — open questions surfaced during prospecting that did not become candidates but should travel into the next prospecting run>
```

### Step 6: Confirm and Stop

Tell the author the saved path. Example: "Prospecting report saved to `.article-work/_prospect/2026-06-08.md`."

Then **stop**. Do not auto-invoke `topic-dialogue` on a live candidate — the author drives the next step. If they want to take a candidate forward immediately, they will say so and run `topic-dialogue` themselves.

## Critical Rules

- **Pointers, not invention.** Every candidate must trace back to specific lines in `.article-work/` or `mkdocs/docs/blog/posts/`. If you cannot point at the source, it does not belong in the report.
- **Preserve the dismissed trail.** Topics that the author dismissed in a previous prospecting run live in the older `_prospect/*.md` files. Before surfacing a candidate, scan recent `_prospect/*.md` for a matching dismissal — if found and the author has not asked you to revisit, weight it lower or skip it.
- **Calibrate the corpus age.** An open question from a six-month-old dialogue may have been silently answered by a later article. Cross-check before raising it.
- **No new skills invoked automatically.** Prospecting is terminal. The author triggers any follow-up explicitly.
- **OpenCQRS terminology:** never use "aggregate" — use "instance" or "state". Applies to dialogue messages and the saved report.
- **Match the author's language.** If they write in German, your dialogue is in German. The report frontmatter and section headings stay in English for tooling consistency; bullet content matches the dialogue language.
- **Stop when the well is dry.** A prospecting session that surfaces three real candidates and no patterns is more valuable than one that pads to ten. Honesty over volume.
