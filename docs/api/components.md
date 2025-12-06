# Limner Components API

The `limner.components` namespace provides high-level UI widgets ready for use in your applications.

## Panel
`limner.components.panel`

A container component with a border, title, and content area.

### `panel`
`(panel & options)`

Creates a panel component.

**Options:**
- `:title`: String title.
- `:title-pos`: `:left`, `:center`, `:right` (default `:left`).
- `:content`: String or vector of strings.
- `:border-style`: Border style keyword (default `:single`).
- `:padding`: Inner padding (default 0).
- `:width`, `:height`: Fixed dimensions.
- `:scrollable`: Enable scrolling (default `false`).

### `render`
`(render panel-state)`

Renders the panel to a vector of strings.

### `nest-panels`
`(nest-panels parent children)`

Embeds child panels inside a parent panel.

---

## Input
`limner.components.input`

Interactive text input field.

### `input`
`(input & options)`

Creates an input component.

**Options:**
- `:value`: Current text.
- `:cursor`: Cursor position.
- `:width`: Field width.
- `:placeholder`: Text to show when empty.
- `:masked`: Boolean (e.g., for passwords).
- `:multiline`: Enable textarea mode.
- `:validator`: Function `(fn [val] -> bool)`.

### Text Manipulation
- `(insert-char state char)`
- `(delete-char state)`
- `(backspace state)`
- `(move-cursor state pos)`
- `(validate state)`

---

## List
`limner.components.list`

Scrollable, selectable item list.

### `list-component`
`(list-component & options)`

Creates a list component.

**Options:**
- `:items`: Vector of strings or maps `{:label "..." :value ...}`.
- `:selected`: Index of selected item.
- `:height`: Visible height (default 10).
- `:multi-select`: Enable multiple selection.
- `:filter-text`: Filter string.

### Operations
- `(select-next state n)`
- `(select-prev state n)`
- `(set-filter state text)`
- `(toggle-selection state)`

---

## Markdown
`limner.components.markdown`

Renders Markdown text with ANSI styling.

### `render`
`(render text & options)`

Parses and renders Markdown text.

**Supported Syntax:**
- Headers (`#`, `##`, ...)
- Bold (`**text**`), Italic (`*text*`)
- Lists (`- item`, `1. item`)
- Code blocks (````lang ... ````)
- Inline code (`` `code` ``)
- Blockquotes (`> text`)

---

## Progress
`limner.components.progress`

Progress bars, spinners, and activity indicators.

### `progress-bar`
`(progress-bar & options)`

Creates a progress bar.

**Options:**
- `:value`: 0-100.
- `:width`: Character width.
- `:type`: `:determinate` or `:indeterminate`.

### `spinner`
`(spinner & options)`

Creates a loading spinner.

**Options:**
- `:style`: `:dots`, `:line`, `:arrow`, etc.

### `render`
`(render component)`

Renders the progress component to a string.

---

## StatusBar
`limner.components.statusbar`

Application status line with sections.

### `statusbar`
`(statusbar & options)`

Creates a status bar.

**Options:**
- `:left`: Left section content.
- `:center`: Center section content.
- `:right`: Right section content.
- `:bg-color`: Background color keyword.
- `:fg-color`: Text color keyword.

### `render`
`(render statusbar-state)`

Renders the status bar string.
