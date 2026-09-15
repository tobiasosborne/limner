# limner

limner is a Clojure and babashka library for composing terminal output with
ANSI colours, borders, constraint-based layouts, and stateful components whose
renderers return strings or lines. Its name comes from the medieval *limners*
who illuminated manuscripts with borders, colour, and careful lettering.

## Status

limner is pre-release software in the 0.x series. The API may change before
1.0, and the library supports both the JVM and babashka.

Unicode width handling is being rebuilt from pinned Unicode data; follow
[issue #2](https://github.com/tobiasosborne/limner/issues/2) for that work.
Until the shared text layer is replaced, keep these limitations in mind:

- Styles do not nest correctly yet: an inner colour or style can reset an
  outer one.
- Components can misalign or clip text containing CJK characters, emoji, or
  existing ANSI colours.
- Width calculation recognises SGR colour sequences but not every terminal
  control sequence, including OSC hyperlinks.

The core surface is the colour, border, layout, terminal, panel, list, input,
progress, and status-bar namespaces. These namespaces are experimental while
their foundations are reworked: `limner.render`, `limner.events`,
`limner.state`, `limner.streaming`, `limner.syntax`, and
`limner.components.markdown`. The top-level `limner` namespace is deprecated;
require the focused namespaces instead.

## Installation

Once published, the `v0.1.0` tag will mark the legacy baseline at this
revision. Until then, this coordinate pins that commit by its full SHA; replace
the SHA when you want a different revision.

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
component examples deliberately use ASCII content while Unicode width work is
in progress.

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
directly to let a terminal display the word in green.

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
| `limner.core` | ANSI colours and current display-width helpers | Core |
| `limner.borders` | Boxes, titled boxes, borders, and shadows | Core |
| `limner.layout` | Box geometry and stack, split, grid, and overlay layouts | Core |
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
