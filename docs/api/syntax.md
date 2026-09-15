# Limner Syntax API

`limner.syntax` is an experimental regex-based highlighter with configurable
themes. It is not a language parser: keyword recognition can match inside
identifiers, and several keys present in `language-rules` and the built-in
themes are not consumed by the tokenizer.

## Usage

```clojure
(require '[limner.syntax :as syntax])
```

## Highlighting

### `highlight`
`(highlight code lang & options)`

Applies the configured token colors for a language rule set.

**Parameters:**
- `code`: The source code string.
- `lang`: Language keyword (`:clojure`, `:python`, `:javascript`).
- `options`:
  - `:theme`: Theme keyword (default `:default`) or a custom theme map.

**Returns:**
- A string with ANSI color codes applied.

**Example:**
```clojure
(syntax/highlight "(def x 1)" :clojure :theme :monokai)
```

### `highlight-with-line-numbers`
`(highlight-with-line-numbers code lang & options)`

Same as `highlight` but adds line numbers on the left.

## Themes

Themes define color mappings for token types.

### Built-in Themes
- `:default`
- `:monokai`
- `:solarized`

### `get-theme`
`(get-theme theme-name)`

Returns the theme map for a given name.

### `available-themes`
`(available-themes)`

Returns a list of built-in theme names.

## Language Support

Built-in rule sets:
- `:clojure`
- `:python`
- `:javascript`

### `detect-language`
`(detect-language filename)`

Infers language keyword from a file extension.

### `detect-language-from-content`
`(detect-language-from-content code)`

Attempts to guess the language based on code patterns (heuristics).
