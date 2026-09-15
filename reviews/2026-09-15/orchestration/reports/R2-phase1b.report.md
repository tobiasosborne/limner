1. Commits and changes

`3940cca` — limner-p1.1

- `.beads/issues.jsonl`: recorded claim, evidence, and closure.
- `CHANGELOG.md`: documented corrected all-zero cluster behavior.
- `docs/adr/0001-terminal-width-semantics.md`: added the dated amendment and Python `wcwidth` comparison.
- `docs/api/unicode.md`: documented invisible-cluster and isolated-mark semantics.
- `src/limner/unicode/width.clj`: limited width-1 fallback to non-default-ignorable `Mn`/`Me` marks.
- `test/limner/unicode/width_test.clj`: added exact cases and the raw-UCD singleton oracle.

`87947c0` — limner-1-4

- `.beads/issues.jsonl`: recorded conformance, mutation, ICU, performance, and closure evidence.
- `reference/specs/uax29-47.html`: vendored Unicode 17 UAX #29 revision 47.
- `reference/specs/README.md`: recorded its URL and SHA-256.
- [grapheme.clj](/home/tobiasosborne/Projects/limner/src/limner/unicode/grapheme.clj): implemented GB1–GB999, UTF-16 boundaries, lone-surrogate isolation, ASCII fast path, and allocation-free range reduction.
- [grapheme_test.clj](/home/tobiasosborne/Projects/limner/test/limner/unicode/grapheme_test.clj): parses all 766 official cases and tests invariants.
- `test/limner/icu_grapheme_oracle.clj`: optional ICU4J oracle.
- [ADR 0002](/home/tobiasosborne/Projects/limner/docs/adr/0002-grapheme-segmentation.md): recorded algorithm, state, malformed-input policy, and performance.
- `scripts/grapheme_benchmark.clj`: reproducible JVM/babashka comparison with `BreakIterator`.
- `deps.edn`: added test.check, optional ICU4J 78.1, and benchmark aliases.
- `bb.edn`: added the grapheme benchmark task.
- `CLAUDE.md`: documented `clojure -M:test:icu`.
- `docs/api/unicode.md`, `README.md`, `CHANGELOG.md`: documented the public API and behavior.

`279263a` — limner-1-5

- `.beads/issues.jsonl`: recorded red tests, mutations, performance, and closure.
- `reference/specs/ecma-48-5th-edition.pdf`: vendored the unchanged standard.
- `reference/specs/xterm-ctlseqs.txt`: vendored xterm patch 411 documentation.
- `reference/specs/README.md`: recorded URLs, checksums, and redistribution terms.
- [ansi.clj](/home/tobiasosborne/Projects/limner/src/limner/ansi.clj): added lossless CSI/OSC/DCS/APC/PM/SOS/ESC/C0/C1 tokenization, stripping, and OSC 8 extraction.
- [sgr.clj](/home/tobiasosborne/Projects/limner/src/limner/ansi/sgr.clj): added structured SGR attributes, resets, indexed/RGB colors, and unknown preservation.
- [ansi_test.clj](/home/tobiasosborne/Projects/limner/test/limner/ansi_test.clj): exact grammar, helper, round-trip, and totality tests.
- `src/limner/core.clj`: routed width/length stripping through `limner.ansi`.
- `src/limner/syntax.clj`: replaced its SGR regex.
- `src/limner/render.clj`: tokenized controls while retaining the existing UTF-16 cell model and SGR accumulation.
- `src/limner/components/statusbar.clj`: replaced all four regex copies.
- `test/limner/core_test.clj`: promoted probes 2 and 2b to regression tests.
- `test/limner/render_test.clj`, `test/limner/syntax_test.clj`, `test/limner/components/statusbar_test.clj`: verified migrated behavior.
- `test/limner/baselines/reflection_warnings.edn`: removed one warning fixed by the render refactor.
- [ANSI API documentation](/home/tobiasosborne/Projects/limner/docs/api/ansi.md): documented tokens, C1/UTF-8 policy, SGR schema, helpers, and performance.
- `docs/api/core.md`, `docs/api/render.md`, `docs/api/syntax.md`, `docs/api/components.md`, `README.md`, `CHANGELOG.md`: updated user-visible behavior.
- `scripts/ansi_benchmark.clj`, `bb.edn`: added reproducible throughput measurements.

2. Tests observed red first

- `all-zero-clusters-distinguish-marks-from-invisible-content`: seven invisible/structural clusters returned 1 instead of 0 on both runtimes.
- Initial grapheme API, conformance, surrogate, and property tests failed because `limner.unicode.grapheme` did not exist.
- Grapheme mutations broke cases:
  - GB9c: case 748.
  - GB11: case 741.
  - GB12/13: case 281.
  - GB9: case 8.
  - GB3: case 3.
- `test-visible-width-edge-cases`: `ESC[2Khi` returned 5 instead of 2; the OSC 8 link returned 22 instead of 4 on both runtimes.
- All eight initial `limner.ansi-test` tests failed because the API was absent.
- Disabling ESC-`\` ST handling made terminated control strings incomplete.
- Narrowing the CSI final range to `0x40–0x7D` broke the explicit `0x7E` case.

3. Gates, conformance, and performance

Per-bead `bb check` results:

- limner-p1.1: JVM 269 tests/4,922 assertions; babashka 269/4,921; zero failures/errors.
- limner-1-4: JVM 274/5,696; babashka 274/5,695; zero failures/errors.
- limner-1-5 final: JVM 284/5,757; babashka 284/5,756; zero failures/errors.
- `clj-kondo` was unavailable and explicitly skipped.

Additional gates:

- All 766 Unicode 17 grapheme conformance cases pass on both runtimes.
- ICU4J 78.1.0.0: 284 tests/5,758 assertions, zero failures/errors; all 766 cases and 500 generated strings agreed.
- ANSI properties ran 1,000 generated strings per runtime.
- Audit probes 2 and 2b now return 2 and 4.
- Reflection, documentation-reference, and README-example gates pass.

Grapheme portable/legacy throughput:

- JVM clusters/s: ASCII 18.087M/25.019M; marks 2.283M/21.938M; CJK 2.519M/15.163M; emoji 0.910M/13.745M; Devanagari 1.327M/11.125M.
- Babashka: ASCII 730,346/666,164; marks 46,911/635,391; CJK 52,918/618,293; emoji 16,376/588,779; Devanagari 27,781/588,108.

ANSI tokenize/strip throughput:

- JVM MB/s: plain 117.751/119.261; SGR 18.102/24.181; mixed 27.306/19.966.
- Babashka: plain 57.701/56.383; SGR 0.772/0.645; mixed 0.597/0.541.

4. Beads

- Claimed and closed: `limner-p1.1`, `limner-1-4`, `limner-1-5`.
- Created: none.
- `limner-1-6` is now ready and remains unclaimed.

5. Decisions and open items

- Lone U+0301 retains width 1; default-ignorables, controls, lone ZWJ/VS16, and other structural all-zero clusters remain width 0.
- Decoded U+0080–U+009F are treated as C1 controls. LF and TAB remain text.
- Non-ASCII portable grapheme segmentation is slower than `BreakIterator`; ASCII retained 72% of JVM legacy throughput and exceeded it on babashka. Broader budgets remain tracked by `limner-1-8`.
- Grapheme segmentation was not wired into `visible-width`, as required by scope.
- No beads were added, nothing was pushed or tagged, and no GitHub action was taken. The worktree is clean.