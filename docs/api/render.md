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
  - `:on-error`: Optional error handler `(fn [exception])`.

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
`(get-terminal-size)`

Returns dimensions as `{:width w :height h}`. It tries `stty` and then `tput`,
falling back to 80×24. Reported widths below 20 and heights below 10 are also
replaced by those fallback dimensions.

### `clear-screen`
`(clear-screen)`

Clears the terminal and moves the cursor to (0,0).

## Buffer Operations

Lower-level functions for manipulating screen buffers directly.

### `create-buffer`
`(create-buffer width height)`

Creates an empty screen buffer.

### `write-string-to-buffer`
`(write-string-to-buffer buffer x y s)`

Writes a string into the buffer at the specified position, recognizing SGR
color sequences. Each UTF-16 code unit becomes one cell, and other terminal
control sequences are treated as text.

## Rendering Internals

The renderer emits only regions containing changed cells, but finding those
cells scans the complete new buffer on every diff.

### Diff Algorithm
When `render-frame` is called:
1.  Content is written to a **back buffer**.
2.  The back buffer is compared to the **front buffer** (what's currently on screen).
3.  Only cells that differ are collected.
4.  Contiguous dirty cells are grouped into regions.
5.  Only these regions are printed to the terminal.
6.  Buffers are swapped.

Terminal output scales with the dirty regions; diff calculation scales with
the buffer size.

### `render-stats`
`(render-stats state)`

Returns the frame count, estimated and target FPS, dimensions, and basic front-
and back-buffer cell counts.
