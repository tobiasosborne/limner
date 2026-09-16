# Styled text and terminal columns

`limner.text` is the shared representation for styled terminal content. Its
semantics are defined by [ADR 0003](../adr/0003-styled-text-model.md), using
Unicode 17 width and grapheme rules from ADRs 0001 and 0002 and control syntax
from `limner.ansi`.

## Values and styling

A value is a canonical vector of display spans and opaque zero-width controls:

```clojure
(require '[limner.text :as text])

(text/concat (text/styled "R" {:fg :red}) " plain")
;; => [{:text "R", :style {:fg :red}}
;;     {:text " plain", :style {}}]
```

Adjacent text spans with equal styles merge. Supported keys are `:fg`, `:bg`,
`:bold`, `:dim`, `:italic`, `:underline`, `:blink`, `:inverse`, `:hidden`,
`:strike`, and `:link`. Colours are named 16-colour keywords, `{:index n}`, or
`{:rgb [r g b]}`. Underline accepts `:single`, `:double`, or `true`; blink
accepts `:slow` or `true`; boolean attributes use `true`.

- `(styled string style-map)` parses any ANSI already in the string and fills
  absent style keys.
- `(concat & values)` joins ANSI strings or styled values.
- `(style style-map value)` fills keys absent from each inner text span. This
  is why an outer bold style remains active after an inner red span.
- `(from-ansi string)` parses SGR and OSC 8 into styles and preserves other
  controls as `{:raw string}`.
- `(to-ansi value)` emits only changed SGR/OSC 8 state. It closes an active
  link and emits one final reset only when necessary.

For canonical values, parsing rendered output is a semantic round trip:
`(= value (from-ansi (to-ansi value)))`.

## Column operations

All operations accept ANSI strings or styled values. An optional final map on
measuring operations supports `{:ambiguous-width 1}` (the default) or
`{:ambiguous-width 2}`.

- `(width value)` and `(width value options)` return terminal columns.
- `(clusters value)` and `(clusters value options)` return one
  `{:cluster :style :width :raw}` map per grapheme unit. This is the low-level
  seam used by column-aware display buffers; opaque controls remain in `:raw`
  and do not contribute width.
- `(slice-columns value start end)` uses half-open bounds and never splits a
  grapheme. Covering one column of a two-column cluster returns one space with
  that cluster's style.
- `(pad-right value target fill)`, `(pad-left ...)`, and `(center ...)` pad to
  the target with exactly one positive-width fill cluster. Fill defaults to a
  space, and each function also has a four-argument form with width options.
- `(truncate value target :ellipsis value :ambiguous-width n)` counts the
  ellipsis in the target. The default is `…`; if even the ellipsis is too wide,
  it is safely sliced to fit.
- `(wrap value target)` word-wraps whitespace-delimited runs, hard-breaks long
  words at grapheme boundaries, and preserves explicit LF/CRLF line breaks.
  The optional third argument is the width-options map.
- `(lines value)` returns styled lines, preserving leading, internal, and
  trailing empty lines. Style carried across a newline appears on both lines.

Invalid values, options, bounds, targets, fill clusters, styles, and spans
throw `ex-info` with the corresponding `:limner.text/*` or
`:limner.text.style/*` class listed in ADR 0004.

Opaque controls and invisible clusters have width zero. Column operations
preserve opaque spans but do not simulate cursor movement or tab stops.
The measured two-runtime corpus and regression budgets are in
[`bench/BUDGETS.md`](../../bench/BUDGETS.md).

## References

- `docs/adr/0001-terminal-width-semantics.md`
- `docs/adr/0002-grapheme-segmentation.md`
- `docs/adr/0003-styled-text-model.md`
- `reference/specs/ecma-48-5th-edition.pdf`
- `reference/specs/xterm-ctlseqs.txt`
