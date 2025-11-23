# Limner

> *A Clojure/Babashka TUI library for crafting beautiful terminal interfaces*

**Limner** (from Middle English, "one who illuminates manuscripts") is a modular terminal user interface library for Clojure and Babashka. Like the medieval artisans who carefully illuminated manuscripts with intricate borders and elegant lettering, Limner helps you craft sophisticated terminal applications with composable, beautifully-rendered components.

Inspired by [Ink](https://github.com/vadimdemedes/ink) for React, Limner brings a declarative, component-based approach to terminal UI development in the Clojure ecosystem.

## Features

### Core Capabilities
- **Pure Clojure** - Works with both Clojure and Babashka
- **Modular Architecture** - Composable components with clean separation of concerns
- **ANSI Terminal Control** - Full-featured terminal manipulation with colors, cursor control, and styling
- **Layout Engine** - Flexible box model with margins, padding, and borders
- **Zero Dependencies** - Lightweight with minimal external requirements

### Rich Components

#### Panels & Borders
- Multiple border styles (single, double, rounded, thick)
- Titled panels with configurable title positioning
- Collapsible and scrollable content areas
- Nested panel composition
- Shadow effects

#### Text Input
- Single-line and multi-line text fields
- Cursor navigation and editing
- Input validation and masking
- Command history navigation

#### Lists & Selection
- Keyboard-navigable lists (arrows, j/k vim bindings)
- Multi-select with checkboxes
- Built-in search and filtering
- Custom item rendering

#### Markdown Rendering
- Headers, bold, italic, code spans
- Code blocks with syntax highlighting
- Lists (ordered and unordered)
- Blockquotes and links
- ANSI-styled output

#### Progress Indicators
- 10+ spinner animation styles (dots, line, arrow, circle, etc.)
- Determinate progress bars (0-100%)
- Indeterminate progress bars (animated)
- Step indicators with optional labels
- Pulse/breathing text effects
- Custom animation frames

### Layout System
- Vertical stacking
- Horizontal splits
- Constraint-based sizing (fixed, percentage, auto)
- Scrollable regions with automatic clipping
- Z-index layering for overlays

## Installation

Add Limner to your `deps.edn`:

```clojure
{:deps {limner {:git/url "https://github.com/yourusername/limner"
                :sha "..."}}}
```

Or for Babashka projects, use `bb.edn`:

```clojure
{:deps {limner {:git/url "https://github.com/yourusername/limner"
                :sha "..."}}}
```

## Quick Start

### Simple Panel

```clojure
(require '[limner.components.panel :as panel])

(def my-panel
  (panel/panel
    :title "Hello Limner"
    :content "Welcome to beautiful terminal UIs!"
    :border-style :rounded))

(println (panel/render-to-string my-panel))
```

### Progress Bar

```clojure
(require '[limner.components.progress :as progress])

;; Determinate progress
(def bar (progress/progress-bar :value 65 :width 40))
(println (progress/render bar))
;; => [████████████████████░░░░░░░░░░░░░░░░░░░░]  65%

;; Animated spinner
(def spinner (progress/spinner :style :dots))
(loop [s spinner]
  (print "\r" (progress/render s))
  (flush)
  (Thread/sleep 100)
  (recur (progress/tick s)))
```

### Interactive List

```clojure
(require '[limner.components.list :as list])

(def items ["Option 1" "Option 2" "Option 3"])
(def my-list
  (list/list-component
    :items items
    :selected 0
    :multi-select false))

(println (list/render-to-string my-list))
```

### Markdown Rendering

```clojure
(require '[limner.components.markdown :as md])

(def markdown-text "
# Welcome

This is **bold** and this is *italic*.

```clojure
(println \"Code blocks too!\")
```
")

(def rendered (md/render markdown-text))
(println rendered)
```

### Composed Layout

```clojure
(require '[limner.components.panel :as panel]
         '[limner.components.input :as input]
         '[limner.components.markdown :as md])

;; Create a chat-like interface
(def chat-panel
  (panel/panel
    :title "Chat"
    :content (md/render "**Assistant:** How can I help you?")
    :border-style :double
    :height 20
    :scrollable true))

(def input-field
  (panel/panel
    :title "Your message"
    :content "> Type here..."
    :border-style :single))

;; Nest panels
(def app
  (panel/nest-panels
    (panel/panel :title "Limner Chat" :border-style :rounded)
    [chat-panel input-field]
    :spacing 1))

(println (panel/render-to-string app))
```

## Architecture

Limner follows a modular architecture with clear separation of concerns:

```
limner/
├── core.clj              - Terminal control & ANSI codes
├── layout.clj            - Box model & layout engine
├── borders.clj           - Border rendering
└── components/
    ├── panel.clj         - Content containers
    ├── input.clj         - Text input fields
    ├── list.clj          - Selectable lists
    ├── markdown.clj      - Markdown renderer
    ├── progress.clj      - Progress indicators
    └── ...
```

Each module is:
- **Self-contained** - Minimal dependencies between modules
- **Pure** - Immutable data structures, no side effects in rendering
- **Composable** - Components nest and combine naturally
- **Testable** - Comprehensive test coverage

## Examples

Run the included demos to see Limner in action:

```bash
# Progress indicators
bb examples/progress_demo.clj

# Panel layouts
bb examples/panel_demo.clj

# Markdown rendering
bb examples/markdown_demo.clj

# Interactive lists
bb examples/list_demo.clj

# Text input
bb examples/input_demo.clj
```

## Development Status

Limner is under active development. Completed modules:

- ✅ Core (Terminal & ANSI)
- ✅ Layout Engine
- ✅ Borders
- ✅ Panel Component
- ✅ Input Component
- ✅ List Component
- ✅ Markdown Renderer
- ✅ Progress Indicators

Planned modules:

- ⏳ Status Bar
- ⏳ Syntax Highlighting (expansion)
- ⏳ Streaming Text
- ⏳ Event System
- ⏳ Render Loop
- ⏳ State Management

See [plan.md](plan.md) for the complete roadmap.

## Testing

Run all tests:

```bash
bb test
```

Or run specific test suites:

```bash
bb -cp "src:test" -e "(require '[limner.components.progress-test]) (clojure.test/run-tests 'limner.components.progress-test)"
```

## Design Philosophy

Limner embraces the Arts and Crafts movement's principles:

1. **Craftsmanship** - Every component is carefully designed and implemented
2. **Functionality** - Beauty through utility, not decoration for its own sake
3. **Modularity** - Components that work independently yet harmonize together
4. **Simplicity** - Clean, readable code that does one thing well
5. **Quality** - Comprehensive tests and attention to detail

Like medieval manuscript illuminators, we believe that even terminal output can be a work of art.

## Contributing

Contributions are welcome! Please:

1. Read [plan.md](plan.md) to understand the architecture
2. Write tests for new features
3. Keep code modular and well-documented
4. Follow the existing code style
5. Submit a pull request

## License

[Choose your license - MIT, EPL, etc.]

## Acknowledgments

- Inspired by [Ink](https://github.com/vadimdemedes/ink) for React
- Named after the medieval artisans who illuminated manuscripts
- Built for the Clojure and Babashka communities

---

*Crafted with care, one character at a time.* ✒️
