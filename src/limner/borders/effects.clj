(ns limner.borders.effects
  "Shadows, indentation and side-by-side composition for box lines.

  These functions take and return already-drawn box lines. They measure with
  `limner.text` so a CJK glyph is one two-column cluster, not two UTF-16
  units, and they never print or return a fabricated box (ADR 0004)."
  (:require [limner.borders.internal :as internal]
            [limner.core :as core]
            [limner.text :as text]))

(defn- fill-columns
  "Fill exactly `columns` with one styled grapheme, slicing wide fills safely."
  [fill columns]
  (text/to-ansi (text/pad-right "" columns fill)))

(defn add-shadow
  "Add a one-column drop shadow to the right and bottom edges.

  The top border stays unshadowed, matching a light source from above.
  `:shadow-char` defaults to `░`; `:shadow-color` colours the shadow."
  [box-lines & {:keys [shadow-char shadow-color] :or {shadow-char "░"}}]
  (internal/validate-lines box-lines)
  (if (empty? box-lines)
    []
    (let [shadow-ch (if shadow-color
                      (core/color shadow-color shadow-char)
                      shadow-char)
          box-width (text/width (first box-lines))
          right-shadow (fill-columns shadow-ch 1)
          with-right-shadow (map-indexed
                             (fn [idx line]
                               (if (zero? idx)
                                 line
                                 (str line right-shadow)))
                             box-lines)
          bottom-shadow (str "  " (fill-columns shadow-ch box-width))]
      (concat with-right-shadow [bottom-shadow]))))

(defn add-heavy-shadow
  "Add a two-column drop shadow to the right and bottom edges.

  `:shadow-char` defaults to `▓`; `:shadow-color` colours the shadow."
  [box-lines & {:keys [shadow-char shadow-color] :or {shadow-char "▓"}}]
  (internal/validate-lines box-lines)
  (if (empty? box-lines)
    []
    (let [shadow-ch (if shadow-color
                      (core/color shadow-color shadow-char)
                      shadow-char)
          box-width (text/width (first box-lines))
          right-shadow (fill-columns shadow-ch 2)
          with-right-shadow (map-indexed
                             (fn [idx line]
                               (if (zero? idx)
                                 line
                                 (str line right-shadow)))
                             box-lines)
          bottom-shadow-1 (str "  " (fill-columns shadow-ch box-width))
          bottom-shadow-2 (str "  " (fill-columns shadow-ch box-width))]
      (concat with-right-shadow [bottom-shadow-1 bottom-shadow-2]))))

(defn indent-lines
  "Indent every line with `n` spaces; a negative `n` means no indent."
  [lines n]
  (internal/validate-dimension n "indent")
  (internal/validate-lines lines)
  (let [safe-n (max 0 n)
        indent (apply str (repeat safe-n " "))]
    (map #(str indent %) lines)))

(defn nest-box
  "Wrap `inner-box-lines` in `padding` blank lines and `padding` spaces.

  A negative padding means no padding."
  [inner-box-lines padding]
  (internal/validate-dimension padding "padding")
  (internal/validate-lines inner-box-lines)
  (let [safe-padding (max 0 padding)
        indented (indent-lines inner-box-lines safe-padding)
        top-padding (repeat safe-padding "")
        bottom-padding (repeat safe-padding "")]
    (concat top-padding indented bottom-padding)))

(defn side-by-side
  "Place two boxes side by side with `spacing` columns between them.

  A negative spacing means no spacing. The shorter box is padded with spaces
  on the right so both columns stay aligned across every row."
  [left-box-lines right-box-lines spacing]
  (internal/validate-dimension spacing "spacing")
  (internal/validate-lines left-box-lines)
  (internal/validate-lines right-box-lines)
  (if (or (empty? left-box-lines) (empty? right-box-lines))
    []
    (let [safe-spacing (max 0 spacing)
          left-width (text/width (first left-box-lines))
          space-str (apply str (repeat safe-spacing " "))
          max-height (max (count left-box-lines) (count right-box-lines))
          padded-left (concat left-box-lines
                              (repeat (- max-height (count left-box-lines))
                                      (apply str (repeat left-width " "))))
          padded-right (concat right-box-lines
                               (repeat (- max-height (count right-box-lines)) ""))]
      (map #(str %1 space-str %2) padded-left padded-right))))
