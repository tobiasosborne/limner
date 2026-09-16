# Limner Borders API

`limner.borders` provides utilities for drawing decorative frames around content, supporting various styles, titles, shadows, and composition.

## Usage

```clojure
(require '[limner.borders :as borders])
```

## Border Styles

Limner supports several predefined border styles.

### `border-styles`
A map containing character sets for different styles.

**Available Keys:**
- `:single`: Thin lines (┌─┐)
- `:double`: Double lines (╔═╗)
- `:rounded`: Rounded corners (╭─╮)
- `:thick`: Thick lines (┏━┓)
- `:ascii`: ASCII characters (+-+)
- `:dots`: Dotted lines (····)
- `:stars`: Asterisks (****)

### `custom-style`
`(custom-style spec)`

Creates a custom border style specification.

**Parameters:**
- `spec`: A six-string vector in the order `[top-left top-right bottom-left
  bottom-right horizontal vertical]`, or a map containing those six named
  string values. Each value must occupy exactly one display column. Incomplete,
  empty, or geometrically wider custom styles throw `ex-info` with
  `:limner.borders/invalid-border-style`.

## Drawing Functions

### `draw-box`
`(draw-box lines & options)`

Draws a box around a collection of strings.

**Parameters:**
- `lines`: A sequence of strings (the content).
- `options`:
  - `:border-style`: Keyword (e.g., `:rounded`) or custom style definition (default `:single`).

**Returns:**
- A sequence of strings representing the boxed content.

**Example:**
```clojure
(borders/draw-box ["Hello" "World"] :border-style :rounded)
```

### `draw-titled-box`
`(draw-titled-box title lines & options)`

Draws a box with a title embedded in the top border.

**Parameters:**
- `title`: The title string.
- `lines`: Content strings.
- `options`:
  - `:border-style`: Border style (default `:single`).
  - `:title-pos`: Position of title `:left`, `:center`, or `:right` (default `:left`).

**Example:**
```clojure
(borders/draw-titled-box "Alert" ["System Critical"] :border-style :thick :title-pos :center)
```

Unknown border keywords and `:title-pos` values throw their ADR 0004
`:limner/error` classes; they do not select a default or return a placeholder.
Non-string content and titles likewise throw instead of fabricating a box.

Content and title width both go through the Unicode 17 `limner.text/width`
path, so CJK, emoji, and pre-coloured titles keep the box rectangular. Tabs
in content are cursor motion rather than zero-width glyphs: each tab expands
to spaces up to the next multiple of eight columns before the box is laid
out.

## Composition

### `side-by-side`
`(side-by-side left-box-lines right-box-lines spacing)`

Places two boxes side by side with specified spacing.

**Returns:**
- A vector of strings combining both boxes horizontally.

**Example:**
```clojure
(borders/side-by-side box1 box2 2)
```

### `nest-box`
`(nest-box inner-box-lines padding)`

Prepares a box to be nested inside another by adding padding around it.

### `indent-lines`
`(indent-lines lines n)`

Indents a collection of lines by `n` spaces.

## Visual Effects

### `add-shadow`
`(add-shadow box-lines & options)`

Adds a drop shadow to the right and bottom edges of a box. Shadow fills use
`limner.text`'s one-grapheme fill rule, so a wide glyph is sliced to the exact
shadow columns; an empty or multi-grapheme fill throws
`:limner.text/invalid-fill`.

**Options:**
- `:shadow-char`: Character to use for shadow (default "░").
- `:shadow-color`: Color specification for the shadow.

**Example:**
```clojure
(borders/add-shadow box :shadow-color :black)
```

### `add-heavy-shadow`
`(add-heavy-shadow box-lines & options)`

Adds a heavier, thicker drop shadow (2 characters wide/high).

### `colorize-border`
`(colorize-border box-lines color & options)`

Colours only the border segments while leaving content and titles untouched.
The first and last rows are border rows, and every glyph in them that belongs
to the structural runs at either edge is coloured; interior rows colour only
their first and last glyph. Existing title/content colours, attributes, and
OSC 8 links are preserved, and a border-like glyph inside a title is not
reclassified as structure. Pass `:border-style` for a custom glyph set.

**Options:**
- `:border-style`: Border style or custom specification (default: every
  predefined style's glyphs).

**Parameters:**
- `box-lines`: The rendered box (a collection of strings).
- `color`: Color specification (keyword, RGB map, etc.).

**Example:**
```clojure
(borders/colorize-border box :cyan)
```
