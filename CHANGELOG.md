# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed (breaking)

ADR 0004 replaces swallowed errors and fabricated fallbacks with `ex-info`.
The following functions changed their error behaviour:

- `limner.core/rgb` and `bg-rgb` throw `:limner.core/invalid-rgb` instead of
  `AssertionError` for components outside 0..255.
- `limner.core/color-256` and `bg-256` throw
  `:limner.core/invalid-color-index` instead of `AssertionError`.
- `limner.core/color` throws `:limner.core/invalid-color` for an unknown
  or malformed specification and `:limner.core/invalid-string` for non-string text;
  previously it printed a warning and returned the input unchanged.
- `limner.borders/get-border-chars` throws
  `:limner.borders/unknown-border-style` for an unknown keyword instead of
  silently using `:single`; incomplete custom vectors/maps throw
  `:limner.borders/invalid-border-style`.
- `limner.borders/draw-titled-box` throws
  `:limner.borders/invalid-title-pos` instead of defaulting to `:left`.
- `limner.components.progress/spinner` throws
  `:limner.components.progress/invalid-spinner-style` for unknown styles or
  missing, empty, or non-string custom frames instead of creating nil frames.
- `limner.borders/draw-box`, `draw-titled-box`, `add-shadow`,
  `add-heavy-shadow`, `side-by-side`, and `colorize-border` no longer catch
  and return a placeholder box, the input, or `[]`.
- `limner.borders/indent-lines`, `nest-box`, and `side-by-side` throw
  `:limner.borders/invalid-dimension` instead of `AssertionError`.
- `limner.layout/fixed`, `percent`, and `flex` throw
  `:limner.layout/invalid-dimension` or `:limner.layout/invalid-constraint`
  instead of `AssertionError`; `percent` rejects values outside 0..100 rather
  than clamping them.
- `limner.layout/resolve-constraint` throws instead of returning 0;
  `resolve-flex-constraints` throws on negative remaining space.
- `limner.layout/layout-stack`, `layout-hsplit`, `layout-grid`, `layout`,
  `clip`, and `pad-to-box` throw instead of returning `[]`.
- `limner.layout/layout` throws `:limner.layout/unknown-layout-type` for an
  unknown `:type` instead of returning `[]`.
- `limner.render/write-string-to-buffer` and `write-lines-to-buffer` now carry
  `:limner/error` in their `ex-info` data.
- `limner.render/update-back-buffer`, `render-frame`, and `force-render` throw
  on invalid content instead of clearing the buffer or returning the old
  state. Validation happens before a frame-rate skip or terminal clear; the
  render loop's opt-in `:on-error` keeps the last good frame.
- `limner.render/setup-terminal` and `restore-terminal` no longer swallow
  errors; the render loop no longer prints errors, resize messages, or stop
  warnings. Render errors are retained in its stats and sent to its opt-in
  `:on-error` callback.
- `limner.events/dispatch-key` propagates a handler exception instead of
  logging it and returning the old state; the async event processor routes
  handler errors and timeouts to its `:on-error`/`:on-timeout` callbacks and
  stats without printing.
- `limner.text` and `limner.unicode.*` invalid-input exceptions now carry a
  `:limner/error` class in their data map (the classes are listed in ADR 0004).
- `limner.render` (`cell`, `empty-cell`, `write-string-to-buffer`,
  `write-lines-to-buffer`, `find-dirty-cells`, `find-dirty-regions`,
  `apply-diff`, `set-cell`, `get-cell`, `buffer-stats`) moved to the ADR 0005
  grapheme-and-style cell model. `cell` now takes a cluster string and a style
  map; `parse-line-with-ansi` and `extract-ansi-state` are removed. A wide
  cluster is a lead cell plus a continuation cell, cells compare by value, and
  `write-string-to-buffer` never splits a glyph at either edge. `cell` rejects
  values that are not one visible plain grapheme, `set-cell` maintains wide
  continuations, and dirty-region grouping is a strict linear pass.
  `limner.render` is experimental; the changes are listed here so callers can
  migrate.

### Added

- Pinned the Unicode Character Database at 17.0.0, with the Emoji 17.0 test
  data, checksums, an offline integrity test, and a guarded refresh command.
- Added deterministic Unicode table generation and binary-searched property
  lookups in `limner.unicode`, without changing legacy width behaviour yet.
- Defined Unicode 17.0 terminal-width semantics in ADR 0001 and added pure
  code-point and caller-asserted cluster width functions.
- Added portable Unicode 17.0 extended-grapheme segmentation, official
  conformance coverage, property tests, and an optional ICU4J oracle.
- Added one lossless ECMA-48 tokenizer with structured SGR and OSC 8 helpers.
- Added canonical styled text with semantic ANSI/OSC 8 parsing and rendering,
  grapheme-safe slicing, padding, centering, truncation, wrapping, and lines.
- Added in-suite Emoji 17 conformance for all 5,225 fully-qualified,
  minimally-qualified, unqualified, and component sequences.
- Added reproducible glibc, pinned Python wcwidth, and ICU4J width comparisons
  with every scalar disagreement grouped and classified against ADR 0001.
- Added a fixed two-runtime styled-text benchmark corpus and a separate
  `bb bench-check` regression gate with measured width budgets.
- Added `bb test` for the babashka suite and `bb check` for the serial JVM,
  babashka, and available lint gates.
- Added injectable clocks to the experimental render and streaming APIs, and
  an injectable size source to `limner.render/get-terminal-size`.
- Added `limner.text/clusters` (per-grapheme `{:cluster :style :width :raw}`)
  and `limner.text.style/apply-sgr`, and replaced the render buffer with the
  ADR 0005 grapheme-and-style cell model.
- Added a test-only virtual terminal (`test/limner/vt.clj`) and golden and
  property tests for the render buffer, including REVIEW probes 6 and 6c.

### Changed

- Rewrote the README to describe the pre-release status, known text-handling
  limitations, supported surface, and runnable examples accurately.
- Marked `limner.render`, `limner.events`, `limner.state`, `limner.streaming`,
  `limner.syntax`, and `limner.components.markdown` as experimental.
- Archived the historical 2025 tutorial, architecture, and planning documents;
  corrected the active API documentation against the implementation.
- Made deprecated `visible-length` count Unicode code points after removing
  controls, instead of counting UTF-16 code units.

### Fixed

- Made `limner.render/buffer-stats` count a wide grapheme once rather than
  counting its continuation as a second non-empty cell.
- Removed every JVM reflection warning from `src/` with explicit type hints
  (`limner.components.markdown`, `limner.components.statusbar`,
  `limner.events`, `limner.render`). The reflection gate now fails on any
  warning and its baseline is an intentionally empty file (PLAN 2.6).
- Kept invisible and structural all-zero grapheme clusters at zero terminal
  width while retaining the explicit isolated-combining-mark fallback.
- Made non-SGR CSI sequences, OSC 8 hyperlinks, and other ECMA-48 controls
  zero-width everywhere that previously used the SGR-only regular expression.
- Made `visible-width` follow the pinned Unicode 17 width policy and portable
  grapheme segmenter, including the REVIEW C1 probes (#2, reported by @pmonks).
- Made nested `core/color` calls restore outer styles through the styled-text
  model rather than clearing them with an inner full reset.
- Made BMP Unicode property lookup O(1), added proven ASCII/CJK/Hangul run
  paths, and combined grapheme segmentation with width measurement in one pass.

### Deprecated

- Deprecated the top-level `limner` compatibility namespace; require the
  focused namespaces instead.

## [0.1.0] - 2026-09-14

This first tagged point records the codebase as written in 2025 together with
the fixes made on 2026-09-14. It remains pre-release software.

### Fixed

- Added the `deps.edn` manifest needed to consume limner as a git dependency
  (`1f22dee`) (#1, reported by @pmonks).
- Made `visible-width` grapheme-aware for common combining-character and emoji
  sequences (`51e3621`) (#2, reported by @pmonks). The full Unicode-backed fix
  is still in progress.
- Made `supports-ansi-colors?` return a boolean when `COLORTERM` is set
  (`88f7529`).
- Fixed the `bb benchmark` task and its suite-specific variants (`01c9489`).
- Moved `limner.clj` under `src/`, making the `limner` namespace loadable as a
  dependency without running its demo (`5792e05`).
- Fixed `limner/syntax` passing a nil match to `limner.core/color` (`6f7fc6c`).
- Silenced the `clojure.core/list?` and `clojure.core/empty?` shadowing warnings
  in the list and input component namespaces (`01592b2`).

[Unreleased]: https://github.com/tobiasosborne/limner/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/tobiasosborne/limner/releases/tag/v0.1.0
