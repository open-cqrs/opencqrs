---
name: review-pr
description: "Ensemble code review of a GitHub PR for OpenCQRS (all agents on Sonnet): checks out the PR head into a worktree, runs the opencqrs-review workflow (3 independent reviewers per type for correctness, design, tests & docs, impact; one moderator per type) and offers to post findings as PR comments. Trigger: /review-pr <number|url>, 'review the PR'"
---

# /review-pr

Ensemble review of a pull request. The skill only reports — it never changes code, never commits and never posts anything without approval.

Orchestration: workflow `opencqrs-review` (`.claude/workflows/opencqrs-review.js`). Domain foundation for the agents: `.claude/review/opencqrs-context.md`.

**Review, not CI.** Compilation, formatting and tests run in CI (`.github/workflows/qa.yml`); the skill reads their status and does not repeat them. The agents spend their effort on judgement.

**Model.** Every agent runs on Sonnet (`model: sonnet` in each agent definition and on every workflow call). Do not override it.

## Usage

```
/review-pr [<PR number or URL>] [--known "<known issue>; <known issue>"]
```

Without a number: the PR of the current branch (`gh pr view --json number`). `--known` lists issues the team already knows about (e.g. fixed in another PR); matching findings are marked "known" and do not count for the verdict.

## Prerequisites

- `gh` logged in, `jq` installed
- All agent types used by the workflow are registered in the session (`review-correctness`, `review-design`, `review-tests-docs`, `review-impact`, `review-moderator`). Agent files added or renamed during a session are only picked up after a short delay or a new session; a missing type makes that part fail (`failedTypes`) — resume the run once it is available.
- Invoking the skill is the opt-in for the workflow: 17 agents (12 reviewers, 4 moderators, 1 consolidation)

## Procedure

### 1. Prepare

```bash
.claude/scripts/review-prepare.sh pr <nr|url>
```

The script finds the remote of the PR's base repository itself (for forks e.g. `upstream`), creates the worktree under `.review/pr-<nr>/src`, archives results of an earlier run under `archive/<old head>` (numbered if that head was archived before) and generates the diff artifacts. Remember the `KEY=VALUE` output.

Show the user: `LABEL`, author, `COMMITS`, `FILES`, `SHORTSTAT`, `BEHIND_BASE`.

### 2. CI status

```bash
gh pr checks <nr>
```

Summarise in one or two lines (passed / failed / pending per check, with the head SHA the checks ran on). If the checks ran on an older commit than `HEAD_SHA`, say so. This text is the `ciStatus` arg.

### 3. Determine the goal of the change

One sentence on what the PR is meant to achieve — from the PR description (`gh pr view <nr> --json body`) and the commit messages. Only the goal, no assessment and no guesses about weaknesses (the reviewers should be unbiased).

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

In the chat only: verdict, blast radius summary, CI status, the summary table from `rows` (with consensus and status), consensus counters per type, path of the review file. Name dissent rows explicitly — they need the user's decision. No full texts.

### 8. PR comments (optional)

`AskUserQuestion`:
- **"Yes — I'll pick findings"** — the user then names the numbers; only then post
- **"No — the file is enough"** — the skill ends

Post after the selection, as one coherent review:

```bash
gh api repos/<owner>/<repo>/pulls/<nr>/reviews -X POST --input <json>
```

with `commit_id` = `HEAD_SHA`, `event: "COMMENT"`, `body` = summary table (selected findings only) and `comments[]` with `path`, `line`, `side: "RIGHT"` and `body` per finding that has a diff position. Findings without a diff position go into `body` only. Show the JSON to the user before posting.

## Rules

- Never change code in the main repository, never commit or push.
- Never publish anything on GitHub without the user's explicit selection.
- Orchestration lives in the workflow — do not start reviewers via the Agent tool alongside it, and do not pass a different model.
- Only note how far the branch is behind the base; do not rebase.
- Always clean up the worktree, also after failures — but ask first if a resume may be needed.
