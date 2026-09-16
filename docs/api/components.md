# Limner Components API

This page summarizes the component namespaces. There is no aggregate
`limner.components` namespace; require each namespace separately.

## Panel
`limner.components.panel`

A state map and renderer for bordered content. When `:width` is set, every
rendered line is exactly `:width` display columns wide, including the border.
Content and the title are measured, truncated, and padded in terminal columns
through `limner.text`, so CJK and emoji content and content that already
carries ANSI keep their true widths and styles. Omitting `:width` auto-sizes
the panel to its widest line.

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
- `:width`: Total rendered width including the border; every line is exactly
  this wide. A width below 4 throws
  `:limner.components.panel/invalid-width`.
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
terminal input or connect itself to the event system. Cursor positions are
grapheme-cluster indexes, so editing never splits an emoji or a combining
sequence. Rendering goes through `limner.text`, and every rendered line is
exactly the configured `:width` including the border, so a coloured
placeholder keeps its colour while the cursor is an inverse-video span.

When the value is empty and a `:placeholder` is set, the cursor sits on the
first placeholder grapheme. A multiline value ending in a newline opens a new
empty row with the cursor at its start; it does not add a stray row.

### `input`
`(input & options)`

Creates an input component.

**Options:**
- `:value`: Current text.
- `:cursor`: Cursor position in grapheme clusters (default at the end).
- `:width`: Total rendered width including the border (default 40).
- `:placeholder`: Text to show when empty.
- `:masked`: Boolean (e.g., for passwords).
- `:multiline`: Enable textarea mode.
- `:max-length`: Maximum value length in grapheme clusters.
- `:validator`: Function `(fn [val] -> bool)`.

### Text Manipulation
- `(insert-char state char)`
- `(delete-char state)`
- `(backspace state)`
- `(move-cursor state pos)`
- `(validate state)`

`history-prev` returns the input unchanged when the history is empty.

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
- `:width`: Total rendered width including the border (default 40).
- `:multi-select`: Enable multiple selection.
- `:filter-text`: Filter string.
- `:show-indices`: Prefix each row with its index.
- `:show-filter`: Draw the filter prompt as the first row.

Rendering measures, truncates, and pads every label in terminal display
columns through `limner.text`, so CJK and emoji items fit and a label that
already carries ANSI keeps its style. A `:width` too small for the item prefix
or filter prompt throws `:limner.components.list/invalid-width`.

### Operations
- `(select-next state n)`
- `(select-prev state n)`
- `(set-filter state text)`
- `(toggle-selection state)`

Selecting the next or previous item adjusts `:scroll-offset` so the selection
stays inside the viewport. Changing the filter text resets both the selection
and the scroll offset, even when no item matches. `adjust-scroll` remains
available for callers that change `:selected` directly.

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

Completion is numeric: a progress bar is complete when its value is at least
100, so `100` and `100.0` agree. Every named `spinner-styles` entry has its
own frame sequence. Bar fills and the dim pulse compose through
`limner.text`, so a determinate bar is exactly `:width` display columns and a
dim pulse keeps its colour.

### `progress-bar`
`(progress-bar & options)`

Creates a progress bar.

**Options:**
- `:value`: 0-100.
- `:width`: Total rendered width in character columns.
- `:type`: `:determinate` or `:indeterminate`.
- `:show-percentage`: Show the right-aligned percentage label (default true).

### `spinner`
`(spinner & options)`

Creates a loading spinner.

**Options:**
- `:style`: A key in `spinner-styles`, or `:custom` with a non-empty `:frames`
  sequence.

Unknown styles and invalid custom frames throw `ex-info` with
`:limner.components.progress/invalid-spinner-style`; they do not create a
spinner that fails later in `tick`.

### `render`
`(render component)`

Renders the progress component to a string.

---

## StatusBar
`limner.components.statusbar`

Application status line with sections. The bar is one line of exactly the
configured `:width` display columns, and caller styles survive: a section
that already carries a foreground or attributes keeps them, while the base
`:bg-color` and `:fg-color` fill only the keys it does not set.

### `statusbar`
`(statusbar & options)`

Creates a status bar.

**Options:**
- `:left`: Left section content (string or styled value).
- `:center`: Center section content.
- `:right`: Right section content.
- `:width`: Total rendered width (default 80).
- `:bg-color`: Background color keyword (default `:bright-black`).
- `:fg-color`: Text color keyword (default `:white`).

### `render`
`(render statusbar-state)`

Renders the status bar as a single string. Sections are measured, truncated,
and padded in terminal columns through `limner.text`, so CJK and emoji content
takes its true width and an OSC 8 hyperlink occupies no layout columns while
remaining in the output. When the sections do not fit, the left and right
sections are capped at two fifths of the bar each so the centre keeps a
visible region; truncation uses the text layer's `…` ellipsis. An unsupported
`:bg-color` or `:fg-color` throws `:limner.text.style/invalid-color`.
