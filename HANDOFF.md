# HANDOFF — limner

**Last updated:** 2026-09-15, end of session. Written by the orchestrating Claude
Opus 5 agent for the next agent.

Read `CLAUDE.md` in full first. Then read this file, then `bd ready`. The
authoritative state is `git log` plus beads. This file is a snapshot; verify
anything load-bearing against the repository.

## Where things stand

The 2026-09-15 audit (`reviews/2026-09-15/REVIEW.md`) found that limner
over-claimed badly and had three root-cause defect classes:

- text measured in UTF-16 units and styled by string concatenation;
- swallowed errors;
- documentation describing a different library.

The plan (`reviews/2026-09-15/PLAN.md`) has four phases. It is now tracked
in beads.

- **Phase 0 (honesty and scaffolding): done**, except outreach 0.7, which is
  intentionally deferred. The current state:
  - beads and git hooks are set up, and `AGENTS.md` exists;
  - local gates (`bb check`, `bb test`, doc-reference, README-example and
    reflection gates) are in place;
  - the README is honest, fictional docs are archived under
    `docs/archive/2025/`, and the test suite is deterministic and hermetic;
  - `CHANGELOG.md` exists, and a local tag `v0.1.0` marks the legacy baseline
    `01592b2`.
- **Phase 1 (Unicode and text foundation): done.** The epic `limner-p1` is
  closed.
  - UCD 17.0.0 is vendored with checksums in `reference/unicode/17.0.0/`,
    and specs are in `reference/specs/`.
  - Property tables are generated (`bb gen-unicode`, drift-tested) and
    exposed through `limner.unicode`.
  - Width semantics are in ADR 0001 (`limner.unicode.width`), including the
    amendment from bead `limner-p1.1`.
  - Portable UAX #29 segmentation is in ADR 0002
    (`limner.unicode.grapheme`). It passes 766/766 official cases on the JVM
    and babashka and agrees with ICU4J 78.1.
  - A single ECMA-48 tokenizer lives in `limner.ansi` and `limner.ansi.sgr`.
  - The styled-text layer is in ADR 0003 (`limner.text`, `limner.text.*`).
    `core/visible-width` and `core/color` now route through it, so nested
    styles compose. **This is the real fix for issue #2.**
  - Emoji 17 conformance covers all 5,225 entries.
  - Width oracles are reported in `reviews/width-oracles/2026-09-15.md`, with
    measured facts for #2 in `reviews/width-oracles/SUMMARY-for-issue-2.md`.
  - Performance budgets are in `bench/BUDGETS.md` and enforced by
    `bb bench-check`.
- **Phases 2–4: not started.** 20 beads remain open.

**Gates at handoff:** `bb check` is green. JVM and babashka each run 300
tests (5,813 and 5,812 assertions respectively), with 0 failures and
0 errors. `clj-kondo` is not installed, so it is skipped explicitly.

## Owner preferences (binding)

- **Public text.** The owner sees the exact text of every GitHub comment,
  issue or PR before it is posted (CLAUDE.md Rule 14). Agents never post.
- **Design decisions are delegated.** "Make the respectful, experienced
  senior engineer call." Record decisions in an ADR or bead, and mention them
  in progress updates.
- **Execution model.** Work runs serially, with no parallel subagents.
  Implementation is done by Codex `gpt-5.6-sol` at `xhigh` effort. Related
  beads are batched into one run; the worker commits locally once per bead
  after `bb check` passes and never pushes or tags. The orchestrator reviews
  each run's commit range: it re-runs `bb check`, reads the diff, and
  spot-probes behaviour.
- **Shadows.** The drop shadows "were just awful". Bead `limner-p4.1` is the
  final task: an **Opus** subagent does visual analysis from rendered images
  and a redesign. It is an owner-directed exception to the Codex policy. The
  owner looks at the after-images before it closes.
- **Pushing.** The owner pushes, or explicitly asks the agent to. The Claude
  Code permission classifier has blocked agent pushes to this repo before.

## How to continue the orchestration

Everything needed is in `reviews/2026-09-15/orchestration/`:

- `preamble.md`: the worker brief prepended to every run. It defines the
  hard constraints, per-bead commits and report format.
- `run-task.sh <id>`: runs `briefs/<id>.md` with
  `codex exec -m gpt-5.6-sol -c model_reasoning_effort="xhigh" -s workspace-write`.
  Prompts and logs go to `out/`, which is git-ignored. Run it in the
  background (it takes 20–90 minutes) and wait for completion.
- `briefs/`: every brief used so far (T01–T09, R1–R3) plus the **drafted,
  not yet run** `R4-phase2a.md` and `R5-phase2b.md`.
- `reports/`: each worker's final report and `timeline.log`.

**Remaining run plan.** Beads IDs: `bd list`.

| Run | Beads | Brief |
|---|---|---|
| R4 | 2.1 error policy, 2.2 grapheme render buffer + VT test emulator, 2.6 zero reflection warnings | drafted |
| R5 | 2.3 render loop (single writer, injectable effects), 2.4 `limner.tty` (raw mode, key reader, NO_COLOR etc.), 2.5 event concurrency | drafted |
| R6 | Phase 3: borders, layout, table, panel | to write |
| R7 | Phase 3: list, input, progress, statusbar | to write |
| R8 | Phase 3: syntax, markdown, streaming (experimental namespaces) | to write |
| R9 | Phase 4: documentation and release (4-1) | to write |
| R10 | `limner-p4.1` shadows, Opus with visual analysis | to write |

For Phase 3 briefs, each component bead's body already has
Background/Fix/Acceptance. The REVIEW F1–F9 probes in
`reviews/2026-09-15/evidence/probe.clj` are the first red tests, and every
component moves onto `limner.text`.

**ADR numbering.** ADR 0003 is the styled-text model, so the error policy (bead
`limner-2-1`) takes the next free number. The bead title was corrected at
handoff, and the R4 brief already says "next-numbered".

## Pending items that need the owner

1. **Outreach (bead `limner-0-7`, now unblocked).** Redraft the reply on #2
   from `reviews/width-oracles/SUMMARY-for-issue-2.md`. The draft in
   PLAN.md was written before the work and describes intentions; the reply
   should now report what was done, with numbers and commit links, and those
   links only work after the push. Also refresh the clj-wcwidth issue draft
   in PLAN.md. Show both texts to the owner and post nothing.
   - Context: pmonks posted a full glibc `wcwidth` table on #2; the owner
     replied 2026-09-15 promising a deeper look within days.
   - Issue #2 is open.
2. **Tag `v0.1.0`** (legacy baseline, `01592b2`) was pushed at handoff. README's Installation section still says "Once published"; reword it the next time the README changes.
3. **Optional:** install `clj-kondo` so `bb check` lints.

## Known concerns and loose ends

- **babashka throughput on large text.** Budgets pass for 80-column lines,
  but whole-document operations on babashka are slow. On the 10 KiB mixed
  corpus: `wrap` 4.2 calls/s, `truncate` 7.1/s, `width` 32/s (see
  `bench/BUDGETS.md`). Segmentation of supplementary-plane-heavy emoji text
  is still slower than `BreakIterator`. This is fine for TUI-sized strings;
  revisit if a component wraps large documents per frame.
- **Commit trailers in R3.** In commits `e7a92d5`, `a392d33` and `a489070`
  the worker put literal `\n` in the body, so `Bead:` and `Implemented-by:`
  are not real git trailers. Nobody amended them (history rewrite is
  forbidden). For future briefs, tell workers to write the message to a file
  and use `git commit -F`.
- **Oracle headline numbers.** glibc 2.39 agrees on only 287,130 of
  1,112,064 scalars. That is dominated by policy (unassigned code points:
  glibc -1 versus ADR 0001) and by version skew. It is not a limner bug, and
  every difference is classified. Say so if quoting the number publicly.
- **Legacy, not yet fixed.**
  - `limner.render`, `limner.events` and `limner.state` still carry the
    REVIEW E1–E5 defects (Phase 2).
  - The components still measure with legacy code paths in places and carry
    the F1–F9 defects (Phase 3).
  - The README "Status" section lists the user-facing limitations. Keep it
    truthful as phases land.
- **Orchestrator hygiene.**
  - Do not create files in the repo while a Codex run is in progress: the
    worker stages its bead's changes and could sweep them in.
  - Do not write to beads during a run, to avoid Dolt lock contention.
  - Epics block their children in bd 1.0. If `bd ready` returns nothing
    unexpectedly, check `bd blocked` for a stale epic-level dependency.
