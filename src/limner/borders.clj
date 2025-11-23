(ns limner.borders
  "Border drawing utilities with various styles"
  (:require [clojure.string :as str]
            [limner.core :as core]))

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
   - Default to :single if not found"
  [style]
  (cond
    (keyword? style) (get border-styles style (border-styles :single))
    (or (vector? style) (map? style)) (custom-style style)
    :else (border-styles :single)))

(defn draw-horizontal
  "Draw horizontal border line with corners"
  [left right width horizontal-char]
  (str left (apply str (repeat width horizontal-char)) right))

(defn draw-content-line
  "Draw a content line with vertical borders and padding"
  [content width vertical-char]
  (let [visible (core/visible-length content)
        needed  (- width visible)]
    (str vertical-char " " content (apply str (repeat needed " ")) " " vertical-char)))

(defn draw-box
  "Draw a box around lines with the given border style
   Returns a vector of strings (one per line)"
  [lines & {:keys [border-style] :or {border-style :single}}]
  (let [[tl tr bl br h v] (get-border-chars border-style)
        maxw (+ (apply max (map core/visible-length lines)) 4)
        top-line (draw-horizontal tl tr (- maxw 2) h)
        bottom-line (draw-horizontal bl br (- maxw 2) h)
        content-lines (map #(draw-content-line % (- maxw 4) v) lines)]
    (concat [top-line] content-lines [bottom-line])))

(defn draw-titled-box
  "Draw a box with a title in the top border
   Title can be positioned :left, :center, or :right"
  [title lines & {:keys [border-style title-pos]
                  :or {border-style :single title-pos :left}}]
  (let [[tl tr bl br h v] (get-border-chars border-style)
        title-str (str " " title " ")
        title-len (count title-str)
        content-maxw (+ (apply max (map core/visible-length lines)) 4)
        ;; Ensure box is wide enough for both content and title
        maxw (max content-maxw (+ title-len 2))
        inner-width (- maxw 2)

        ;; Calculate title position
        [left-pad right-pad] (case title-pos
                               :left   [0 (- inner-width title-len)]
                               :center (let [pad (quot (- inner-width title-len) 2)]
                                        [pad (- inner-width title-len pad)])
                               :right  [(- inner-width title-len) 0]
                               [0 (- inner-width title-len)])

        top-line (str tl
                     (apply str (repeat left-pad h))
                     title-str
                     (apply str (repeat right-pad h))
                     tr)
        bottom-line (draw-horizontal bl br inner-width h)
        content-lines (map #(draw-content-line % (- maxw 4) v) lines)]
    (concat [top-line] content-lines [bottom-line])))

;; ────────────────────── Shadow Effects ──────────────────────
(defn add-shadow
  "Add a drop shadow effect to box lines
   Shadow appears on the right and bottom edges
   Options:
   - :shadow-char - character to use for shadow (default '░')
   - :shadow-color - ANSI color for shadow (default nil)"
  [box-lines & {:keys [shadow-char shadow-color]
                :or {shadow-char "░"}}]
  (let [shadow-ch (if shadow-color
                    (core/color shadow-color shadow-char)
                    shadow-char)
        ;; Add shadow to right edge of each line (except last)
        with-right-shadow (map-indexed
                          (fn [idx line]
                            (if (< idx (dec (count box-lines)))
                              (str line shadow-ch)
                              line))
                          box-lines)
        ;; Add bottom shadow line
        last-line (last with-right-shadow)
        shadow-width (core/visible-length last-line)
        bottom-shadow (str " " (apply str (repeat (dec shadow-width) shadow-ch)))]
    (concat with-right-shadow [bottom-shadow])))

(defn add-heavy-shadow
  "Add a heavier 2-character shadow effect"
  [box-lines & {:keys [shadow-char shadow-color]
                :or {shadow-char "▓"}}]
  (let [shadow-ch (if shadow-color
                    (core/color shadow-color shadow-char)
                    shadow-char)
        ;; Add 2-char shadow to right edge
        with-right-shadow (map-indexed
                          (fn [idx line]
                            (if (< idx (- (count box-lines) 2))
                              (str line shadow-ch shadow-ch)
                              (if (= idx (- (count box-lines) 2))
                                (str line shadow-ch shadow-ch)
                                line)))
                          box-lines)
        ;; Add 2 bottom shadow lines
        last-line (last with-right-shadow)
        shadow-width (core/visible-length last-line)
        bottom-shadow-1 (str "  " (apply str (repeat (- shadow-width 2) shadow-ch)))
        bottom-shadow-2 (str "  " (apply str (repeat (- shadow-width 2) shadow-ch)))]
    (concat with-right-shadow [bottom-shadow-1 bottom-shadow-2])))

;; ────────────────────── Nested Box Support ──────────────────────
(defn indent-lines
  "Indent all lines by n spaces"
  [lines n]
  (let [indent (apply str (repeat n " "))]
    (map #(str indent %) lines)))

(defn nest-box
  "Nest a box inside another with padding
   Returns lines that can be used as content for outer box"
  [inner-box-lines padding]
  (let [indented (indent-lines inner-box-lines padding)
        top-padding (repeat padding "")
        bottom-padding (repeat padding "")]
    (concat top-padding indented bottom-padding)))

(defn side-by-side
  "Place two boxes side by side with spacing between them
   Returns combined lines suitable for wrapping in outer box"
  [left-box-lines right-box-lines spacing]
  (let [left-width (core/visible-length (first left-box-lines))
        space-str (apply str (repeat spacing " "))
        max-height (max (count left-box-lines) (count right-box-lines))
        ;; Pad shorter box with empty lines
        padded-left (concat left-box-lines
                           (repeat (- max-height (count left-box-lines))
                                  (apply str (repeat left-width " "))))
        padded-right (concat right-box-lines
                            (repeat (- max-height (count right-box-lines)) ""))]
    (map #(str %1 space-str %2) padded-left padded-right)))

;; ────────────────────── Colored Borders ──────────────────────
(defn colorize-border
  "Apply color to border characters in box lines
   Leaves content unchanged"
  [box-lines color]
  (map (fn [line]
         ;; This is a simplified version - colorizes entire line if it's a border
         ;; More sophisticated version would only color border chars
         (if (or (str/starts-with? line "┌")
                (str/starts-with? line "└")
                (str/starts-with? line "╔")
                (str/starts-with? line "╚")
                (str/starts-with? line "├")
                (str/starts-with? line "+"))
           (core/color color line)
           ;; For content lines, color just the edge characters
           (let [first-ch (subs line 0 1)
                 last-idx (dec (count line))
                 last-ch (subs line last-idx)
                 middle (subs line 1 last-idx)]
             (str (core/color color first-ch)
                  middle
                  (core/color color last-ch)))))
       box-lines))
