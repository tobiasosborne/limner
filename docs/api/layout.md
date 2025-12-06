# Limner Layout API

`limner.layout` provides the engine for positioning and sizing components within the terminal window. It implements a flexible box model with support for hierarchical composition and various constraint types.

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

Allocates a proportional share of the *remaining* space after fixed and percent constraints are resolved. Higher `n` values get more space.

### `auto`
`(auto)`

Sizes the component based on its content (min of content size and available space).

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

Overlays components on top of each other. Stacking order is determined by the `:z-index` property of each component's box.

## Layout Resolution

### `layout`
`(layout layout-spec width height)`

Applies the layout specification to the given total dimensions, calculating absolute positions for all components.

**Parameters:**
- `layout-spec`: The layout definition (result of `stack`, `hsplit`, etc.).
- `width`, `height`: The total available area.

**Returns:**
- A sequence of positioned boxes ready for rendering.

## Utility Functions

### `clip`
`(clip content width height)`

Clips string content to fit within the specified dimensions.

### `pad-to-box`
`(pad-to-box lines width height)`

Pads a collection of strings with spaces to fill the specified box dimensions.
