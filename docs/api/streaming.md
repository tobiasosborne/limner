# Limner Streaming API

`limner.streaming` enables progressive text rendering (typing effects) with integrated syntax highlighting and cursor effects.

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

Starts the streaming process.

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

Renders the current visible portion of the stream to a string, applying syntax highlighting and cursor if configured.

## Queries

### `completed?`
`(completed? stream-component)`

Returns true if all text has been revealed.

### `streaming?`
`(streaming? stream-component)`

Returns true if currently active.

### `progress`
`(progress stream-component)`

Returns the completion percentage (0-100).
