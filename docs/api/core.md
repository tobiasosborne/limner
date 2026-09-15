# Limner Core API

`limner.core` provides the fundamental primitives for terminal interaction, including ANSI color generation, Unicode width calculation, and basic cursor control.

## Usage

```clojure
(require '[limner.core :as core])
```

## Color System

Limner can generate SGR sequences for the standard 16 colors, the 256-color
palette, and 24-bit RGB colors.

### `color`
`(color color-spec s)`

Applies a color to a string. Returns the string wrapped in ANSI escape codes.

**Parameters:**
- `color-spec`: A basic-color keyword or an RGB/256-color map.
- `s`: The string to colorize.

**Returns:**
- A string containing ANSI escape codes.

**Examples:**

```clojure
;; Basic colors
(core/color :red "Error")
(core/color :bright-green "Success")

;; Backgrounds
(core/color :bg-blue "Blue Background")

```

Each call parses existing ANSI through `limner.text`, applies the requested
style to spans where that key is absent, and returns canonical ANSI. Nested
calls therefore restore an outer style after an inner span instead of emitting
a reset in the middle.

### `available-colors`
`(available-colors)`

Returns a sorted sequence of all available basic color keywords (e.g., `:red`, `:bg-blue`, `:bold`).

### `rgb`
`(rgb r g b)`

Creates a Truecolor (24-bit) color specification.

**Parameters:**
- `r`, `g`, `b`: Integers from 0-255.

**Returns:**
- A color specification map `{:type :rgb ...}`.

**Example:**
```clojure
(core/color (core/rgb 255 128 0) "Orange Text")
```

### `bg-rgb`
`(bg-rgb r g b)`

Creates a Truecolor background color specification.

**Example:**
```clojure
(core/color (core/bg-rgb 50 50 50) "Dark Gray Background")
```

### `color-256`
`(color-256 n)`

Creates a 256-color palette specification.

**Parameters:**
- `n`: Integer from 0-255.

**Example:**
```clojure
(core/color (core/color-256 196) "Bright Red (Palette 196)")
```

### `bg-256`
`(bg-256 n)`

Creates a 256-color background specification.

### `colors`
A map of semantic color presets.

**Keys:** `:error`, `:success`, `:warning`, `:info`, `:muted`, `:primary`, `:secondary`.

**Example:**
```clojure
(core/color (core/colors :error) "Operation Failed")
```

## Unicode & String Width

The compatibility width function uses the Unicode 17 text layer. New code that
also needs slicing, padding, truncation, or wrapping should require
`limner.text` directly.

### `visible-width`
`(visible-width s)`

Returns the display width of an ANSI string or styled-text value.

**Parameters:**
- `s`: The string to measure.

**Returns:**
- Integer width (visual columns).

This delegates to `limner.text/width`: ANSI controls are zero-width, grapheme
clusters follow UAX #29 revision 47, and widths follow ADR 0001. The default
ambiguous-width policy is one column; use `limner.text/width` directly to pass
the explicit two-column option.

**Example:**
```clojure
(core/visible-width "Hello")       ;; => 5
(core/visible-width "你好")        ;; => 4 (2 chars * 2 width)
(core/visible-width "👋 World")    ;; => 8 (2 for emoji + 1 space + 5 text)
```

### `visible-length`
`(visible-length s)`

**DEPRECATED**. Use `visible-width` instead. Removes ECMA-48 controls and counts
Unicode code points; it does not measure grapheme clusters or terminal columns.

## Terminal Control

Basic utilities for controlling the terminal state.

### `hide-cursor`
`(hide-cursor)`

Writes the ANSI sequence to hide the cursor. Flushes output immediately.

### `show-cursor`
`(show-cursor)`

Writes the ANSI sequence to show the cursor. Flushes output immediately.

### `clear-line`
`(clear-line)`

Clears the current line content.
