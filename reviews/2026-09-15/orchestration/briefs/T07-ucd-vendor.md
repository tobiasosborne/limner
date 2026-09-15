## T07 — PLAN 1.1: vendor the pinned Unicode Character Database files

Claim the beads issue for plan item 1.1. Law 1 applies directly: this task
creates the ground truth that every later Unicode task cites.

### Files

Vendor exactly these files, from the official Unicode 17.0.0 locations,
under `reference/unicode/17.0.0/`. Keep the upstream relative paths:

- `ucd/EastAsianWidth.txt`
- `ucd/emoji/emoji-data.txt`
- `ucd/auxiliary/GraphemeBreakProperty.txt`
- `ucd/auxiliary/GraphemeBreakTest.txt`
- `ucd/DerivedCoreProperties.txt`. This contains the `InCB` property needed for
  GB9c and `Default_Ignorable_Code_Point`. Verify that InCB is really in this
  file for 17.0.0; if it is somewhere else, vendor that file and say so.
- `ucd/extracted/DerivedGeneralCategory.txt`
- `emoji/17.0/emoji-test.txt`. Find the correct official URL for emoji-test at
  Unicode 17 / Emoji 17.0 by checking unicode.org's directory listings; do not
  guess.

Rules for the vendoring:

- Check that each file's header states version 17.0.0 (or Emoji 17.0), and
  record the first header lines in your report.
- Do not modify the files.
- Check the licence: the Unicode data files are under the Unicode License v3.
  Vendor the licence text, fetched from unicode.org, as
  `reference/unicode/LICENSE` (or put it under the version directory if that
  fits better). Add a short `reference/unicode/README.md` covering what is
  vendored, from where, under what licence, the pinned version, and how to
  refresh the files.

### Integrity and refresh

1. **`reference/unicode/17.0.0/SHA256SUMS`**, in `sha256sum -c`-compatible
   format with paths relative to that directory.
2. **Fetch script:** a babashka script at `scripts/fetch-ucd.clj` (or a bb task
   `bb fetch-ucd`). It takes the version, downloads into a temporary directory,
   verifies against `SHA256SUMS` when one exists, refuses to overwrite on any
   mismatch, and prints what it did. It never runs as part of `bb check`,
   because no network access happens in tests.
3. **Integrity test:** a test in `bb check` that recomputes the SHA-256 of
   every vendored file and compares it with `SHA256SUMS`, on both runtimes.
   Red first: run it before `SHA256SUMS` exists, or against a deliberately
   wrong checksum, and see it fail.
4. **Line endings:** add a `.gitattributes` entry so these files are never
   normalised, e.g. `reference/** -text`. This keeps the checksums stable
   across platforms.

### Scope

Do not write generators, tables or segmentation code; that is 1.2 onward. Do
not touch `src/`.

In your report, give the total size of what was vendored. If
`GraphemeBreakTest.txt` plus the others exceeds a few MB, that is fine; just
report it.

Verify: `bb check` passes. Report the counts.
