# Limner Terminal API

`limner.terminal` handles feature detection and graceful degradation, ensuring your application works correctly across different terminal emulators (from modern GPU-accelerated ones to legacy TTYs).

## Usage

```clojure
(require '[limner.terminal :as terminal])
```

## Capability Detection

Functions to inspect the runtime environment.

### `detect-capabilities`
`(detect-capabilities)`

Scans environment variables (`TERM`, `COLORTERM`, `LANG`) to determine supported features.

**Returns:**
A map with boolean flags:
- `:ansi-colors`: Basic 16 colors.
- `:256-colors`: 256-color palette.
- `:truecolor`: 24-bit RGB color.
- `:unicode`: UTF-8 support.
- `:box-drawing`: Unicode box-drawing characters.
- `:mouse`: Mouse tracking support.

### `get-capabilities`
`(get-capabilities)`

Returns the current capabilities map. Caches the result of `detect-capabilities`.

### `supports-feature?`
`(supports-feature? feature-key)`

Checks if a specific feature is supported.

**Example:**
```clojure
(if (terminal/supports-feature? :truecolor)
  (use-fancy-colors)
  (use-basic-colors))
```

## Graceful Degradation

Helpers to automatically adapt UI based on capabilities.

### `with-fallback`
`(with-fallback feature value fallback)`

Returns `value` if `feature` is supported, otherwise `fallback`.

**Example:**
```clojure
(terminal/with-fallback :unicode "✓" "[OK]")
```

### `select-border-style`
`(select-border-style)`

Returns `:single` (Unicode) if box drawing is supported, otherwise `:ascii`.

### `select-color-mode`
`(select-color-mode)`

Returns the best supported color mode: `:truecolor`, `:256-colors`, `:ansi`, or `:none`.

## Reporting

### `capability-report`
`(capability-report)`

Returns a human-readable string summarizing the detected terminal environment. Useful for debugging user issues.

## Testing Helpers

### `with-simulated-capabilities`
`(with-simulated-capabilities caps-map & body)`

Macro to force specific capabilities within a scope. Useful for testing fallback logic.

**Example:**
```clojure
(terminal/with-simulated-capabilities {:unicode false}
  (terminal/select-border-style)) ;; => :ascii
```

### `simulate-dumb-terminal`
`(simulate-dumb-terminal)`

Returns a capability map representing a minimal "dumb" terminal.

### `simulate-modern-terminal`
`(simulate-modern-terminal)`

Returns a capability map representing a fully-featured modern terminal.
