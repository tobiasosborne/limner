# Unicode property data

`limner.unicode` exposes the Unicode properties that the text layer uses as
inputs. The data is pinned to Unicode Character Database 17.0.0 and generated
from the unmodified files under `reference/unicode/17.0.0`; it does not depend
on the host JDK's Unicode version.

`(version)` returns `"17.0.0"`. The code-point lookup functions are:

- `(east-asian-width cp)`
- `(grapheme-break cp)`
- `(extended-pictographic? cp)`
- `(emoji-presentation? cp)`
- `(emoji-modifier? cp)`
- `(default-ignorable? cp)`
- `(incb cp)`
- `(general-category cp)`

Each accepts an integer from 0 through U+10FFFF and throws `ex-info` outside
that domain. Property names are keywords matching their UCD spelling, such as
`:W`, `:ZWJ`, and `:Regional_Indicator`. General Category retains the values
needed by terminal-width rules—`:Mn`, `:Me`, `:Mc`, `:Cf`, `:Cc`, `:Zl`,
`:Zp`, `:Cs`, `:Co`, and `:Cn`—and returns `:other` for other assigned
categories.

The generator applies each file's `@missing` default. East Asian Width also
has header-defined wide defaults for unassigned CJK ranges and undesignated
code points in planes 2 and 3; those ranges are read from the vendored header.

Regenerate and verify the committed source with:

```sh
bb gen-unicode
bb test -n limner.unicode-test
```

The generated namespace embeds the source paths and checksums and uses merged,
flat primitive source ranges. At load time `limner.unicode` packs BMP GCB,
InCB, EAW, width class, General Category, and emoji flags into a 65,536-entry
primitive table; supplementary planes use page indexes into the source ranges. `limner.text`
combines these properties with ANSI-aware column operations, and
`limner.core/visible-width` delegates to that layer.

## Terminal width policy

`limner.unicode.width` implements the pure scalar and single-cluster policy in
[ADR 0001](../adr/0001-terminal-width-semantics.md):

```clojure
(require '[limner.unicode.width :as width])

(width/code-point-width 0x2605 {})                     ; => 1
(width/code-point-width 0x2605 {:ambiguous-width 2})   ; => 2
(width/cluster-width "❤️" {})                           ; => 2
```

`cluster-width` assumes its non-empty string is one grapheme cluster; it does
not perform segmentation. A cluster made only from zero-width code points has
width 1 only when it begins with a non-default-ignorable `Mn` or `Me` mark;
invisible and structural singleton clusters have width 0. Whole-string width
is available as `limner.text/width`.

## Extended grapheme clusters

`limner.unicode.grapheme` implements the default extended-grapheme rules from
Unicode 17.0.0 UAX #29 revision 47:

```clojure
(require '[limner.unicode.grapheme :as grapheme])

(grapheme/clusters "aé😀")             ; => ["a" "é" "😀"]
(grapheme/cluster-boundaries "a😀b")   ; => [0 1 3 4]
```

Boundaries are UTF-16 indices, matching Clojure's string APIs. For consumers
that only need to count or inspect source ranges, `reduce-clusters` calls a
four-argument reducing function with the accumulator, original string, start,
and end index; it does not allocate cluster substrings. A lone UTF-16 surrogate
is an opaque one-unit cluster and never joins adjacent content.

## Conformance and independent oracles

The normal JVM and babashka suites check the pinned grapheme corpus, the full
Emoji 17 sequence corpus, and the ADR 0001 scalar-width policy. Run the
out-of-suite external comparisons with:

```sh
bb width-oracles
```

That task compiles the glibc `wcwidth` probe, installs pinned Python
`wcwidth==0.2.13` in `.cache/width-oracles/venv`, and compares ICU4J through the
existing `:icu` alias. It writes raw scratch data under `.cache/` and the
classified report under `reviews/width-oracles/`.
