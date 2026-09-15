# Limner Streaming API

`limner.streaming` is an experimental state machine for progressive text
rendering with optional syntax highlighting. Positions are UTF-16 indexes, so
the current implementation can split non-BMP characters and grapheme
clusters.

## Usage

```clojure
(require '[limner.streaming :as streaming])
```

## Component Creation

### `stream`
`(stream & options)`

Creates a streaming component state map.

**Options:**
- `:text`: The full text to stream.
- `:delay-ms`: Delay between characters (speed).
- `:lang`: Language for syntax highlighting.
- `:theme`: Theme for highlighting.
- `:show-cursor`: Whether to show a cursor.
- `:cursor-char`: Character to use for cursor (default "▋").

**Returns:**
- A stream component map.

## State Management

### `start`
`(start stream-component)`

Sets the component state to `:streaming`; callers must continue invoking
`tick` themselves.

### `pause`
`(pause stream-component)`

Pauses streaming at the current position.

### `resume`
`(resume stream-component)`

Resumes streaming from a paused state.

### `reset-stream`
`(reset-stream stream-component)`

Resets the stream to the beginning.

## Rendering Loop

### `tick`
`(tick stream-component)`

Updates the stream state based on elapsed time. Should be called in your render loop before rendering.
- Advances the text position if `delay-ms` has passed.
- Updates cursor blink state.

### `render`
`(render stream-component)`

Renders the current visible portion to a string and optionally applies syntax
highlighting. The cursor is appended only when the component is in the
`:completed` state and its blink state is visible.

## Queries

### `completed?`
`(completed? stream-component)`

Returns true if all text has been revealed.

### `streaming?`
`(streaming? stream-component)`

Returns true if currently active.

### `progress`
`(progress stream-component)`

Returns an integer completion percentage (0-100) based on UTF-16 positions.
