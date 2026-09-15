# Worker brief: limner

You are one worker in a serial sequence of tasks on the limner repository
(`/home/tobiasosborne/Projects/limner`, a Clojure + babashka terminal UI
library). An orchestrator gives you one run of one or more beads, and reviews the
commits of your run afterwards. No other agent is working in the repo while you
run.

## Before you do anything

1. Read `CLAUDE.md` in full. Its Laws and Rules are binding for you.
2. Read `reviews/2026-09-15/REVIEW.md` and `reviews/2026-09-15/PLAN.md`.
   Finding IDs (A1, B3, F2, ...) and plan items (0.4, 1.6, ...) refer to
   these files. Decisions D1–D7 in PLAN.md are **accepted as recommended**.
3. Run `git log --oneline -15` and `git status` to see what previous tasks
   did. Verify their state against the code rather than trusting any
   document.
4. If beads is initialised (`.beads/` exists), run `bd ready` and
   `bd show <id>` for the issue(s) named in your task. Claim them with
   `bd update <id> --claim`.

## Hard constraints

- **Stay in scope.** Do only the beads in your run. If you find other problems, file a
  bead with `bd create` (or list them in your report if beads is not set up
  yet); do not fix them.
- **Never post anything to GitHub.** No issues, comments, PRs, or releases,
  on any repo. Never `git push`, never create or push tags, never rewrite
  history.
- **Commit locally, once per bead.** This run covers several beads, done in the
  order given. When a bead is complete, `bb check` passes, and its bead is
  closed, commit exactly that bead's changes (including `.beads/issues.jsonl`,
  which the pre-commit hook exports). Use an imperative subject line, a body
  that says what changed and why and cites the reference or ADR used, and
  these trailers:
  `Bead: <id>` and `Implemented-by: Codex gpt-5.6-sol (xhigh), run <run-id>`.
  Write the commit message to a temporary file and use `git commit -F <file>`
  so newlines and trailers are real (run R3 embedded literal `\n`).
  Never push, never tag, never amend or rebase earlier commits. If a bead
  cannot be finished well, commit nothing for it, stop the run there, and
  explain in your report; later beads depend on earlier ones.
- **Red-green TDD** (CLAUDE.md Rule 4). For every behaviour change, write
  the failing test first, run it, and see it fail for the right reason.
  Then implement. Say in your report which tests you saw fail first.
- **Both runtimes** (Rule 6). Run the full suite on the JVM
  (`clojure -M:test`) and on babashka (`bb test` if the task exists; before
  that, a scratch runner that calls `clojure.test/run-tests` on every test
  namespace). Report the real counts from both.
- **Machine limits.** This is WSL, which has frozen from runaway processes.
  Wrap long commands in `timeout`, never run test suites in parallel, and
  never leave background processes running.
- **Docs in lockstep** (Law 2) and **no marketing prose** (Rule 10).
- **Don't guess.** If the task needs a decision that PLAN.md does not
  settle, pick the most conservative option consistent with CLAUDE.md,
  implement it, and flag it clearly in your report. If it cannot be done
  well, stop and explain why.

## Final report (your last message)

Plain prose and short lists, no fluff:

1. Per bead: its commit SHA, and what changed, file by file.
2. The tests you wrote first and saw fail (name and failure reason).
3. Suite results on the JVM and on babashka, with real counts
   (tests/assertions/failures/errors), plus any other gate you ran.
4. Beads issues claimed, closed and created (with IDs).
5. Deviations from the brief, decisions you made, and anything left open.

---

# Your run
