Review completed. No defects remain open.

### Defects fixed

- High — render buffers could store and emit orphaned wide cells through `set-cell`; edge writes and zero-width out-of-bounds writes also violated buffer invariants.
- High — invalid render content could be silently accepted when a frame was not due, while `force-render` cleared the terminal before validation.
- Medium — `find-dirty-regions` sorted rows/cells and was not linear.
- Medium — malformed structured colours leaked lower-layer errors, incomplete custom borders were accepted, and unknown spinner styles created unusable components.
- Low — wide continuations were double-counted by `buffer-stats`.
- Low — README, API documentation, changelog, and reflection guidance contradicted the implementation.

### Per bead

`limner-2-1`

- `d890730` — fail-loud validation:
  - `src/limner/core.clj`: validate structured colour maps.
  - `src/limner/borders.clj`: validate complete six-string custom styles.
  - `src/limner/render.clj`: validate line collections before timing skips or terminal output.
  - `test/limner/error_policy_test.clj`: exact error-class and data assertions.
  - ADR 0004, CHANGELOG, and active ANSI/borders/core/events/layout/render/text/unicode API docs updated.
- `351b21d` — spinner validation:
  - `src/limner/components/progress.clj`: reject unknown styles and invalid custom frames.
  - Progress and error-policy tests added.
  - ADR 0004, components API, and CHANGELOG updated.

`limner-2-2`

- `1b2a5f3` — render-buffer invariants and linear diffs:
  - `src/limner/render.clj`: validate cells, preserve wide continuations through `set-cell`, handle both edges safely, make zero-width out-of-bounds writes no-ops, and replace sorting with a linear row-major region scan.
  - `test/limner/render_test.clj`: exact invariant, edge, and no-sorting tests.
  - ADRs 0004/0005, render API, and CHANGELOG updated.
- `dfbe8e1` — documentation:
  - `README.md`: corrected the obsolete UTF-16-buffer claim and publication wording.
  - `docs/api/text.md`: documented `limner.text/clusters`.
- `f2c471d` — statistics:
  - `src/limner/render.clj`: exclude continuation markers from non-empty counts.
  - Exact regression test and CHANGELOG entry added.

`limner-2-6`

- `fe688c1`:
  - `CLAUDE.md`: corrected the reflection-warning guidance from 18 to zero.
  - Tracker export updated.

Every commit includes `.beads/issues.jsonl` and the required trailers.

### Red-green tests observed

- `core-color-rejects-malformed-structured-specs`: received `:limner.text.style/invalid-color`, not the promised core error.
- `borders-reject-malformed-custom-styles`: invalid styles were accepted.
- `render-rejects-non-strings`: `nil` lines were accepted.
- `render-validates-before-skipping-or-clearing-a-frame`: idle rendering accepted invalid content; forced rendering emitted a clear sequence first.
- `test-cell-requires-one-visible-grapheme`: empty, multi-grapheme, ANSI, and zero-width cells were accepted.
- `test-set-cell-maintains-wide-cell-invariants`: wide continuations were absent or orphaned.
- `test-wide-cluster-crossing-left-edge-clears-visible-half`: the visible half was not cleared.
- `test-zero-width-write-outside-buffer-is-a-no-op`: writes threw or corrupted zero-width buffers.
- `test-find-dirty-regions`: the no-sorting trap caught `sort-by`.
- `test-spinner-rejects-unknown-or-empty-styles`: all invalid variants were accepted.
- `test-buffer-stats`: one emoji counted as two non-empty cells.

### Final gates

- JVM: 317 tests, 5,890 assertions, 0 failures, 0 errors.
- Babashka: 317 tests, 5,888 assertions, 0 failures, 0 errors.
- Reflection: zero JVM warnings.
- `bb bench-check`: passed on both runtimes.
- `git diff --check`: passed.
- `clj-kondo`: skipped because it is not installed.
- JVM and babashka spot probes returned identical results.

### Beads and deviations

- Reopened, claimed, and closed: `limner-2-1`, `limner-2-2`, `limner-2-6`.
- `limner-2-6` required `--force` because its existing dependency on open bead `limner-2-5` remains.
- Created: none.
- Left open from this review: none.
- The frozen `reviews/2026-09-15/evidence/probe.clj` was untouched.
- The pre-existing executable-bit change to `reviews/2026-09-15/orchestration/run-task.sh` remains unstaged and untouched.
- Nothing was pushed, tagged, or posted to GitHub.