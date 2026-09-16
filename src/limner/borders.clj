(ns limner.borders
  "Border drawing utilities with various styles.

  Invalid input throws `ex-info` with an ADR 0004 class:
  `:limner.borders/invalid-lines`, `:limner.borders/invalid-border-style`,
  `:limner.borders/unknown-border-style`, `:limner.borders/invalid-title`,
  `:limner.borders/invalid-title-pos`, or
  `:limner.borders/invalid-dimension`."
  (:require [clojure.string :as str]
            [limner.core :as core]))

;; ────────────────────── Error Handling & Validation ──────────────────────

(defn- validate-lines
  "Validate that lines is a collection of strings"
  [lines]
  (when-not (coll? lines)
    (throw (ex-info "Lines must be a collection"
                    {:limner/error :limner.borders/invalid-lines
                     :lines lines :type (type lines)})))
  (doseq [[idx line] (map-indexed vector lines)]
    (when-not (string? line)
      (throw (ex-info (str "Line " idx " is not a string")
                      {:limner/error :limner.borders/invalid-lines
                       :line line :index idx :type (type line)}))))
  lines)

(def ^:private custom-style-keys
  [:top-left :top-right :bottom-left :bottom-right :horizontal :vertical])

(defn- valid-custom-style? [style]
  (cond
    (vector? style) (and (= 6 (count style)) (every? string? style))
    (map? style) (every? #(string? (get style %)) custom-style-keys)
    :else false))

(defn- invalid-border-style! [style]
  (throw (ex-info "Border style must be a known keyword or six-string vector/map"
                  {:limner/error :limner.borders/invalid-border-style
                   :style style :type (type style)
                   :limner/hint (str "Use a predefined keyword or supply strings for "
                                     (pr-str custom-style-keys))})))

(defn- validate-border-style
  "Validate that a border style is a keyword or a complete custom style."
  [style]
  (when-not (or (keyword? style) (valid-custom-style? style))
    (invalid-border-style! style))
  style)

(defn- validate-custom-style [style]
  (when-not (valid-custom-style? style)
    (invalid-border-style! style))
  style)

;; ────────────────────── Border Styles ──────────────────────
(def border-styles
  "Available border styles with their character sets
   Format: [top-left top-right bottom-left bottom-right horizontal vertical]"
  {:single  ["┌" "┐" "└" "┘" "─" "│"]
   :double  ["╔" "╗" "╚" "╝" "═" "║"]
   :rounded ["╭" "╮" "╰" "╯" "─" "│"]
   :thick   ["┏" "┓" "┗" "┛" "━" "┃"]
   :ascii   ["+" "+" "+" "+" "-" "|"]
   :dots    ["·" "·" "·" "·" "·" "·"]
   :stars   ["*" "*" "*" "*" "*" "*"]})

(defn custom-style
  "Create a custom border style
   Takes a map with keys: :top-left :top-right :bottom-left :bottom-right :horizontal :vertical
   Or a vector: [top-left top-right bottom-left bottom-right horizontal vertical]"
  [spec]
  (validate-custom-style spec)
  (if (vector? spec)
    spec
    [(:top-left spec)
     (:top-right spec)
     (:bottom-left spec)
     (:bottom-right spec)
     (:horizontal spec)
     (:vertical spec)]))

(defn get-border-chars
  "Get border characters for given style
   - If style is a keyword, look it up in border-styles
   - If style is a vector or map, treat as custom style

   An unknown keyword throws `ex-info` with
   `:limner.borders/unknown-border-style`; a value of another type throws
   `:limner.borders/invalid-border-style` (ADR 0004)."
  [style]
  (cond
    (keyword? style)
    (or (get border-styles style)
        (throw (ex-info (str "Unknown border style: " style)
                        {:limner/error :limner.borders/unknown-border-style
                         :style style
                         :known (sort (keys border-styles))})))

    (or (vector? style) (map? style)) (custom-style style)

    :else (invalid-border-style! style)))

(defn draw-horizontal
  "Draw horizontal border line with corners"
  [left right width horizontal-char]
  (str left (apply str (repeat width horizontal-char)) right))

(defn draw-content-line
  "Draw a content line with vertical borders and padding"
  [content width vertical-char]
  (let [visible (core/visible-width content)
        needed  (- width visible)]
    (str vertical-char " " content (apply str (repeat needed " ")) " " vertical-char)))

(defn draw-box
  "Draw a box around lines with the given border style
   Returns a vector of strings (one per line)

   Handles edge cases:
   - Empty lines: creates empty box
   - Invalid border style: throws ex-info
   - Non-string lines: throws ex-info

   Throws ex-info if lines is not a collection or contains non-strings."
  [lines & {:keys [border-style] :or {border-style :single}}]
  (validate-lines lines)
  (validate-border-style border-style)
  (if (empty? lines)
    ;; Empty box - minimum 2x2
    (let [[tl tr bl br h _] (get-border-chars border-style)]
      [(str tl h h tr)
       (str bl h h br)])
    ;; Normal box with content
    (let [[tl tr bl br h v] (get-border-chars border-style)
          maxw (+ (apply max 0 (map core/visible-width lines)) 4)
          top-line (draw-horizontal tl tr (max 0 (- maxw 2)) h)
          bottom-line (draw-horizontal bl br (max 0 (- maxw 2)) h)
          content-lines (map #(draw-content-line % (max 0 (- maxw 4)) v) lines)]
      (concat [top-line] content-lines [bottom-line]))))

(defn draw-titled-box
  "Draw a box with a title in the top border
   Title can be positioned :left, :center, or :right

   Handles edge cases:
   - Empty lines: creates box with title only
   - Empty title: renders an empty title segment
   - Invalid title-pos: throws ex-info
   - Non-string lines: throws ex-info

   Throws ex-info if lines is not a collection or contains non-strings."
  [title lines & {:keys [border-style title-pos]
                  :or {border-style :single title-pos :left}}]
  (validate-lines lines)
  (validate-border-style border-style)
  (when-not (string? title)
    (throw (ex-info "Title must be a string"
                    {:limner/error :limner.borders/invalid-title
                     :title title :type (type title)})))
  (when-not (#{:left :center :right} title-pos)
    (throw (ex-info (str "Invalid title position: " title-pos)
                    {:limner/error :limner.borders/invalid-title-pos
                     :title-pos title-pos :allowed [:left :center :right]})))
  (let [[tl tr bl br h v] (get-border-chars border-style)
        title-str (str " " title " ")
        title-len (count title-str)
        ;; Handle empty lines case
        content-maxw (if (empty? lines)
                      0
                      (+ (apply max 0 (map core/visible-width lines)) 4))
        ;; Ensure box is wide enough for both content and title
        maxw (max content-maxw (+ title-len 2))
        inner-width (max 0 (- maxw 2))

        [left-pad right-pad] (case title-pos
                               :left   [0 (max 0 (- inner-width title-len))]
                               :center (let [pad (quot (- inner-width title-len) 2)]
                                        [pad (max 0 (- inner-width title-len pad))])
                               :right  [(max 0 (- inner-width title-len)) 0])

        top-line (str tl
                     (apply str (repeat (max 0 left-pad) h))
                     title-str
                     (apply str (repeat (max 0 right-pad) h))
                     tr)
        bottom-line (draw-horizontal bl br inner-width h)
        content-lines (map #(draw-content-line % (max 0 (- maxw 4)) v) lines)]
    (concat [top-line] content-lines [bottom-line])))

;; ────────────────────── Shadow Effects ──────────────────────
(defn add-shadow
  "Add a drop shadow effect to box lines
   Shadow appears on the right and bottom edges (light source from above)
   Options:
   - :shadow-char - character to use for shadow (default '░')
   - :shadow-color - ANSI color for shadow (default nil)

   Handles edge cases:
   - Empty box-lines: returns empty vector
   - Non-collection box-lines: throws ex-info"
  [box-lines & {:keys [shadow-char shadow-color]
                :or {shadow-char "░"}}]
  (validate-lines box-lines)
  (if (empty? box-lines)
      []
      (let [shadow-ch (if shadow-color
                        (core/color shadow-color shadow-char)
                        shadow-char)
            ;; Get width before adding shadows
            box-width (core/visible-width (first box-lines))
            ;; Add shadow to right edge - skip first line (light hits it from above)
            with-right-shadow (map-indexed
                              (fn [idx line]
                                (if (zero? idx)
                                  line  ; No shadow on top border
                                  (str line shadow-ch)))
                              box-lines)
            ;; Add bottom shadow line - offset by 2 spaces, fills entire width
            bottom-shadow (str "  " (apply str (repeat box-width shadow-ch)))]
        (concat with-right-shadow [bottom-shadow]))))

(defn add-heavy-shadow
  "Add a heavier 2-character shadow effect (light source from above)

   Handles edge cases:
   - Empty box-lines: returns empty vector
   - Non-collection box-lines: throws ex-info"
  [box-lines & {:keys [shadow-char shadow-color]
                :or {shadow-char "▓"}}]
  (validate-lines box-lines)
  (if (empty? box-lines)
      []
      (let [shadow-ch (if shadow-color
                        (core/color shadow-color shadow-char)
                        shadow-char)
            ;; Get width before adding shadows
            box-width (core/visible-width (first box-lines))
            ;; Add 2-char shadow to right edge - skip first line (light hits it from above)
            with-right-shadow (map-indexed
                              (fn [idx line]
                                (if (zero? idx)
                                  line  ; No shadow on top border
                                  (str line shadow-ch shadow-ch)))
                              box-lines)
            ;; Add 2 bottom shadow lines - offset by 2 spaces, fills entire width
            bottom-shadow-1 (str "  " (apply str (repeat box-width shadow-ch)))
            bottom-shadow-2 (str "  " (apply str (repeat box-width shadow-ch)))]
        (concat with-right-shadow [bottom-shadow-1 bottom-shadow-2]))))

;; ────────────────────── Nested Box Support ──────────────────────
(defn indent-lines
  "Indent all lines by n spaces

   Validates inputs and handles edge cases:
   - Negative n: uses 0 (no indent)
   - Empty lines: returns empty sequence"
  [lines n]
  (when-not (integer? n)
    (throw (ex-info "Indent must be an integer"
                    {:limner/error :limner.borders/invalid-dimension
                     :n n :type (type n)})))
  (validate-lines lines)
  (let [safe-n (max 0 n)
        indent (apply str (repeat safe-n " "))]
    (map #(str indent %) lines)))

(defn nest-box
  "Nest a box inside another with padding
   Returns lines that can be used as content for outer box

   Validates inputs and handles edge cases:
   - Negative padding: uses 0
   - Empty inner-box-lines: returns padding lines only"
  [inner-box-lines padding]
  (when-not (integer? padding)
    (throw (ex-info "Padding must be an integer"
                    {:limner/error :limner.borders/invalid-dimension
                     :padding padding :type (type padding)})))
  (validate-lines inner-box-lines)
  (let [safe-padding (max 0 padding)
        indented (indent-lines inner-box-lines safe-padding)
        top-padding (repeat safe-padding "")
        bottom-padding (repeat safe-padding "")]
    (concat top-padding indented bottom-padding)))

(defn side-by-side
  "Place two boxes side by side with spacing between them
   Returns combined lines suitable for wrapping in outer box

   Validates inputs and handles edge cases:
   - Negative spacing: uses 0
   - Different heights: pads shorter box with spaces
   - Empty boxes: returns empty sequence"
  [left-box-lines right-box-lines spacing]
  (when-not (integer? spacing)
    (throw (ex-info "Spacing must be an integer"
                    {:limner/error :limner.borders/invalid-dimension
                     :spacing spacing :type (type spacing)})))
  (validate-lines left-box-lines)
  (validate-lines right-box-lines)
  (if (or (empty? left-box-lines) (empty? right-box-lines))
      []
      (let [safe-spacing (max 0 spacing)
            left-width (core/visible-width (first left-box-lines))
            space-str (apply str (repeat safe-spacing " "))
            max-height (max (count left-box-lines) (count right-box-lines))
            ;; Pad shorter box with empty lines
            padded-left (concat left-box-lines
                               (repeat (- max-height (count left-box-lines))
                                      (apply str (repeat left-width " "))))
            padded-right (concat right-box-lines
                                (repeat (- max-height (count right-box-lines)) ""))]
        (map #(str %1 space-str %2) padded-left padded-right))))

;; ────────────────────── Colored Borders ──────────────────────
(defn colorize-border
  "Apply color to border characters in box lines
   Leaves content unchanged

   Validates inputs and handles edge cases:
   - Empty box-lines: returns empty sequence
   - Invalid color: throws ex-info with :limner.core/invalid-color
   - Single character lines: colors entire line"
  [box-lines color]
  (validate-lines box-lines)
  (if (empty? box-lines)
      []
      (map (fn [line]
             (if (or (str/starts-with? line "┌")
                      (str/starts-with? line "└")
                      (str/starts-with? line "╭")
                      (str/starts-with? line "╰")
                      (str/starts-with? line "╔")
                      (str/starts-with? line "╚")
                      (str/starts-with? line "┏")
                      (str/starts-with? line "┗")
                      (str/starts-with? line "├")
                      (str/starts-with? line "+"))
                 (core/color color line)
                 ;; For content lines, color just the edge characters
                 (if (< (count line) 2)
                   ;; Line too short, color entire line
                   (core/color color line)
                   (let [first-ch (subs line 0 1)
                         last-idx (dec (count line))
                         last-ch (subs line last-idx)
                         middle (subs line 1 last-idx)]
                     (str (core/color color first-ch)
                          middle
                          (core/color color last-ch))))))
           box-lines)))
