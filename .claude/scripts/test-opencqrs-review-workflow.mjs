#!/usr/bin/env node
// Harness test for .claude/workflows/opencqrs-review.js.
//
// The workflow runtime provides agent()/parallel()/pipeline()/log()/phase() and the `args` global;
// this file provides minimal stand-ins so the orchestration logic runs without any model call.
//
// Usage: node .claude/scripts/test-opencqrs-review-workflow.mjs

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(here, '../workflows/opencqrs-review.js'), 'utf8').replace(/^export const meta/m, 'const meta')
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor
const workflow = new AsyncFunction('args', 'agent', 'parallel', 'pipeline', 'log', 'phase', source)

const parallel = thunks => Promise.all(thunks.map(t => Promise.resolve().then(t).catch(() => null)))
const pipeline = (items, ...stages) => Promise.all(items.map(async (item, i) => {
  let value = item
  for (const stage of stages) {
    try {
      value = await stage(value, item, i)
    } catch {
      return null
    }
  }
  return value
}))
const noop = () => {}

const baseArgs = {
  repo: '/r/src', diffFiles: '/r/files.txt', diffByFileDir: '/r/by-file', fullDiff: '/r/full.diff',
  reportsDir: '/r/reports', reviewFile: '/r/review.md', headSha: 'def', baseRef: 'upstream/main',
  label: 'PR #1 — x', title: 'x', date: '2026-01-01', goal: 'do x', ciStatus: 'qa: pass',
  behindBase: 0, commits: 1, files: 2,
}

// Fake agents keyed by agentType / label. `fail` is a set of labels that return null.
function makeAgent({ fail = new Set() } = {}) {
  const calls = []
  const agent = async (prompt, opts) => {
    calls.push({ prompt, ...opts })
    if (fail.has(opts.label)) return null
    const out = (prompt.match(/^OUTPUT=(.*)$/m) ?? [])[1]
    switch (opts.agentType) {
      case 'review-correctness':
      case 'review-design':
      case 'review-tests-docs':
      case 'review-impact':
        return { outputPath: out, errors: 1, warnings: 0, suggestions: 0 }
      case 'review-moderator':
        return { outputPath: out, confirmed: 1, singleConfirmed: 0, rejected: 0, dissent: 0 }
      default:
        if (opts.label === 'consolidate') return { reviewFile: baseArgs.reviewFile, verdict: 'blocked', blastRadius: 'wide', rows: [] }
        throw new Error(`unexpected agent ${opts.agentType} ${opts.label}`)
    }
  }
  return { agent, calls }
}

let failures = 0
async function test(name, fn) {
  try {
    await fn()
    console.log(`ok   ${name}`)
  } catch (e) {
    failures++
    console.log(`FAIL ${name}: ${e.message}`)
  }
}
const assert = (cond, msg) => { if (!cond) throw new Error(msg) }
const run = (args, agent) => workflow(args, agent, parallel, pipeline, noop, noop)

await test('missing args are rejected', async () => {
  const { agent } = makeAgent()
  let err
  try { await run({ ...baseArgs, ciStatus: '' }, agent) } catch (e) { err = e }
  assert(err && /ciStatus/.test(err.message), 'expected missing ciStatus error')
})

await test('full run: 12 reviewers, 4 moderators, 1 consolidation, all on sonnet', async () => {
  const { agent, calls } = makeAgent()
  const r = await run(baseArgs, agent)
  const count = t => calls.filter(c => c.agentType === t).length
  for (const t of ['review-correctness', 'review-design', 'review-tests-docs', 'review-impact']) assert(count(t) === 3, `${t}: ${count(t)} instead of 3`)
  assert(count('review-moderator') === 4, 'four moderators')
  assert(calls.length === 17, `17 agents expected, got ${calls.length}`)
  assert(calls.every(c => c.model === 'sonnet'), 'every agent runs on sonnet')
  const entries = calls.filter(c => c.agentType === 'review-design').map(c => c.label).sort().join()
  assert(entries === 'design:build,design:prod,design:test', `entry points ${entries}`)
  const mod = calls.find(c => c.label === 'moderate:impact').prompt
  assert(/impact-prod\.md[\s\S]*impact-test\.md[\s\S]*impact-build\.md/.test(mod), 'moderator gets all three reports')
  assert(r.failedTypes.length === 0, 'nothing should fail')
  const p = calls.find(c => c.label === 'consolidate').prompt
  assert(/## Blast radius/.test(p) && /## Dissent/.test(p), 'consolidation sections')
})

await test('one reviewer fails: moderator gets two reports', async () => {
  const { agent, calls } = makeAgent({ fail: new Set(['correctness:test']) })
  const r = await run(baseArgs, agent)
  const mod = calls.find(c => c.label === 'moderate:correctness').prompt
  assert(!/correctness-test\.md/.test(mod) && /correctness-prod\.md/.test(mod), 'failed report not passed')
  assert(r.failedTypes.length === 0, 'type still moderated')
})

await test('moderator fails: type in failedTypes and marked in the header', async () => {
  const { agent, calls } = makeAgent({ fail: new Set(['moderate:design']) })
  const r = await run(baseArgs, agent)
  assert(r.failedTypes.join() === 'design', `failedTypes ${r.failedTypes}`)
  assert(/no consensus for design/.test(calls.find(c => c.label === 'consolidate').prompt), 'header marks failure')
})

await test('CI status and known issues reach the consolidation', async () => {
  const { agent, calls } = makeAgent()
  await run({ ...baseArgs, ciStatus: 'qa: fail (spotlessCheck)', knownIssues: 'thread switch on the read side' }, agent)
  const p = calls.find(c => c.label === 'consolidate').prompt
  assert(/qa: fail \(spotlessCheck\)/.test(p), 'CI status listed')
  assert(/thread switch on the read side/.test(p), 'known issues listed')
  assert(calls.filter(c => c.agentType?.startsWith('review-') && c.agentType !== 'review-moderator')
    .every(c => /CI_STATUS=qa: fail/.test(c.prompt)), 'reviewers get the CI status')
})

if (failures) {
  console.log(`\n${failures} test(s) failed`)
  process.exit(1)
}
console.log('\nall tests passed')
