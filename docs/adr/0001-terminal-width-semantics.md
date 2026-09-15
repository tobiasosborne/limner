# ADR 0001: Terminal width semantics

## Context

Terminal cell width is a tailored convention, not a value supplied directly
by Unicode. UAX #11 revision 44 says that `East_Asian_Width` is not an
off-the-shelf solution for modern terminal emulators and must be combined with
case-by-case policy. It also says that ambiguous characters should default to
narrow when reliable context is unavailable, and that a nonspacing mark's EAW
value is not its advance width.

UTS #51 revision 29 defines emoji and text presentation selectors, RI flag
pairs, modifier sequences, keycaps, and ZWJ sequences. It specifies their
structure and presentation, not a terminal cell count. A two-column result for
an emoji sequence is therefore a terminal interoperability choice.

The established implementations are useful precedents, but they do not form a
single current standard. Markus Kuhn's Unicode 5.0 `wcwidth.c` returns `-1` for
C0/C1 controls, gives soft hyphen width 1, zeroes Mn, Me, most Cf, and Hangul
medial/final Jamo, and provides a separate ambiguous-wide entry point. Python
`wcwidth` retains the scalar `-1` control convention while its higher-level
string API handles controls, VS15/VS16, RI pairs, modifiers, and ZWJ clusters.
Limner needs non-negative widths for pure layout and will handle or reject
cursor-affecting characters at the later text/layout boundary.

## Decision

The policy is versioned with UCD 17.0.0 and is exposed by
`limner.unicode/version`. Rules are stated in terms of generated UCD properties
except where UTS #51 defines a sequence by literal code points. Earlier rules
take precedence.

`code-point-width` accepts a code point and an options map:

1. Values outside 0..U+10FFFF and surrogate code points (`General_Category =
   Cs`) throw `ex-info`. A surrogate is a UTF-16 code unit, not a Unicode scalar
   value; silently assigning it a display width would conceal broken text.
2. C0/C1 controls and all other `Cc`, plus line and paragraph separators
   (`Zl`, `Zp`), have width 0. This makes measurement non-negative. Tabs,
   newlines, and other cursor-affecting controls remain structure: Phase 3
   layout functions must reject them or handle them explicitly rather than
   treating zero as cursor behaviour. Tabs and cursor-position-dependent width
   are out of scope here.
3. `Default_Ignorable_Code_Point`, `Mn`, `Me`, and `Cf` have width 0. There is
   no soft-hyphen exception: U+00AD follows the current UCD derived property,
   unlike Markus Kuhn's historical width-1 exception.
4. Hangul Jamo with `Grapheme_Cluster_Break` V or T have width 0, so medial and
   final Jamo add no cell inside an L+V+T cluster.
5. EAW W and F have width 2. EAW A has width 1 by default and width 2 only when
   `:ambiguous-width 2` is passed. The sole supported values are 1 and 2.
   An options map is explicit, pure, and injectable; ambient dynamic state
   would make the same call depend on thread bindings.
6. Everything else has width 1. `Mc` is not treated as a nonspacing mark: it
   follows EAW (normally 1, but 2 when W/F). Unassigned code points and
   noncharacters follow their pinned General Category and EAW defaults; this
   gives unassigned CJK-designated ranges width 2 and other unassigned or
   noncharacter code points width 1. Private-use code points are EAW A and
   therefore follow the explicit ambiguous-width option.

`cluster-width` accepts a non-empty string that its caller asserts is exactly
one extended grapheme cluster, plus the same options map. It does not segment
or attempt to validate that assertion; UAX #29 segmentation is ADR 0002 and
Phase 1.4. It applies these ordered rules:

1. Start with the first non-zero `code-point-width` in the cluster. If every
   code point is zero-width, return 1 only when the first code point is an
   `Mn` or `Me` combining mark that is not `Default_Ignorable_Code_Point`.
   Return 0 for invisible and structural clusters, including controls, format
   characters, default-ignorables, and Hangul V/T continuations.
2. VS15 (U+FE0E) immediately after an emoji base requests text presentation;
   use that base's EAW-resolved width and do not apply emoji widening.
3. VS16 (U+FE0F) immediately after an `Extended_Pictographic` or
   `Emoji_Presentation` base requests width 2.
4. A pair of `Grapheme_Cluster_Break = Regional_Indicator` code points has
   width 2. A singleton RI is not a well-formed flag sequence and remains
   width 1, matching its EAW N value.
5. An emoji modifier sequence—an `Extended_Pictographic` base followed by an
   `Emoji_Modifier` code point—has width 2. A cluster
   containing a ZWJ between at least two `Extended_Pictographic` code points
   also has width 2.
6. The UTS #51 keycap form `[0-9#*] U+FE0F U+20E3` has width 2.
7. Otherwise retain the base width. Widths are not summed within a cluster.

These rules deliberately do not make every `Extended_Pictographic` wide.
U+1F170 and U+1F321 therefore remain width 1 without VS16, as their EAW values
A and N require.

### Amendment: all-zero clusters (2026-09-15)

The original rule returned 1 whenever every scalar width was zero. That made
invisible singleton clusters such as U+200B ZERO WIDTH SPACE, U+FEFF, U+00AD,
U+200D ZWJ and U+FE0F VS16 advance by one cell; it also made structural C0
characters such as tab and newline advance. This contradicted the earlier
scalar rules and would have made whole-string width depend on whether the same
property was measured before or after segmentation.

The fallback is now limited to a leading `Mn` or `Me` that is not default
ignorable. Limner assigns an isolated U+0301 width 1 because terminals commonly
show a leading mark with a dotted-circle or spacing fallback, but returns 0 for
the invisible and structural cases above. Python `wcwidth` at the surveyed
commit accumulates only positive scalar widths in `wcswidth`, so both an
isolated mark and a zero-width format character total 0. Limner follows that
precedent for invisible content while deliberately retaining its documented
isolated-mark convention.

Terminals and C libraries lag Unicode releases. In particular, U+2630..U+2637
and U+268A..U+268F changed to EAW W in Unicode 16 and are W in the pinned 17.0
data, while the recorded glibc probe returns width 1. Limner returns 2 for the
pinned data. Consumers can inspect `limner.unicode/version`; runtime terminal
guessing does not rewrite the tables.

### Amendment: Emoji 17 qualification statuses (2026-09-15)

The original decision states rules for individual presentation and sequence
forms but does not name the qualification statuses in `emoji-test.txt`.
Applying those rules to every record in the pinned Emoji 17 corpus gives:

- all 3,944 fully-qualified entries are one extended grapheme cluster of
  width 2;
- all 1,029 minimally-qualified entries remain one cluster of width 2 because
  the qualifying omission does not remove their sequence-level emoji rule;
- all 9 isolated component entries are one cluster of width 2;
- all 243 unqualified entries remain one cluster, but absent emoji
  presentation selectors are semantically significant. Ordinary ordered
  cluster rules give width 1 to 213 and width 2 to 30.

This is not a rule that every string listed in `emoji-test.txt` is wide.
Qualification controls presentation: unqualified text-default symbols follow
their EAW-resolved base width unless another ADR 0001 sequence rule widens
them. The counts above are conformance assertions tied to the pinned file
`reference/unicode/17.0.0/emoji/17.0/emoji-test.txt` and will be reviewed when
that version changes.

## Consequences

JVM and babashka results are identical and testable without consulting the
host libc, JDK Unicode version, locale, or environment. Callers choose legacy
CJK ambiguous width at each pure call. Controls can be measured without a
negative sentinel, but downstream layout still has to distinguish structure
from printable zero-width content. A cluster width is a documented terminal
convention and may differ from a particular font or terminal.

This ADR does not define grapheme segmentation, tab stops, cursor movement,
escape-sequence effects, or whole-string width. Those belong to the following
text-layer phases.

## Alternatives considered

- Applying only EAW was rejected because UAX #11 warns against it, combining
  marks would advance, and emoji presentation sequences would be ignored.
- Returning `-1` for controls, as POSIX-style scalar `wcwidth` functions do,
  was rejected because limner's layout widths are non-negative; the later
  structural text boundary is the right place to reject or interpret them.
- Treating ambiguous characters as wide by default was rejected by UAX #11's
  recommendation and modern non-CJK practice. The explicit option preserves
  legacy CJK use.
- Making all pictographs or emoji width 2 was rejected because it contradicts
  the pinned EAW and presentation data for examples such as U+1F170 and
  U+1F321.
- A dynamic variable for ambiguous width was rejected because an options map
  keeps the pure core explicit and injectable.
- Host `wcwidth`, the JDK, and ICU were rejected as production authorities
  because they introduce version and runtime skew. They remain useful oracles.

## References

- `reference/specs/uax11-44.html`, UAX #11 revision 44 for Unicode 17.0.0,
  especially sections 2, 4.2, 5, 6.1, and 6.2.
- `reference/specs/uts51-29.html`, UTS #51 revision 29 for Emoji 17.0,
  especially sections 1.4.2–1.4.5, 4, and Annex B.
- `reference/unicode/17.0.0/ucd/EastAsianWidth.txt` and the other source files
  listed in `src/limner/unicode/tables.clj`.
- `reviews/2026-09-15/evidence/glibc-wcwidth-U2600-U27BF.tsv`, local glibc
  results for the Yijing version-skew example.
- Python `wcwidth`, surveyed at commit
  `d11cbea58ae5a6f8f928522e7a75e6c1da44d64f`:
  `https://github.com/jquast/wcwidth/blob/d11cbea58ae5a6f8f928522e7a75e6c1da44d64f/wcwidth/_wcwidth.py`
  and `_wcswidth.py`. Surveyed for precedent only; no source was copied.
- Markus Kuhn, `wcwidth.c` (Unicode 5.0, 2007-05-26),
  `https://www.cl.cam.ac.uk/~mgk25/ucs/wcwidth.c`. Surveyed for precedent only;
  no source was copied.
