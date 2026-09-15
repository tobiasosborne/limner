# limner plan — from the 2026-09-15 review to a codebase worth the attention

This document is temporary. Once the decisions below are made, turn every
phase item into a beads issue (epic per phase) and delete this file's
"Phases" section in favour of `bd ready`. Findings IDs (A1, B3, …) refer to
`REVIEW.md`.

## Guiding idea

Most defects share a few root causes (REVIEW §Summary). So the work is not
"fix 40 bugs". It has three parts:

1. Be honest about the current state now.
2. Build one correct foundation for text: escape sequences, grapheme
   clusters, display width, and styled spans, all against pinned Unicode
   data.
3. Move each component onto that foundation, test-first, using the probe
   list as the first red tests.

Issue #2 gets its real fix in step 2. Nothing is released until it is
verified on both runtimes.

## Decisions needed from the owner

**Status (2026-09-15):** the owner accepted all recommendations below
("sounds all good"). D1–D7 are decided as recommended. Outreach (D8) still
needs the owner to approve the exact text immediately before anything is
posted (CLAUDE.md Rule 14).

**Execution policy (owner directive, 2026-09-15):** work is orchestrated
serially, one task at a time. Each task is implemented by a Codex
`gpt-5.6-sol` subagent at `xhigh` reasoning effort; no parallel subagents.
The orchestrator verifies each task (both test suites, the diff, CLAUDE.md
compliance) before committing it.

**D1. Runtimes.** Keep babashka as a first-class runtime next to the JVM?
- **Recommend: yes.** It is the library's distinctive niche. It rules out
  hard Java dependencies (such as ICU4J) and a Java markdown parser, and it
  means Unicode segmentation should be implemented in Clojure rather than
  borrowed from the JDK (D2).
- Declared minimums: JDK 17+ and babashka ≥ 1.12. Pick the exact versions
  after checking what the test matrix can actually run.

**D2. How limner gets Unicode right.** Options:
- **(a)** Generate tables from a pinned UCD version and implement UAX #29
  grapheme segmentation in portable Clojure, verified against the official
  test file.
- **(b)** Keep `BreakIterator` and fix only the width tables.
- **(c)** Depend on clj-wcwidth.
- **(d)** Use ICU4J when present, as pmonks suggests.

**Recommend (a)**, with ICU4J used only in the test suite as a
cross-check oracle.
- Why not (b): the JDK fails GB9c and differs between JDKs (REVIEW C3).
- Why not (c): today clj-wcwidth has the U+2600–27BF bug, and without
  ICU4J on the classpath it has the same JDK segmentation dependence.
  Offer the generated data upstream instead (see D8).
- Why not (d) as the main path: it cannot run in babashka, so the two
  runtimes would disagree.
- What (a) gives: deterministic results in every runtime, and a data
  generator that is reusable and could be shared with clj-wcwidth.

**D3. Unicode version and ambiguous width.**
- **Recommend:** pin UCD 17.0.0. Treat East Asian Ambiguous as width 1 by
  default, with an explicit option for 2.
- Record in an ADR that terminals lag the standard (glibc still reports
  1 for the Yijing symbols), and expose the pinned version as data
  (`limner.unicode/version`).

**D4. Scope.** 16 namespaces are too many to make excellent at once.
- **Recommend a core:** `text` (new), `ansi` (new), `unicode` (new),
  `core` (colour), `borders`, `layout`, `table` (promoted out of the
  `limner` namespace), `terminal`, and the components `panel`, `list`,
  `input`, `progress` and `statusbar`.
- **Label experimental** until reworked: `render`, `events`, `state`,
  `streaming`, `syntax`, `markdown`. Say so in their namespace docstrings
  and in the README.
- **Deprecate** the `limner` top-level namespace.
- Alternative: cut the experimental namespaces entirely. That is cleaner,
  but it deletes working-ish code before there is a replacement.

**D5. Versioning and API stability.**
- **Recommend:** relabel as `0.x`, pre-release, with the API allowed to
  change until 1.0.
- Start `CHANGELOG.md`, and tag `v0.1.0` at the current commit as the
  "legacy" baseline so git-dep users have a stable coordinate.
- Tag `v0.2.0` after Phase 1.
- Consider Clojars publishing, as pmonks suggested on #1, once Phase 3
  lands.

**D6. Styled text model.**
- **Recommend:** a small data model, a vector of spans
  `{:text "…" :style {:fg … :bg … :bold true …}}`.
- Existing ANSI strings get parsed into it.
- Width, slicing, padding and truncation work on it.
- Rendering to a string emits minimal SGR transitions, never a blanket
  reset inside nested styles.
- Keep `core/color` returning a string for compatibility, implemented on
  top of spans.
- This fixes REVIEW B1–B4 as a class.

**D7. Terminal input.** Provide a real terminal I/O namespace (`limner.tty`)
or declare input out of scope?
- **Recommend:** provide a small POSIX-only `limner.tty` covering:
  - raw mode via `stty` with guaranteed restore;
  - a reader with an ESC timeout;
  - the xterm key table (including Shift-Tab);
  - SGR mouse and bracketed paste.
- Windows is explicitly unsupported until someone needs it.
- Without this, the event and input components cannot be used as
  documented.

**D8. Outreach (owner approves every word, CLAUDE.md Rule 14).**
- **Recommend:** reply on #2 now with the evidence (draft below).
- File the clj-wcwidth issue pmonks asked for (draft below).
- Later, offer the UCD table generator as a PR to clj-wcwidth.

## Phases

### Phase 0: honesty and scaffolding (small; do first)

- **0.1 Beads.** `bd init` in the repo, install hooks as in
  scientist-workbench (`pre-commit` exports `.beads/issues.jsonl`), and
  file this plan as epics and issues.
- **0.2 README honesty pass (A1–A3, A5).**
  - State the status plainly: pre-release, what works, what is
    experimental, known issues linked to #2.
  - Remove the unbacked claims, the performance and compatibility
    sections, the placeholder citation, and the "production-ready"
    wording.
  - Replace the broken examples with examples that run (and are tested,
    see 0.4).
  - Keep the tone warm and the limner preface. Drop the "mathematical
    foundations" section unless it matches the code.
- **0.3 Archive the fiction (A4, A6).**
  - Move `plans/` and `docs/tutorial.md` to `docs/archive/2025/`, each
    with a banner saying "historical, describes APIs that never existed".
  - Delete `benchmark_results.txt`.
  - Remove the "100% bulletproof" header.
  - Deduplicate `.gitignore`.
- **0.4 Quality gates (G4, G5).** Each gate is one command, runs locally,
  and has no CI.
  - `bb test` task: the same runner on babashka.
  - `clojure -M:test`: the JVM suite.
  - Doc-reference check: `evidence/docrefs.clj` promoted to a test
    namespace, failing on any reference to a missing var.
  - README examples: extracted and executed as tests.
  - Reflection: a test that loads all namespaces with
    `*warn-on-reflection*` and fails on new warnings (baseline 18, driven
    to 0 in Phase 2).
  - `clj-kondo` lint, if the owner is happy to install it.
  - A `bb check` task that runs everything above.
- **0.5 Test hygiene (G2, G3).**
  - Inject the clock, output writer and terminal size.
  - Remove `Thread/sleep`-based assertions and the wall-clock performance
    assertion; performance moves to benchmarks.
  - Capture output with `with-out-str` instead of writing to the real
    terminal.
- **0.6** `CHANGELOG.md` and the `v0.1.0` baseline tag (after D5).
- **0.7 Outreach (after D8):** post the reply on #2 and file the
  clj-wcwidth issue.

**Acceptance:**
- `bb check` is green on both runtimes.
- The README contains no claim that isn't backed by a test or a file in
  the repo.
- The doc-reference check shows 0 missing references.

### Phase 1: ground truth and the text foundation (fixes #2 properly)

- **1.1 Vendor UCD 17.0.0 (Law 1).**
  - Files go under `reference/unicode/17.0.0/`: `EastAsianWidth.txt`,
    `emoji/emoji-data.txt`, `emoji/emoji-test.txt`,
    `auxiliary/GraphemeBreakProperty.txt`,
    `auxiliary/GraphemeBreakTest.txt`, `DerivedCoreProperties.txt` (for
    InCB and Default_Ignorable), and `DerivedGeneralCategory.txt`.
  - Add a `SHA256SUMS` file and a `bb` fetch script that verifies it.
- **1.2 Table generator.** A `bb` script turns the UCD files into
  `src/limner/unicode/tables.clj`.
  - The output is compact range vectors searched by binary search, and
    carries an AUTOGENERATED banner stating the Unicode version.
  - A drift test regenerates the tables and compares them byte-for-byte.
- **1.3 ADR 0001, width semantics.** Stated in terms of UCD properties:
  - W/F → 2; A → 1 by default (option: 2); N, Na, H → 1.
  - Default_Ignorable_Code_Point and Mn/Me/Cf → 0; C0/C1 controls →
    policy to decide (0, or an error); tab is left to the caller.
  - Emoji_Presentation → 2; VS16 → 2; VS15 → text width.
  - A regional-indicator pair → 2; a lone regional indicator → 1.
  - A cluster's width is the width of its base, adjusted by VS15/VS16.
- **1.4 ADR 0002 and `limner.unicode.grapheme`.** A UAX #29 extended
  grapheme cluster implementation in portable Clojure, driven by the
  generated property tables (including GB9c InCB).
  - Port-and-verify: all 766 `GraphemeBreakTest` cases pass on the JVM
    and on babashka.
  - Mutation-proven.
  - Cross-checked against ICU4J in a JVM-only test alias.
- **1.5 `limner.ansi` (B2).** One ECMA-48 tokenizer covering CSI with
  parameter/intermediate/final bytes, OSC terminated by BEL or ST,
  DCS/APC/PM/SOS, two-byte ESC sequences, and C1 controls.
  - Property test: tokenizing then re-serialising returns the input
    unchanged.
  - Replaces all seven regex copies.
- **1.6 `limner.text` (B1, B3, B4; D6).**
  - Span model; parse from an ANSI string; `width`; `slice-columns`
    (never splits a cluster or a wide character; pads a half-cut wide
    character with a space); `pad`; `truncate` with an ellipsis; `wrap`;
    `render` with minimal SGR transitions.
  - `core/visible-width` delegates here.
  - Property tests: `(<= (width (truncate s n)) n)`; slicing never
    produces a lone surrogate; rendering then re-parsing gives the same
    spans; a nested style survives an inner style.
- **1.7 Conformance and oracles.**
  - Every fully-qualified sequence in `emoji-test.txt` measures 2.
  - Every EAW code point matches ADR 0001.
  - A comparison report against glibc `wcwidth` (the C probe, as pmonks
    did) and Python `wcwidth`, with each difference explained by version
    or policy, never silently accepted.
- **1.8 Performance.**
  - A benchmark corpus (ASCII, Latin with marks, CJK, emoji sequences,
    styled text) run on the JVM and babashka.
  - Record a baseline and set a budget (for example, no slower than the
    current `visible-width` on ASCII, and at least 10× faster than the
    current code under babashka for CJK). The budget is set from
    measurements, not guessed.

**Acceptance:**
- Every probe in `evidence/probe.clj` under C1, B1 and B2 now gives the
  UCD/ADR answer.
- All conformance suites are green on both runtimes.
- A follow-up on #2 with the evidence is drafted.

### Phase 2: fail-loud core and a sound render path

- **2.1 ADR 0003, error policy (D1–D3).**
  - Remove catch-and-fabricate and stderr printing from library code.
  - `ex-info` with `:limner/error` data keys.
  - `:pre` replaced by explicit validation that throws `ex-info`, so
    `AssertionError` never escapes.
  - One opt-in error boundary in the render loop.
- **2.2 Render buffer on graphemes.**
  - Cells hold one cluster plus its style; a wide cluster takes two cells
    (the second is a continuation cell).
  - A style state machine replaces accumulated escape strings.
  - A row-run diff.
  - Golden tests run the emitted byte stream through a minimal
    virtual-terminal model in the test suite and compare the resulting
    screen grid, which does not depend on byte-level formatting choices.
- **2.3 Render loop (E3).**
  - A single writer thread.
  - No I/O inside `swap!`.
  - `force-render!` becomes a request to that writer.
  - Injectable clock, writer and size source.
  - `stop!` semantics documented and tested.
- **2.4 `limner.tty` (D7, E4, E5).**
  - Raw mode with `finally` restore; a key reader with an ESC timeout;
    the xterm key table from ctlseqs, including `ESC[Z` and modifiers.
  - Bracketed paste; SGR mouse; size via `stty` with an injectable
    source.
  - Capability detection gains `NO_COLOR`, `FORCE_COLOR`, TTY detection
    via `System/console`, and a check for what babashka exposes.
- **2.5 Events (E1, E2), after D4.**
  - Either keep, with no blocking ops in `go`, `swap!`-based updates and
    documented cancellation, or move to experimental with a clear
    warning.
- **2.6** Reflection warnings down to 0.

**Acceptance:** no `catch Exception` remains in library code outside the
documented boundary, and the render golden tests pass on both runtimes.

### Phase 3: components on the foundation (test-first, one bead each)

For each component: turn its probes (REVIEW F1–F9, B3) into failing tests,
add a property test ("every rendered line has the configured width"), move
it onto `limner.text`, split files over 200 lines of code, and rewrite its
namespace docstring as a chapter.

- **borders:**
  - Title measured in columns, with truncation when too long.
  - `colorize-border` rebuilt from box structure, not by sniffing glyphs.
  - Tab policy decided.
- **layout:**
  - Distribute flex remainder by the largest-remainder method, so the
    sizes sum to the available space.
  - Consistent validation.
  - README's layout example becomes a test.
- **table:** a new `limner.table` (alignment options, CJK-correct,
  rejects ragged rows).
- **list:** column-correct truncation; minimum-width validation;
  `set-filter` fix; selection keeps scroll in view.
- **input:** grapheme-indexed cursor; placeholder rendering; multiline
  cursor after a trailing newline; `history-prev` always returns the
  state; no `color :reset`.
- **progress:** `complete?` numeric comparison; spinner style validation;
  remove the duplicate style.
- **statusbar:** keeps the caller's styles via spans; columns, not
  characters.
- **panel:** check against the new text layer.
- **Experimental namespaces, per D4:**
  - `syntax`: a linear-time tokenizer with identifier boundaries, and the
    unused config either removed or implemented.
  - `markdown`: either a CommonMark subset tested against the matching
    spec examples, with the arity bug fixed and a dynamic-binding
    registry, or left experimental with its known issues listed.
  - `streaming`: grapheme positions.

**Acceptance:** all probes under F give correct answers, and every
component has golden and property tests on both runtimes.

### Phase 4: documentation and release

- API docs generated from docstrings.
- A new, short tutorial whose every code block is executed by the test
  suite.
- `CHANGELOG.md` crediting the reporters.
- Tag `v0.2.0` or later; publish to Clojars if D5 says so.
- Draft a closing comment on #2 for the owner to post.

## Draft outreach (for owner approval; do not post as-is without review)

### Reply on limner#2

> Thanks for running that. It was very useful. I compared your glibc table
> with limner and with the Unicode 17.0.0 `EastAsianWidth.txt`:
>
> - Over U+2600–U+27BF, limner (at `51e3621`) agrees with your glibc output
>   on all 448 code points.
> - UCD 17.0 disagrees with both on exactly 14 of them: the Yijing
>   trigrams and digrams (U+2630–2637, U+268A–268F), which became Wide in
>   Unicode 16. The block as a whole is 308 N, 80 A and 60 W.
>
> So, as you say, it's a heterogeneous mix, and the "right" width also
> depends on which Unicode version the terminal is using.
>
> On `BreakIterator`, you're right too. Against `GraphemeBreakTest-17.0.0`,
> JDK 21 passes 749 of 766 cases. All the emoji cases pass, but 16 of the
> failures are Indic conjuncts (GB9c) and one is a ZWJ case, and the result
> also depends on the JDK (which isn't necessarily the same under
> babashka).
>
> What I'm going to do rather than patch ranges:
>
> - generate the width tables from a pinned UCD version;
> - implement UAX #29 segmentation in portable Clojure, so the JVM and
>   babashka give identical answers;
> - test against the official conformance files, with ICU4J as a
>   cross-check in the test suite.
>
> I'll also open the clj-wcwidth issue you asked about, with the breakdown.
> The scripts behind these numbers are in
> `reviews/2026-09-15/evidence/` if you want to check them.

### Issue on pmonks/clj-wcwidth

**Title:** `wide?` treats all of U+2600–U+27BF as wide; per UAX #11 most of
the block is narrow

> In `src/wcwidth/api.clj` (at `2681e5a`), `wide?` includes
> `(and (>= cp 0x2600) (<= cp 0x27BF))`.
>
> In UCD 17.0.0 `EastAsianWidth.txt` those 448 code points are 308 N,
> 80 A and 60 W. Only the 60 W are wide: for example ☔ U+2614, ⚡ U+26A1,
> ✅ U+2705, ❌ U+274C, ✨ U+2728. Common symbols such as ✓ U+2713,
> ✗ U+2717, ⚠ U+26A0, ❤ U+2764 and ☀ U+2600 are narrow, and glibc
> `wcwidth` agrees (table in tobiasosborne/limner#2).
>
> Note that glibc and UCD 17 differ on U+2630–2637 and U+268A–268F, which
> became W in Unicode 16, so pinning a Unicode version matters.
>
> Suggested fix: generate the wide ranges from `EastAsianWidth.txt` (W and
> F) instead of the block range. I'm writing such a generator for limner
> and would be happy to contribute it if useful.
