# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Pinned the Unicode Character Database at 17.0.0, with the Emoji 17.0 test
  data, checksums, an offline integrity test, and a guarded refresh command.
- Added `bb test` for the babashka suite and `bb check` for the serial JVM,
  babashka, and available lint gates.
- Added injectable clocks to the experimental render and streaming APIs, and
  an injectable size source to `limner.render/get-terminal-size`.

### Changed

- Rewrote the README to describe the pre-release status, known text-handling
  limitations, supported surface, and runnable examples accurately.
- Marked `limner.render`, `limner.events`, `limner.state`, `limner.streaming`,
  `limner.syntax`, and `limner.components.markdown` as experimental.
- Archived the historical 2025 tutorial, architecture, and planning documents;
  corrected the active API documentation against the implementation.

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
