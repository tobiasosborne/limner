# ECMA-48 tokenization

`limner.ansi` is the shared boundary between terminal control syntax and text.
Its grammar is derived from the vendored ECMA-48 fifth edition, especially
Sections 5.3, 5.4, 5.6, and 8.3, with concrete terminal conventions from the
vendored xterm control-sequence table.

## Tokens and round trips

`tokenize` accepts any Java string and returns a vector. Every token has its
exact source in `:raw`, so this invariant always holds:

```clojure
(require '[limner.ansi :as ansi])

(def input "\u001B[31mred\u001B[0m")
(= input (apply str (map :raw (ansi/tokenize input)))) ; => true
```

Non-string input throws `ex-info` with `:limner.ansi/invalid-input`.

The token shapes are:

- text: `{:type :text :text "..." :raw "..."}`
- CSI: `{:type :csi :params "..." :intermediates "..." :final \m :raw "..."}`
- OSC: `{:type :osc :data "..." :terminator :bel|:st :raw "..."}`
- DCS, APC, PM, and SOS: the OSC shape with their respective `:type` and an ST
  terminator
- escape sequences: `{:type :esc :intermediates "..." :final char :raw "..."}`
- other controls: `{:type :c0|:c1 :char char :raw "..."}`
- unterminated prefixes: `{:type :incomplete :kind kind :raw "..."}`

Both ESC-prefixed 7-bit introducers and U+0080..U+009F C1 controls are
recognized. A Clojure string no longer records whether U+009B came from a raw
C1 byte or decoded UTF-8, so this character-level API treats it as CSI. Xterm's
reference warns that byte-oriented UTF-8 parsers must make this decision before
decoding; callers at that layer should not pass decoded C1 as literal text.

Line feed and tab remain inside `:text` tokens because limner's later layout
layer interprets them structurally. Other C0 controls, DEL, and C1 controls are
separate tokens. `strip` concatenates only `:text`, retaining line feeds and
tabs. An incomplete control prefix is not exposed as text and is never silently
dropped from `tokenize`: its `:raw` value preserves it for lossless callers.

## SGR operations

`sgr-params` accepts a CSI token whose final byte is `m` and returns ordered
maps with `:attribute`, `:value`, and the exact parameter substring in `:raw`.
Other token shapes throw `ex-info` with
`:limner.ansi.sgr/invalid-token`.
Resets are explicit: parameter 0 is `{:attribute :all :value :default}`, 22
sets intensity to `:normal`, 23 clears italic, 24 clears underline, 25 clears
blink, 27 clears inverse, 28 clears hidden, 29 clears crossed-out, and 39/49
restore default foreground/background.

The helper covers the xterm table's base, bright, indexed, and RGB colors. Both
`38;2;r;g;b` and `38:2::r:g:b` forms are accepted, with corresponding forms
for background parameter 48. Color values are maps such as
`{:mode :indexed :index 42}` or `{:mode :rgb :r 1 :g 2 :b 3}`. Parameters
outside the implemented table—including reserved parameter 26—remain
`{:attribute :unknown ...}` operations with their raw spelling.

`hyperlink` recognizes OSC data of the form `8;params;URI` and returns
`{:params string :uri string-or-nil}`. An empty URI is the closing operation.

## Performance

`scripts/ansi_benchmark.clj` measures a fixed plain-text corpus, an SGR-heavy
corpus, and a mixed CSI/SGR/OSC corpus. Results below are median rates from
three rounds on 2026-09-15. The legacy comparator is the removed SGR-only regex;
it is fast but gives the wrong result for the mixed corpus.
The JVM run used Clojure 1.11.1 on Java 21.0.12; the babashka run used 1.12.212
with SCI's Clojure 1.12.3 compatibility on its Java 25 runtime.

| Corpus | Tokenize JVM | Strip JVM | Tokenize bb | Strip bb |
| --- | ---: | ---: | ---: | ---: |
| Plain | 117.751 MB/s | 119.261 MB/s | 57.701 MB/s | 56.383 MB/s |
| SGR-heavy | 18.102 MB/s | 24.181 MB/s | 0.772 MB/s | 0.645 MB/s |
| Mixed CSI/OSC | 27.306 MB/s | 19.966 MB/s | 0.597 MB/s | 0.541 MB/s |

The scanner uses byte-class state for every control sequence. A compiled regex
only finds control-character boundaries and enables the plain-text fast path;
measurement improved babashka's plain tokenization from 0.794 MB/s to 57.701
MB/s. Run the benchmark with `clojure -M:benchmark -m ansi-benchmark` and
`bb benchmark:ansi`.

## References

- `reference/specs/ecma-48-5th-edition.pdf`, Sections 5.3, 5.4, 5.6, and 8.3.
- `reference/specs/xterm-ctlseqs.txt`, sections “Control Bytes, Characters, and
  Sequences”, “C1 (8-Bit) Control Characters”, “Character Attributes (SGR)”,
  and “Set Text Parameters”.
