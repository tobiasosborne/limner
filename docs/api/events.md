# Limner Events API

`limner.events` handles input processing, including keyboard and mouse events, focus management, and asynchronous event routing.

## Usage

```clojure
(require '[limner.events :as events])
```

## Event Parsing

Functions to convert raw terminal input into structured event maps.

### `parse-key`
`(parse-key input)`

Parses raw input string into an event map.

**Returns:**
- Key Event: `{:type :key :key :enter :char nil :modifiers #{:ctrl} ...}`
- Mouse Event: `{:type :mouse :x 10 :y 5 :button :left ...}`
- Unknown: `{:type :unknown :raw ...}`

**Example:**
```clojure
(events/parse-key "\u001b[A") ;; => {:type :key :key :up ...}
```

### `key-combo`
`(key-combo event-or-spec)`

Standardizes key representation into a vector like `[:ctrl :c]`.

### `key-matches?`
`(key-matches? event combo)`

Checks if an event matches a specific key combination.

**Example:**
```clojure
(events/key-matches? event [:ctrl :c])
```

## Keybindings

A registry system for mapping key combinations to handler functions.

### `keybindings`
`(keybindings bindings-map)`

Creates a new keybinding registry.

### `bind-key!`
`(bind-key! registry combo handler)`

Adds a binding. Handler signature: `(fn [event state] -> new-state)`.

### `dispatch-key`
`(dispatch-key registry event state)`

Finds and executes the matching handler for an event.

## Focus Management

Utilities for managing focus state in an application with multiple interactive components.

### `focus-state`
`(focus-state & options)`

Creates a focus state map.
**Options:** `:components` (vector of IDs), `:focused` (initial ID), `:wrap` (boolean).

### `focus-next` / `focus-prev`
`(focus-next state)` / `(focus-prev state)`

Cycles focus to the next/previous component in the list.

## High-Level Processing

### `process-event`
`(process-event event state)`

The main entry point for synchronous event handling. It orchestrates:
1.  Tab navigation (Focus switching).
2.  Global keybindings.
3.  Mouse event routing (hit testing).
4.  Component-specific event routing.

## Async Event System

Limner supports non-blocking, asynchronous event processing using `core.async`.

### `create-async-event-system`
`(create-async-event-system & options)`

Creates a complete async event processing system.

**Options:**
- `:state-atom`: The application state atom.
- `:process-fn`: Main handler function `(fn [event state])`.
- `:buffer-size`: Size of input buffer (default 100).
- `:handler-timeout-ms`: Max time for a handler (default 5000ms).
- `:batch-events?`: Whether to process rapid events in batches (default false).

**Returns:**
A map with control functions:
- `:put!`: `(fn [event])` - Queues an event.
- `:stop!`: Stops the processor.
- `:get-stats`: Returns event processing statistics.

**Example:**
```clojure
(def sys (events/create-async-event-system
           :state-atom app-state
           :process-fn events/process-event))

((:put! sys) {:type :key :key :a})
```
