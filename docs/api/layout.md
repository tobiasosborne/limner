# Limner Layout API

`limner.layout` calculates rectangular positions and sizes from box maps and
fixed, percent, flex, or auto constraints.

## Usage

```clojure
(require '[limner.layout :as layout])
```

## The Box Model

Every element in Limner is treated as a rectangular box.

### `box`
`(box & options)`

Creates a box specification.

**Options:**
- `:x`, `:y`: Position (default 0).
- `:width`, `:height`: Dimensions (default 0).
- `:margin`: Outer spacing (default 0).
- `:padding`: Inner spacing (default 0).
- `:z-index`: Stacking order (default 0).

**Returns:**
- A map representing the box.

**Example:**
```clojure
(layout/box :width 20 :height 10 :padding 1)
```

### `content-box`
`(content-box box)`

Calculates the inner content box after subtracting padding.

## Constraint System

Layouts are defined using constraints that describe how space should be allocated.

### `fixed`
`(fixed n)`

Allocates exactly `n` characters/lines.

### `percent`
`(percent p)`

Allocates `p` percent (0-100) of the available space.

### `flex`
`(flex n)`

Allocates a proportional share of the *remaining* space after fixed, percent,
and auto constraints are resolved. Each item receives its exact share rounded
down, and the leftover columns or rows go one each to the items with the
largest fractional share (ties broken by position), so the allocated sizes
always sum to the remaining space and no final row is dropped.

`resolve-flex-constraints` exposes the allocation directly:

```clojure
(layout/resolve-flex-constraints [(layout/flex 1) (layout/flex 1) (layout/flex 1)] 10)
;; => [4 3 3]
```

### `auto`
`(auto)`

Uses the content map's `:height` in a stack or `:width` in an `hsplit`,
defaulting to zero. The value is not capped to the available space.

Invalid constraint values throw `ex-info` with the ADR 0004 class
`:limner.layout/invalid-dimension` or `:limner.layout/invalid-constraint`.
`percent` rejects values outside 0..100 rather than clamping them.

## Layout Types

Limner supports several layout strategies for arranging components.

### `stack` (Vertical)
`(stack components & options)`

Stacks components vertically from top to bottom.

**Options:**
- `:spacing`: Vertical space between items (default 0).
- `:padding`: Padding around the stack (default 0).

**Component Format:**
Each component in the `components` vector should be a map with:
- `:constraint`: The height constraint (`fixed`, `percent`, `flex`, `auto`).
- `:content`: The content to render.

**Example:**
```clojure
(layout/stack
  [{:constraint (layout/fixed 1) :content "Header"}
   {:constraint (layout/flex 1)  :content "Body"}
   {:constraint (layout/fixed 1) :content "Footer"}]
  :spacing 1)
```

### `hsplit` (Horizontal)
`(hsplit components & options)`

Splits space horizontally from left to right.

**Options:**
- `:spacing`: Horizontal space between items (default 0).
- `:padding`: Padding around the split (default 0).

**Component Format:**
Each component should specify a width constraint.

**Example:**
```clojure
(layout/hsplit
  [{:constraint (layout/percent 30) :content "Sidebar"}
   {:constraint (layout/percent 70) :content "Main"}]
  :padding 1)
```

### `grid`
`(grid components & options)`

Arranges components in a grid structure.

**Options:**
- `:columns`: Number of columns (Required).
- `:rows`: Number of rows (Optional, auto-calculated).
- `:spacing`: Space between cells.
- `:padding`: Padding around the grid.

**Example:**
```clojure
(layout/grid
  [{:content "1"} {:content "2"}
   {:content "3"} {:content "4"}]
  :columns 2
  :spacing 1)
```

### `overlay`
`(overlay components)`

Overlays components on top of each other. Stacking order is determined by the
`:z-index` property on each component map.

## Layout Resolution

### `layout`
`(layout layout-spec width height)`

Applies the layout specification to the given total dimensions, calculating absolute positions for all components.

**Parameters:**
- `layout-spec`: The layout definition (result of `stack`, `hsplit`, etc.).
- `width`, `height`: The total available area.

**Returns:**
- A sequence of positioned boxes ready for rendering.

Negative dimensions, invalid spacing, empty component collections, malformed
grids, and unknown layout types throw their documented `:limner.layout/*`
error class; layout functions do not return an empty fallback.

## Utility Functions

### `clip`
`(clip content width height)`

Splits string content into lines, keeps at most `height` lines, and clips each
line to `width` terminal columns through `limner.text`. A wide cluster that is
only partly inside the width becomes spaces, so a returned line is never wider
than `width` and never contains half a grapheme.

### `pad-to-box`
`(pad-to-box lines width height)`

Pads each line to `width` terminal columns with `limner.text/pad-right` and
adds exact-width blank lines until there are at least `height` rows. Content is
measured by column width, so CJK and emoji lines are padded correctly, and an
already-styled line keeps its spans.
