# Measured width facts for issue #2

- Unicode 17 grapheme conformance: 766/766 official
  `GraphemeBreakTest.txt` cases on the JVM and babashka.
- Unicode 17 scalar-width conformance: 1,112,064/1,112,064 scalar values on
  the JVM; 65,542/65,542 fixed cross-plane samples on babashka.
- Emoji 17 conformance: all 5,225 entries are one grapheme cluster. Width is 2
  for 3,944/3,944 fully-qualified, 1,029/1,029 minimally-qualified, and 9/9
  component entries. Unqualified entries are 213 at width 1 and 30 at width 2.
- glibc 2.39 `wcwidth` under `C.UTF-8` agrees on 287,130/1,112,064 scalar
  values. Its locale data identifies Unicode 15.1 coverage. The 824,934
  differences classify as 814,730 pinned-unassigned policy differences,
  66 non-negative control/separator policy differences, 26 other ADR 0001
  policy differences, and 10,112 Unicode-version-skew differences.
- Local glibc agrees on 448/448 values with the posted U+2600–U+27BF table.
- jquast/wcwidth 0.2.13 with Unicode 15.1 tables agrees on
  1,107,327/1,112,064 scalar values. The 4,737 differences classify as 4,307
  ADR 0001 policy differences and 430 Unicode-version-skew differences.
- Python `wcswidth` agrees on 5,107/5,225 Emoji 17 sequences. The 118
  differences classify as 104 sequence/presentation policy differences and
  14 Emoji 16/17 version-skew differences.
- ICU4J 78.1, using Unicode 17, agrees with limner's East_Asian_Width property
  on 1,112,064/1,112,064 scalar values.
- REVIEW C1 before (`01592b2`): the five UCD-wide probes returned
  `[1 1 1 1 1]`; U+1F170/U+1F321 returned `[2 2]`; U+00AD/U+2060/U+FEFF
  returned `[1 1 1]`; a lone regional indicator returned 2.
- After the Unicode width, grapheme, ANSI, and text commits (`dba5432`,
  `87947c0`, `279263a`, `e7a92d5`): the same results are `[2 2 2 2 2]`,
  `[1 1]`, `[0 0 0]`, and 1.
