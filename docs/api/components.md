# Limner Components API

This page summarizes the component namespaces. There is no aggregate
`limner.components` namespace; require each namespace separately.

## Panel
`limner.components.panel`

A state map and renderer for bordered content.

### `panel`
`(panel & options)`

Creates a panel component.

**Options:**
- `:title`: String title.
- `:title-pos`: `:left`, `:center`, `:right` (default `:left`).
- `:content`: String or vector of strings.
- `:border-style`: Border style keyword (default `:single`).
- `:padding`: Inner padding (default 0).
- `:height`: Maximum number of rendered content lines.
- `:width`: Stored in the state and used by collapsed rendering; expanded
  rendering does not currently enforce it.
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

Editable input state and rendering helpers. The namespace does not read
terminal input or connect itself to the event system. Cursor positions and
editing operations currently use UTF-16 indexes rather than grapheme clusters.

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

Selection movement does not automatically update `:scroll-offset`; call
`adjust-scroll` when the selected item must remain visible.

---

## Markdown
`limner.components.markdown`

An experimental, regex-based Markdown subset that renders ANSI-styled text. It
is not a CommonMark implementation: underscore-delimited italics can match
inside identifiers, lists are flat, and fenced code is not syntax-highlighted.

### `render`
`(render text)` or `(render text options-map)`

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
- `:style`: A key in `spinner-styles`, or `:custom` with a non-empty `:frames`
  sequence.

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

The renderer strips SGR sequences already present in section content and uses
UTF-16 positions rather than terminal columns, so styled or wide Unicode
content is not preserved reliably.
