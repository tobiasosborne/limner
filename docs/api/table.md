# Limner Table API

`limner.table` renders rectangular rows as a bordered table. Every cell is
measured, padded, and aligned in terminal display columns through
`limner.text`, so ASCII, CJK, emoji ZWJ sequences, and cells that already
carry ANSI all line up.

## Usage

```clojure
(require '[limner.table :as table])
```

## `table`
`(table rows & options)`

Renders `rows` as a bordered table and returns a vector of strings, one per
output line.

**Parameters:**
- `rows`: A non-empty rectangular collection of rows. Each cell is a string,
  a `limner.text` styled value, or a scalar that is coerced with `str`.

**Options:**
- `:header?`: Draw a rule after the first row (default `true`).
- `:align`: `:left` (default), `:right`, `:center`, or a vector with one
  alignment per column.
- `:padding`: Spaces on each side of every cell (default `1`).

Column widths are the maximum display width of the cells in that column.
Every returned line has the same width.

**Example:**

```clojure
(table/table [["Name" "Age"] ["Alice" "30"] ["Bob" "7"]])
;; => ["┌───────┬─────┐"
;;     "│ Name  │ Age │"
;;     "├───────┼─────┤"
;;     "│ Alice │ 30  │"
;;     "│ Bob   │ 7   │"
;;     "└───────┴─────┘"]
```

**Right-aligned columns:**

```clojure
(table/table [["a" "bb"] ["ccc" "d"]] :align [:left :right])
```

## Errors

Invalid input throws `ex-info` with an ADR 0004 `:limner/error` class:

- `:limner.table/invalid-rows` when `rows` is not a sequential collection;
- `:limner.table/empty-rows` when `rows` is empty;
- `:limner.table/invalid-row` when a row is not a sequential collection;
- `:limner.table/ragged-rows` when a row's cell count differs from the first
  row's;
- `:limner.table/invalid-alignment` when `:align` is unknown or has the wrong
  number of entries;
- `:limner.table/invalid-padding` when `:padding` is not a non-negative
  integer.

## Deprecated compatibility

`limner/table` delegates to `limner.table/table` with right alignment and is
deprecated; require `limner.table` directly. For compatibility it retains the
legacy return type: one newline-terminated string rather than the focused
namespace's vector of lines. Ragged rows now propagate
`:limner.table/ragged-rows` instead of drawing malformed output.
