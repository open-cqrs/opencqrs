---
name: write-article
description: Write well-structured professional articles. Use when creating blog posts, technical articles, or thought leadership content.
argument-hint: "[topic-or-article-brief]"
allowed-tools: AskUserQuestion, Read, Write, Edit, Glob, Grep, Bash
---

# Article Writing Skill

Write a professional article based on the following input: $ARGUMENTS

## Workflow

### Step 1: Determine Input Type

Examine the input carefully. It falls into one of two categories:

**A) Structured Article Brief** — The input contains an Article Brief with fields like **Title**, **Slug**, **Category**, **Tags**, **Author**, **Core Thesis**, **Article Structure**, **Technical Details**, etc. This typically comes from the `brainstorm-article` skill. In this case, **skip Step 2 entirely** and proceed directly to Step 3 (Writing). Use all information from the brief — do not re-ask questions that are already answered.

**B) Loose topic or instructions** — The input is a free-form topic, a rough idea, or a set of instructions without a structured brief. In this case, proceed to Step 2 to gather the information you need before writing.

### Step 2: Lightweight Intake (only if no Article Brief)

This step only runs when the input is a loose topic without a structured brief.

First, ask the user whether this article should be **based on a specific repository or codebase**, or whether it should be **written freely** without a code reference. Use AskUserQuestion for this.

- If the user chooses a **codebase-oriented article**, ask for the repository path (or confirm the current working directory). Then explore the codebase thoroughly — read relevant source files, understand the domain model, identify patterns, and extract code examples that can serve as inspiration for the article. **Do not copy code verbatim from the codebase into the article.** Instead, understand the patterns and re-express them using a fictional domain that fits the article's narrative.
- If the user chooses a **freely written article**, proceed based on the provided topic and your own knowledge.

Then confirm the following with the user via AskUserQuestion:

- **Title** for the article
- **Category** (e.g., "Event Sourcing", "Architecture", "Testing")
- **Tags** (3-5 relevant tags)
- **Author** (default: `kersten` — confirm or ask for a different author key from `.authors.yml`)
- Whether this article is **part of a series** (and if so, which series and position)

### Step 3: Writing

Write the article following all the style and formatting rules defined below. If an Article Brief was provided, follow its structure, sections, key insights, and technical details closely. Present the full article to the user for review before saving.

### Step 4: MkDocs Integration

Once the user approves the article, save it into the mkdocs blog structure:

1. **Determine the next post number** by listing existing files in `mkdocs/docs/blog/posts/` and incrementing the highest number found.
2. **Create the post file** at `mkdocs/docs/blog/posts/post-{N}-{slug}.md` with the following front matter format:

```yaml
---
title: <Article Title>
date: <YYYY-MM-DD>
authors:
  - <author-key>
categories:
  - <Category>
tags:
  - <tag1>
  - <tag2>
  - <tag3>
slug: <url-slug>
---
```

3. **Add the `<!-- more -->` excerpt marker** after the introduction paragraph (first paragraph after the H1 heading). This controls where the preview cuts off on the blog index page.

4. **Update `mkdocs/mkdocs.yml`** — add the new post to the `nav` section under `Blog`. If the article belongs to a series, add it under the existing series heading. If it starts a new series or is a standalone article, add it as a direct entry under Blog.

5. **Verify the author key** exists in `mkdocs/docs/blog/.authors.yml`. If the author is not yet listed, ask the user for the required information (name, description) and add the entry.

## Language and Tone

- Write in **American English** exclusively. Use American spelling (e.g., "behavior" not "behaviour", "optimize" not "optimise", "color" not "colour").
- Maintain a **professional, elevated tone**. The writing should feel authoritative and polished, yet approachable and engaging.
- **Address the reader directly** using "you" and "your". Make them feel like the article is written specifically for them.
- Write in **first person singular** ("I") when sharing opinions, experiences, or recommendations.
- When referring to **products or services by digital frontiers**, use first person **plural** ("we", "our") and always refer to the company as "digital frontiers" (lowercase "digital").
- Never mix languages. If the article is in English, every term must be in English — no German, no untranslated domain terms, no foreign-language phrases unless they are established technical jargon.

## Structure

- Use **headings up to second level only** (`##`). Never use `###` or deeper heading levels.
- Each section (under a heading) must contain **between 3 and 5 paragraphs**.
- Each paragraph must contain **between 3 and 5 sentences**. No single-sentence or two-sentence paragraphs. No paragraphs with 6 or more sentences.
- Code blocks and diagrams are **standalone elements** between paragraphs. They do not count toward the paragraph limit of a section, but they must always be framed by a preceding paragraph that sets context and a following paragraph that explains the takeaway.
- Start with a compelling introduction that frames the problem or topic and draws the reader in.
- End with a conclusion that summarizes key insights and gives the reader a clear takeaway or call to action.
- End each section with a **memorable insight** — a sentence or two that captures the core lesson of that section in a quotable, punchy way.

## Article Series

- When writing a series of related articles, maintain **consistent terminology and domain language** across all parts.
- Reference previous articles with a brief recap so readers who start in the middle can follow along.
- End each article (except the last) with a **teaser** for the next article that frames the upcoming problem.
- Use the same fictional domain, actors, and example data throughout the entire series.

## Formatting and Emphasis

- **Bold approximately 20% of the text** to highlight important statements, key concepts, and critical insights. Distribute bold text evenly throughout the article.
- **Bold all links** without exception. Every hyperlink must be wrapped in bold formatting, e.g., `**[link text](url)**`.
- Use bold to emphasize key terms when they are first introduced, important conclusions, and actionable advice.
- Do not use italic for emphasis. Reserve italic only for technical terms, book titles, or non-English words if absolutely necessary.

## Writing Style

- Prefer **active voice** over passive voice. Write "You configure the interceptor" rather than "The interceptor is configured".
- Use **concrete examples** to illustrate abstract concepts. Show, don't just tell.
- Keep sentences clear and direct. Avoid unnecessarily complex sentence structures, but do not oversimplify either — the audience consists of professionals.
- Use **transitional phrases** between paragraphs and sections to maintain a natural reading flow.
- Avoid filler words and phrases. Every sentence should convey meaningful information or advance the argument.

## Technical Content

- When including code examples, keep them **concise and focused** on the point being made. Do not include boilerplate or unrelated code.
- Introduce code examples with context — explain what the reader is about to see and why it matters.
- After a code example, explain the key aspects and connect them back to the broader argument.
- Default to **framework-agnostic** explanations. Describe the pattern first, then show the implementation. The reader should understand the concept even if they use a different framework.
- Use **Mermaid diagrams** to visualize workflows, state machines, sequences, or architectural concepts whenever a visual representation adds clarity that prose alone cannot achieve.

## Fictional Examples and Domain Language

- When illustrating concepts with fictional examples, **all domain terms must fit the fictional domain**. Never leak internal terminology from a source codebase into the article.
- Before finalizing, review every class name, field name, and domain term in the article. Ask: would a reader unfamiliar with the source project understand this term in the context of the fictional domain?
- Keep the fictional domain **simple and universally understood**. Every developer should intuitively grasp the domain without needing background knowledge.

## Things to Avoid

- Do not use emojis.
- Do not use bullet point lists in the article body. Integrate information into flowing paragraphs instead. Lists are only acceptable in a brief summary or takeaway section at the very end, if appropriate.
- Do not use headings deeper than second level.
- Do not write paragraphs shorter than 3 sentences or longer than 5 sentences.
- Do not use British English spelling or conventions.
