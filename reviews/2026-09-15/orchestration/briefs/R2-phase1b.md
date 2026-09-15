## Run R2 — bead limner-p1.1, then Phase 1.4 and 1.5 (three beads, in this order)

Run R1 left behind:
- vendored UCD 17.0.0 under `reference/unicode/17.0.0/`
- generated property tables and the `limner.unicode` lookup namespace
- ADR 0001 and `limner.unicode.width`

Check all of these from `git log` and the code before you start.

## Part 0 — bead limner-p1.1: zero-width clusters (bug found in review of R1)

Run `bd show limner-p1.1` and do exactly that bead, red-green, as its own commit. It fixes
a rule in ADR 0001 / `limner.unicode.width` before segmentation builds on it.

---

## Part 1 — PLAN 1.4: portable UAX #29 extended grapheme clusters

Claim the 1.4 bead.

### Ground truth

- **Specification:** vendor the UAX #29 text matching Unicode 17.0.0 under
  `reference/specs/` in the same way R1 did: URL and SHA-256 in the README.
- **Rules:** implement the extended grapheme cluster boundary rules exactly
  as that text states them. This covers GB1–GB999, including:
  - GB9c (Indic conjunct break, which uses InCB)
  - GB11 (Extended_Pictographic Extend* ZWJ × Extended_Pictographic)
  - GB12/GB13 (regional indicator pairing)
  Implement them from the spec text, not from memory.
- **Conformance data:**
  `reference/unicode/17.0.0/ucd/auxiliary/GraphemeBreakTest.txt`.

### ADR

Write `docs/adr/0002-grapheme-segmentation.md` covering:
- why portable Clojure rather than `BreakIterator` or ICU4J (cite REVIEW C3
  and PLAN D2)
- the algorithm structure
- how the implementation represents rule state
- the performance approach

### Implementation: `limner.unicode.grapheme`

**Public API:**
- `clusters` returns a vector of cluster strings.
- A reducible or transducer-friendly variant avoids allocation for callers
  that only need widths or counts.
- `cluster-boundaries` returns UTF-16 indices.

**Constraints:**
- **Code points:** iterate by code point (`Character/codePointAt`), keeping
  surrogate pairs intact.
- **Ill-formed input:** a lone surrogate is its own cluster and is never
  merged. Add this to the ADR with the spec reference for ill-formed input.
- **Hot paths:**
  - no regex
  - no `subs` per code point
  - no lazy sequences
  - property lookups through `limner.unicode`
  - no reflection warnings
- **ASCII fast path:** a run of printable ASCII with no CR is one cluster
  per char. Verify it against the rules; CR LF is GB3.

### Tests, red first

**Conformance:**
- Parse `GraphemeBreakTest.txt` in the test.
- Assert all cases (766 in 17.0.0; assert the exact count read) on the JVM
  **and** babashka.
- A failure message shows the case line, the expected clusters and the
  actual clusters, as code points in hex.

**Mutation-prove.** Disable each of the following one at a time and confirm
specific conformance cases go red, then restore:
- GB9c
- GB11
- GB12/13
- GB9 (Extend/ZWJ)
- GB3 (CR LF)

Record in the bead which case numbers each mutation broke.

**Property tests** (test.check; add it as a test-only dependency working on
both runtimes, and check babashka supports test.check first):
- concatenating the clusters returns the input
- no cluster is empty
- boundaries are strictly increasing
- no surrogate pair is split

**ICU4J cross-check (JVM only, optional alias):**
- Add a `:icu` alias in `deps.edn` with ICU4J.
- The test runs only when ICU4J is on the classpath, loaded via
  `requiring-resolve`, and says it was skipped otherwise, never silently.
- Compare cluster boundaries on the conformance inputs and on a generated
  corpus.
- Report the ICU4J version and the agreement result.
- Do not add ICU4J to `bb check`. Document `clojure -M:test:icu` in CLAUDE.md
  under Practical guidance.

### Performance

- Measure clusters per second on a fixed corpus (ASCII, Latin with marks,
  CJK, emoji ZWJ sequences, Devanagari conjuncts) on the JVM and babashka.
- Compare against the current `BreakIterator` path in `limner.core`.
- Record the numbers in the ADR and the bead.
- It must not be drastically slower on ASCII than the current implementation.
  If it is, fix the fast path before committing.

### Scope

Do not wire this into `limner.core/visible-width` yet; that belongs to the
1.6 bead in the next run.

---

## Part 2 — PLAN 1.5: one ECMA-48 escape-sequence tokenizer

Claim the 1.5 bead.

### Ground truth

- **ECMA-48:** fetch the 5th edition PDF from ecma-international.org and
  vendor it under `reference/specs/` with its SHA-256 (check licence/terms).
  If the PDF cannot be vendored, record the URL and SHA and cite the sections
  used, 5.4 and 8.3.
- **xterm ctlseqs:** vendor `ctlseqs.txt` or html from
  invisible-island.net.
- Cite the local paths in the namespace docstring and in the commit.

### Implementation: `limner.ansi`

`tokenize` turns a string into a vector of tokens:
- `{:type :text :text "…"}`
- `{:type :csi :params "…" :intermediates "…" :final \m :raw "…"}`
- `{:type :osc :data "…" :terminator :bel|:st :raw "…"}`
- `{:type :dcs|:apc|:pm|:sos …}`
- `{:type :esc :intermediates "…" :final … :raw "…"}` (two-byte and nF
  escape sequences)
- `{:type :c0|:c1 :char … :raw "…"}` for other control characters. Decide
  whether plain text keeps `\n` and `\t` inside `:text`, and document it.

**Rules:**
- Handle both the 7-bit (ESC-prefixed) and 8-bit C1 introducers:
  - CSI: `ESC [` and U+009B
  - OSC: `ESC ]` and U+009D
  - DCS, APC, PM and SOS likewise
  Decide how 8-bit C1 is treated in UTF-8 text, where U+0080–U+009F are rare
  and ambiguous. Justify the choice in the docstring with the ECMA-48
  reference.
- **Incomplete sequences** (no final byte before the end of the string)
  become a `{:type :incomplete :raw …}` token. Never drop bytes silently, and
  never throw on arbitrary input; a tokenizer must be total.
- **Round trip:** `(apply str (map :raw tokens))` equals the input, always.
- **Helpers:**
  - `strip`: text only
  - `sgr-params`: parse an SGR CSI into structured attributes, covering
    0/1/2/3/4/5/7/8/9/21–29, 30–37/39/40–47/49, 90–97/100–107, 38;5;n,
    38;2;r;g;b (and the colon forms 38:2::r:g:b) and 48 likewise, including
    how they reset. Unknown parameters are preserved, not dropped.
  - `hyperlink` extraction from OSC 8
- Namespace docstring as a chapter. No regex in hot paths if it hurts
  performance; measure.

### Replace every copy of the old regex

Replace the old SGR regex at every site listed in REVIEW B2:
- `core.clj:343`
- `syntax.clj:304`
- `render.clj:52`
- `statusbar.clj:64, 122-124`

Use `limner.ansi/strip`, or the tokenizer where structure is needed.

**Behaviour change:** non-SGR CSI and OSC 8 now strip to zero width. That
fixes REVIEW B2.
- Probes 2 and 2b in `reviews/2026-09-15/evidence/probe.clj` (current answers
  5 and 22) become red tests first; after the fix they give 2 and 4.
- Add a CHANGELOG `[Unreleased]` entry under Fixed.

Do not change the render buffer's cell model beyond swapping the regex; the
2.2 bead does that.

### Tests, red first

- Exact tokenization cases covering:
  - every introducer
  - BEL vs ST termination
  - an OSC 8 hyperlink
  - SGR colon and semicolon forms
  - incomplete sequences at end of input
  - ESC followed by an ordinary character
  - adjacent sequences
  - text containing `[`
- Property tests:
  - the round trip holds for arbitrary strings drawn from a generator biased
    toward ESC, `[`, `]`, BEL, digits, `;`, `:` and C1 characters
  - `strip` never contains ESC
- Cross-check SGR parsing against the xterm ctlseqs table for every attribute
  listed above.
- Mutation-prove the OSC ST terminator handling and the CSI final-byte range
  (0x40–0x7E).

Both beads: `bb check` passes on both runtimes; report the counts, the
conformance results and the performance numbers.
