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
flat primitive range tables with binary search. It is an input layer only:
`limner.core/visible-width` continues to use the legacy rules until the shared
text layer is switched over.
