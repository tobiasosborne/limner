Completed all three beads in order. Workspace is clean; branch is 16 commits ahead. Nothing was pushed or posted to GitHub.

## Bead 1.6 — `e7a92d5`

- [.beads/issues.jsonl](/home/tobiasosborne/Projects/limner/.beads/issues.jsonl): recorded closure.
- [docs/adr/0003-styled-text-model.md](/home/tobiasosborne/Projects/limner/docs/adr/0003-styled-text-model.md): defined canonical spans, raw controls, transitions, slicing, wrapping, and compatibility decisions.
- [src/limner/text.clj](/home/tobiasosborne/Projects/limner/src/limner/text.clj): public styled-text API.
- [src/limner/text/style.clj](/home/tobiasosborne/Projects/limner/src/limner/text/style.clj): ANSI/OSC parsing, style composition, canonicalisation, and minimal rendering.
- [src/limner/text/columns.clj](/home/tobiasosborne/Projects/limner/src/limner/text/columns.clj): width, slicing, padding, centering, truncation, wrapping, and lines.
- [src/limner/core.clj](/home/tobiasosborne/Projects/limner/src/limner/core.clj): routed `color` and `visible-width` through the text layer; made deprecated `visible-length` code-point based.
- [test/limner/text_test.clj](/home/tobiasosborne/Projects/limner/test/limner/text_test.clj): exact and property tests.
- [test/limner/core_test.clj](/home/tobiasosborne/Projects/limner/test/limner/core_test.clj): exact nesting, C1 probes, and code-point length tests.
- [README.md](/home/tobiasosborne/Projects/limner/README.md), [CHANGELOG.md](/home/tobiasosborne/Projects/limner/CHANGELOG.md), and API documentation under `docs/api/`: updated behavior and limitations.

No component expectations required changes.

## Bead 1.7 — `a392d33`

- [.beads/issues.jsonl](/home/tobiasosborne/Projects/limner/.beads/issues.jsonl): recorded closure.
- [test/limner/unicode/emoji_test.clj](/home/tobiasosborne/Projects/limner/test/limner/unicode/emoji_test.clj): checked all 5,225 Emoji 17 entries.
- [docs/adr/0001-terminal-width-semantics.md](/home/tobiasosborne/Projects/limner/docs/adr/0001-terminal-width-semantics.md): specified qualification-status behavior.
- [scripts/oracles/wcwidth.c](/home/tobiasosborne/Projects/limner/scripts/oracles/wcwidth.c): exhaustive glibc probe.
- [scripts/oracles/python_wcwidth.py](/home/tobiasosborne/Projects/limner/scripts/oracles/python_wcwidth.py): pinned Python scalar and sequence probe.
- [scripts/width-oracles.clj](/home/tobiasosborne/Projects/limner/scripts/width-oracles.clj): compilation, collection, classification, and report generation.
- [reviews/width-oracles/2026-09-15.md](/home/tobiasosborne/Projects/limner/reviews/width-oracles/2026-09-15.md): complete contiguous-range report.
- [reviews/width-oracles/SUMMARY-for-issue-2.md](/home/tobiasosborne/Projects/limner/reviews/width-oracles/SUMMARY-for-issue-2.md): measured facts only.
- [.gitignore](/home/tobiasosborne/Projects/limner/.gitignore), [bb.edn](/home/tobiasosborne/Projects/limner/bb.edn), [CHANGELOG.md](/home/tobiasosborne/Projects/limner/CHANGELOG.md), and [docs/api/unicode.md](/home/tobiasosborne/Projects/limner/docs/api/unicode.md): scratch exclusion, task, and documentation.

Oracle results:

- glibc 2.39/Unicode 15.1: 287,130/1,112,064 scalar agreements; all 824,934 differences classified. Local U+2600–U+27BF matched the posted table 448/448.
- Python `wcwidth==0.2.13`, Unicode 15.1: 1,107,327/1,112,064 scalar agreements.
- Python `wcswidth`: 5,107/5,225 Emoji 17 sequence agreements; 104 policy differences and 14 version-skew differences.
- ICU4J 78.1/Unicode 17: 1,112,064/1,112,064 East_Asian_Width agreements.
- No limner width bug was found.

## Bead 1.8 — `a489070`

- [.beads/issues.jsonl](/home/tobiasosborne/Projects/limner/.beads/issues.jsonl): closed the bead and automatically closed epic `limner-p1`.
- [bench/limner/text_bench.clj](/home/tobiasosborne/Projects/limner/bench/limner/text_bench.clj): fixed corpus, legacy comparator, five-round measurements, and regression gate.
- [bench/BUDGETS.md](/home/tobiasosborne/Projects/limner/bench/BUDGETS.md): full JVM/babashka table, budgets, initial misses, and load-time measurements.
- [src/limner/unicode.clj](/home/tobiasosborne/Projects/limner/src/limner/unicode.clj): packed BMP properties and supplementary page indexes.
- [src/limner/unicode/grapheme.clj](/home/tobiasosborne/Projects/limner/src/limner/unicode/grapheme.clj): numeric hot loop and proven CJK/Hangul run path.
- [src/limner/unicode/width.clj](/home/tobiasosborne/Projects/limner/src/limner/unicode/width.clj) and [src/limner/unicode/width/string.clj](/home/tobiasosborne/Projects/limner/src/limner/unicode/width/string.clj): packed scalar width and one-pass segmentation/measurement.
- [src/limner/text/columns.clj](/home/tobiasosborne/Projects/limner/src/limner/text/columns.clj) and [src/limner/text.clj](/home/tobiasosborne/Projects/limner/src/limner/text.clj): allocation-free width routing and whole-run shortcuts.
- [test/limner/text_test.clj](/home/tobiasosborne/Projects/limner/test/limner/text_test.clj) and [test/limner/unicode/grapheme_test.clj](/home/tobiasosborne/Projects/limner/test/limner/unicode/grapheme_test.clj): fast/slow equivalence properties.
- [CLAUDE.md](/home/tobiasosborne/Projects/limner/CLAUDE.md), `bb.edn`, `deps.edn`, ADRs 0002/0003, API docs, and changelog: documented commands and measured design.

Key budgets:

| Metric | JVM | Babashka |
|---|---:|---:|
| ASCII slowdown vs legacy | 0.503× | 0.712× |
| Mixed 80-column slowdown | 1.484× | 1.144× |
| CJK speedup | 6.86× | 59.77× |

For the exact 10 KiB mixed corpus:

| Operation | JVM calls/s | Babashka calls/s |
|---|---:|---:|
| width | 499.3 | 32.0 |
| clusters | 871.3 | 21.9 |
| from-ansi | 13,231.0 | 4,987.5 |
| to-ansi | 14,112.3 | 8,257.6 |
| truncate | 107.2 | 7.1 |
| wrap | 57.3 | 4.2 |

## Red tests and mutation proofs

- Exact nested `core/color` test failed because the legacy result contained an inner `ESC[0m`.
- REVIEW C1 tests failed with `[1 1 1 1 1]`, `[2 2]`, `[1 1 1]`, and lone-RI width 2.
- `visible-length "😀"` failed as 2 instead of 1.
- Initial text tests failed because `limner.text` did not exist.
- Bypassing partial-wide replacement caused five slicing, padding, and truncation failures.
- Changing RI-pair width to 1 caused 259 fully-qualified Emoji failures.
- The one-pass equivalence property found `🇩🇪ᤠ` incorrectly measured as 2 instead of 1.
- Tripling width work made `bench-check` fail at 1.94× ASCII slowdown; restoring it passed.

## Gates

Per-bead `bb check`:

- 1.6: JVM 296 tests/5,804 assertions; babashka 296/5,803.
- 1.7: JVM 297/5,808; babashka 297/5,807.
- 1.8 final: JVM 300/5,813; babashka 300/5,812.
- All had 0 failures and 0 errors.
- ICU grapheme oracle: 6 tests/778 assertions, 766 conformance cases plus 500 generated strings agreed.
- `bb bench-check` passed both runtimes.
- `bb width-oracles` completed with zero ICU EAW disagreements.
- `clj-kondo` was unavailable and explicitly skipped by `bb check`.

## Tracker and deviations

Claimed and closed: `limner-1-6`, `limner-1-7`, `limner-1-8`. Epic `limner-p1` auto-closed. No issues were created.

Decisions are recorded in ADR 0003: opaque controls are preserved, final SGR reset is conditional, partial wide clusters become styled spaces, and undersized ellipses are safely sliced. Supplementary-heavy emoji segmentation remains slower than `BreakIterator`, although correctness and all required width budgets pass.

Three execution deviations:

- The commit commands embedded literal `\n` text, so the requested trailer text is visible in each commit message but is not recognized by Git as structured trailers. I did not amend because the brief explicitly forbids amendment.
- Two early scoped namespace checks were accidentally run concurrently; every full suite and long benchmark was run serially.
- One benchmark command was accidentally invoked without `timeout`; it terminated, and I verified no process remained.