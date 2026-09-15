1. What changed

- Initialized beads with prefix `limner`.
- [.beads/](/home/tobiasosborne/Projects/limner/.beads): added local-database ignores, factual usage notes, configuration/metadata, and the exported 38-issue JSONL.
- [.gitignore](/home/tobiasosborne/Projects/limner/.gitignore): added beads/Dolt exclusions; verified `issues.jsonl` remains trackable.
- [.githooks/pre-commit](/home/tobiasosborne/Projects/limner/.githooks/pre-commit): exports and stages `issues.jsonl`.
- [.githooks/post-merge](/home/tobiasosborne/Projects/limner/.githooks/post-merge): imports incoming issue state.
- [scripts/setup-device.sh](/home/tobiasosborne/Projects/limner/scripts/setup-device.sh): configures hooks, runs `bd bootstrap --yes`, and removes automatically inferred Dolt remotes.
- [AGENTS.md](/home/tobiasosborne/Projects/limner/AGENTS.md): points to `CLAUDE.md` and repeats the three required safety rules.
- [CLAUDE.md](/home/tobiasosborne/Projects/limner/CLAUDE.md): documents hooks, bootstrap, JSONL synchronization, and local-only tracker behavior.
- [PLAN.md](/home/tobiasosborne/Projects/limner/reviews/2026-09-15/PLAN.md): replaced the phase details with pointers to `bd list` and `bd ready`; retained decisions, execution policy, and outreach drafts.
- Configured this checkout’s `core.hooksPath=.githooks`. Nothing is staged or committed.

2. Red-first tests

- A temporary workflow test first failed because `.githooks/pre-commit` did not exist.
- After discovering beads’ inferred remote, the expanded test failed because setup did not yet remove that remote.
- Final temporary-clone test passed: bootstrap imported 38 issues, a throwaway issue was created, pre-commit exported 39 issues, and the throwaway commit contained `.beads/issues.jsonl`.

3. Verification

- JVM, `clojure -M:test`: 243 tests, 1,469 assertions, 0 failures, 0 errors.
- Babashka scratch runner across all 15 test namespaces: 243 tests, 1,469 assertions, 0 failures, 0 errors. `bb test` does not exist yet; that is the next bead.
- Passed: shell syntax checks, post-merge import, `git diff --check`, JSONL validation, ignore/track checks, dependency-cycle check, and trailing-whitespace check.
- `bd doctor` exited successfully but reports that doctor is not yet supported for embedded mode.

4. Beads

- Created 38 issues: 5 epics and 33 tasks.
- Claimed and closed `limner-0-1`.
- Phase issues: `limner-0-*`, `limner-1-*`, `limner-2-*`, eleven named `limner-3-*` component issues, and `limner-4-1`.
- Only `limner-0-6` and `limner-0-7` carry `needs-owner`.
- Counts: 1 closed, 5 epic containers in progress, 32 open.
- `bd ready` now returns exactly:

```text
limner-0-4  P0  0.4 Add local quality gates
```

- No additional issues were created.

5. Decisions and deviations

- Beads 1.0 forbids using epics to block tasks. The executable tasks therefore form the serial dependency chain directly; epics are in-progress containers with their own phase chain.
- `bd init` inferred a GitHub-backed Dolt remote and attempted an automatic push during the first issue creation. DNS resolution failed before connection or authentication, so nothing was transmitted. I immediately removed the remote; `bd dolt remote list` now reports none, and setup removes any inferred remote on fresh devices.
- No library code changed and nothing remains open for T01.