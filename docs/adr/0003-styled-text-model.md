# ADR 0003: Styled text and column operations

## Context

Terminal text has three distinct concerns that cannot be handled safely by
string length or by wrapping strings in an opening SGR sequence and `ESC[0m`:
Unicode grapheme boundaries, terminal column width, and style state. REVIEW
B1, B3, and B4 show the resulting nesting, slicing, and accumulated-state
defects. PLAN D6 selects a styled-text value as their common foundation.

ADR 0001 defines terminal width against Unicode 17.0.0. ADR 0002 defines
portable extended-grapheme segmentation. `limner.ansi` implements the
ECMA-48 tokenizer and structured SGR and OSC 8 interpretation used here.

## Decision

A styled-text value is a vector containing two span forms:

- `{:text "..." :style {...}}` contains display text and its complete style.
- `{:raw "..."}` contains an ECMA-48 control that has no representation in
  the style map.

The canonical form has no empty text or raw spans and merges adjacent text
spans whose style maps are equal. Raw spans are opaque boundaries, so text
spans on opposite sides are not adjacent for canonicalisation.

The supported style keys are `:fg`, `:bg`, `:bold`, `:dim`, `:italic`,
`:underline`, `:blink`, `:inverse`, `:hidden`, `:strike`, and `:link`. A key's
absence means terminal default; there is no second source of style state.
Foreground and background values are one of the sixteen named colour
keywords, `{:index n}` for 0..255, or `{:rgb [r g b]}` with byte components.
Boolean attributes use `true`. Underline is `:single` or `:double` (input
`true` canonicalises to `:single`), and blink is `:slow` (input `true`
canonicalises to `:slow`). A link value is a non-empty URI string.

`from-ansi` tokenises a string once. It applies every SGR operation represented
by the style map and treats OSC 8 open/close tokens as changes to `:link`.
Unknown SGR operations and every other control token become raw spans. OSC 8
parameters are deliberately not retained: the model represents its URI, and
rendering uses an empty parameter field. This canonicalises equivalent input
instead of preserving its escape spelling.

`to-ansi` emits a transition only for style keys that change. Attribute-off
codes and colour defaults are used when possible; intensity changes account
for SGR 22 clearing both bold and dim. Hyperlinks close and reopen only when
the URI changes. Raw spans pass through unchanged. After the last text span it
closes an active link and emits one `ESC[0m` only when an SGR attribute remains
active. There is no blanket reset between spans. Consequently
`(from-ansi (to-ansi t))` equals canonical `t`.

The public constructors are:

- `styled`, which applies a style map to a string after parsing any ANSI it
  contains;
- `concat`, which joins strings or styled-text values and canonicalises them;
- `style`, which applies an outer style to existing text. Inner keys win, so
  the outer map fills only absent keys.

This fill-only rule makes nesting compositional. In particular, applying bold
to red `"R"` followed by unstyled `" after"` leaves the first span red and
makes both spans bold.

## Column operations

Every operation accepts either styled text or an ANSI string. Operations that
measure text accept an options map whose `:ambiguous-width` value has the ADR
0001 meaning and defaults to 1. Unsupported options fail with `ex-info`.

`width` segments all display text as one stream, even where style spans meet,
and sums `cluster-width`; raw spans have width zero. `slice-columns` uses
half-open column bounds. It never splits a grapheme. When a width-two cluster
is only partly covered, the result contains spaces equal to the covered
columns, using the style of the cluster's first text fragment. This preserves
the requested column geometry without emitting half a glyph.

Raw spans and zero-width clusters are anchored at a column boundary. A raw
span before a display cluster travels with that following cluster; trailing
raw spans and standalone zero-width clusters travel with the interval on their
left, while column-zero anchors belong to a slice starting at zero. This makes
a full-width slice lossless and avoids duplicating opaque controls when
adjacent boundary-aligned slices are concatenated. Raw spans inside a partly
covered wide cluster are retained in source order and ignored for width.

`pad-right`, `pad-left`, and `center` add repetitions of one positive-width
fill cluster; the default fill is a space. If the remaining width cuts a wide
fill, the same partial-cluster space rule applies. Centering puts an odd extra
column on the right. Text already at or beyond the target is unchanged.

`truncate` includes its ellipsis in the target width. The default ellipsis is
`"…"`; callers may supply styled text. If the target is smaller than the
ellipsis, the ellipsis itself is sliced to the target using the same safe
column rule. Text whose width already fits is unchanged.

`wrap` preserves explicit LF line breaks, wraps before whitespace clusters,
drops boundary whitespace, and hard-breaks a word wider than the requested
positive width. Runs of whitespace inside a line remain display content when
they fit. `lines` splits on LF while retaining empty leading, internal, and
trailing lines. Because style belongs to every text fragment rather than to
an output cursor, a style active across LF is present on both resulting lines.
CRLF is treated as a single line break and a lone CR remains ordinary
zero-width structural text, consistent with the ECMA-48 scanner and ADR 0001.

## Compatibility surface

`limner.core/visible-width` delegates to `limner.text/width` and therefore
adopts ADR 0001 exactly. `visible-length` remains deprecated and continues to
strip controls and count Unicode code points rather than UTF-16 code units;
this preserves its character-count purpose without retaining surrogate-pair
miscounting. `limner.core/color` still returns an ANSI string. It parses its
input, applies the requested style through the fill-only rule, and renders the
result, so nested calls restore the outer style after an inner span.

## Consequences

Styled values are pure data and behave identically on the JVM and babashka.
Column operations allocate canonical span vectors, while width segments and
measures plain text in one pass without cluster strings. Arbitrary cursor
movement remains opaque:
preserving such controls avoids information loss, but column operations do not
attempt to simulate terminal cursor state.

String rendering is semantic rather than byte-preserving. Equivalent SGR
spellings, reset placement, colon colour syntax, and OSC 8 parameters may be
canonicalised. Callers requiring byte identity retain the original string;
callers using styled text get stable semantic round trips.

## Alternatives considered

- Dropping non-style controls was rejected because parsing would destroy
  information and prevent callers from carrying commands through formatting.
- Storing opening and closing SGR strings on every span was rejected because
  it recreates reset-clobbered nesting and accumulated escape history.
- Treating style-span boundaries as grapheme boundaries was rejected because
  ANSI can legally change style between a base and combining character.
- Emitting `ESC[0m` between every span was rejected because it clears enclosing
  attributes and produces redundant output.
- Returning a partial wide glyph from slicing was rejected because terminals
  cannot display half a grapheme and the result would exceed or undershoot the
  requested geometry unpredictably.

## References

- `docs/adr/0001-terminal-width-semantics.md`.
- `docs/adr/0002-grapheme-segmentation.md`.
- `reference/specs/ecma-48-5th-edition.pdf`, especially SGR in Section
  8.3.117.
- `reference/specs/xterm-ctlseqs.txt`, character attributes and OSC 8.
- `reference/specs/uax29-47.html` and
  `reference/unicode/17.0.0/ucd/auxiliary/GraphemeBreakTest.txt`.
- REVIEW B1, B3, B4 and PLAN D6 in `reviews/2026-09-15/`.
