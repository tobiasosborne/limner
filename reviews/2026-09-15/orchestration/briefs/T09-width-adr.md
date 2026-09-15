## T09 — PLAN 1.3: ADR 0001 on terminal width semantics, plus the width functions it defines

Claim the beads issue for plan item 1.3. Your inputs are the lookups from
`limner.unicode` (T08) and the vendored UCD 17.0.0 data.

### 1. Research, reading only the primary sources

- **UAX #11 (East Asian Width).** Fetch the text of the version matching
  Unicode 17 from unicode.org. Read what it says about ambiguous width,
  and its recommendation that East_Asian_Width must not be applied
  blindly.
- **UTS #51 (Emoji).** Read the sections on emoji presentation, VS15 and
  VS16, and on RI flag sequences.
- **Existing implementations**, to know where the ecosystem stands. You
  are surveying, not copying:
  - Python `wcwidth` (jquast), in particular its `wcswidth` and its
    zero-width and VS16 handling. Read the source from its GitHub repo
    with curl.
  - glibc's `wcwidth` behaviour. The table pmonks posted is in
    `reviews/2026-09-15/evidence/glibc-wcwidth-U2600-U27BF.tsv`.
  - Markus Kuhn's `wcwidth.c` rules.
- Save the upstream texts you actually used under `reference/specs/`
  (UAX #11 and UTS #51, HTML or text), with their URLs and SHA-256s in a
  README, so that the ADR can cite local paths (Law 1). Check licence
  compatibility: Unicode technical reports are under the Unicode terms of
  use, so note that. Do not vendor wcwidth source code; cite it by URL
  and commit hash.

### 2. Write `docs/adr/0001-terminal-width-semantics.md`

Structure: Context / Decision / Consequences / Alternatives considered /
References.

The decision must be expressed only in terms of UCD properties, so that it
is testable. Starting points (PLAN D3), which you must justify or amend
from the sources:

- **Code point width**
  - C0/C1 controls (Cc) other than those listed below: decide between
    width 0, an error, or leaving them to the caller. Consider what callers
    of `visible-width` need: newlines and tabs appear in real text. The
    recommended starting point is width 0 for Cc, with documentation that
    layout functions reject or handle `\n` and `\t` explicitly (a bead for
    Phase 3). Justify whatever you pick.
  - `Default_Ignorable_Code_Point` → 0.
  - General categories Mn, Me → 0. Decide Mc explicitly: spacing marks are
    usually width 1 in terminals, but check what wcwidth does, and give
    reasons.
  - Cf → 0, except for any exceptions you find (for example U+00AD SOFT
    HYPHEN: check the `wcwidth` precedent).
  - Hangul Jamo medial and final (GCB V and T) → 0 inside a cluster.
  - East_Asian_Width W or F → 2.
  - East_Asian_Width A → 1 by default. There is an explicit option for 2.
    Its API shape is either a dynamic var or an options map; pick one and
    justify it against CLAUDE.md Rule 7 (pure core, injectable).
  - Everything else → 1.
  - Surrogates, unassigned code points and noncharacters: decide and
    justify.
- **Grapheme cluster width**
  - The base width, with adjustments.
  - VS16 (U+FE0F) following an emoji base → 2.
  - VS15 (U+FE0E) → text presentation, width per EAW.
  - An RI pair → 2; a lone RI → 1 (EAW N). Say what the Emoji
    specification and terminals do.
  - Emoji modifier sequences and ZWJ sequences → 2.
  - Keycap sequences → 2.
  - A cluster whose base is zero-width (for example a leading combining
    mark) → the width of its first non-zero code point, or 1. Choose and
    justify.
- **Version skew.** Say plainly that terminals and libcs lag the standard
  (Yijing example: U+2630–2637 and U+268A–268F are W in Unicode 16+ but
  glibc reports 1), and that limner follows the pinned UCD version, which
  is exposed as data.
- **Out of scope:** tabs and cursor-position-dependent width.

### 3. Implement `limner.unicode.width`

The ADR is the specification.

- `code-point-width` takes a code point and an options map.
- `cluster-width` takes a string that the caller asserts is one grapheme
  cluster, plus options. It does not segment; segmentation is T10.
- Write the namespace docstring as a chapter that follows the ADR and
  cites it.

### Tests, red first

- An exhaustive test over all code points 0..0x10FFFF, comparing
  `code-point-width` against an independent restatement of the ADR rules
  written directly from the raw UCD files inside the test. It must be
  fast enough for the suite: measure it, and if it is too slow on bb, run
  every code point on the JVM and a deterministic sample of at least 50k
  on bb, and document that split.
- Exact tests for every example in the ADR and every C1 probe in
  `reviews/2026-09-15/evidence/probe.clj`: U+3000, U+3001, U+3131, U+3220,
  U+338F, U+1F170, U+1F321, U+00AD, U+2060, U+FEFF, and a lone RI.
- `cluster-width` tests for ❤️ (U+2764 U+FE0F), ❤︎ (U+2764 U+FE0E),
  🇩🇪, 👍🏽, 👨‍👩‍👧‍👦, the keycap 1️⃣, e + U+0301, and a Hangul L+V+T jamo
  sequence.
- The ambiguous-width option: U+2605 ★ is 1 by default and 2 with the
  option.
- Mutation-prove: flip the A rule and the VS16 rule one at a time, confirm
  the tests go red, and restore.

Do not change `limner.core/visible-width` yet; T12 (1.6) will switch it
over. Put the glibc and Python `wcwidth` comparison into 1.7. Here, only
note in the ADR where our choices differ from those two and why.

`bb check` must pass. Report the counts.
