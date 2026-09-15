## Run R3 — Phase 1.6, 1.7, 1.8 (three beads, in this order)

Earlier runs landed `limner.unicode`, `limner.unicode.width` (ADR 0001, amended by
limner-p1.1), `limner.unicode.grapheme` (ADR 0002), `limner.ansi` and
`limner.ansi.sgr`. ADR numbers 0001 and 0002 are taken. Verify them in the code
before relying on them.

---

## Part 1 — PLAN 1.6: `limner.text`, the styled text layer (fixes REVIEW B1, B3, B4; PLAN D6)

Claim the 1.6 bead. This is the foundation every component moves onto in
Phase 3, so the design has to be right. Write `docs/adr/0003-styled-text-model.md`
before the code. The next free ADR number may differ; check.

### Model

A styled text value is a vector of spans `{:text "..." :style {...}}`.

- **Adjacent spans with equal style** are merged; the representation is
  canonical.
- **Style keys** cover:
  - `:fg` and `:bg`, each one of: a named 16-colour keyword, `{:index n}`, or
    `{:rgb [r g b]}`;
  - `:bold`, `:dim`, `:italic`, `:underline` (with an underline style if you
    support SGR 4:x), `:blink`, `:inverse`, `:hidden`, `:strike`;
  - `:link`, for an OSC 8 URI.
- **Absent key** means default. Make the style map the single source of
  truth for what SGR is emitted.
- **Parsing:** `from-ansi` turns a string with escapes into styled text,
  interpreting SGR via `limner.ansi/sgr-params` and OSC 8 as `:link`.
  - Other control sequences cannot be represented as style. Either keep them
    as zero-width `:raw` passthrough spans or drop them, and decide explicitly
    in the ADR. Recommendation: keep them as zero-width opaque spans so no
    information is lost, but let width, slice and friends ignore them.
- **Rendering:** `to-ansi` turns styled text into a string. It emits only
  the SGR transitions needed between adjacent spans, and never a blanket
  reset between nested styles.
  - The one exception is the end of the string: emit a single reset or the
    minimal closing sequence only if a style is active. Document the choice.
  - Round-trip property: `(= (from-ansi (to-ansi t)) t)` for canonical `t`.
- **Constructors for users:**
  - `styled` takes a string and a style.
  - `concat` joins text values.
  - A `style` function applies a style over existing text: it fills in only
    the keys the inner spans don't set.
  - This is the fix for nesting. `(style {:bold true} (concat (styled "R" {:fg :red}) " after"))`
    must render " after" bold.

### Column operations

All operations are grapheme- and width-correct: they use
`limner.unicode.grapheme` and `limner.unicode.width`, and treat
escape/opaque spans as width 0. Each accepts a string (parsed via `from-ansi`)
or styled text, and has documented options including ambiguous width.

- `width`
- `slice-columns [t start end]`
  - Never splits a grapheme cluster.
  - A wide cluster cut by a boundary is replaced by spaces of the covered
    width, carrying the cluster's style. Say so in the ADR.
- `pad-right`, `pad-left`, `center`, taking a target width and a fill.
- `truncate [t n & {:keys [ellipsis]}]`
  - The ellipsis counts towards the width.
  - Width `n` smaller than the ellipsis width is a defined case; decide it.
- `wrap [t n]`
  - Word-wrap on whitespace clusters, with hard breaks for over-long words.
  - Explicit newlines are preserved.
- `lines`
  - Split on newlines.
  - A style active across a newline carries into the next line.

### Wire-up

These are behaviour fixes, each red-test first and each in CHANGELOG
`[Unreleased]`:

- **`limner.core/visible-width`:** delegates to `limner.text/width`. This is
  the real fix for issue #2. The REVIEW C1 probes (3–3f) and the grapheme
  tests become exact tests against ADR 0001.
- **`limner.core/visible-length`:** keep it, deprecated, but make it
  code-point based. Or leave it as is; decide and document, since callers may
  rely on it.
- **`limner.core/color`:** keeps returning a string, now implemented via the
  text layer so nested calls compose: `(color :bold (str (color :red "R") " after"))`
  keeps " after" bold. Probe 1 becomes a red test first.
  - Rewrite the legacy `test-color-nesting` in `core_test.clj` to assert the
    exact string. Rule 5 calls out that test as the example of a test that
    passes while the behaviour is broken.
- **Out of scope:** components (borders, list and the rest) are Phase 3. Do
  not touch them, except where the `visible-width` or `color` change breaks
  an existing test. For each such case:
  - Analyse whether the old expectation encoded a bug, per REVIEW.
  - If it did, update the expectation and say why in the commit.
  - If it did not, fix the regression.
  - List every such test in the report.

### Tests

- **Exact cases** for every operation with CJK, emoji ZWJ, flags, combining
  marks, styled spans crossing the cut, OSC 8 links and nested styles.
- **test.check properties:**
  - `(<= (width (truncate t n)) n)`
  - `(= n (width (pad-right t n)))` when `(<= (width t) n)`
  - `slice-columns` never produces a lone surrogate or a broken cluster
  - `from-ansi`/`to-ansi` round trip
  - concatenation of slices at a cluster boundary reconstructs the text
  - the width of the concatenation is the sum of the widths
- **Mutation-prove** the nesting rule and the wide-cluster-split rule.

---

## Part 2 — PLAN 1.7: conformance suites and independent width oracles

Claim the 1.7 bead.

### In-suite conformance (`bb check`, both runtimes)

- Every `fully-qualified` sequence in `emoji-test.txt` has `limner.text/width`
  = 2 and is exactly one grapheme cluster.
  - Components, minimally-qualified and unqualified entries: check what ADR
    0001 implies for them and assert that. If the ADR is silent, amend the ADR
    in this bead and say so.
- Width for every code point follows ADR 0001. This may already exist from
  R1; if so, reuse it rather than duplicate.

### Oracles (report only, not in `bb check`)

Write `scripts/width-oracles.clj`, or a bb task `bb width-oracles`. It produces
`reviews/width-oracles/<date>.md`: a comparison of limner against

1. glibc `wcwidth()` on this machine. Write a tiny C probe under
   `scripts/oracles/wcwidth.c`, compiled with the system `cc`, iterating all
   code points under a UTF-8 locale. Record the glibc version.
2. Python `wcwidth` (jquast). Install it in a venv under the scratch or
   `.cache` directory, never system-wide, with a pinned version. Its
   `wcwidth` and `wcswidth` apply to single code points and to the
   emoji-test sequences.
3. ICU4J, if `:icu` is available: the `EAST_ASIAN_WIDTH` property.

For each oracle the report has:

- the number of disagreeing code points, grouped into contiguous ranges with
  UCD property values;
- a classification of each group:
  - version skew (the oracle's Unicode version is older; state it),
  - policy difference (point to the ADR 0001 decision), or
  - limner bug.

A limner bug is a stop condition for this bead: fix it red-green in this
bead, or stop the run and report.

Also report the glibc results for U+2600–U+27BF versus pmonks's posted table
(`reviews/2026-09-15/evidence/glibc-wcwidth-U2600-U27BF.tsv`). The same
glibc data should match; note the local glibc version.

Commit the generated report.

### Evidence for issue #2

The owner will reply on #2 after this bead. Write a short
`reviews/width-oracles/SUMMARY-for-issue-2.md` containing only the measured
facts:

- conformance counts,
- oracle agreement numbers and classifications,
- the before/after behaviour of the REVIEW C1 probes,
- the commit SHAs.

Do not draft the reply; the orchestrator writes it.

---

## Part 3 — PLAN 1.8: performance baselines and budgets

Claim the 1.8 bead.

### Benchmark harness

- Extend `test/limner/benchmarks.clj`, or add `bench/limner/text_bench.clj`
  if that is cleaner. It stays outside the test suite.
- Fixed corpus: ASCII, Latin with combining marks, CJK, emoji ZWJ sequences,
  Devanagari conjuncts, styled text with many spans, and 10 KB mixed text.
- Operations: `width`, grapheme `clusters`, `from-ansi`, `to-ansi`,
  `truncate`, `wrap`.
- Measure throughput after warm-up with several repetitions, and report the
  median plus spread, on the JVM and babashka.
- Compare against the legacy implementation. Keep a copy of legacy
  `visible-width` in the bench namespace only, so the baseline can be
  measured.

### Budgets

- Write down numeric budgets derived from these measurements, in an ADR
  addendum or a `bench/BUDGETS.md`:
  - ASCII `width` must not be slower than legacy by more than a stated
    factor;
  - babashka CJK `width` must be at least 10x legacy, as PLAN states, or
    explain why that target was wrong.
- If a budget is missed, profile and optimise within this bead: the ASCII
  fast path, avoiding allocation, primitive arrays for tables. Re-measure.

### Known performance gap from run R2 (orchestrator note)

R2 measured portable grapheme segmentation against `BreakIterator`:

| Runtime | Portable, non-ASCII | `BreakIterator` |
|---|---|---|
| babashka | 16k–53k clusters/s | about 590k–635k |
| JVM | 0.9M–2.5M clusters/s | 11M–15M |

The ANSI tokenizer on babashka runs at 0.6–0.8 MB/s for styled text.

A full 80×24 screen of CJK text is about 1,920 clusters, so on babashka it
currently costs tens of milliseconds just to segment. This bead must close
most of that gap without giving up correctness. The hard constraints stay:

- 766/766 conformance on both runtimes
- no JDK or ICU dependence in production
- identical results on both runtimes

Directions to measure, not assume:

1. **Flatten the property lookup.** Replace per-code-point binary search
   with an O(1) packed lookup, for example a precomputed BMP table giving
   GCB, InCB, ExtPict and width class in a few bits per code point. Keep
   binary search only for supplementary planes. Build it from the generated
   tables at load time and measure the load-time cost on babashka.
2. **Make the babashka hot loop cheap.** Avoid keyword or map state per
   code point, use `unchecked` primitive arithmetic, avoid vars that are
   re-resolved in loops, and avoid interop that babashka reflects on every
   call. Profile with a micro-benchmark per candidate change.
3. **Add fast paths for whole runs.** Whole runs of ASCII, Latin-1, and CJK
   ideographs or Hangul syllables with no Extend/ZWJ/Prepend/SpacingMark
   neighbours are one cluster per code point with a known width. Prove each
   fast path against the rules in a comment and in a test that compares the
   fast and slow paths on generated input.
4. **Compute width in one pass.** `width` should segment and measure
   together, without materialising cluster strings.

Set the budgets from the measured results. Minimum acceptable outcome:

- `limner.text/width` on an 80-column line of mixed CJK, Latin and emoji
  takes at most 2× the legacy `visible-width` time on babashka and at most
  3× on the JVM.
- ASCII is no slower than legacy on either runtime.

If a target is truly unreachable without violating the hard constraints,
stop and report the best achieved numbers with evidence, rather than
lowering the bar silently. The PLAN.md "≥10× legacy on bb for CJK" figure
was a guess made before measuring; replace it with the measured, justified
budget.

### Regression check

- Add a `bb bench-check` task that runs a short version and fails if a
  budget is exceeded by a generous margin.
- It is not part of `bb check`, since timing is noisy. Document it in
  CLAUDE.md under Practical guidance.

All three beads: `bb check` passes on both runtimes. Report the counts, the
oracle summaries and the benchmark tables.
