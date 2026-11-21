---
name: review-branch
description: "Ensemble code review (all agents on Sonnet) of the current local branch against main (or a given base) for OpenCQRS, without a PR: checks out HEAD into a worktree and runs the opencqrs-review workflow (3 independent reviewers per type for correctness, design, tests & docs, impact; one moderator per type). Trigger: /review-branch [<base>], 'review my branch'"
---

# /review-branch

Ensemble review of the committed state of the current branch, e.g. before opening a PR. The skill only reports — it never changes code, never commits and never pushes.

Orchestration: workflow `opencqrs-review` (`.claude/workflows/opencqrs-review.js`). Domain foundation for the agents: `.claude/review/opencqrs-context.md`.

**Review, not CI.** Compilation, formatting and tests are the job of the build and CI (`.github/workflows/qa.yml`), not of this review. Without a PR there is no CI result; the review does not run them as a substitute. The agents spend their effort on judgement.

**Model.** Every agent runs on Sonnet (`model: sonnet` in each agent definition and on every workflow call). Do not override it.

## Usage

```
/review-branch [<base-branch>] [--known "<known issue>; <known issue>"]
```

Default base: `main` on `upstream` (if present, otherwise `origin`). Pass e.g. `origin/main` explicitly to review against the fork. `--known` lists issues the team already knows about (e.g. fixed in another PR); matching findings are marked "known" and do not count for the verdict.

## Prerequisites

- `jq` installed
- All agent types used by the workflow are registered in the session (`review-correctness`, `review-design`, `review-tests-docs`, `review-impact`, `review-moderator`). Agent files added or renamed during a session are only picked up after a short delay or a new session; a missing type makes that part fail (`failedTypes`) — resume the run once it is available.
- Invoking the skill is the opt-in for the workflow: 17 agents (12 reviewers, 4 moderators, 1 consolidation)

## Procedure

### 1. Prepare

```bash
.claude/scripts/review-prepare.sh branch [<base>]
```

The script determines the base (`upstream` before `origin`), creates the worktree for `HEAD` under `.review/branch-<name>/src`, archives results of an earlier run under `archive/<old head>` (numbered if that head was archived before) and generates the diff artifacts. Remember the `KEY=VALUE` output.

If the script prints `WARN=Uncommitted changes …` on stderr, tell the user that only the committed `HEAD` is reviewed.

Show the user: `LABEL`, author, `COMMITS`, `FILES`, `SHORTSTAT`, `BEHIND_BASE`.

### 2. CI status

There is no PR, so there are no CI checks. The `ciStatus` arg is:

```
not available (branch review) — compilation, spotlessCheck and tests are not checked
```

Suggest to the user to run `./gradlew spotlessCheck test` before opening the PR; do not run it as part of the review.

### 3. Determine the goal of the change

One sentence on what the branch is meant to achieve — from the commit messages (`git log --format=%s <BASE_REF>..HEAD`). If they do not make it clear, briefly ask the user. Only the goal, no assessment and no guesses about weaknesses (the reviewers should be unbiased).

### 4. Run the workflow

```
Workflow(name: "opencqrs-review", args: {
  repo: REPO, diffFiles: DIFF_FILES, diffByFileDir: DIFF_BY_FILE_DIR, fullDiff: FULL_DIFF,
  reportsDir: REPORTS_DIR, reviewFile: REVIEW_FILE, headSha: HEAD_SHA, baseRef: BASE_REF,
  label: LABEL, title: TITLE, author: AUTHOR, commits: COMMITS, files: FILES, shortstat: SHORTSTAT,
  behindBase: BEHIND_BASE, date: <today, YYYY-MM-DD>, goal: <sentence from step 3>,
  ciStatus: <text from step 2>,
  knownIssues: <text of --known, omit if not given>
})
```

Pass args as a JSON object, not as a string. While the workflow runs, do not rebuild anything in parallel.

If the workflow was changed during the session, the named lookup may still serve the old script (check the summary line of the tool result against `meta.description`). Then start it by path instead: `Workflow(scriptPath: ".claude/workflows/opencqrs-review.js", args: …)`.

**On abort or failure:** do not restart; use `Workflow(scriptPath: <path from the tool result>, resumeFromRunId: <runId>)` with identical args.

### 5. Check the result

After the notification: is `failedTypes` empty? Does `REVIEW_FILE` exist and is it non-empty (`ls -la`, do not read the whole file)? Report failures to the user and offer a resume before cleaning up.

### 6. Clean up

```bash
.claude/scripts/review-cleanup.sh <SLUG>
```

Removes the worktree and the ref. Reports stay under `.review/<SLUG>/` (gitignored).

### 7. Show the result

In the chat only: verdict, blast radius summary, the summary table from `rows` (with consensus and status), consensus counters per type, path of the review file. Name dissent rows explicitly — they need the user's decision. No full texts.

Then offer to discuss individual findings. Fixing them happens only on the user's explicit request and as a separate step after this skill — not as part of the review.

## Rules

- Never change code in the main repository, never commit or push.
- Orchestration lives in the workflow — do not start reviewers via the Agent tool alongside it, and do not pass a different model.
- Only note how far the branch is behind the base; do not rebase.
- Always clean up the worktree, also after failures — but ask first if a resume may be needed.
