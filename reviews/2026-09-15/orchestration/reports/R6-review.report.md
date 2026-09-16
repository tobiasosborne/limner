Review complete. Seven defects were found, fixed, committed, and closed. Nothing was pushed or posted to GitHub; the worktree is clean.

## Defects and commits

- High — border recolouring erased title/content styles and OSC 8 links; wide custom borders and shadows broke geometry. Commit `8797486`.
  - `src/limner/borders.clj`: style-preserving structural recolouring and custom-glyph validation.
  - `src/limner/borders/effects.clj`: column-based shadow fills.
  - Tests, borders API docs, ADR 0004, CHANGELOG, and bead export updated.

- High — list/input labels and narrow input statuses exceeded configured widths. Commit `c219206`.
  - `src/limner/components/list.clj`: exact-width titles, measured custom prefixes, width validation.
  - `src/limner/components/input.clj`: exact-width labels/statuses and component-specific width errors.
  - Tests, component docs, ADR 0004, CHANGELOG, and bead export updated.

- High — CJK, ZWJ, and OSC 8 progress fills rendered 22–62 columns when 12 were requested. Commit `4333921`.
  - `src/limner/components/progress.clj`: fill patterns repeated and sliced through `limner.text`; width/fill validation.
  - Exact four-class goldens, docs, ADR 0004, CHANGELOG, and bead export updated.

- Medium — deprecated `limner/table` silently changed from a newline-terminated string to a vector and changed alignment. Commit `2ce6bb4`.
  - `src/limner.clj`: restored right-aligned, newline-terminated compatibility output while delegating to `limner.table`.
  - Compatibility tests, table docs, CHANGELOG, and bead export updated.

- Medium — the text-operation gate allowed `count`/`nth` across whole namespaces, and properties lacked explicit zero-width clusters. Commit `6bdc7dd`.
  - `test/limner/error_gates_test.clj`: every exception now identifies an exact var and operation.
  - `test/limner/component_support.clj`: explicit wide and zero-width generators.
  - `test/limner/component_support_test.clj`: verifies generator width classes.
  - Bead export updated.

- Medium — invalid statusbar widths leaked lower-level exceptions or `ClassCastException`. Commit `01ce765`.
  - `src/limner/components/statusbar.clj`: namespaced width validation.
  - `src/limner/components/panel.clj`: corrected the width-four documentation.
  - Tests, component docs, ADR 0004, CHANGELOG, and bead export updated.

- Medium — multi-grapheme insertion could exceed input `:max-length`. Commit `a9755c9`.
  - `src/limner/components/input.clj`: atomic resulting-length enforcement.
  - Tests, component docs, CHANGELOG, and bead export updated.

## Red-green evidence

Tests observed failing before implementation:

- `colorize-border-preserves-existing-styles-and-controls`: blue/green styles and OSC 8 links were erased.
- `custom-border-and-shadow-glyphs-obey-column-geometry`: invalid custom styles were accepted and wide shadows distorted widths.
- `list-label-cannot-widen-the-configured-width`: requested 12 rendered as 21.
- `fixed-width-label-and-status-goldens`: requested widths 12 and 5 rendered as 21 and 7.
- `progress-fill-pattern-goldens`: requested width 12 rendered as 22–62.
- `deprecated-table-preserves-its-string-contract`: returned a left-aligned vector instead of the historical string.
- `component-text-allow-list-is-var-scoped`: ten namespace-wide exceptions failed.
- `fragment-generators-have-their-stated-width-classes`: correctly caught that 🅰 is width 1, not a wide fragment.
- `statusbar-width-validation`: returned text-layer errors or `ClassCastException`.
- `max-length-applies-to-the-resulting-grapheme-count`: insertion crossed the configured limit.

## Verification

Final `bb check`:

- JVM: 375 tests, 6,118 assertions, 0 failures, 0 errors.
- Babashka: 375 tests, 6,116 assertions, 0 failures, 0 errors.
- `clj-kondo`: skipped because it is not installed.
- ICU4J oracle: skipped by the normal gate as configured.

Additional gates:

- `bb bench-check` passed:
  - JVM: ASCII slowdown 1.143, CJK speedup 7.272, mixed slowdown 1.091.
  - Babashka: ASCII slowdown 0.687, CJK speedup 35.942, mixed slowdown 1.177.
- `git diff --check e8334b4..HEAD`: clean.
- Original F1–F9 component probes and the CJK/ZWJ/styled/OSC 8 matrix passed manually on both runtimes.
- Coloured-title probe retained blue title spans and red border spans.

## Beads

Created, claimed, and closed:

- `limner-r6r-borders`
- `limner-r6r-widths`
- `limner-r6r-progress`
- `limner-r6r-table`
- `limner-r6r-gates`
- `limner-r6r-statusbar`
- `limner-r6r-input-max`

No review defect was left open and no original R6 bead required reopening. The pre-existing `limner-b1u` namespace-splitting bead remains open and untouched.

No scope deviations: experimental namespaces were not changed, and there was no push, tag, history rewrite, or GitHub activity.