# Limner Render API

`limner.render` manages the display update cycle. It uses a double-buffering technique with a differential update algorithm to ensure flicker-free rendering and minimal data transmission to the terminal.

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
  - `:on-frame`: Optional callback `(fn [render-state])` called after each frame.
  - `:on-error`: Optional error handler `(fn [exception])`.

**Returns:**
A map containing control functions:
- `:stop!`: Stops the loop (blocks until clean shutdown).
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

Renders a single static frame and immediately cleans up. Useful for testing or simple CLI output.

## Terminal Operations

### `get-terminal-size`
`(get-terminal-size)`

Returns the current terminal dimensions as `{:width w :height h}`. It attempts multiple methods (`stty`, `tput`, environment variables) to find accurate values.

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

Writes a string (parsing ANSI codes) into the buffer at the specified position.

## Rendering Internals

Limner's rendering engine minimizes terminal I/O.

### Diff Algorithm
When `render-frame` is called:
1.  Content is written to a **back buffer**.
2.  The back buffer is compared to the **front buffer** (what's currently on screen).
3.  Only cells that differ are collected.
4.  Contiguous dirty cells are grouped into regions.
5.  Only these regions are printed to the terminal.
6.  Buffers are swapped.

This ensures O(changed-pixels) performance rather than O(screen-size).

### `render-stats`
`(render-stats state)`

Returns detailed statistics about the render engine (FPS, dirty regions, buffer utilization).
