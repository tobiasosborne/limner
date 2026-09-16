# limner

limner is a Clojure and babashka library for composing terminal output with
ANSI colours, borders, constraint-based layouts, and stateful components whose
renderers return strings or lines. Its name comes from the medieval *limners*
who illuminated manuscripts with borders, colour, and careful lettering.

## Status

limner is pre-release software in the 0.x series. The API may change before
1.0, and the library supports both the JVM and babashka.

Unicode width handling is grounded in pinned Unicode 17.0.0 data; follow
[issue #2](https://github.com/tobiasosborne/limner/issues/2) for its measured
conformance work. [`limner.text`](docs/api/text.md) provides styled spans,
portable grapheme segmentation, exact terminal width, safe slicing, padding,
truncation, and wrapping. [`limner.ansi`](docs/api/ansi.md) is the shared,
lossless ECMA-48 tokenizer beneath it.

The core rendering surface routes all user-visible text through that layer:
`limner.borders`, `limner.layout`, `limner.table`,
`limner.components.panel`, `limner.components.list`,
`limner.components.input`, `limner.components.progress`, and
`limner.components.statusbar` measure, slice, pad, and wrap in display
columns, compose styles as spans, and have exact goldens plus
every-line-width properties for ASCII, CJK, emoji ZWJ, and pre-coloured text.

What remains:

- `limner.syntax`, `limner.components.markdown`, and `limner.streaming` still
  use legacy text handling and are experimental.
- `limner.render` (its render loop performs terminal effects directly),
  `limner.events`, and `limner.state` remain experimental; the REVIEW E1–E5
  render-loop, terminal-input, and event-concurrency work is deferred (beads
  2.3–2.5 are out of scope for this project).
- There is no terminal input layer; `limner.tty` is not implemented.
- The top-level `limner` namespace is deprecated; require the focused
  namespaces instead.

The experimental render buffer stores grapheme clusters and style values,
including continuation cells for wide clusters.

## Installation

The `v0.1.0` tag marks the legacy baseline at this revision. The coordinate
below pins that commit by its full SHA; replace the SHA when you want a
different revision.

For a Clojure project, add the dependency to `deps.edn`:

```edn
{:deps
 {io.github.tobiasosborne/limner
  {:git/url "https://github.com/tobiasosborne/limner.git"
   :git/sha "01592b2ede8d9f0b01a0d61f8d9537bd84b81052"}}}
```

Babashka reads git dependencies from the top-level `:deps` map in `bb.edn`.
Merge the same entry into that map:

```edn
{:deps
 {io.github.tobiasosborne/limner
  {:git/url "https://github.com/tobiasosborne/limner.git"
   :git/sha "01592b2ede8d9f0b01a0d61f8d9537bd84b81052"}}}
```

## Quick start

Each example below runs as written on the JVM and on babashka. The box and
component examples deliberately use ASCII content until those components move
to the styled-text column operations.

### A bordered box

```clojure
(require '[limner.borders :as borders])

(doseq [line (borders/draw-box ["Hello, terminal!"]
                               :border-style :rounded)]
  (println line))
```

Output:

```text
╭──────────────────╮
│ Hello, terminal! │
╰──────────────────╯
```

### Colour

`core/color` returns a string containing ANSI escapes. This example makes the
escape byte visible as `<ESC>` so its output is readable here; print `rendered`
directly to let a terminal display the word in green. Nested calls preserve
outer attributes through the styled-text layer.

```clojure
(require '[clojure.string :as str]
         '[limner.core :as core])

(def rendered (core/color :green "Ready"))
(println (str/replace rendered "\u001b" "<ESC>"))
```

Output:

```text
<ESC>[32mReady<ESC>[0m
```

### Progress

The same escape-byte substitution exposes the green section of this
determinate progress bar.

```clojure
(require '[clojure.string :as str]
         '[limner.components.progress :as progress])

(def rendered
  (progress/render
   (progress/progress-bar :value 40
                          :width 20
                          :filled-char "#"
                          :empty-char "-"
                          :color :green)))

(println (str/replace rendered "\u001b" "<ESC>"))
```

Output:

```text
[<ESC>[32m#####<ESC>[0m--------]  40%
```

### Layout calculation

`layout/stack` describes the constraints; `layout/layout-stack` resolves them
for an actual width and height. Each output tuple is `[y height content]`.

```clojure
(require '[limner.layout :as layout])

(def page
  (layout/stack
   [{:constraint (layout/fixed 2) :content :header}
    {:constraint (layout/flex 1) :content :body}
    {:constraint (layout/fixed 1) :content :footer}]
   :spacing 1))

(println
 (mapv (juxt :y :height :content)
       (layout/layout-stack page 40 10)))
```

Output:

```text
[[0 2 :header] [3 5 :body] [9 1 :footer]]
```

### A panel component

Components are maps, and rendering is a separate operation.

```clojure
(require '[limner.components.panel :as panel])

(println
 (panel/render-to-string
  (panel/panel :title "Menu"
               :content ["Open" "Quit"]
               :border-style :ascii)))
```

Output:

```text
+ Menu +
| Open |
| Quit |
+------+
```

## Namespace overview

| Namespace | What it does | Status |
|---|---|---|
| `limner.ansi` | Lossless ECMA-48 tokenization and control stripping | Core |
| `limner.unicode` | Generated Unicode 17 property lookups | Core |
| `limner.unicode.grapheme` | Portable extended grapheme segmentation | Core |
| `limner.core` | ANSI colours and current display-width helpers | Core |
| `limner.borders` | Boxes, titled boxes, borders, and shadows | Core |
| `limner.layout` | Box geometry and stack, split, grid, and overlay layouts | Core |
| `limner.table` | Column-correct bordered tables with per-column alignment | Core |
| `limner.terminal` | Environment-based terminal capability detection | Core |
| `limner.components.panel` | Panel state and rendering | Core |
| `limner.components.list` | Selectable and filterable list state and rendering | Core |
| `limner.components.input` | Editable input state and rendering | Core |
| `limner.components.progress` | Progress bars, spinners, steps, and pulse state | Core |
| `limner.components.statusbar` | Sectioned status-bar rendering | Core |
| `limner.render` | Screen buffers, diffs, and a render-loop experiment | Experimental |
| `limner.events` | Event parsing and dispatch helpers; not a terminal reader | Experimental |
| `limner.state` | Reactive state helpers and render binding | Experimental |
| `limner.streaming` | State and rendering helpers for progressive text | Experimental |
| `limner.syntax` | Regex-based syntax highlighting | Experimental |
| `limner.components.markdown` | Lightweight Markdown parsing and rendering | Experimental |

## Development

Quality gates run locally:

```bash
bb check
clojure -M:test
bb test
```

`bb check` runs both suites in sequence, followed by `clj-kondo` when it is
installed. Contributor and agent rules are in [CLAUDE.md](CLAUDE.md).

Beads tracks repository work: use `bd ready`, claim with
`bd update <id> --claim`, and finish with `bd close <id>`.

## Acknowledgements

- [pmonks](https://github.com/pmonks) reported
  [issue #1](https://github.com/tobiasosborne/limner/issues/1), which led to
  the `deps.edn` fix, and contributed the emoji, grapheme, and `wcwidth`
  analysis in [issue #2](https://github.com/tobiasosborne/limner/issues/2).
- [Ink](https://github.com/vadimdemedes/ink) is an existing inspiration for
  declarative terminal interfaces.
- The name honours the medieval limners who illuminated manuscripts.

## License

MIT License — See [LICENSE](LICENSE) for details.
