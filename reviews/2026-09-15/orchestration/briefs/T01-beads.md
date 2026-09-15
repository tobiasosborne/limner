## T01 — PLAN 0.1: set up beads, file the plan as issues, add AGENTS.md

beads is not initialised in this repo yet, so there are no issues to claim for this task.

### 1. Initialise beads

Model the setup on the owner's `scientist-workbench` repo. Read these first:

- `/home/tobiasosborne/Projects/scientist-workbench/CLAUDE.md`, Rule 9
- `/home/tobiasosborne/Projects/scientist-workbench/.githooks/`
- `/home/tobiasosborne/Projects/scientist-workbench/scripts/setup-device.sh`, if it exists
- `/home/tobiasosborne/Projects/PadeTaylor.jl/CLAUDE.md`, "Session close" (the lesson about exporting `.beads/issues.jsonl`)

Then do the following:

- Run `bd init` in the limner repo. Use a short issue prefix, e.g. `limner`, following `bd init --help`.
- Add tracked git hooks under `.githooks/`:
  - **pre-commit:** exports `.beads/issues.jsonl` and stages it.
  - **post-merge:** imports.
  - Only add the other hooks scientist-workbench has if they are relevant here.
- Add `scripts/setup-device.sh`. It sets `core.hooksPath` to `.githooks` and runs `bd bootstrap --yes`. It must never run `bd init --force`.
- Run the setup script yourself.
- Make sure `.gitignore` keeps the local bd database out of git and tracks `.beads/issues.jsonl`.
- Check what `bd init` added to CLAUDE.md or AGENTS.md. It may inject a default "BEADS INTEGRATION" block. That block forbids TaskCreate and MEMORY.md, and says the agent must push, which conflicts with CLAUDE.md's rule that pushing is the owner's call. Do not let it silently override CLAUDE.md:
  - Remove any injected block.
  - Make sure CLAUDE.md's own Rule 12 and "Session close" section accurately describe the setup you created, including the hooks and the setup script.

### 2. Add AGENTS.md

Codex and other harnesses read `AGENTS.md`. Write a short one that:

- tells the reader `CLAUDE.md` is the authoritative instruction file and must be read in full;
- repeats the three most safety-critical rules in one line each: never post to GitHub without the owner's approval of the exact text, never push, and run both runtimes;
- does not duplicate the rest of CLAUDE.md.

### 3. File the plan as beads issues

Turn `reviews/2026-09-15/PLAN.md` into issues:

- **Epics:** one epic per phase (Phase 0 to Phase 4).
- **Plan items:** one issue per numbered plan item (0.1–0.7, 1.1–1.8, 2.1–2.6).
- **Phase 3:** one issue per component bullet (borders, layout, table, list, input, progress, statusbar, panel, syntax, markdown, streaming).
- **Phase 4:** one issue.

Each issue body follows scientist-workbench's shape: **Background / Fix / Acceptance**.

- **Background** cites the REVIEW finding IDs and PLAN item.
- **Fix** summarises the plan text.
- **Acceptance** is concrete and checkable.

Set priorities and dependencies so that `bd ready` returns items in the intended serial order:

- Phase 0 is ready first.
- Within Phase 0, the order is 0.4 gates → 0.2 README → 0.3 archive → 0.5 test hygiene → 0.6 changelog → 0.7 outreach.
- Phase 1 items depend on Phase 0 being done.
- Inside Phase 1, the order is 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6 → 1.7 → 1.8.
- The remaining phases follow the same pattern.

Mark 0.1 (this task) closed once done.

Mark two issues as needing the owner:

- **0.6:** the tag is created and pushed by the owner.
- **0.7:** posting requires owner approval of the exact text.

Use a label or a note in the body, whichever bd supports.

Replace the "Phases" section of PLAN.md with a pointer to beads, e.g. "Tracked in beads: `bd list`". Keep the decisions, the execution policy and the draft outreach sections.

### 4. Verify and report

- Show `bd ready` output and `bd list` counts in your report.
- Run `bd export -o .beads/issues.jsonl`. Stage nothing yourself; leave the changes uncommitted.
- The pre-commit hook must work when the orchestrator commits. Test it with a throwaway commit in a temporary clone (for example `git clone . /tmp/...`), not in this repo.
- Show that the test suites still pass on both runtimes. You don't need to change code for this task, but run them anyway.
