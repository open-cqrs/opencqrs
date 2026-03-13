---
name: enrich-article
description: Enrich a blog article with cross-links to OpenCQRS documentation, abbreviation tooltips, admonitions, and content annotations. Fully automatic — reads the article, enriches it, and presents the result.
argument-hint: "[path-to-article]"
allowed-tools: Read, Write, Edit, Glob, Grep
---

# Article Enrichment Skill

Enrich an existing blog article with documentation cross-links, tooltips, admonitions, and content annotations: $ARGUMENTS

You are an **automatic article enricher**. Your job is to take a finished blog article and enhance it with mkdocs-material features that connect the article to the OpenCQRS documentation, provide readers with contextual explanations, and improve the reading experience. A key goal is to **break up the wall of text** — admonitions, annotations, and cross-links add visual variety and interactive elements that make the article more approachable and less monotonous. You do this **without changing the article's content, structure, or wording** — you only add enrichment on top.

## Workflow

### Step 1: Read the Article and the Documentation Structure

1. Read the article file at the path provided in `$ARGUMENTS`. If no path is provided, list files in `mkdocs/docs/blog/posts/` and ask which article to enrich.
2. Read `mkdocs/mkdocs.yml` to understand the full navigation structure and available documentation pages.
3. Read `mkdocs/includes/glossary.md` to know which abbreviations already have global definitions.
4. Scan the documentation pages under `mkdocs/docs/reference/`, `mkdocs/docs/concepts/`, `mkdocs/docs/tutorials/`, and `mkdocs/docs/howto/` to build a mental map of what documentation exists and what terms map to which pages.

### Step 2: Identify Enrichment Opportunities

Analyze the article for the following enrichment types:

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

1. First pass: Add cross-links (first occurrence per section).
2. Second pass: Insert admonitions at appropriate positions.
3. Third pass: Add content annotations with `{ .annotate }` on paragraphs that contain terms deserving expanded explanations.
4. Final pass: Append abbreviation tooltip definitions at the end of the file.

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
