# Limner Core API

`limner.core` provides the fundamental primitives for terminal interaction, including ANSI color generation, Unicode width calculation, and basic cursor control.

## Usage

```clojure
(require '[limner.core :as core])
```

## Color System

Limner supports a comprehensive color system including standard 16-colors, 256-color palette, and 24-bit Truecolor (RGB).

### `color`
`(color color-spec s)`

Applies a color to a string. Returns the string wrapped in ANSI escape codes.

**Parameters:**
- `color-spec`: A keyword (basic color), map (RGB/256), or nested color.
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

;; Nesting (Bold Red)
(core/color :bold (core/color :red "Bold Red"))
```

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

Functions for correctly handling string width in a terminal environment, accounting for CJK characters, emojis, and combining marks.

### `visible-width`
`(visible-width s)`

Calculates the display width of a string. This is the **recommended** function for layout calculations.

**Parameters:**
- `s`: The string to measure.

**Returns:**
- Integer width (visual columns).

**Details:**
- **0 width**: ANSI codes, combining marks, zero-width spaces.
- **1 width**: Standard ASCII/Latin characters.
- **2 width**: CJK ideographs, fullwidth forms, most emojis.

**Example:**
```clojure
(core/visible-width "Hello")       ;; => 5
(core/visible-width "你好")        ;; => 4 (2 chars * 2 width)
(core/visible-width "👋 World")    ;; => 8 (2 for emoji + 1 space + 5 text)
```

### `visible-length`
`(visible-length s)`

**DEPRECATED**. Use `visible-width` instead. Calculates string length stripping ANSI codes but counting characters (1 char = 1 width).

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
