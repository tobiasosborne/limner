(ns limner.text
  "Styled terminal text and Unicode-column operations.

  Values are canonical vectors of display spans and opaque control spans as
  defined by ADR 0003. ANSI parsing delegates to the single ECMA-48 tokenizer;
  grapheme boundaries and widths come from ADRs 0002 and 0001 at pinned
  Unicode 17.0.0. The style map is the only rendering state, which makes outer
  styles survive inner spans without reset-string bookkeeping.

  Column functions accept ANSI strings or styled values. Their optional map
  supports `:ambiguous-width` 1 (default) or 2. Slicing and truncation never
  split a grapheme; a partly covered wide cluster becomes styled spaces."
  (:refer-clojure :exclude [concat lines])
  (:require [limner.text.columns :as columns]
            [limner.text.style :as style]))

(defn from-ansi
  "Parse an ANSI string into canonical styled text, preserving opaque controls."
  [input] (style/from-ansi input))

(defn to-ansi
  "Render styled text with minimal transitions and a final reset only if needed."
  [input] (style/to-ansi input))

(defn styled
  "Apply style-map to an ANSI string; inner parsed style keys take precedence."
  [input style-map] (style/styled input style-map))

(defn concat
  "Join any number of ANSI strings or styled-text values into canonical text."
  [& inputs] (apply style/concat inputs))

(defn style
  "Fill absent style keys in input; existing inner style keys take precedence."
  [style-map input] (style/style style-map input))

(defn width
  "Return display columns. Options support `:ambiguous-width` 1 or 2."
  ([input] (columns/default-width input))
  ([input options] (columns/width input options)))

(defn slice-columns
  "Return columns `[start,end)`. Partial wide clusters become styled spaces."
  ([input start end] (columns/slice-columns input start end {}))
  ([input start end options] (columns/slice-columns input start end options)))

(defn pad-right
  "Pad on the right to target columns with one fill cluster (a space by default)."
  ([input target] (columns/pad-right input target " " {}))
  ([input target fill] (columns/pad-right input target fill {}))
  ([input target fill options] (columns/pad-right input target fill options)))

(defn pad-left
  "Pad on the left to target columns with one fill cluster (a space by default)."
  ([input target] (columns/pad-left input target " " {}))
  ([input target fill] (columns/pad-left input target fill {}))
  ([input target fill options] (columns/pad-left input target fill options)))

(defn center
  "Center to target columns; an odd spare column is placed on the right."
  ([input target] (columns/center input target " " {}))
  ([input target fill] (columns/center input target fill {}))
  ([input target fill options] (columns/center input target fill options)))

(defn truncate
  "Fit input in target columns, counting the ellipsis (default `…`) in the limit."
  [input target & {:keys [ellipsis ambiguous-width] :or {ellipsis "…" ambiguous-width 1}
                   :as supplied}]
  (let [unknown (seq (remove #{:ellipsis :ambiguous-width} (keys supplied)))]
    (when unknown
      (throw (ex-info "Truncate options contain unsupported keys"
                      {:options supplied :unsupported (vec unknown)})))
    (columns/truncate input target ellipsis {:ambiguous-width ambiguous-width})))

(defn wrap
  "Word-wrap at positive target columns, preserving explicit newline boundaries."
  ([input target] (columns/wrap input target {}))
  ([input target options] (columns/wrap input target options)))

(defn lines
  "Split on LF/CRLF, retaining empty lines and style on both sides of a break."
  [input] (columns/lines input))
