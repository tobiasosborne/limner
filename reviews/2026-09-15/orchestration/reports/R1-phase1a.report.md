Implemented all three beads in order, each as one local commit. Working tree is clean; nothing was pushed or tagged.

## 1.1 — Vendor Unicode data

Commit `c1ffac4b959b008f8f81a43c75de08f5b8e0bca2`

- [reference/unicode/17.0.0/SHA256SUMS](/home/tobiasosborne/Projects/limner/reference/unicode/17.0.0/SHA256SUMS): pins all seven data files and the licence.
- Vendored, unmodified:
  - `ucd/EastAsianWidth.txt`
  - `ucd/emoji/emoji-data.txt`
  - `ucd/auxiliary/GraphemeBreakProperty.txt`
  - `ucd/auxiliary/GraphemeBreakTest.txt`
  - `ucd/DerivedCoreProperties.txt`
  - `ucd/extracted/DerivedGeneralCategory.txt`
  - `emoji/17.0/emoji-test.txt`
- [reference/unicode/LICENSE](/home/tobiasosborne/Projects/limner/reference/unicode/LICENSE): Unicode License v3.
- [reference/unicode/README.md](/home/tobiasosborne/Projects/limner/reference/unicode/README.md): provenance, licensing, paths, and refresh procedure.
- [scripts/fetch-ucd.clj](/home/tobiasosborne/Projects/limner/scripts/fetch-ucd.clj): temporary staging, header checks, checksum verification, and no overwrite on mismatch.
- [test/limner/unicode_data_test.clj](/home/tobiasosborne/Projects/limner/test/limner/unicode_data_test.clj): offline integrity test.
- [.gitattributes](/home/tobiasosborne/Projects/limner/.gitattributes): `reference/** -text`.
- [bb.edn](/home/tobiasosborne/Projects/limner/bb.edn): added `bb fetch-ucd`.
- `CHANGELOG.md` and `.beads/issues.jsonl` updated.

Vendored size, including the licence: 2,618,484 bytes.

Headers checked:

- `EastAsianWidth-17.0.0.txt`, dated 2025-07-24.
- `emoji-data.txt`, dated 2025-07-25, `Version: 17.0`.
- `GraphemeBreakProperty-17.0.0.txt`, dated 2025-06-30.
- `GraphemeBreakTest-17.0.0.txt`, dated 2025-03-24.
- `DerivedCoreProperties-17.0.0.txt`, dated 2025-07-30.
- `DerivedGeneralCategory-17.0.0.txt`, dated 2025-07-24.
- `emoji-test.txt`, dated 2025-08-04, `Version: 17.0`.
- Licence begins `UNICODE LICENSE V3`.

`InCB` is in `DerivedCoreProperties.txt`, with `@missing ... None` and U+094D listed as `Linker`.

The official Unicode 17 location for `emoji-test.txt` is the [Unicode 17.0.0 emoji directory](https://www.unicode.org/Public/17.0.0/emoji/), not `/Public/emoji/17.0/`. Its requested local versioned path was retained.

## 1.2 — Generate Unicode tables

Commit `140834b03afc16a65fd6de9eb5c5ed14b0adba68`

- [scripts/gen-unicode-tables.clj](/home/tobiasosborne/Projects/limner/scripts/gen-unicode-tables.clj): deterministic parser/compiler, including `@missing` and EAW’s header-defined implicit CJK-wide ranges.
- [src/limner/unicode/tables.clj](/home/tobiasosborne/Projects/limner/src/limner/unicode/tables.clj): generated flat primitive range tables with source paths and SHA-256s.
- [src/limner/unicode.clj](/home/tobiasosborne/Projects/limner/src/limner/unicode.clj): validated constant-memory binary-search lookups.
- [test/limner/unicode_test.clj](/home/tobiasosborne/Projects/limner/test/limner/unicode_test.clj): byte drift, independent raw-data sampling, defaults, edges, and exact-value tests.
- [docs/api/unicode.md](/home/tobiasosborne/Projects/limner/docs/api/unicode.md): public API and generation documentation.
- `bb.edn` and `deps.edn`: generator task and script test path.
- `README.md`, `CHANGELOG.md`, and `.beads/issues.jsonl` updated.

General Category retains the width-relevant subset and maps other assigned categories to `:other`.

Part 2 performance recorded in the bead:

- Generated source: 79,982 bytes at that commit; currently 80,094 bytes after Part 3 added `Emoji_Modifier`.
- Namespace load: JVM 119.942 ms; babashka 22.827 ms.
- Seven-property fixed corpus: JVM 28,425,279 lookups/s; babashka 272,229 lookups/s.
- Babashka EAW comparison:
  - Flat `int-array`: 332,423 lookups/s and 83,180 constructions/s.
  - Nested triples: 289,856 lookups/s and 6,855 constructions/s.

The generated file’s SHA-256 was identical before and after regeneration.

## 1.3 — Width ADR and implementation

Commit `dba543288a68d5428c663f01a81bb0b6c6ba39c3`

- [docs/adr/0001-terminal-width-semantics.md](/home/tobiasosborne/Projects/limner/docs/adr/0001-terminal-width-semantics.md): control, mark, ambiguous, surrogate, unassigned, noncharacter, emoji, and cluster policy.
- [src/limner/unicode/width.clj](/home/tobiasosborne/Projects/limner/src/limner/unicode/width.clj): `code-point-width` and `cluster-width`.
- [test/limner/unicode/width_test.clj](/home/tobiasosborne/Projects/limner/test/limner/unicode/width_test.clj): exhaustive JVM oracle, deterministic babashka sample, all requested probes and sequences.
- [reference/specs/uax11-44.html](/home/tobiasosborne/Projects/limner/reference/specs/uax11-44.html): UAX #11 revision 44 for Unicode 17.0.0.
- [reference/specs/uts51-29.html](/home/tobiasosborne/Projects/limner/reference/specs/uts51-29.html): UTS #51 revision 29 for Emoji 17.0.
- [reference/specs/README.md](/home/tobiasosborne/Projects/limner/reference/specs/README.md): URLs, checksums, and Unicode Terms of Use distinction.
- Generator, generated tables, `limner.unicode`, and property tests extended with `Emoji_Modifier`.
- `README.md`, API docs, changelog, and `.beads/issues.jsonl` updated.

Primary specifications used were [UAX #11 revision 44](https://www.unicode.org/reports/tr11/tr11-44.html) and [UTS #51 revision 29](https://www.unicode.org/reports/tr51/tr51-29.html).

The JVM oracle checked all 1,114,112 code points in 2.57 seconds. Babashka checked 65,542 deterministic cross-plane points in 1.85 seconds.

## Red tests observed

- `vendored-unicode-files-match-sha256-manifest`: failed on JVM and babashka because `SHA256SUMS` did not exist.
- Initial `limner.unicode-test` load: failed on both runtimes because the generator and lookup namespaces did not exist.
- U+3000 mutation from `F` to `N`: `generated-tables-have-not-drifted`, the independent U+3000 spot check, and the exact-value test all failed.
- `emoji-modifier?` test: failed on both runtimes because the lookup did not yet exist.
- Initial `limner.unicode.width-test` load: failed because `limner.unicode.width` did not exist.
- Ambiguous-width mutation: `ambiguous-width-is-an-explicit-option` failed, returning 1 instead of 2.
- VS16 mutation: `cluster-width-examples` failed for ❤️, returning 1 instead of 2.

All mutations were restored.

## Suite results

Per-bead `bb check` results:

- Part 1: JVM 259 tests / 1,543 assertions; babashka 259 / 1,542. Zero failures or errors.
- Part 2: JVM 263 / 4,473; babashka 263 / 4,472. Zero failures or errors.
- Final committed tree: JVM 267 / 4,919; babashka 267 / 4,918. Zero failures or errors.

Additional gates:

- `sha256sum -c SHA256SUMS`: all eight entries passed.
- `bb fetch-ucd 17.0.0`: downloaded, verified, and installed all eight entries successfully.
- `bb gen-unicode`: deterministic regeneration passed.
- `bb check`: passed; `clj-kondo` was explicitly skipped because it is not installed.

## Beads and deviations

Claimed and closed:

- `limner-1-1`
- `limner-1-2`
- `limner-1-3`

Created: none.

Decisions and deviations:

- The brief called U+3000 EAW `W`; the pinned UCD records it as `F`. The lookup returns `:F`, while terminal width remains 2.
- `Emoji_Modifier` was added during Part 3 so the modifier-sequence rule is expressed directly through a UCD property.
- Controls, `Zl`, and `Zp` are width 0; surrogates throw; `Mc` follows EAW; all-zero clusters use width 1. These choices are recorded in ADR 0001.
- `limner.core/visible-width` remains unchanged as required.
- The Unicode reports are governed by Unicode’s Terms of Use, not the data-file Unicode License v3; this is recorded in the specification README.
- No in-scope work remains open. Phase 1.4 remains the next separate bead.