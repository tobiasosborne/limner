All eight beads are implemented, gated, closed and committed. Here is the run report.

## 1. Per-bead commits and changes

**`limner-3-borders` — `7b483eb`**
- `src/limner/borders.clj`: measured/padded content and titles through `limner.text`; structural border colouring (position + known glyph set); tab expansion to 8-column stops; new docstring.
- `src/limner/borders/internal.clj` (new): shared line/dimension validation.
- `src/limner/borders/effects.clj` (new): shadows, indent, nest, side-by-side.
- `test/limner/borders_test.clj`, `test/limner/component_support.clj` (new), `test/limner/error_gates_test.clj` (new static gate), docs, CHANGELOG.

**`limner-3-layout` — `2c5abf0`**
- `src/limner/layout.clj`: largest-remainder flex distribution; actionable negative-space throw; `clip`/`pad-to-box` through `limner.text`.
- `test/limner/layout_test.clj`, docs, CHANGELOG, the gate scope/allow-list.

**`limner-3-table` — `b00d6b8`**
- `src/limner/table.clj` (new): column-correct bordered table, per-column `:align`, `:padding`, ragged-row `ex-info`.
- `src/limner.clj`: legacy `table` delegates and is deprecated.
- `docs/api/table.md` (new), `docs/adr/0004-error-policy.md`, tests, README namespace row, CHANGELOG.

**`limner-3-list` — `bf83baa`**
- `src/limner/components/list.clj`: total-width rendering via `limner.text`, `:limner.components.list/invalid-width`, scroll-following selection, filter resets scroll, new docstring.
- tests, docs, ADR 0004, CHANGELOG.

**`limner-3-input` — `5ddb529`**
- `src/limner/components/input.clj`: grapheme-index cursor, grapheme editing, total-width line contract, span-composed inverse cursor, total `history-prev`, corrected trailing-newline and placeholder rendering.
- tests, docs, CHANGELOG (breaking cursor-index note).

**`limner-3-progress` — `7724a73`**
- `src/limner/components/progress.clj`: numeric `complete?`, distinct `:square` frames, `limner.text`-composed fills and dim pulse.
- tests, docs, CHANGELOG.

**`limner-3-statusbar` — `f314939`**
- `src/limner/components/statusbar.clj`: exact-width single line, caller styles preserved, private colour table and ANSI stripper removed, hyperlinks preserved.
- tests, docs, CHANGELOG.

**`limner-3-panel` — `cea3a07`**
- `src/limner/components/panel.clj`: exact `:width`, `limner.text` fitting/padding, title cannot widen the border, `:limner.components.panel/invalid-width`.
- tests, docs, ADR 0004, CHANGELOG.

**Wrap-up commits:** `a63fd7a` (README Status), `982f170` (gate checks every banned use, mutation-proved), `1f24c55` (`limner-b1u` filed).

## 2. Tests written first and seen fail

- **borders:** `draw-titled-box-cjk-golden` (18-wide title line over 11-wide content), `draw-titled-box-styled-golden` (9 vs 18), `tabs-expand-to-eight-column-stops` (tab width 0), `colorize-border-is-structural`, `every-drawn-line-has-one-width` — 5 failures against old code.
- **layout:** `test-flex-distribution` (`[3 3 3]` vs `[4 3 3]`), `flex-stack-allocates-every-row` (sum 9 vs 10), `test-clip` (`"日本語"` width 6 vs `"日 "`), `test-pad-to-box` (`"日本   "`), both properties.
- **list:** `list-narrow-width-throws` (`StringIndexOutOfBoundsException` / nil), `list-cjk-styled-emoji-golden` (wrong pad and `ESC[0m`), `list-width-is-total-width` (22 vs 20), `selection-keeps-visible` (scroll 0 vs 3), `set-filter-resets-scroll` (1 vs 0), width property.
- **input:** `grapheme-cursor-operations` (cursor 7 vs 3; emoji split by backspace), `history-prev-is-total` (nil), `placeholder-cursor-golden` (cursor escape spliced into colour span), `trailing-newline-cursor-golden` (`StringIndexOutOfBoundsException`), width properties.
- **progress:** `complete?-is-numeric` (`100.0` incomplete), `spinner-style-frames-are-distinct` (`:box` = `:square`), `progress-goldens` (bare resets), width property — 7 failures.
- **statusbar:** verified red against `7724a73` by checkout: `statusbar-width-property`, `caller-styles-and-wide-sections` (golden + span), `non-sgr-controls…`, `test-overflow-truncation` — 6 failures.
- **panel:** verified red against `f314939`: `panel-cjk-styled-emoji-golden`, `panel-rejects-unusable-width`, `panel-width-property` — 3 failures.
- **table:** new namespace, so the first red was the missing `limner.table` namespace; the two properties then caught my own expected-width formula errors before the goldens passed.

## 3. Suite results and gates

- `bb check` (final): **JVM 363 tests / 6038 assertions / 0 failures / 0 errors**; **babashka 363 tests / 6036 assertions / 0 failures / 0 errors**. `clj-kondo` skipped (not installed); ICU4J grapheme oracle skipped as configured.
- `bb bench-check`: JVM `{:ascii-slowdown 1.307, :cjk-speedup 7.687, :mixed-slowdown 1.344}`; babashka `{:ascii-slowdown 0.698, :cjk-speedup 36.18, :mixed-slowdown 1.204}` — all within budget.
- Mutation-proofed the static gate: adding `(count s)` plus `(subs s 0 1)` to one layout var makes the gate fail on `subs`.

## 4. Beads

- Claimed and closed: `limner-3-borders`, `limner-3-layout`, `limner-3-table`, `limner-3-list`, `limner-3-input`, `limner-3-progress`, `limner-3-statusbar`, `limner-3-panel`.
- Created: `limner-b1u` (P3) for the deferred Rule 11 splits.
- No other issues created; nothing pushed or tagged.

## 5. Deviations, decisions, open items

- **Rule 11 splits:** borders was split (`effects`, `internal`); layout, list, input, panel, progress remain above the ~200-code-line aim because a clean split needs a facade re-exporting every public var. Filed as `limner-b1u` with the exact files and seam condition, and noted in the commit body.
- **Tab policy:** content tabs expand to spaces up to the next multiple of eight columns before layout.
- **Width contract:** for `list`, `input`, `statusbar`, and `panel`, `:width` is the total rendered width including the border; every line equals it, and too-small widths throw a namespaced `ex-info` (classes added to ADR 0004).
- **Breaking changes recorded in CHANGELOG:** input `:cursor` is now a grapheme index and `:max-length` counts clusters; `:square` spinner frames differ from `:box`; statusbar truncation uses `…`.
- **Gate scope:** the static text-operation gate covers the eight migrated namespaces. `limner.syntax`, `limner.components.markdown`, and `limner.streaming` still use legacy text handling and are deferred to R8 (R6 scope excludes them).
- **TDD note:** for statusbar I rewrote before adding the new tests, then confirmed red by checking out the previous file; every other component's new tests ran red first.
- **Open:** `limner-b1u`; the README now lists exactly what remains (experimental render/events/state; no `limner.tty`; syntax/markdown/streaming legacy; top-level `limner` deprecated).
