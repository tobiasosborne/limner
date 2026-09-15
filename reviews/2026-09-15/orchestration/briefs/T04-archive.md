## T04 — PLAN 0.3: archive the fiction and tidy the repo

Claim the beads issue for plan item 0.3. It addresses REVIEW findings A4, A5 and A6, and it covers the tutorial part of bead `limner-p0.1`, which empties the doc baselines. That bead was created by T02; check its exact ID with `bd list`.

### Steps

1. **Move the historical documents.**
   - Move `plans/` (three files) and `docs/tutorial.md` to `docs/archive/2025/` with `git mv`, so history is preserved.
   - Prepend a short banner to each archived file:
     > **Historical document (archived 2026-09-15).** Written in 2025 by earlier coding agents. It describes plans and APIs that were never built or have since changed, and it contains claims that are not true. It is kept for history only. See `README.md`, `CLAUDE.md` and `reviews/2026-09-15/REVIEW.md` for the current state.
   - Add `docs/archive/README.md`, a few lines saying what the archive is.

2. **Check `docs/api/*.md` and `docs/architecture.md`.**
   - The T02 doc-reference gate already scans them, and today they have no missing references. They can still over-claim or describe behaviour the code doesn't have.
   - Read each one against the code. Delete sentences that are false and correct the ones that are fixable in wording.
   - If a file is mostly fiction, archive it with the banner instead.
   - Don't write new documentation for new behaviour: Phase 4 does that.
   - List what you changed, per file, in your report.

3. **Delete** `benchmark_results.txt`. The benchmarks can regenerate it, and a stale results file misleads.

4. **Fix `src/limner.clj`.**
   - Replace the header comment "100% bulletproof, no more broken borders (2025)" with a namespace docstring saying the namespace is deprecated (PLAN D4).
   - The docstring should point to `limner.borders`, `limner.syntax`, and the future `limner.table` (tracked in beads).
   - This is a docstring and comment change only. No behaviour change; runtime deprecation warnings are out of scope.

5. **Deduplicate `.gitignore`.** Keep one clean, commented section per concern. Keep the beads/Dolt lines that T01 added, and everything that is actually needed for Clojure CLI, babashka, the editors in use and the OS. Remove Leiningen-only entries only if nothing in the repo uses Leiningen; check first.

6. **Update the baseline.** The tutorial's 20 entries in `test/limner/baselines/doc_references.edn` now disappear, because archive paths are excluded from the gate. Remove them from the baseline so the gate stays strict.
   - If `limner-p0.1` is then fully satisfied (the README entries were removed by T03, and the README-examples baseline is empty), close it.
   - Otherwise update the bead with what remains.

7. **Update references.** Check `README.md`, `CLAUDE.md` and `AGENTS.md` for references to the moved paths.
   - CLAUDE.md mentions `plans/` in its "What limner is, honestly" section and in Rule 12. Update the wording so it points to `docs/archive/2025/` and still says "never add planning docs; use beads".

### Verify

- `bb check` passes. Report the counts.
- `git status` shows renames, not delete-plus-add, for the moved files.
