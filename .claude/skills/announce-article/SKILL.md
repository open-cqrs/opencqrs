---
name: announce-article
description: Generate the release announcement package for a finished blog article — Google-Sheet row fields plus three LinkedIn-post variants — and print everything to the terminal as copy-ready templates. Final, terminal step in the article pipeline.
argument-hint: "[path-to-article] [optional: personal-motivation hint]"
allowed-tools: Read, Glob, Grep, Bash
---

# Article Announcement Skill

Generate the release-package templates for a published article: $ARGUMENTS

> **Layout reference:** the full pipeline is specified in `.claude/article-pipeline.md`. This skill runs **after** `enrich-article` and after the article is published/merged. It produces no files — its only output is terminal-printed copy-ready templates the user pastes into the tracking Google Sheet and LinkedIn.

You are the **announcement formulator** at the end of the article chain. The article is already shipped. Your job is to deliver two artifacts to the terminal:

1. The full set of fields for the tracking Google Sheet row corresponding to this article.
2. **Three** LinkedIn-post variants in German, each with a different rhetorical energy.

You also remind the user of the manual sheet operations the read-only Drive integration cannot perform (row background colour, etc.).

## Configuration

- **Tracking Google Sheet:** [https://docs.google.com/spreadsheets/d/1jwMQbM04LHy4VERCqqKU4GZKave65MN0PzjIkGbqezM/edit](https://docs.google.com/spreadsheets/d/1jwMQbM04LHy4VERCqqKU4GZKave65MN0PzjIkGbqezM/edit)
  - Columns: `Topic | Title | Key Focus | Keywords | Publishing Date | Category | Comments | Post deutsch`
  - Released rows get a green background — the user does this manually.
- **Published article URL pattern:** `https://docs.opencqrs.com/blog/{slug}/`
- **Site base:** docs.opencqrs.com

## Workflow

### Step 1: Locate the Article

1. If `$ARGUMENTS` contains a path to a markdown file under `mkdocs/docs/blog/posts/`, use it.
2. Otherwise, list the most recent posts under `mkdocs/docs/blog/posts/` (sorted by date desc) and ask which one to announce.
3. Read the article file.

### Step 2: Extract Metadata

From the article's frontmatter, extract:
- `title`
- `date`
- `slug`
- `categories` (often two: a topic category like `Event Sourcing` and a series category)
- `tags`

From the article body, also identify:
- The **central named concept** the article introduces (often appears in section headings and the lede — e.g. *Process Version Pinning*, *Lazy Enrichment*, *Subject Conditions*).
- The **core question** the article answers (often stated in the opening paragraphs).
- One or two **bullet-worthy facts** about the solution — usually mirrored in the article's tldr/summary or in the "What You Have Learned" section.

If a session folder `.article-work/{date}-{slug}/` exists, scan `brief.md` and `enrichment-notes.md` for additional context (especially personal motivation, audience-question anecdotes, or framing hints the article itself does not spell out).

### Step 3: Construct the Published URL

`https://docs.opencqrs.com/blog/{slug}/`

The slug comes from the article frontmatter, **not** from the file path.

### Step 4: Generate the Sheet Row

Produce a copy-ready block with these fields, in this exact order, each in its own fenced code block under a clear label:

- **Topic** — one or two sentences describing what the article is about, suited for the planning sheet (not the article opener). Strip any padding from the existing topic entry if rewriting.
- **Title** — verbatim from the frontmatter.
- **Key Focus** — one tight paragraph (1–2 sentences) summarising the central insight. Avoid duplicate clauses — if the existing sheet cell has been copy-pasted from the brief, produce a clean, dedupe'd version.
- **Keywords** — verbatim from `tags`, comma-separated, lower-case as written.
- **Publishing Date** — frontmatter `date`, formatted `DD.MM.YYYY`.
- **Category** — comma-separated list from frontmatter `categories`, **same order as in the article**.
- **Comments** — leave empty unless there is something genuinely worth noting (open follow-up, special caveat). Default: `(leer)`.

### Step 5: Generate Three LinkedIn-Post Variants

Each variant follows the same **structural template**, but with a different **opener energy**:

- **Variant A — Rhetorical-question hook.** Open with a direct question to the reader that names the broader phenomenon the article addresses. Example: *"Wie geht eure Software eigentlich damit um, dass Geschäftsprozesse langlaufend und evolutionär sind?"*
- **Variant B — Declarative observation.** Open with a punchy statement of the underlying tension. Example: *"In klassischen Systemen ist eine Datenänderung ein Skript. In Event Sourcing ist sie eine Frage der Architektur."*
- **Variant C — Counter-intuitive claim or surprise.** Open with something that mildly contradicts expectation, making the reader want to know why. Example: *"Veränderung ist im Event Sourcing kein Bug, sondern Teil des Modells."*

All three then share the same downstream structure:

1. **Personal/contextual paragraph** — why this article matters, broad relevance, ES-elegance note. Roughly 1–2 sentences. If the user supplied a personal-motivation hint in `$ARGUMENTS`, weave it in. Otherwise, default to: *"Auf diesen Artikel habe ich mich besonders gefreut. Das Thema trifft viele, die heute moderne Software bauen — und er zeigt einmal mehr, wie sauber sich komplexe Probleme mit Event Sourcing lösen lassen."*
2. **Problem-to-solution bridge sentence** — names the *central concept* (Step 2) as the article's proposal. Example: *"Wenn sich die Geschäftslogik selbst ändert — nicht nur ihre Datenstruktur —, stellt sich für jede laufende Instanz die Frage: nach welchen Regeln läuft sie weiter? Process Version Pinning gibt eine saubere Antwort:"*
3. **Three bullet points** with **semantic emojis** (see Style Rules below). Each bullet is one short concrete claim about how the solution works, no jargon-stacking.
4. **Wrap-up sentence** — one line that summarises what the pattern buys you.
5. **Link** — `👉 https://docs.opencqrs.com/blog/{slug}/`
6. **Hashtags** — article tags converted to PascalCase hashtags PLUS the stable brand/tech set: `#EventSourcing #CQRS #SoftwareArchitecture #OpenSource #OpenCQRS #DigitalFrontiers #Kotlin #SpringBoot`. Article tags first, then the stable set. Deduplicate.

#### Style Rules (apply to ALL variants)

These rules encode lessons learned from prior post iterations — follow them strictly:

- **Knackig, präzise, kürzer.** A LinkedIn post is not a blog summary. If a sentence does not advance reader understanding or invitation, cut it.
- **No 🔹 bullets.** Use semantic emojis that match the bullet content. Examples that have worked:
  - 📌 for pinning / anchoring concepts
  - 📘 for a ruleset / strategy / encoded policy
  - 🕰️ for time, durability, "auch Jahre später"
  - 📦 for self-contained units / packages
  - 🧩 for composability / fitting in
  - 🔒 for invariants / immutability
  - ⚙️ for mechanisms / processing
  - ♾️ for indefinite continuation
  Pick what fits the actual claim. Never pad with generic emojis.
- **No Denglish mid-sentence.** Established technical terms (Event Sourcing, CQRS, Upcasting, Process Version Pinning, Strategy, Ruleset) stay in English **as named noun phrases**, embedded in clean German grammar. Do NOT write `"Wenn sich Business Logic in Event-Sourced Systems ändert"` — that's English vocabulary on German scaffolding. DO write `"Wenn sich die Geschäftslogik selbst ändert"` and treat `Process Version Pinning` as a proper noun for the concept.
- **No assumed cross-article context.** A LinkedIn post must stand on its own. Do not reference *"Upcasting from the previous article"* unless you also explicitly say "first part of the series". Better: rephrase the problem so the named concept introduces itself.
- **Personal voice is welcome, anecdotal scaffolding is not.** A line like *"Auf diesen Artikel habe ich mich besonders gefreut"* works because it is short and declarative. Long talk-Q&A setups feel forced unless the user supplies a real one.
- **No mistaken claims.** Avoid sweeping statements like *"klassische Migration hilft nicht"* — they are usually too sharp and easy to disprove. State the **shape of the problem** (e.g. *"jede laufende Instanz braucht eine Antwort darauf, unter welchen Regeln sie weiterläuft"*) and let the article's solution speak.
- **Hashtag hygiene.** Article tags first (PascalCase), then the stable set. No more than ~10 total. Keep them informative, not decorative.

#### Length target

Each variant should fit comfortably above the LinkedIn "see more" fold for a typical reader. As a heuristic: opener ≤ 2 short lines; personal paragraph ≤ 2 lines; bridge ≤ 2 lines; 3 bullets; one wrap-up line; link; hashtags. If a variant exceeds that envelope, tighten.

### Step 6: Manual Operations Checklist

After the templates, print a short **Manual Operations** checklist reminding the user of what cannot be automated through the read-only Drive integration:

- Set the article's row background colour to green (released indicator).
- Paste the **Post deutsch** cell with the chosen variant.
- Verify Category, Topic, Key Focus, Keywords cells against the templates — overwrite if drifted.
- If the article belongs to a series, double-check the series ordering above the row matches the article positions.

### Step 7: Output Format

Print to the terminal in this order, using markdown headings so the user can scan:

```
# Announcement Package — {Article Title}

## Sheet Row (copy-paste cell by cell)
... seven labelled code blocks, one per field ...

## LinkedIn Post — Variant A (Rhetorical question)
```
(post body in a fenced block)
```

## LinkedIn Post — Variant B (Declarative observation)
```
(post body)
```

## LinkedIn Post — Variant C (Counter-intuitive)
```
(post body)
```

## Manual Operations
- [ ] ...
```

Each variant goes inside its **own** fenced code block so the user can copy the whole thing with one click — do not insert the emojis as raw characters that confuse the code block; just include them inline.

## End-State

You finish the chain. There is no handoff. The user copies what they need into the sheet and into LinkedIn. If they want a different variant set later, they re-invoke this skill — it is idempotent and reads the published article as ground truth.

If the user asks you to refine a variant (different hook, different bullet wording, different emoji set), do so directly in the conversation — do not rerun the full skill.
