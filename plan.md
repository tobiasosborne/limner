# Limner TUI - Modular Architecture Plan

## Overview
A clojure/babaschka native modular TUI library with CLI graphics with composable components.

## Module Structure

### 1. `core.clj` - Terminal & ANSI Core -> Completed!
**Responsibility:** Low-level terminal control and ANSI escape sequences
- ANSI color codes, cursor movement, screen clearing
- Terminal size detection (`tput cols`, `tput lines`)
- Cursor show/hide, save/restore position
- Raw mode handling for input capture

**Tests:**
- ANSI code generation correctness
- Visible length calculation with nested ANSI codes
- Color composition and reset behavior

### 2. `layout.clj` - Layout Engine -> Completed!
**Responsibility:** Positioning and sizing components in 2D space
- Box model (margins, padding, borders)
- Layout strategies: vertical stack, horizontal split, grid
- Constraint-based sizing (fixed, percentage, auto)
- Z-index/layer management for overlays

**Tests:**
- Calculate bounds for nested layouts
- Vertical stacking respects constraints
- Horizontal splits distribute space correctly
- Scrollable regions clip content

### 3. `borders.clj` - Border Rendering -> Completed!
**Responsibility:** Drawing boxes with various styles
- Border styles: single, double, rounded, thick, custom
- Corner and edge calculation for nested boxes
- Title rendering in borders (left, center, right aligned)
- Shadow effects

**Tests:**
- Border width calculation with titles
- Multi-line content padding
- Style composition (nested borders)

### 4. `components/panel.clj` - Panel Component -> Completed!
**Responsibility:** Content container with title and border
- Named panels with optional titles
- Sub-panel nesting and composition
- Collapsible/expandable panels
- Scrollable content with scrollbar indicators

**Tests:**
- Panel with title renders correctly
- Nested panels maintain proper spacing
- Scroll offset affects visible content
- Width calculation with syntax-highlighted content

### 5. `components/input.clj` - Text Input Fields -> Completed!
**Responsibility:** Interactive text entry
- Single-line text input with cursor
- Multi-line text area
- Input validation and masking
- Key handling (backspace, delete, arrows, home/end)
- History navigation (up/down arrows)

**Tests:**
- Cursor position after insertions/deletions
- Selection and clipboard operations
- Overflow handling (horizontal scroll)
- Multi-line cursor navigation

### 6. `components/list.clj` - Selectable Lists -> Completed!
**Responsibility:** Scrollable, selectable item lists
- Keyboard navigation (arrow keys, j/k)
- Multi-select with checkboxes
- Search/filter capability
- Custom item rendering

**Tests:**
- Selection wraps at boundaries
- Filtered list preserves selection
- Multi-select toggles correctly
- Scroll viewport follows selection

### 7. `components/markdown.clj` - Markdown Renderer -> Completed!
**Responsibility:** Render markdown to ANSI-styled text
- Headers (size differentiation)
- Bold, italic, code spans, links
- Code blocks with syntax highlighting integration
- Lists (ordered, unordered)
- Blockquotes

**Tests:**
- Nested formatting (bold + italic)
- Code block language detection
- Link text extraction
- List indentation levels

### 8. `components/progress.clj` - Progress Indicators -> Completed!
**Responsibility:** Visual progress feedback
- Spinner animation frames
- Progress bar (percentage, determinate/indeterminate)
- Pulsing/breathing effects
- Step indicators (1/5, 2/5, etc.)

**Tests:**
- Progress bar fills correctly (0-100%)
- Spinner cycles through frames
- Animation timing consistency

**Implementation Notes:**
- Includes 10 predefined spinner styles (dots, line, arrow, circle, box, etc.)
- Progress bars support both determinate (0-100%) and indeterminate (animated) modes
- Step indicators can include optional labels and embedded progress bars
- Pulse effect uses frame-based opacity simulation with ANSI dim codes
- All components use a consistent `tick` function for animation advancement
- Custom spinner frames and progress bar characters fully supported
- Components integrate seamlessly with panels for complex layouts

### 9. `components/statusbar.clj` - Status Line -> Completed!
**Responsibility:** Bottom status bar like Claude Code
- Left/center/right sections
- Git branch, file info, timestamps
- Keybinding hints
- Background coloring

**Tests:**
- Three-section layout distributes width
- Overflow truncates gracefully
- Dynamic updates don't flicker

### 10. `syntax.clj` - Syntax Highlighting -> Completed!
**Responsibility:** Language-aware code coloring (expand existing)
- Pluggable language rules (Clojure, Python, JavaScript, etc.)
- Token-based highlighting (keywords, strings, comments, functions)
- Theme support (multiple color schemes)

**Tests:**
- Clojure: keywords, symbols, strings, comments
- Python: keywords, f-strings, decorators
- JavaScript: template literals, arrow functions
- Nested string escapes

### 11. `streaming.clj` - Streaming Text -> Completed!
**Responsibility:** Character-by-character text display
- Stream text with controlled delay
- Cancel/pause/resume streaming
- Syntax highlighting during streaming
- Cursor blink effect at end

**Tests:**
- Streaming respects delay timing
- Cancel stops at correct position
- Syntax applied incrementally
- No flicker during updates

**Implementation Notes:**
- Component-based architecture with state machine (:pending, :streaming, :paused, :cancelled, :completed)
- Time-based character advancement using `delay-ms` parameter (default 30ms)
- Full pause/resume/cancel/reset controls with state preservation
- Incremental syntax highlighting applies to visible text only
- Cursor blink effect uses time-based frame calculation (configurable blink interval)
- Progress tracking with percentage and remaining character count
- Supports all language syntax highlighters (:clojure, :python, :javascript)
- `tick` function handles both text advancement and cursor animation
- Direct position setting for seeking within stream
- Helper function for instant completion (useful for testing)
- Zero flicker - only renders currently visible portion of text

### 12. `events.clj` - Event System
**Responsibility:** Keyboard/mouse input handling
- Key event parsing (including modifiers)
- Event routing to focused component
- Focus management and tab order
- Keybinding registry

**Tests:**
- Parse complex key combos (Ctrl+Shift+X)
- Focus cycles through interactive elements
- Keybindings dispatch correct handlers
- Mouse click coordinates map to components

### 13. `render.clj` - Render Loop
**Responsibility:** Efficient screen updates
- Diff-based rendering (only update changed cells)
- Double buffering to prevent flicker
- Frame rate control
- Dirty region tracking

**Tests:**
- Only changed regions redrawn
- Full screen clear on demand
- No tearing during rapid updates

### 14. `state.clj` - Application State
**Responsibility:** Component state management
- Atom-based state tree
- State watchers for reactive updates
- Undo/redo history
- State serialization

**Tests:**
- State updates trigger re-render
- Undo/redo maintains history
- Watchers fire on changes only

## Integration Example

```clojure
(ns limner.demo
  (:require [limner.layout :as layout]
            [limner.components.panel :as panel]
            [limner.components.input :as input]
            [limner.components.markdown :as md]
            [limner.components.statusbar :as status]
            [limner.render :as render]))

(def app-state
  {:main-panel {:title "Claude's Response"
                :content (md/render "## Hello\nThis is **bold**.")
                :scroll-offset 0}
   :input {:value "" :cursor 0}
   :status {:left "limner v1.0" :right "Ctrl+C to quit"}})

(def layout
  (layout/stack
    [(panel/panel (:main-panel @app-state) {:flex 1})
     (input/field (:input @app-state) {:height 3})
     (status/bar (:status @app-state))]))

(render/start layout app-state)
```

## Migration Path

1. Extract existing code into `core.clj` and `borders.clj`
2. Refactor panel function into `components/panel.clj`
3. Build layout engine to support composition
4. Implement input and list components
5. Expand markdown and syntax highlighting
6. Add render loop and event system
7. Build demo app showcasing all features

## File Structure

```
limner/
├── src/
│   ├── limner/
│   │   ├── core.clj
│   │   ├── layout.clj
│   │   ├── borders.clj
│   │   ├── syntax.clj
│   │   ├── streaming.clj
│   │   ├── events.clj
│   │   ├── render.clj
│   │   ├── state.clj
│   │   └── components/
│   │       ├── panel.clj
│   │       ├── input.clj
│   │       ├── list.clj
│   │       ├── markdown.clj
│   │       ├── progress.clj
│   │       └── statusbar.clj
├── test/
│   └── limner/
│       ├── core_test.clj
│       ├── layout_test.clj
│       ├── borders_test.clj
│       ├── syntax_test.clj
│       └── components/
│           ├── panel_test.clj
│           ├── input_test.clj
│           └── ...
├── examples/
│   ├── basic_demo.clj
│   ├── claude_clone.clj
│   └── form_example.clj
└── README.md
```

## Known Issues / Future Improvements

### Shadows (borders.clj)
**Issue:** Shadow effects don't render properly - the right-hand side shadow doesn't join cleanly with the bottom shadow, creating a disconnected appearance.

**TODO:** Refactor shadow rendering to:
- Make right edge shadow connect smoothly to bottom shadow
- Consider using corner characters for shadow joins
- Possibly use gradient shadow characters (░ ▒ ▓ █) for depth effect

### Double-line Border Rendering
**Issue:** Double-line borders (╔═╗║║╚═╝) sometimes have broken characters on middle content lines, particularly visible in nested or titled boxes.

**TODO:** Fix double-line border rendering:
- Investigate why middle lines occasionally show incorrect border characters
- Ensure vertical border characters (║) are consistently applied
- Add regression tests for double-line border integrity

### Markdown renderer
**Issue** Strange behaviour on some code rendering, e.g., python

**TODO:** Fix this
- Investigate causes, behaviour

### Progress Indicators (components/progress.clj)
**Enhancement Opportunities:**
- Add more sophisticated pulse effects using full RGB ANSI codes for smoother opacity transitions
- Implement progress bar gradient fills for visual appeal
- Add ETA (estimated time remaining) calculation helpers for progress bars
- Support for multi-line progress displays (e.g., parallel downloads)
- Add sound/notification hooks when progress completes
- Consider adding logarithmic progress for long-running tasks that slow down over time

**Known Issues:**
- Pulse effect opacity simulation is basic - uses only dim ANSI codes rather than full opacity levels
- Indeterminate progress bar animation could be smoother with more frame interpolation
- No built-in rate limiting for tick updates (callers must manage animation frame rate)

## Testing Strategy

- Unit tests for pure functions (rendering, layout calculations)
- Snapshot tests for component output (compare ANSI strings)
- Integration tests with simulated keyboard input
- Visual regression tests (capture screenshots)

## Dependencies

- `babashka.process` - for terminal size detection
- `clojure.test` - testing framework
- Consider: `lanterna` or `clojure-term` for advanced terminal features (optional)
