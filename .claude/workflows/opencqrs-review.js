export const meta = {
  name: 'opencqrs-review',
  description: 'OpenCQRS ensemble review (Sonnet): 3 independent reviewers per type (correctness, design, tests & docs, impact), one moderator per type, consolidated report',
  whenToUse: 'Invoked by /review-pr and /review-branch after review-prepare.sh has run. Not meant to be started by hand.',
  phases: [
    { title: 'Review', detail: '3 reviewers per type with different entry points (production code, tests, build/config/docs)', model: 'sonnet' },
    { title: 'Moderate', detail: 'one review-moderator per type as soon as its 3 reports exist', model: 'sonnet' },
    { title: 'Consolidate', detail: 'merge the consensus reports into the review file', model: 'sonnet' },
  ],
}

// Input contract (values from review-prepare.sh, passed through by the calling skill):
//   repo, diffFiles, diffByFileDir, fullDiff, reportsDir, reviewFile, headSha, baseRef, title, label, date
//   behindBase, commits, files, shortstat, author (optional, header only)
//   goal (one sentence: what the change is meant to do, from PR body / commits)
//   ciStatus (text: the PR's CI checks, or "not available")
//   knownIssues (string, optional): issues the team already knows about; matching findings are marked, not dropped
const a = args
const required = ['repo', 'diffFiles', 'diffByFileDir', 'fullDiff', 'reportsDir', 'reviewFile', 'headSha', 'baseRef', 'label', 'date', 'goal', 'ciStatus']
const missing = required.filter(k => a?.[k] === undefined || a[k] === null || a[k] === '')
if (missing.length) throw new Error(`opencqrs-review: missing args ${missing.join(', ')}`)

// Every agent runs on Sonnet. The agent definitions say so as well; this keeps the workflow explicit.
const MODEL = 'sonnet'

const TYPES = [
  { key: 'correctness', name: 'Correctness', prefix: 'C' },
  { key: 'design', name: 'Design', prefix: 'D' },
  { key: 'tests-docs', name: 'Tests & Docs', prefix: 'T' },
  { key: 'impact', name: 'Impact', prefix: 'I' },
]

// Same task for all three, only the entry point differs (diverse exploration path, not diverse task).
const ENTRIES = [
  { key: 'prod', hint: 'Start your analysis with the production code files.' },
  { key: 'test', hint: 'Start your analysis with the test files.' },
  { key: 'build', hint: 'Start your analysis with build files, configuration, autoconfiguration registration and docs.' },
]

const SEVERITY = { type: 'string', enum: ['Error', 'Warning', 'Suggestion'] }

const REPORT_SCHEMA = {
  type: 'object',
  required: ['outputPath', 'errors', 'warnings', 'suggestions'],
  properties: {
    outputPath: { type: 'string', description: 'Path of the report written with Write' },
    errors: { type: 'integer' },
    warnings: { type: 'integer' },
    suggestions: { type: 'integer' },
  },
}

const CONSENSUS_SCHEMA = {
  type: 'object',
  required: ['outputPath', 'confirmed', 'singleConfirmed', 'rejected', 'dissent'],
  properties: {
    outputPath: { type: 'string' },
    confirmed: { type: 'integer' },
    singleConfirmed: { type: 'integer' },
    rejected: { type: 'integer' },
    dissent: { type: 'integer' },
  },
}

const SUMMARY_SCHEMA = {
  type: 'object',
  required: ['reviewFile', 'verdict', 'rows'],
  properties: {
    reviewFile: { type: 'string' },
    verdict: { type: 'string', enum: ['ready to merge', 'changes requested', 'blocked'] },
    blastRadius: { type: 'string', description: 'The one or two sentence blast radius summary as written' },
    rows: {
      type: 'array',
      items: {
        type: 'object',
        required: ['number', 'severity', 'location', 'title', 'sources', 'consensus', 'status'],
        properties: {
          number: { type: 'integer' },
          severity: SEVERITY,
          location: { type: 'string' },
          title: { type: 'string' },
          sources: { type: 'string', description: 'Consensus ids, e.g. "C1, I2"' },
          consensus: { type: 'string', description: 'e.g. "3/3", "2/3", "single", "dissent"' },
          status: { type: 'string', enum: ['open', 'known', 'dissent'] },
        },
      },
    },
  },
}

const inputs = `REPO=${a.repo}
BASE_REF=${a.baseRef}
DIFF_FILES=${a.diffFiles}
DIFF_BY_FILE_DIR=${a.diffByFileDir}
FULL_DIFF=${a.fullDiff}
CI_STATUS=${a.ciStatus}`

const context = `CONTEXT=${a.label}. Goal of the change: ${a.goal}`

const reviewerPrompt = (type, entry) => `${inputs}
ENTRY=${entry.hint}
OUTPUT=${a.reportsDir}/${type.key}-${entry.key}.md
${context}

Review the whole diff from your perspective; the entry point only sets where you start. Do not run Gradle.
Write the complete report with Write to OUTPUT. Return only the structured confirmation (path and counters), never the report text.`

// Pipeline over types: three reviewers per type, then that type's moderator — no barrier between types.
const ensembles = pipeline(
  TYPES,
  type => parallel(ENTRIES.map(entry => () =>
    agent(reviewerPrompt(type, entry), {
      agentType: `review-${type.key}`,
      model: MODEL,
      phase: 'Review',
      label: `${type.key}:${entry.key}`,
      schema: REPORT_SCHEMA,
    }))),
  async (reports, type) => {
    // parallel() keeps input order and maps a failed agent to null; the file names are ours, not the agent's.
    const written = ENTRIES.map(e => `${a.reportsDir}/${type.key}-${e.key}.md`).filter((_, i) => Boolean(reports?.[i]))
    if (written.length < ENTRIES.length) log(`${type.key}: only ${written.length}/${ENTRIES.length} reviewer reports -- moderator documents the gap`)
    if (!written.length) return null
    return agent(`TYPE=${type.name} (finding prefix ${type.prefix})
REPORTS=
${written.map(p => `- ${p}`).join('\n')}
REPO=${a.repo}
BASE_REF=${a.baseRef}
OUTPUT=${a.reportsDir}/${type.key}-consensus.md

Consolidate the reports into the consensus report. Return only the structured confirmation, never the report text.`, {
      agentType: 'review-moderator',
      model: MODEL,
      phase: 'Moderate',
      label: `moderate:${type.key}`,
      schema: CONSENSUS_SCHEMA,
    }).then(c => (c ? { key: type.key, name: type.name, reviewers: written.length, ...c } : null))
  },
)

// The consolidation needs every consensus report: the one justified barrier.
const done = ((await ensembles) ?? []).filter(Boolean)
const failedTypes = TYPES.filter(t => !done.some(d => d.key === t.key)).map(t => t.key)
if (failedTypes.length) log(`no consensus for: ${failedTypes.join(', ')} -- the review file marks these sections as failed`)

phase('Consolidate')
const sections = TYPES.map(t => {
  const d = done.find(x => x.key === t.key)
  return `- ${t.name}: ${d ? d.outputPath : 'MISSING (no consensus)'}`
})

const summary = await agent(`Consolidate an OpenCQRS ensemble review into ${a.reviewFile}. Report only, never change code.

Consensus reports (read them yourself with Read; each already carries the consensus class per finding):
${sections.join('\n')}
CI status: ${a.ciStatus}
${a.knownIssues ? `\nKnown issues (the team already knows these; a matching finding gets status "known", stays in the table, but does not count for the verdict):\n${a.knownIssues}\n` : ''}
Write the file with this structure (English prose):

# Code review: ${a.label}

${a.author ? `**Author:** ${a.author} · ` : ''}**Head:** \`${a.headSha}\` · **Base:** \`${a.baseRef}\` · **Date:** ${a.date}
**Scope:** ${a.commits ?? '?'} commits · ${a.files ?? '?'} files${a.shortstat ? ` · ${a.shortstat}` : ''}
**Reviewer:** Claude Code (opencqrs-review, Sonnet: 3 reviewers per type for correctness, design, tests & docs, impact; one moderator per type)
**CI:** <one line from the CI status>
${Number(a.behindBase) > 0 ? `\n> **Note:** The reviewed state was ${a.behindBase} commit(s) behind the base (not rebased).\n` : ''}${failedTypes.length ? `\n> **Incomplete:** no consensus for ${failedTypes.join(', ')}.\n` : ''}
**Verdict:** ready to merge | changes requested | blocked  (blocked as soon as an open Error exists; "known" and "dissent" rows do not block)

## Blast radius
The impact map from the Impact consensus report as a table (dimension, touched, effect, who is affected), followed by its one or two sentence summary.

## Summary
| # | Severity | Location | Title | Source | Consensus | Status |
One row per consolidated finding, numbered, sorted Error -> Warning -> Suggestion.

## Findings
One "### <#> [Severity] <Title>" section per row: Location, Consensus, Evidence, Scenario, Recommendation (with code sketches from the reports), Moderator note, Source.

## Dissent
Every row with status "dissent": both positions with their evidence. The reader decides.

## Rejected
Merged from the consensus reports' "Rejected" sections, one line each.

## Checked without findings
Merged from the consensus reports.

Rules:
- Dedupe across types: findings of different types about the same location and the same cause become one row; list all ids in Source and the strongest consensus.
- Do not invent findings and do not drop dissent.

Return the structured summary: file path, verdict, blast radius summary and the table rows exactly as written.`, {
  model: MODEL,
  phase: 'Consolidate',
  label: 'consolidate',
  schema: SUMMARY_SCHEMA,
})

return {
  reviewFile: a.reviewFile,
  verdict: summary?.verdict ?? null,
  blastRadius: summary?.blastRadius ?? null,
  rows: summary?.rows ?? [],
  consensus: done.map(({ key, reviewers, confirmed, singleConfirmed, rejected, dissent }) => ({ type: key, reviewers, confirmed, singleConfirmed, rejected, dissent })),
  failedTypes,
}
