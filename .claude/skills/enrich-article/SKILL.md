---
name: enrich-article
description: Enrich a blog article with cross-links to OpenCQRS documentation, abbreviation tooltips, admonitions, and content annotations. Fully automatic — reads the article, enriches it, and presents the result.
argument-hint: "[path-to-article]"
allowed-tools: Read, Write, Edit, Glob, Grep
---

# Article Enrichment Skill

Enrich an existing blog article with documentation cross-links, tooltips, admonitions, and content annotations: $ARGUMENTS

> **Layout reference:** the full artifact layout is specified in `.claude/article-pipeline.md`. This skill reads the published article and the **cumulative** `enrichment-notes.md` from the matching session folder under `.article-work/{date}-{slug}/`. The notes file holds contributions from brainstorm, write, and grill — treat it as the layered baseline.

You are an **automatic article enricher**. Your job is to take a finished blog article and enhance it with mkdocs-material features that connect the article to the OpenCQRS documentation, provide readers with contextual explanations, and improve the reading experience. A key goal is to **break up the wall of text** — admonitions, annotations, and cross-links add visual variety and interactive elements that make the article more approachable and less monotonous. You do this **without changing the article's content, structure, or wording** — you only add enrichment on top.

## Workflow

### Step 1: Read the Article, the Companion Notes, and the Documentation Structure

1. Read the article file at the path provided in `$ARGUMENTS`. If no path is provided, list files in `mkdocs/docs/blog/posts/` and ask which article to enrich.
2. Extract the article's `slug` from its frontmatter. Locate the matching session folder under `.article-work/` by looking for a folder whose name ends in `-{slug}` (typical pattern `.article-work/{YYYY-MM-DD}-{slug}/`). If multiple match, pick the most recent date prefix.
3. **Read `enrichment-notes.md` from that session folder** if it exists. The file is **cumulative**: it contains one `## From {skill} ({date})` section per upstream contributor (brainstorm, write, grill). Read all contributor sections — they layer on top of one another. Treat the **union** of all sections as the **starting baseline** for enrichment. Apply each item unless it violates the global rules in this skill. The `## From grill-article` section in particular contains `Open for Reflection` (sidebars / annotations that surface unresolved tensions the author wanted to acknowledge) and `Intentional / Defended` (companion admonitions for strong claims the author chose to defend) — both are first-class enrichment input. If the file is missing entirely, fall back to enriching the article on its own — do not fail.
4. Read `mkdocs/mkdocs.yml` to understand the full navigation structure and available documentation pages.
5. Read `mkdocs/includes/glossary.md` to know which abbreviations already have global definitions.
6. Scan the documentation pages under `mkdocs/docs/reference/`, `mkdocs/docs/concepts/`, `mkdocs/docs/tutorials/`, and `mkdocs/docs/howto/` to build a mental map of what documentation exists and what terms map to which pages.

### Step 2: Identify Enrichment Opportunities

If an `enrichment-notes.md` exists, use **the union of all contributor sections** as your baseline. Iterate through each item and translate it into the corresponding enrichment type below:

- Collapsible Deep Dives → collapsible `??? tip` / `??? info` admonitions
- Cross-Link Targets → cross-links
- Admonitions list → admonitions
- Abbreviation Tooltips → abbreviation definitions at the file end
- Content Annotations → `{ .annotate }` markers
- Open for Reflection (grill section) → typically a `??? tip "Worth Considering"` collapsible admonition, occasionally an annotation
- Intentional / Defended (grill section) → typically a `??? info "Why we chose this framing"` collapsible admonition that acknowledges the trade-off without weakening the article's voice
- External References → grounding inside admonitions (do not invent links to external sites unless the URL is in the notes)
- Code Reference Hints → anchor points for annotations or admonitions
- Style/Voice Notes → constraints to respect throughout

If two contributor sections name the same target (e.g. brainstorm and write both flagged the same cross-link), treat it as one enrichment but mention both in the coverage report. Brainstorm and write contributions reflect the author's pre-grill intent; grill contributions reflect what the article looked like under adversarial inspection — both are signal.

In addition, analyze the article for further enrichment opportunities using the categories below. Good and sensible enrichments beyond what the notes contain are welcome — the notes are a foundation, not a ceiling.

#### A) Cross-Links to Documentation

Identify domain-specific terms and concepts that have a corresponding documentation page. Common linkable terms include but are not limited to:

| Term | Documentation Target |
|------|---------------------|
| Command Handler / CommandHandler | `../../reference/extension_points/command_handler/index.md` |
| State Rebuilding Handler | `../../reference/extension_points/state_rebuilding_handler/index.md` |
| Event Handler / EventHandler | `../../reference/extension_points/event_handler/index.md` |
| Command Router / CommandRouter | `../../reference/core_components/command_router/index.md` |
| Event Repository / EventRepository | `../../reference/core_components/event_repository/index.md` |
| Event Handling Processor | `../../reference/core_components/event_handling_processor/index.md` |
| ESDB Client | `../../reference/core_components/esdb_client/index.md` |
| Event Sourcing | `../../concepts/event_sourcing/index.md` |
| Events (as a concept) | `../../concepts/events/index.md` |
| Upcasting / Event Upcasting | `../../concepts/upcasting/index.md` |
| CQRS | `../../concepts/cqrs/index.md` |

**This table is a starting point, not an exhaustive list.** Always check the actual documentation structure for additional matches. Use relative paths from the blog post's location to the documentation target.

**Linking frequency: once per section.** Link a term the first time it appears within each `##` section. Do not link the same term again within the same section. If the term reappears in a later section, link it again on its first occurrence there.

**Link formatting:** Follow the article's existing style conventions. In articles that bold all links, use `**[term](path)**`. In articles without bold links, use `[term](path)`.

**Do not link terms inside code blocks, headings, or admonitions.**

#### B) Abbreviation Tooltips

Add `*[Term]: Explanation` definitions at the **very end of the article** for technical terms that benefit from a mouseover tooltip. These create automatic tooltips on every occurrence of the term throughout the article.

**Do not duplicate terms that already exist in `mkdocs/includes/glossary.md`** — those are auto-appended globally. Only add article-specific terms.

Good candidates for abbreviation tooltips:
- OpenCQRS-specific concepts (e.g., `*[CommandRouter]: The core component in OpenCQRS that routes commands to their registered handlers and manages write model reconstruction`)
- Domain-specific jargon used in the article's fictional domain
- Architecture patterns mentioned but not deeply explained in the article

Keep definitions concise (one sentence) and include the OpenCQRS connection where relevant.

Aim for **5 to 15 abbreviation definitions** per article, depending on the density of technical terms.

#### C) Admonitions

Insert admonitions where they add genuine value. Use them to:

- **`!!! info "OpenCQRS Feature"`** — Point out that a concept discussed in the article maps directly to an OpenCQRS feature, with a brief explanation of how OpenCQRS implements it.
- **`??? tip "Deep Dive"` (collapsible)** — Offer additional context or nuance that would interrupt the article's flow if inline, but is valuable for curious readers.
- **`!!! warning`** — Highlight common pitfalls or mistakes related to the topic.

**Prefer collapsible admonitions** (`???`) over static ones (`!!!`). Collapsible admonitions are visually appealing, invite exploration, and break up the wall of text without overwhelming the reader. They are a key tool for making the article feel interactive and layered. Use static `!!!` admonitions only for critical warnings or information the reader must not miss.

**Placement rules:**
- Place admonitions **between paragraphs**, never inside a paragraph.
- Do not place admonitions inside code block framing (between the intro paragraph and the code block, or between the code block and the explanation paragraph).
- Use **3 to 6 admonitions per article**, distributed across sections. Collapsible admonitions (`???`) can be used more generously than static ones.
- Admonition content should be **2 to 4 sentences**.

**Admonition syntax:**
```markdown
!!! info "Title Here"
    Content of the admonition. This should provide
    additional context that connects to OpenCQRS or
    deepens understanding.
```

#### D) Content Annotations

Use mkdocs-material **content annotations** to attach expandable explanations to specific terms or statements in the article. These appear as small clickable marker icons inline in the text. When the reader clicks the marker, an explanation box expands below it. This is the primary tool for adding deeper context without cluttering the reading flow.

Content annotations are already used throughout the OpenCQRS documentation (e.g., in tutorials and how-to guides). They require the `attr_list` and `md_in_html` extensions (both enabled) and the `content.code.annotate` theme feature (enabled).

**Syntax for annotations on a paragraph:**

```markdown
Some text explaining a concept (1) and continuing with more details about another topic (2).
{ .annotate }

1.  This is the expanded explanation for the first annotation marker. It can contain
    `code`, **formatting**, links, and multiple sentences.

2.  This is the second annotation. Keep it focused on one specific point.
```

The `{ .annotate }` attribute must be placed on its own line directly after the paragraph it applies to. The numbered list following it provides the content for each marker.

**What to annotate:**

- **OpenCQRS-specific terms** — When the article mentions a concept that maps to an OpenCQRS component (e.g., "upcaster", "command handler", "event repository"), add an annotation that briefly explains the OpenCQRS implementation and optionally links to the relevant documentation page.
- **Technical terms that deserve a deeper explanation** — When a term or statement would benefit from 2-3 sentences of context that would interrupt the paragraph flow if written inline.
- **Connections between the article's fictional examples and real-world OpenCQRS usage** — Help the reader bridge from the article's illustrative domain to their own codebase.

**Placement rules:**

- Place **2 to 4 annotations per article** — use them sparingly for high-value explanations that do not fit into an admonition. Prefer collapsible admonitions (`???`) when the explanation is longer or more self-contained. Annotations work best for brief, term-specific context. Avoid clustering multiple annotations in one paragraph — aim for at most 1 per paragraph.
- Do not place annotations inside code blocks. For code explanations, use code annotations with `/* (1)! */` syntax instead (these are a separate feature).
- Do not place annotations in headings or inside admonitions.
- The `{ .annotate }` attribute must be on its own line immediately after the paragraph (no blank line between paragraph and attribute).

**Annotation content guidelines:**

- Each annotation should be **2 to 4 sentences**.
- Start with the most important information — what this means in the context of OpenCQRS or the reader's codebase.
- Include a link to the relevant documentation page when applicable: `See [Event Upcasting](../../../../concepts/upcasting/index.md) for details.`
- Keep the tone consistent with the article — professional, direct, helpful.

### Step 3: Apply Enrichments

Apply all enrichments to the article. Work through the article section by section:

1. First pass: Add cross-links (first occurrence per section). Prioritize the cross-link targets named in `enrichment-notes.md`; then add any additional ones the standard rules surface.
2. Second pass: Insert admonitions at appropriate positions. Place the admonitions named in `enrichment-notes.md` first; then add any additional ones if the article still benefits from them and you are within the recommended count.
3. Third pass: Add content annotations with `{ .annotate }` on paragraphs that contain terms deserving expanded explanations — starting with the anchor phrases listed in `enrichment-notes.md`.
4. Final pass: Append abbreviation tooltip definitions at the end of the file — including the ones from `enrichment-notes.md` plus any additional article-specific terms that warrant tooltips.

**Critical rules while enriching:**
- **Never use the term "aggregate"** when adding enrichment content (admonitions, annotations, abbreviations). OpenCQRS does not have aggregates — use **"instance"** or **"state"** instead. If the article's original text uses "aggregate," flag it in the summary but do not change the article's wording (that is an editing concern, not an enrichment concern).
- **Never change the article's wording, structure, or content.** You are adding enrichment, not editing.
- **Never modify text inside code blocks.** Code blocks are untouchable.
- **Never modify the front matter** (the YAML between `---` markers).
- **Never modify or remove the `<!-- more -->` excerpt marker.**
- **Preserve all existing formatting** — bold, italic, links that already exist.
- **Do not add enrichment to the article title (H1 heading).**

### Step 4: Present the Result

After applying all enrichments, present a summary to the user listing:

1. **Cross-links added** — which terms were linked and to where
2. **Abbreviation tooltips added** — list of terms and their definitions
3. **Admonitions added** — type, title, and placement (after which paragraph/section)
4. **Content annotations added** — which paragraphs received `{ .annotate }`, what each annotation explains
5. **Notes file coverage** — if an `enrichment-notes.md` was present, explicitly state, **per contributor section** (`## From brainstorm-article`, `## From write-article`, `## From grill-article`), which items were applied and which were intentionally skipped (and why). This lets the author see how each upstream layer landed.

Then save the enriched article back to the original file path using the Edit tool.

## Quality Checklist

Before finalizing, verify:

- [ ] All relative links resolve correctly from the blog post's directory to the target documentation page
- [ ] No term is linked more than once within the same `##` section
- [ ] No abbreviation duplicates a term from `mkdocs/includes/glossary.md`
- [ ] Admonitions are placed between paragraphs, not interrupting paragraph flow
- [ ] Code blocks are completely untouched
- [ ] Front matter is completely untouched
- [ ] The `<!-- more -->` marker is preserved
- [ ] Content annotations have `{ .annotate }` directly after their paragraph (no blank line)
- [ ] Annotation numbered lists follow immediately after `{ .annotate }` with proper indentation (4 spaces)
- [ ] No more than 1 annotation marker per paragraph, 2 to 4 per article total
- [ ] Collapsible admonitions (`???`) are preferred over static ones (`!!!`) except for critical warnings
- [ ] No enrichment content uses the term "aggregate" — use "instance" or "state" instead
- [ ] The article reads naturally — enrichments enhance, not clutter
- [ ] If an `enrichment-notes.md` was present, every item it contained — across all contributor sections — was either applied or explicitly skipped with a stated reason
