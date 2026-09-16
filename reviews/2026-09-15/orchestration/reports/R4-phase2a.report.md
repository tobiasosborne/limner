All three beads are committed and the final `bb check` is green. Report follows.

## 1. Per bead

**limner-2-1 — `ec544f7` "Adopt the fail-loud error policy in ADR 0004"**
- `docs/adr/0004-error-policy.md` (new): the policy, the full `:limner/error` class table, the single render-loop `:on-error` boundary, capability-vs-error rule, open-keyword-arguments decision.
- `src/limner/core.clj`: `rgb`/`bg-rgb`, `color-256`/`bg-256`, `color` validate explicitly with `:limner/error`; the invalid-colour warning is gone.
- `src/limner/borders.clj`: unknown border styles and invalid `:title-pos` throw; placeholder-box/uncoloured-line/bare-input catches removed; `:pre` integer checks replaced.
- `src/limner/layout.clj`: `fixed`/`percent`/`flex`, `resolve-constraint`, layout functions and `clip`/`pad-to-box` throw instead of returning `0`/`[]`; `percent` no longer clamps; unknown layout type throws.
- `src/limner/render.clj`: buffer/render-output validation throws; loop keeps last good frame and reports via `:on-error` without printing; resize log and stop warning removed; `TODO(limner-2-3)`.
- `src/limner/events.clj`: `dispatch-key` propagates handler errors; async processor routes to callbacks/stats without printing; `TODO(limner-2-5)`.
- Foundation: `ansi.clj`, `ansi/sgr.clj`, `text.clj`, `text/columns.clj`, `text/style.clj`, `unicode.clj`, `unicode/grapheme.clj`, `unicode/width.clj`, `unicode/width/string.clj` now carry `:limner/error`.
- `test/limner/error_policy_test.clj` (new) and `test/limner/error_gates_test.clj` (new, the grep gates); legacy assertions in `core_test.clj`, `borders_test.clj`, `events_test.clj` updated.
- `CHANGELOG.md` breaking section.

**limner-2-2 — `a16a8de` "Make the render buffer grapheme- and style-aware"**
- `docs/adr/0005-render-buffer.md` (new).
- `src/limner/render.clj`: cell = `{:cluster :style :width}`; wide = lead + continuation; writes go through `limner.text/clusters`; edge rules clear half-wide cells; value-based diffs; per-row region grouping (quadratic `.indexOf` gone); `render-region` emits cursor + `limner.text/to-ansi`; `parse-line-with-ansi`/`extract-ansi-state` removed.
- `src/limner/text.clj`, `text/columns.clj`: public `clusters`; `src/limner/text/style.clj`: public `apply-sgr`.
- `test/limner/vt.clj` (new test-only VT) and `test/limner/render_test.clj` (rewritten: probes 6/6c, edge rules, golden grid tests, direct byte tests, `test.check` diff-vs-full-redraw and no-half-wide properties).
- `test/limner/baselines/reflection_warnings.edn`: removed the fixed render warning.
- `docs/api/render.md`, `CHANGELOG.md`.

**limner-2-6 — `a99fbd3` "Eliminate JVM reflection warnings"**
- `src/limner/components/markdown.clj` (hint text/match), `components/statusbar.clj` (`^java.time.LocalTime`), `events.clj` (`^Character`, `^java.util.List`), `render.clj` (`(long sleep-time)`).
- `test/limner/baselines/reflection_warnings.edn`: now `[]`; `test/limner/reflection_test.clj`: asserts the baseline is empty and fails on any warning.
- `CHANGELOG.md`.

**Extra doc commit — `ec4e8e2` "Document the remaining ADR 0004 error classes"**: adds the three classes the ADR table had missed (`:limner.events/invalid-argument`, `:limner.render/invalid-callback`, `:limner.render/invalid-dimension`). I could not fold this into `ec544f7` without amending, which is forbidden.

## 2. Tests written first and seen fail

- `limner.error-policy-test`: first run against the old code was **53 failures, 1 error**, plus uncaptured stderr: `Warning: Invalid color specification…`, `Warning: Invalid title-pos…`, `Warning: Unknown layout type…`. That is the red for 2.1.
- `limner.render-test`: probes 6/6c and B3 cases. Verified red against the pre-change code in a temporary worktree at `ec544f7`: probe 6 returned `2` cells for `"😀"` and probe 6c returned `[ESC[31m, ESC[31mESC[0m]`. The rewritten namespace then failed on its own first run with 3 failures/3 errors (VT `parse-cup` on empty params, an over-strict surrogate helper, a wrong clock threshold), which I fixed.

## 3. Suite results

Final `bb check` (serial, fail-fast):

- **JVM**: Ran 305 tests, 5838 assertions, 0 failures, 0 errors. ICU4J oracle skipped.
- **babashka**: Ran 305 tests, 5836 assertions, 0 failures, 0 errors. ICU4J oracle skipped.
- **clj-kondo**: skipped explicitly (not installed).
- `bb bench-check` passes: JVM `{:ascii-slowdown 0.849, :cjk-speedup 4.908, :mixed-slowdown 1.183}`; babashka `{:ascii-slowdown 0.719, :cjk-speedup 37.374, :mixed-slowdown 1.184}`. 2.6 changed no `limner.text`/`limner.unicode*` hot path, so these are unchanged from the previous run.

Grep-gate allow-lists (each carries a reason in `error_gates_test.clj`):

- Printing: `limner/live-display`, `limner/-main` — deprecated demo namespace writing its example to stdout. Everything else in `src/` is clean.
- `catch Exception`/`Throwable`: `render/create-render-loop`, `render/report-render-error!` (render-loop boundary, `limner-2-3`); `render/terminal-size-from-system`, `render/get-terminal-size` (terminal-size capability probe/default); `events/call-handler-with-timeout`, `events/create-async-event-processor` (async processor boundary, `limner-2-5`). `events/parse-integer` uses a narrow `catch NumberFormatException`, which the gate does not flag.

## 4. Beads

- Claimed and closed: `limner-2-1`, `limner-2-2`, `limner-2-6`.
- `limner-2-6` was closed with `--force` because its declared dependency `limner-2-5` is still open; the run brief places 2.6 in R4 and 2.5 in R5, and the 2.6 work does not touch event concurrency.
- Created: none.

## 5. Deviations, decisions, open items

- `layout/percent` now throws for values outside 0..100 instead of clamping; this is the conservative reading of "invalid input throws" and is documented as breaking.
- `find-dirty-regions` is linear for the row-major output of `find-dirty-cells` and O(n log n) for arbitrary input (one bucket pass plus a per-row sort). The quadratic `.indexOf` is gone; I chose correctness over a strict linear claim and said so in ADR 0005.
- Kept the reflection baseline file, now empty, with an explicit emptiness assertion rather than deleting the mechanism (justified in the commit body).
- Removed `parse-line-with-ansi`/`extract-ansi-state` from the experimental `limner.render`; recorded as breaking.
- `reviews/2026-09-15/evidence/probe.clj` still calls the removed `parse-line-with-ansi`, so re-running that frozen historical evidence script now errors. I did not edit it because the review marks it frozen; the probes live on as tests in `render_test.clj`.
- `CLAUDE.md`'s practical-guidance line still says loading printed 18 reflection warnings. It is now zero; I did not edit the authoritative instruction file without being asked.
- Nothing pushed, tagged, or posted. Working tree is clean; the extra doc commit is the only structural deviation from "one commit per bead".
