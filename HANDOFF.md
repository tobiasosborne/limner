# HANDOFF — limner

**Last updated:** 2026-09-16, end of session. Written by the orchestrating
Claude Fable 5.1 agent for the next agent.

Read `CLAUDE.md` in full first. Then read this file, then `bd ready`. The
authoritative state is `git log` plus beads. This file is a snapshot; verify
anything load-bearing against the repository.

## The goal, as the owner stated it on 2026-09-16

Get limner to "an ok state" for the outside people who took an interest
(issues #1 and #2), then stop. The owner is not funding the full
2026-09-15 plan. Token efficiency matters. Do not reopen the deferred
work below unless the owner asks.

## Where things stand

- **Phase 0 (honesty and scaffolding): done** except the second half of
  outreach (see "Pending items").
- **Phase 1 (Unicode and text foundation): done.** ADRs 0001–0003,
  `limner.unicode*`, `limner.ansi*`, `limner.text`. This fixed
  `visible-width` for issue #2.
- **Phase 2: partly done, rest deferred.** Done: ADR 0004 error policy
  (bead 2.1), ADR 0005 grapheme render buffer with a test-only VT emulator
  (2.2), zero reflection warnings (2.6). **Deferred by owner decision:**
  2.3 render loop, 2.4 `limner.tty`, 2.5 event concurrency. Each bead
  carries a note with the condition that would force it. The Phase 3 epic
  no longer depends on the Phase 2 epic.
- **Phase 3 core components: done.** borders, layout, `limner.table`
  (promoted), list, input, progress, statusbar and panel all measure and
  compose through `limner.text`; a grep gate in
  `test/limner/error_gates_test.clj` bans UTF-16 text operations in them.
  Every rendered line of every component is exactly the requested width;
  verified independently with CJK, a family ZWJ sequence, a pre-coloured
  span and an OSC 8 link on both runtimes. Not migrated and still
  experimental: `syntax`, `markdown`, `streaming`, `render`, `events`,
  `state`. The README "Status" section states this.
- **Release:** `CHANGELOG.md` has `[0.2.0] - 2026-09-16`; the tag `v0.2.0`
  is local until the owner pushes.

**Gates at handoff:** `bb check` green. JVM 375 tests / 6,118 assertions,
babashka 375 / 6,116, 0 failures, 0 errors. `clj-kondo` not installed, so
lint is reported as skipped. `bb bench-check` passes on both runtimes.

## Execution model used this session (owner directive 2026-09-16)

- **Heavy lifting: pi with `deepseek-flash`** at thinking `high`, via
  `reviews/2026-09-15/orchestration/run-pi.sh <brief>`. It runs one brief
  (several beads), commits once per bead after `bb check`, never pushes.
  R4 (three beads) took 23 minutes; R6 (eight beads) took 28 minutes.
- **Quality: one Codex `gpt-5.6-sol` xhigh review pass per run**, via
  `run-task.sh <brief>-review`, with authority to fix and commit. It found
  and fixed 2 high-severity defects after R4 and 3 after R6 (orphaned wide
  cells, progress bars 22–62 columns wide when 12 were requested, border
  recolouring erasing title styles). Do not skip this pass.
- **Orchestrator** verifies only: exit code, `bb check` counts, the two
  reports, and one independent REPL probe. No piecemeal diff reading.
- Runners must be launched with `bash run-*.sh` in the tool's background
  mode; a detached `nohup` launch from the harness was killed silently.
- Never create or edit repo files while a run is in progress: the worker
  stages its bead's changes and could sweep them in. Never write to beads
  during a run (Dolt lock contention).
- Briefs and reports: `orchestration/briefs/`, `orchestration/reports/`.
  `out/` is local and git-ignored via `.git/info/exclude`.

## Owner preferences (binding)

- **Public text.** The owner sees the exact text of every GitHub comment,
  issue or PR before it is posted (CLAUDE.md Rule 14). Post only with an
  explicit "post it" and verify the posted body against the approved text.
- **Design decisions are delegated:** "make the respectful, experienced
  senior engineer call", recorded in an ADR or bead.
- **Pushing** is the owner's call.
- **Shadows** (`limner-p4.1`) were "just awful" and were to be redesigned
  with visual analysis; this is now below the stopping line unless the
  owner asks.

## Pending items that need the owner

1. **Push** `main` and the tag `v0.2.0`.
2. **clj-wcwidth issue** (bead `limner-0-7`): pmonks asked for it on #2
   and the reply promised it. The exact text is in
   `reviews/2026-09-15/PLAN.md` under "Outreach status". Post only on
   approval, then close `limner-0-7`.
3. **Closing note on #2** after the push, saying the components are on the
   text layer as of `v0.2.0` and pointing at `examples/issue2_demo.clj`.
   Draft it, show it, post on approval. The owner decides whether to close
   the issue.
4. Optional: install `clj-kondo` so `bb check` lints.

## Known concerns and loose ends

- `limner-b1u`: layout, list, input, panel and progress exceed the Rule 11
  200-line aim; the bead states the exact split plan. Low priority.
- `reviews/2026-09-15/evidence/probe.clj` is frozen and calls the removed
  `render/parse-line-with-ansi`, so it no longer runs end to end. The
  probes live on as tests. Do not edit it; supersede it with a new dated
  review if one is ever written.
- glibc oracle headline numbers are dominated by policy on unassigned code
  points and version skew, not limner bugs. Say so if quoted publicly.
- babashka throughput on whole-document text operations is low (see
  `bench/BUDGETS.md`); fine for TUI-sized strings.
- Legacy: `limner.render`, `limner.events`, `limner.state` keep the REVIEW
  E1–E5 concurrency defects (deferred beads 2.3–2.5). `syntax`,
  `markdown`, `streaming` still use legacy text handling (open beads
  `limner-3-syntax`, `limner-3-markdown`, `limner-3-streaming`, P3).
