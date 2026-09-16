# ADR 0004: Fail-loud error policy

## Context

REVIEW D1 records 41 `catch Exception` blocks that print a warning to
`*err*` and return an invented value: a `"+-+"` box, an empty layout, width
`0`, an uncoloured string, a `nil` key. D2 records the inconsistency this
creates with `:pre` conditions, whose `AssertionError` is an `Error` and so
escapes `catch Exception` entirely: an invalid layout sometimes returns `[]`
and sometimes throws `AssertionError`. D3 records invalid colour specs,
border styles and spinner styles becoming silent defaults or `nil` frames.
CLAUDE.md Rule 1 requires the opposite: fail fast, fail loud, and never print
into a caller's terminal.

This ADR is the policy. PLAN item 2.1 applies it across `src/`. The render
loop's opt-in `:on-error` boundary is specified here but implemented by bead
`limner-2-3`; the async event-processor boundary belongs to `limner-2-5`.

## Decision

### Library code never swallows exceptions

Library code does not catch an exception to print a warning and return a
fallback value. It does not write to `*out*` or `*err*` as a way of
reporting an error. It does not return a fabricated value (a placeholder
box, an empty layout, a zero width, an unstyled string) in place of the
result the caller asked for.

### Invalid input throws `ex-info`

Every public function that is given an invalid argument throws
`clojure.lang.ExceptionInfo` (via `ex-info`). The message is a complete,
human-readable sentence. The data map contains:

- `:limner/error`, a namespaced keyword naming the error class;
- the offending value or values, under a key that names them;
- where the fix is not obvious, `:limner/hint`, a string saying how to
  supply valid input.

`:limner/error` is namespaced by the throwing namespace, for example
`:limner.layout/invalid-constraint`. The classes are listed below.

### `:pre` and `assert` are not argument validation

Public functions do not validate arguments with `:pre` conditions or
`assert`. `AssertionError` extends `Error`, so `catch Exception` does not
catch it; assertions are also disabled when `*assert*` is false, so the
validation would silently disappear in production builds. Argument
validation is explicit code that throws `ex-info`. Internal invariant
checks may keep `assert`; they are not part of a public contract.

### The one error boundary

The render loop's opt-in `:on-error` is the single error boundary the
library defines. When `render-fn` throws:

- the loop keeps its last good frame (it does not swap in a partial or
  fabricated frame);
- the loop calls `:on-error` with the exception, once per failing frame;
- the loop does not print anything to `*out*` or `*err*`;
- if `:on-error` itself throws, that error is recorded in the loop stats and
  the loop continues, so a broken callback cannot kill the render loop.

The async event processor is a second, experimental boundary. Until bead
`limner-2-5` replaces it, it routes handler errors and timeouts to its
`:on-error` and `:on-timeout` callbacks and records them in its stats; it
does not print. Bead `limner-2-5` owns its final shape.

### Capability fallbacks are not errors

Selecting `:ascii` borders on a terminal without Unicode is a documented
capability selection, not a silent fallback. It is allowed. The terminal
size default of 80 by 24 is likewise a documented capability default: when
no size source can answer, the renderer uses the conventional size. These
choices are reported through the capability map, not thrown.

Parsing untrusted terminal input is also not an error path. `parse-key`
returns a `{:type :unknown ...}` map for bytes it does not recognise; that is
its documented contract, and it does not need to throw. It must not print.

### Unknown options and keywords throw

An enumerated value that the library does not know is an error. The
following throw a `:limner/error` exception rather than defaulting:

- invalid colour specifications in `limner.core/color`;
- unknown border styles in `limner.borders`;
- unknown `:title-pos` values in `limner.borders/draw-titled-box`;
- unknown spinner styles, or invalid custom frames, in
  `limner.components.progress/spinner`;
- unknown layout `:type` values in `limner.layout/layout`;
- invalid constraint types in `limner.layout/validate-constraint`.

Unknown keys in a keyword-argument or options map do **not** throw. Clojure
maps are open by convention, callers routinely thread extra data through
option maps, and rejecting an unknown key would break that convention while
catching very few real mistakes. Enumerated *values* are closed; option
*keys* are open. The one exception is the small, fixed option set in
`limner.text` and `limner.unicode.width`, where an unsupported key means the
caller believes an option is in effect that is not, so those throw.

## Error classes

| `:limner/error` | Thrown by | Trigger |
|---|---|---|
| `:limner.ansi/invalid-input` | `limner.ansi/tokenize`, `strip`, `display-text?` | input is not a string |
| `:limner.ansi.sgr/invalid-token` | `limner.ansi.sgr/sgr-params` | token is not a CSI sequence with final `m` |
| `:limner.core/invalid-rgb` | `limner.core/rgb`, `bg-rgb` | component is not an integer 0..255 |
| `:limner.core/invalid-color-index` | `limner.core/color-256`, `bg-256` | index is not an integer 0..255 |
| `:limner.core/invalid-color` | `limner.core/color` | spec is not a known colour |
| `:limner.core/invalid-string` | `limner.core/color` | text is not a string |
| `:limner.components.progress/invalid-spinner-style` | `limner.components.progress/spinner` | style is unknown, or `:custom` frames are missing, empty, or not strings |
| `:limner.text/invalid-input` | `limner.text` column operations | input is not a string or styled-text vector |
| `:limner.text/invalid-options` | `limner.text` width operations | options are not a map, contain an unsupported key, or set `:ambiguous-width` to something other than 1 or 2 |
| `:limner.text/invalid-bounds` | `limner.text/slice-columns` | bounds are not ordered non-negative integers |
| `:limner.text/invalid-target` | `limner.text` pad/truncate | target is not a non-negative integer |
| `:limner.text/invalid-fill` | `limner.text` pad/centre; `limner.borders` shadows | fill is not exactly one positive-width grapheme |
| `:limner.text/invalid-width` | `limner.text/wrap` | limit is not a positive integer |
| `:limner.text.style/invalid-color` | `limner.text.style/normalize-style` | a `:fg`/`:bg` value is not a supported colour |
| `:limner.text.style/invalid-style` | `limner.text.style/normalize-style` | style is not a map, has an unsupported key, or has the wrong value type |
| `:limner.text.style/invalid-span` | `limner.text.style/append-span` | a span is neither `:text` nor `:raw`, or holds a non-string |
| `:limner.text.style/invalid-input` | `limner.text.style` entry points | input is not a string or span vector |
| `:limner.unicode/invalid-code-point` | `limner.unicode` | code point is not an integer 0..0x10FFFF |
| `:limner.unicode.grapheme/invalid-string` | `limner.unicode.grapheme/cluster-boundaries` | input is not a string |
| `:limner.unicode.grapheme/invalid-reduce-fn` | `limner.unicode.grapheme/reduce-clusters` | reducing function is missing |
| `:limner.unicode.width/invalid-code-point` | `limner.unicode.width/code-point-width` | code point is invalid or a surrogate |
| `:limner.unicode.width/invalid-options` | `limner.unicode.width` | options are not a map, contain an unsupported key, or set `:ambiguous-width` to something other than 1 or 2 |
| `:limner.unicode.width/invalid-cluster` | `limner.unicode.width/cluster-width` | cluster is not a non-empty string |
| `:limner.unicode.width/invalid-string` | `limner.unicode.width/string-width` | input is not a string |
| `:limner.unicode.width.string/surrogate` | `limner.unicode.width.string` | input contains an unpaired surrogate |
| `:limner.borders/invalid-lines` | `limner.borders/validate-lines` | input is not a collection, or holds a non-string |
| `:limner.borders/invalid-border-style` | `limner.borders/custom-style`, `get-border-chars` | style is neither a keyword nor a complete six-one-column-string vector/map |
| `:limner.borders/unknown-border-style` | `limner.borders/get-border-chars` | keyword style is not in `border-styles` |
| `:limner.borders/invalid-title` | `limner.borders/draw-titled-box` | title is not a string |
| `:limner.borders/invalid-title-pos` | `limner.borders/draw-titled-box` | position is not `:left`, `:center`, or `:right` |
| `:limner.borders/invalid-dimension` | `limner.borders/indent-lines`, `nest-box`, `side-by-side` | a size is not an integer |
| `:limner.table/invalid-rows` | `limner.table/table` | `rows` is not a sequential collection |
| `:limner.table/empty-rows` | `limner.table/table` | `rows` is empty |
| `:limner.table/invalid-row` | `limner.table/table` | a row is not a sequential collection of cells |
| `:limner.table/ragged-rows` | `limner.table/table` | a row's cell count differs from the first row's |
| `:limner.table/invalid-alignment` | `limner.table/table` | `:align` is unknown or has the wrong arity |
| `:limner.table/invalid-padding` | `limner.table/table` | `:padding` is not a non-negative integer |
| `:limner.components.list/invalid-width` | `limner.components.list/render-list` | content columns cannot fit the item prefix or filter prompt |
| `:limner.components.input/invalid-width` | `limner.components.input/render` | width is not a non-negative integer, or a bordered input is narrower than four columns |
| `:limner.components.panel/invalid-width` | `limner.components.panel/render` | `:width` is not an integer or leaves no room for the border |
| `:limner.layout/invalid-constraint` | `limner.layout/validate-constraint` | constraint is not a map, or its `:type` is unknown |
| `:limner.layout/invalid-dimension` | `limner.layout` | a dimension is not a non-negative integer |
| `:limner.layout/invalid-spacing` | `limner.layout` | spacing is not a non-negative number |
| `:limner.layout/unknown-layout-type` | `limner.layout/layout` | `:type` is not stack, hsplit, grid, or overlay |
| `:limner.layout/empty-components` | `limner.layout/stack`, `hsplit`, `grid` | component collection is empty |
| `:limner.layout/invalid-grid` | `limner.layout/grid` | `:columns`/`:rows` missing or not positive integers |
| `:limner.render/invalid-string` | `limner.render/write-string-to-buffer`, `cell` | content is not a string |
| `:limner.render/invalid-cell` | `limner.render/cell`, `set-cell` | a cell is not exactly one positive-width plain grapheme with canonical width |
| `:limner.render/invalid-lines` | `limner.render/write-lines-to-buffer`, `render-frame`, `force-render` | lines are not sequential, or a line is not a string/styled value |
| `:limner.render/invalid-render-output` | `limner.render/validate-render-output` | `render-fn` did not return a sequence of strings |
| `:limner.render/invalid-callback` | `limner.render/create-render-loop` | `:render-fn`, `:on-frame`, or `:on-error` is not a function |
| `:limner.render/invalid-dimension` | `limner.render/create-buffer` | a buffer dimension is not a non-negative integer |
| `:limner.events/invalid-argument` | `limner.events/create-async-event-processor`, `create-async-event-system` | a required option is missing or not a function |

## Consequences

Callers that relied on a fallback must now handle the exception or fix
their input. That is the point: a misaligned or missing frame is worse than
a stack trace, and a library that prints into a running TUI corrupts the
display it is meant to draw. `CHANGELOG.md` lists every function whose
error behaviour changed.

The error classes above are also the contract the quality gates enforce:
`test/limner/error_policy_test.clj` asserts the class and the presence of
the offending values, and `test/limner/error_gates_test.clj` scans `src/`
for stray printing or stray catches.

## Alternatives considered

- Catching, logging through a configurable logger and returning a fallback
  was rejected: it still hides a bug behind a plausible-looking value, and
  it adds a logging dependency to a library that should stay pure.
- Throwing on every unknown keyword-argument key was rejected: it breaks
  Clojure's open-map convention and would reject harmless extra keys.
- Using `:pre` for validation was rejected because `AssertionError` is not
  an `Exception` and because `*assert*` can disable it.

## References

- CLAUDE.md Rules 1, 2 and 4.
- REVIEW D1, D2, D3 in `reviews/2026-09-15/REVIEW.md`.
- PLAN item 2.1 and decisions D1–D3.
- ADR 0003 (`limner.text`, `limner.text.*`) for the text-layer error classes.
