# ADR 0002: Portable extended grapheme segmentation

## Context

User-visible terminal text must be divided at extended grapheme cluster
boundaries before it can be measured or sliced. UAX #29 revision 47,
conformance clause C1-1 and Section 3.1.1, define those boundaries for Unicode
17.0.0. The matching property data is vendored under
`reference/unicode/17.0.0/`.

The former private implementation in `limner.core` uses
`java.text.BreakIterator`. REVIEW C3 measured JDK 21 against the same Unicode
17 corpus: it passed 749 of 766 cases, missing sixteen Indic conjunct cases
covered by GB9c and one ZWJ case. Its data also follows the host JDK, so results
can differ between JVM versions and the Java runtime embedded in babashka.

PLAN D2 therefore selects a portable Clojure implementation. ICU4J is a strong
independent oracle, but a production dependency would exclude babashka and
violate the two-runtime requirement. It remains an explicit JVM-only test
capability.

## Decision

`limner.unicode.grapheme` implements the default extended rules GB1 through
GB999 in their specified order. It obtains Grapheme_Cluster_Break,
Indic_Conjunct_Break, and Extended_Pictographic from `limner.unicode`; no host
Unicode data, locale, regex, or lazy sequence participates.

The scanner advances through a Java string by Unicode code point. A valid
surrogate pair advances two UTF-16 units and can never be split. Each candidate
boundary is decided using the previous GCB value and three compact suffix
states:

- InCB state 0 has no eligible consonant suffix, state 1 has a consonant
  followed only by InCB Extend/Linker values, and state 2 records that this
  suffix includes a Linker. State 2 followed by a Consonant implements GB9c.
- Emoji state 0 has no relevant suffix, state 1 represents
  `Extended_Pictographic Extend*`, and state 2 represents that suffix followed
  by ZWJ. State 2 followed by Extended_Pictographic implements GB11.
- RI state counts the current regional-indicator run. An odd count suppresses
  the next RI boundary for GB12/GB13; a completed pair starts the next pairing
  state.

The pairwise rules GB3 through GB9b are direct ordered predicates. GB999 is the
final default boundary. GB1 and GB2 are represented by emitting the zero and
final UTF-16 indices. `clusters` returns allocated substrings only after their
boundaries are known. `cluster-boundaries` returns all UTF-16 offsets.
`reduce-clusters` instead calls a reducer with the original string and each
start/end range, so width and count consumers need not allocate substrings or a
boundary vector.

A string made entirely of printable ASCII U+0020..U+007E takes a verified fast
path: none of its values is CR or has a special grapheme property, so GB999
places a boundary between every code unit. The general scanner handles the
same values identically when they occur beside non-ASCII text.

### Ill-formed UTF-16

UAX #29 C1-1 defines offsets within a sequence of Unicode characters and bases
the rules on UCD code-point properties. An unpaired UTF-16 surrogate is not a
Unicode character; the pinned
`reference/unicode/17.0.0/ucd/extracted/DerivedGeneralCategory.txt` identifies
U+D800..U+DFFF as `General_Category=Cs`. Java strings can nevertheless contain
those code units.

For total behavior on arbitrary Java strings, each unpaired surrogate is an
opaque one-unit cluster with a forced boundary on both sides. It is never
looked up as a scalar and never absorbs an Extend, ZWJ, or SpacingMark. This is
an out-of-domain extension, not a tailoring of valid Unicode input. Throwing or
silently replacing it would prevent lossless tokenization and round trips.

## Performance

The fixed corpus and runner are in `scripts/grapheme_benchmark.clj`. Each value
below is the median of three rounds on 2026-09-15, after 100 warm-up calls. The
JVM used 3,000 iterations; babashka used 300. Rates count the conforming
segmenter's cluster count for both implementations. The legacy side reproduces
the current `limner.core` path: a fresh `BreakIterator` and a substring vector
per call.

| Corpus | Portable JVM | BreakIterator JVM | Ratio | Portable bb | BreakIterator bb | Ratio |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Printable ASCII | 18,086,579/s | 25,019,413/s | 0.723 | 730,346/s | 666,164/s | 1.096 |
| Latin with marks | 2,283,365/s | 21,938,095/s | 0.104 | 46,911/s | 635,391/s | 0.074 |
| CJK | 2,519,397/s | 15,163,488/s | 0.166 | 52,918/s | 618,293/s | 0.086 |
| Emoji ZWJ | 909,937/s | 13,745,309/s | 0.066 | 16,376/s | 588,779/s | 0.028 |
| Devanagari conjuncts | 1,326,785/s | 11,125,175/s | 0.119 | 27,781/s | 588,108/s | 0.047 |

The portable implementation is materially slower on non-ASCII because it
performs up to three generated-property lookups per code point, while the host
iterator uses compiled runtime tables and is not conforming for the Indic
corpus. ASCII, the critical common path, retains 72% of the JVM legacy rate and
is faster under babashka, so no ASCII fast-path redesign is required for this
phase. PLAN 1.8 will establish text-layer budgets after ANSI parsing, width,
and styled spans can be measured together.

Run the comparison with `clojure -M:benchmark -m grapheme-benchmark` and
`bb benchmark:grapheme`. Run the independent oracle with
`clojure -M:test:icu`; ICU4J 78.1.0.0 agreed on all 766 conformance cases and
500 deterministic generated strings in this run.

### Phase 1.8 performance amendment

The first implementation's per-code-point binary searches caused the text
layer to miss every babashka width budget. Phase 1.8 now packs BMP GCB, InCB,
Extended_Pictographic, width, and related properties in one 65,536-entry
primitive table. Supplementary code points use small page indexes over the
generated ranges, with a grapheme-only lookup that avoids unrelated width properties. Pure CJK
ideograph and precomposed Hangul runs use GB999/GB6–GB8-proven unit boundaries.

The same fixed grapheme benchmark, rerun after those changes, measured:

| Corpus | Portable JVM | BreakIterator JVM | Ratio | Portable bb | BreakIterator bb | Ratio |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Printable ASCII | 18,623,515/s | 24,930,196/s | 0.747 | 731,757/s | 673,201/s | 1.087 |
| Latin with marks | 4,133,686/s | 23,008,205/s | 0.180 | 104,710/s | 622,692/s | 0.168 |
| CJK | 25,862,874/s | 17,674,937/s | 1.463 | 619,717/s | 610,282/s | 1.015 |
| Emoji ZWJ | 1,312,047/s | 15,236,007/s | 0.086 | 21,492/s | 590,290/s | 0.036 |
| Devanagari conjuncts | 2,525,000/s | 12,736,201/s | 0.198 | 63,141/s | 586,000/s | 0.108 |

CJK now closes the measured host-iterator gap on both runtimes. Latin and
Devanagari roughly double on babashka; supplementary-heavy emoji remains the
largest segmentation gap because it still performs multiple range lookups. The
user-facing width path additionally segments and measures in one pass and
meets the mixed-line budgets recorded in `bench/BUDGETS.md`.

## Consequences

The JVM and babashka use the same Unicode version and produce identical
boundaries. Callers that need cluster strings get a small vector API, while hot
paths can consume ranges without intermediate strings. The implementation is
larger and slower than delegating to the host for non-ASCII input, but its
behavior is pinned, mutation-proven, and correct for GB9c.

`limner.core/visible-width` is deliberately unchanged. Moving it to this
segmenter belongs to PLAN 1.6 after the ANSI tokenizer and styled text model are
available.

## Alternatives considered

- `java.text.BreakIterator` was rejected because REVIEW C3 demonstrates both
  conformance failures and runtime-version dependence.
- ICU4J as production code was rejected because it does not run in babashka.
  The optional `:icu` alias preserves its value as an independent oracle.
- A regex translation of the rules was rejected because it would allocate,
  obscure ordered-rule state, and duplicate property tables in the hot path.
- Precomputing a full DFA was deferred because the three suffix states are
  small, auditable against the written rules, and the measured ASCII path meets
  the phase requirement.

## References

- `reference/specs/uax29-47.html`, especially conformance clause C1-1,
  Sections 3.1.1, 6.3, and 7.
- `reference/unicode/17.0.0/ucd/auxiliary/GraphemeBreakProperty.txt`.
- `reference/unicode/17.0.0/ucd/DerivedCoreProperties.txt` for InCB.
- `reference/unicode/17.0.0/ucd/emoji/emoji-data.txt` for
  Extended_Pictographic.
- `reference/unicode/17.0.0/ucd/auxiliary/GraphemeBreakTest.txt`.
- REVIEW C3 and PLAN D2 in `reviews/2026-09-15/`.
