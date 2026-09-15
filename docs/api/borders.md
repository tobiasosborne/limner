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
- `spec`: A map or vector defining characters for `[top-left top-right bottom-left bottom-right horizontal vertical]`.

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

Content width now uses the Unicode 17 `limner.text/width` path through
`limner.core/visible-width`, but title positioning still uses string length.
Titles containing ANSI sequences or wide Unicode characters can therefore
misalign until the component migration phase.

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

Adds a drop shadow to the right and bottom edges of a box.

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
`(colorize-border box-lines color)`

Attempts to color the border characters while leaving the content uncolored.
The current border-line detection is incomplete: top and bottom lines in
styles such as `:rounded` and `:thick` are not fully colored.

**Parameters:**
- `box-lines`: The rendered box (a collection of strings).
- `color`: Color specification (keyword, RGB map, etc.).

**Example:**
```clojure
(borders/colorize-border box :cyan)
```
