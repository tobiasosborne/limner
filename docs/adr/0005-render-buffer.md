# ADR 0005: Render buffer on grapheme cells

## Context

REVIEW B3 records that the render buffer stores text one UTF-16 code unit per
cell, so `😀` becomes two surrogate-half cells and every later cell on the
row is misplaced. REVIEW B4 records that a cell stores an accumulated ANSI
string: after `ESC[31m` and `ESC[0m` the cell's `:ansi` is
`ESC[31mESC[0m`, SGR state grows without bound, and two visually identical
cells compare unequal, so the diff redraws cells that did not change. REVIEW
B3 also records that `find-dirty-regions` calls `.indexOf` inside
`partition-by`, which is quadratic in the number of dirty cells on a row.

PLAN item 2.2 replaces the buffer with a grapheme-and-style model and tests
it through a virtual terminal. This ADR fixes the model that
`limner.render` (an experimental namespace) implements.

## Decision

### A cell is a grapheme cluster and a style value

A cell is a map:

```clojure
{:cluster "a" :style {:fg :red} :width 1}
```

- `:cluster` is one grapheme cluster string. A space cell has `:cluster " "`.
- `:style` is the canonical ADR 0003 style map. There is no accumulated SGR
  string. Cells compare with `=`, so equivalent styles compare equal and the
  diff does not redraw them.
- `:width` is the cluster's terminal width from `limner.unicode.width`.

Opaque non-style ECMA-48 controls have no display meaning and are not stored
on a cell; the buffer is a display model, not a log of the input bytes.

A wide cluster occupies two grid cells: the lead cell holds the cluster with
`:width 2`, and the next cell is a **continuation** cell with `:cluster ""`
and `:width 0` that renders nothing. A continuation can never hold text of
its own.

The public `cell` constructor accepts exactly one positive-width plain
grapheme. `set-cell` canonicalises that value and maintains the continuation
invariant; a wide cell at the right edge becomes a space. Continuation markers
are created only by the buffer writer.

Zero-width content attaches to the preceding cluster, as ADR 0002
segmentation guarantees; a leading zero-width cluster attaches to the
following cluster. A cell never holds a lone surrogate because the text
layer never produces one.

### Writing goes through `limner.text`

`limner.text` gains a `clusters` function that returns one map per grapheme
unit, `{:cluster :style :width :raw}`, built on the same segmentation and
style machinery as `slice-columns`. Writing a line parses the input once,
walks those units, and places them on the grid. Parsing, segmentation and
width all come from the text layer; the buffer does no UTF-16 arithmetic.

### Writes at the edges

The screen never shows half a glyph:

- A write that would start in the middle of an existing wide cell replaces
  that wide cell with a space before placing the new cluster.
- A write that would leave half of a new wide cluster at the right edge
  writes a space instead of the wide cluster.
- A narrow write over a wide lead clears the continuation; a wide write over
  an existing wide pair clears both old cells first.

### Diffs and rendering

`find-dirty-cells` compares cells by value and returns row-major coordinates.
`find-dirty-regions` consumes that sequence in one linear pass, merging
horizontally adjacent columns without grouping, sorting, or searching the row
for each cell. Arbitrarily ordered direct input remains correct but may produce
more regions.

Rendering emits, per region, a cursor-position sequence followed by the
region's clusters rendered through `limner.text/to-ansi`. That function
already emits only the style transitions that change and returns the SGR
state to default at the end, so no cell stores an escape string and no
region leaks style into the next. A continuation cell contributes no output.

### Public surface

`create-buffer`, `get-cell`, `set-cell`, `write-string-to-buffer`,
`write-lines-to-buffer`, `find-dirty-cells`, `find-dirty-regions`,
`apply-diff`, `render-frame`, `force-render` and `buffer-stats` keep their
names. `cell` now takes a cluster and a style map rather than a character and
an accumulated ANSI string; `parse-line-with-ansi` is replaced by
`limner.text/clusters`. `limner.render` is experimental, so these changes are
recorded in `CHANGELOG.md` as breaking.

### Testing through a virtual terminal

`test/limner/vt.clj` is a test-only virtual terminal. It consumes the byte
stream the renderer emits: CUP cursor positioning, SGR through
`limner.ansi.sgr`, graphemes with widths from `limner.text`, and ED/EL
clears. It maintains a screen grid of `{:cluster :style}`. Golden tests
render frames, feed the bytes through the virtual terminal, and compare the
grid, so they are robust to byte-level formatting. A few direct byte tests
pin exact output. Property tests assert that for any frame sequence the
diffs produce the same grid as a full redraw of the last frame, and that no
cell holds half a wide cluster.

## Consequences

The buffer is pure data, so it behaves identically on the JVM and babashka,
and cells can be inspected and compared without a terminal. Rendering does
more per-frame work than the old string concatenation because it re-renders
each changed region's style transitions, but the regions are small and the
output is correct; the old path emitted a full reset per region and could
still corrupt an enclosing style.

Dropping `parse-line-with-ansi` removes a public helper. It was an
experimental-namespace internal and its behaviour (surrogate halves,
accumulated escapes) is the defect being fixed.

## Alternatives considered

- Keeping the ANSI-string cell and resetting it on write was rejected: it
  cannot represent a wide cell or a continuation without extra state, and it
  keeps the diff comparing escape spellings.
- Representing a wide cluster as two identical cells was rejected because
  rendering either half alone would emit a partial glyph.
- Storing the raw SGR string alongside the style map was rejected because it
  recreates the two-sources-of-truth bug in ADR 0003.
- Moving the buffer out of `limner.render` into a new namespace was rejected
  for now: the namespace is experimental, bead 2.3 rewrites the loop, and a
  move would widen the blast radius of this bead.

## References

- ADR 0002 (portable grapheme segmentation) and ADR 0003 (styled text and
  column operations).
- `docs/adr/0004-error-policy.md` for the buffer's invalid-input classes.
- REVIEW B3, B4 and PLAN item 2.2 in `reviews/2026-09-15/`.
- REVIEW probes 6 and 6c in `reviews/2026-09-15/evidence/probe.clj`.
