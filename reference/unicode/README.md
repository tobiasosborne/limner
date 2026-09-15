# Vendored Unicode data

Limner pins its Unicode ground truth to Unicode Character Database 17.0.0
and Emoji 17.0. The files below are unmodified downloads from Unicode, Inc.:

- `17.0.0/ucd/EastAsianWidth.txt`
- `17.0.0/ucd/emoji/emoji-data.txt`
- `17.0.0/ucd/auxiliary/GraphemeBreakProperty.txt`
- `17.0.0/ucd/auxiliary/GraphemeBreakTest.txt`
- `17.0.0/ucd/DerivedCoreProperties.txt`
- `17.0.0/ucd/extracted/DerivedGeneralCategory.txt`
- `17.0.0/emoji/17.0/emoji-test.txt`

The UCD files came from `https://www.unicode.org/Public/17.0.0/ucd/`.
Unicode's versioned directory and UAX #41 place the Emoji 17 test file at
`https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt`; its local path
retains the requested Emoji-version component. `DerivedCoreProperties.txt`
contains both `Default_Ignorable_Code_Point` and the `InCB` values used by
grapheme rule GB9c.

The data files are distributed under the Unicode License v3, reproduced in
`LICENSE`. Their exact bytes are pinned by `17.0.0/SHA256SUMS`. From that
directory, `sha256sum -c SHA256SUMS` checks every vendored file, including
the shared licence.

To refresh this version from the same official locations, run:

```sh
bb fetch-ucd 17.0.0
```

The command stages every download in a temporary directory. When a manifest
already exists, no destination file is replaced unless all downloaded bytes
match it. Adding another Unicode version requires reviewing its files and the
newly written manifest before committing either.
