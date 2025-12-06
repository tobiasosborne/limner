# Limner State API

`limner.state` provides a reactive state management system built on top of Clojure atoms. It offers path-based updates and granular watchers to facilitate building interactive TUI applications.

## Usage

```clojure
(require '[limner.state :as state])
```

## State Management

### `create-state`
`(create-state & options)`

Creates a new application state atom.

**Options:**
- `:initial-value`: The starting state (default `{}`).
- `:watchers`: A map of `{watcher-id watcher-fn}`.

**Returns:**
- A standard Clojure atom extended with watcher tracking metadata.

### `get-state`
`(get-state state-atom)`

Dereferences the state atom (equivalent to `@state-atom`).

### `get-in-state`
`(get-in-state state-atom path)`

Retrieves a value from a nested path within the state.

## Updates

Helpers for safe, atomic state mutations.

### `set-state!`
`(set-state! state-atom new-value)`

Resets the state to a new value.

### `update-state!`
`(update-state! state-atom f & args)`

Updates the state by applying a function (equivalent to `swap!`).

### `update-in-state!`
`(update-in-state! state-atom path f & args)`

Updates a value at a nested path (equivalent to `swap!` with `update-in`).

**Example:**
```clojure
(state/update-in-state! app-state [:user :score] inc)
```

### `assoc-in-state!`
`(assoc-in-state! state-atom path value)`

Sets a value at a nested path.

### `dissoc-in-state!`
`(dissoc-in-state! state-atom path)`

Removes a value at a nested path.

## Watchers

React to state changes.

### `add-watcher!`
`(add-watcher! state-atom id watcher-fn)`

Registers a watcher. `watcher-fn` signature: `(fn [key ref old-state new-state])`.

### `remove-watcher!`
`(remove-watcher! state-atom id)`

Removes a watcher by ID.

### `list-watchers`
`(list-watchers state-atom)`

Returns a list of registered watcher IDs.

## Reactive Watchers

Higher-level watchers for specific change patterns.

### `watch-path`
`(watch-path state-atom path id callback)`

Fires `callback` `(fn [old-val new-val])` only when the value at `path` changes.

**Example:**
```clojure
(state/watch-path app-state [:settings :theme] :theme-watcher
  (fn [old new]
    (println "Theme changed to" new)))
```

### `watch-keys`
`(watch-keys state-atom keys id callback)`

Fires `callback` `(fn [changed-keys old-state new-state])` when any of the specified top-level keys change.

### `watch-predicate`
`(watch-predicate state-atom id pred callback)`

Fires `callback` when `pred` transitions from false to true.

**Example:**
```clojure
(state/watch-predicate app-state :game-over-watcher
  #(:game-over %)
  (fn [old new] (println "Game Over!")))
```

## Render Integration

### `create-reactive-state`
`(create-reactive-state & options)`

Creates a state atom that automatically triggers a callback on changes.

**Options:**
- `:on-change`: `(fn [old new])`.

### `bind-to-render`
`(bind-to-render state-atom render-control)`

Connects the state to a render loop, forcing a re-render on every state change.
