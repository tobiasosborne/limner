# Limner Render API

`limner.render` is an experimental display loop with screen buffers and
diff-based terminal writes. It currently performs terminal effects directly
and is not safe for concurrent calls to its render controls.

## Usage

```clojure
(require '[limner.render :as render])
```

## The Render Loop

The core of an interactive application is the render loop, which repeatedly draws the current state to the screen at a target frame rate.

### `create-render-loop`
`(create-render-loop app-state-atom & options)`

Starts a background thread (via `future`) that continuously renders the application state.

**Parameters:**
- `app-state-atom`: An atom holding your application state.
- `options`:
  - `:render-fn`: Function `(fn [state])` -> vector of strings. (Required)
  - `:fps`: Target frames per second (default 60).
  - `:on-frame`: Optional callback `(fn [render-state])` called after each loop
    iteration.
  - `:on-error`: Optional error boundary `(fn [exception])`. The loop keeps
    its last good frame, calls this callback, and does not print (ADR 0004).

**Returns:**
A map containing control functions:
- `:stop!`: Requests a stop, waits up to two seconds, and may cancel the
  render future. It rethrows an error retained by the loop.
- `:force-render!`: Triggers an immediate full redraw.
- `:get-stats`: Returns rendering statistics map.
- `:running?`: Checks if the loop is active.

**Example:**
```clojure
(def loop-ctrl
  (render/create-render-loop
    my-state
    :render-fn my-render-function
    :fps 30))

;; Later...
((:stop! loop-ctrl))
```

### `render-once`
`(render-once content-lines)`

Runs terminal setup, renders one static frame, and then restores the terminal.
These calls are not protected by `finally`, so an exception can prevent
restoration.

## Terminal Operations

### `get-terminal-size`
`(get-terminal-size)` or `(get-terminal-size size-source)`

Returns dimensions as `{:width w :height h}`. It tries `stty` and then `tput`,
falling back to 80×24. Reported widths below 20 and heights below 10 are also
replaced by those fallback dimensions. The optional zero-argument source is
intended for deterministic callers and tests; returning `nil` or throwing uses
the fallback without invoking the production source.

### `render-state`
`(render-state width height & {:keys [clock]})`

Creates the double-buffer state. `:clock` is an optional zero-argument
monotonic nanosecond clock. It defaults to `System/nanoTime` and is retained for
frame and resize decisions.

### `clear-screen`
`(clear-screen)`

Clears the terminal and moves the cursor to (0,0).

## Buffer Operations

Lower-level functions for manipulating screen buffers directly. The cell
model is fixed by ADR 0005: a cell is one grapheme cluster plus its ADR 0003
style map, and a wide cluster occupies a lead cell and a continuation cell.

### `cell`
`(cell cluster style-map)`

Creates a display cell from one grapheme cluster and a style map. `:width`
comes from `limner.text`. There is no accumulated SGR string, so two cells
with the same cluster and style compare equal. Empty strings, multiple
graphemes, zero-width structural content, and embedded controls throw
`:limner.render/invalid-cell`.

`set-cell` preserves the same invariant: a wide value creates its continuation,
a narrow value clears any wide pair it overlaps, and a wide value at the right
edge becomes a space.

### `empty-cell`
`(empty-cell)`

Creates a space cell.

### `create-buffer`
`(create-buffer width height)`

Creates an empty screen buffer.

### `write-string-to-buffer`
`(write-string-to-buffer buffer x y s)`

Writes an ANSI string or styled value into the buffer at the specified
position. Parsing, segmentation and width come from `limner.text/clusters`,
so a non-BMP emoji is one lead cell plus a continuation, never two surrogate
halves. A write that starts inside a wide cell replaces it with spaces, and a
write that would leave half a wide cluster at the right edge writes a space
instead.

### `write-lines-to-buffer`
`(write-lines-to-buffer buffer x y lines)`

Writes several lines starting at `(x, y)`. A non-string, non-styled line
throws `ex-info` with `:limner.render/invalid-lines`; so does a value that is
not a sequential collection. `render-frame` validates this input even when the
frame-rate clock says no frame is due, and `force-render` validates it before
clearing the terminal.

## Rendering Internals

The renderer emits only regions containing changed cells.

### Diff Algorithm
When `render-frame` is called:
1.  Content is written to a **back buffer**.
2.  The back buffer is compared to the **front buffer** (what's currently on screen).
3.  Only cells that differ by value are collected.
4.  The row-major dirty cells are grouped into regions in one linear pass,
    without grouping or sorting.
5.  Each region is emitted as a cursor move plus `limner.text/to-ansi` of its
    clusters, which emits only the SGR transitions that change and returns the
    style to default.
6.  Buffers are swapped.

Terminal output scales with the dirty regions; diff calculation scales with
the buffer size.

Golden tests feed the emitted bytes through the test-only virtual terminal in
`test/limner/vt.clj` and compare the resulting `{:cluster :style}` grid, so
they do not depend on escape-sequence spelling. Property tests confirm that a
sequence of diffs reaches the same grid as a full redraw and that no cell
holds half a wide cluster.

### `render-stats`
`(render-stats state)`

Returns the frame count, estimated and target FPS, dimensions, and basic front-
and back-buffer cell counts.
