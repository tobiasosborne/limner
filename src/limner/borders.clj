(ns limner.borders
  "Column-correct box drawing.

  A box is measured in terminal display columns with `limner.text`, never in
  UTF-16 units, so a CJK or emoji title fits the same geometry as ASCII and a
  title that already carries ANSI keeps its style. Border segments are
  coloured from their position in the box and their known glyph set, not by
  sniffing one corner character. Recolouring preserves existing content and
  title spans, including OSC 8 links.

  Tabs in content are terminal cursor motion, not glyphs. Measuring a tab as
  zero columns would make the box disagree with the terminal, so content tabs
  expand to spaces up to the next multiple of eight columns before layout.

  Invalid input throws `ex-info` with an ADR 0004 class:
  `:limner.borders/invalid-lines`, `:limner.borders/invalid-border-style`,
  `:limner.borders/unknown-border-style`, `:limner.borders/invalid-title`,
  `:limner.borders/invalid-title-pos`, or `:limner.borders/invalid-dimension`."
  (:require [limner.borders.effects :as effects]
            [limner.borders.internal :as internal]
            [limner.core :as core]
            [limner.text :as text]))

;; ────────────────────── Validation ──────────────────────

(defn- validate-lines [lines]
  (internal/validate-lines lines))

(def ^:private custom-style-keys
  [:top-left :top-right :bottom-left :bottom-right :horizontal :vertical])

(defn- valid-border-glyph? [glyph]
  (and (string? glyph) (= 1 (text/width glyph))))

(defn- valid-custom-style? [style]
  (cond
    (vector? style) (and (= 6 (count style)) (every? valid-border-glyph? style))
    (map? style) (every? #(valid-border-glyph? (get style %)) custom-style-keys)
    :else false))

(defn- invalid-border-style! [style]
  (throw (ex-info "Border style must be a known keyword or six one-column strings"
                  {:limner/error :limner.borders/invalid-border-style
                   :style style :type (type style)
                   :limner/hint (str "Use a predefined keyword or supply one-column strings for "
                                     (pr-str custom-style-keys))})))

(defn- validate-border-style [style]
  (when-not (or (keyword? style) (valid-custom-style? style))
    (invalid-border-style! style))
  style)

(defn- validate-custom-style [style]
  (when-not (valid-custom-style? style)
    (invalid-border-style! style))
  style)

;; ────────────────────── Styles ──────────────────────

(def border-styles
  "Available border styles with their character sets.
  Format: [top-left top-right bottom-left bottom-right horizontal vertical]"
  {:single  ["┌" "┐" "└" "┘" "─" "│"]
   :double  ["╔" "╗" "╚" "╝" "═" "║"]
   :rounded ["╭" "╮" "╰" "╯" "─" "│"]
   :thick   ["┏" "┓" "┗" "┛" "━" "┃"]
   :ascii   ["+" "+" "+" "+" "-" "|"]
   :dots    ["·" "·" "·" "·" "·" "·"]
   :stars   ["*" "*" "*" "*" "*" "*"]})

(def ^:private all-border-glyphs
  "Every glyph used by a predefined style, for structural border detection."
  (into #{} (mapcat identity (vals border-styles))))

(defn custom-style
  "Create a custom border style.

  `spec` is a six-string vector `[top-left top-right bottom-left bottom-right
  horizontal vertical]` or a map with those named keys. Every string must
  occupy exactly one display column. An incomplete or geometrically invalid
  spec throws `:limner.borders/invalid-border-style`."
  [spec]
  (validate-custom-style spec)
  (if (vector? spec)
    spec
    [(:top-left spec) (:top-right spec) (:bottom-left spec) (:bottom-right spec)
     (:horizontal spec) (:vertical spec)]))

(defn get-border-chars
  "Return `[top-left top-right bottom-left bottom-right horizontal vertical]`.

  A keyword is looked up in `border-styles`; a vector or map is a custom
  style. An unknown keyword throws `:limner.borders/unknown-border-style`; a
  value of another type throws `:limner.borders/invalid-border-style`."
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

;; ────────────────────── Tab policy ──────────────────────

(defn- expand-tabs
  "Expand each tab to spaces up to the next multiple of eight columns.

  Returns canonical styled text so an already-coloured line keeps its spans.
  A tab is a cursor motion, not a zero-width glyph, so without this the box
  and the terminal would disagree about the line's physical width."
  [line]
  (let [units (text/clusters line)]
    (loop [remaining (seq units), column 0, spans []]
      (if-let [{:keys [cluster width style raw]} (first remaining)]
        (let [prefix (if (seq raw) [{:raw raw}] [])]
          (if (= cluster "\t")
            (let [spaces (- 8 (mod column 8))]
              (recur (next remaining) (+ column spaces)
                     (into (into spans prefix)
                           [{:text (apply str (repeat spaces " ")) :style style}])))
            (recur (next remaining) (+ column width)
                   (if (empty? cluster)
                     (into spans prefix)
                     (conj (into spans prefix) {:text cluster :style style})))))
        (text/concat spans)))))

;; ────────────────────── Drawing ──────────────────────

(defn draw-horizontal
  "Draw a horizontal border line with `left` and `right` corners."
  [left right width horizontal-char]
  (str left (apply str (repeat width horizontal-char)) right))

(defn draw-content-line
  "Draw one content line between `vertical-char` edges, padded to `width`.

  `content` may be a string or a styled value. An already-styled line keeps
  its spans; it is not flattened to plain text."
  [content width vertical-char]
  (let [visible (text/width content)
        needed (max 0 (- width visible))]
    (text/to-ansi
     (text/concat vertical-char " " content
                  (apply str (repeat needed " ")) " " vertical-char))))

(defn draw-box
  "Draw a box around `lines` with `:border-style` (default `:single`).

  Every returned line has the same display width. `lines` may hold ANSI
  strings or styled values; content is measured and padded through
  `limner.text`. Empty input still draws a two-row empty box."
  [lines & {:keys [border-style] :or {border-style :single}}]
  (validate-lines lines)
  (validate-border-style border-style)
  (let [lines (mapv expand-tabs lines)]
    (if (empty? lines)
      (let [[tl tr bl br h _] (get-border-chars border-style)]
        [(str tl h h tr) (str bl h h br)])
      (let [[tl tr bl br h v] (get-border-chars border-style)
            maxw (+ (apply max 0 (map text/width lines)) 4)
            top-line (draw-horizontal tl tr (max 0 (- maxw 2)) h)
            bottom-line (draw-horizontal bl br (max 0 (- maxw 2)) h)
            content-lines (map #(draw-content-line % (max 0 (- maxw 4)) v) lines)]
        (concat [top-line] content-lines [bottom-line])))))

(defn draw-titled-box
  "Draw a box with `title` embedded in the top border.

  `:title-pos` is `:left`, `:center` (default `:left`) or `:right`; anything
  else throws `:limner.borders/invalid-title-pos`. The title is measured in
  display columns, so CJK and ANSI titles keep the box rectangular. An empty
  title still renders its two surrounding spaces."
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
  (let [lines (mapv expand-tabs lines)
        [tl tr bl br h v] (get-border-chars border-style)
        title-str (text/concat " " title " ")
        title-len (text/width title-str)
        content-maxw (if (empty? lines)
                       0
                       (+ (apply max 0 (map text/width lines)) 4))
        maxw (max content-maxw (+ title-len 2))
        inner-width (max 0 (- maxw 2))
        [left-pad right-pad] (case title-pos
                               :left   [0 (max 0 (- inner-width title-len))]
                               :center (let [pad (quot (- inner-width title-len) 2)]
                                         [pad (max 0 (- inner-width title-len pad))])
                               :right  [(max 0 (- inner-width title-len)) 0])
        top-line (text/to-ansi
                  (text/concat tl
                               (apply str (repeat (max 0 left-pad) h))
                               title-str
                               (apply str (repeat (max 0 right-pad) h))
                               tr))
        bottom-line (draw-horizontal bl br inner-width h)
        content-lines (map #(draw-content-line % (max 0 (- maxw 4)) v) lines)]
    (concat [top-line] content-lines [bottom-line])))

;; ────────────────────── Coloured borders ──────────────────────

(defn- unit-value [{:keys [cluster style raw]}]
  (text/concat (if (seq raw) [{:raw raw}] [])
               (if (empty? cluster) [] [{:text cluster :style style}])))

(defn- color-unit [unit color]
  (text/from-ansi (core/color color (text/to-ansi (unit-value unit)))))

(defn- edge-flags
  "Mark the uninterrupted border-glyph run at the start of `units`."
  [units glyph-set]
  (loop [remaining (seq units), at-edge? true, result []]
    (if-let [unit (first remaining)]
      (let [border? (and at-edge? (contains? glyph-set (:cluster unit)))]
        (recur (next remaining) border? (conj result border?)))
      result)))

(defn- border-row-flags
  "Mark structural runs from both edges, leaving a title between spaces alone."
  [units glyph-set]
  (let [left (edge-flags units glyph-set)
        right (reverse (edge-flags (reverse units) glyph-set))]
    (mapv #(or %1 %2) left right)))

(defn- content-row-flags
  "Mark only the first and last grapheme units of an interior row."
  [units]
  (loop [remaining (seq units), first? true, result []]
    (if (nil? remaining)
      result
      (recur (next remaining) false
             (conj result (or first? (nil? (next remaining))))))))

(defn- colorize-line [line flags color]
  (let [units (text/clusters line)]
    (text/to-ansi
     (apply text/concat
            (map (fn [unit border?]
                   (if border? (color-unit unit color) (unit-value unit)))
                 units flags)))))

(defn colorize-border
  "Colour only the border segments of `box-lines`, leaving content unchanged.

  `box-lines` is a box returned by `draw-box` or `draw-titled-box`. The first
  and last rows are border rows: every glyph that belongs to a known border
  style is coloured, so rounded and thick rows are coloured end to end while a
  title stays uncoloured even when it contains a border-like glyph. Interior
  rows colour only their first and last glyph. Existing title/content styles
  and OSC 8 links survive. Pass `:border-style` for a custom glyph set; the
  default recognises every predefined style's glyphs.

  Non-string lines throw `:limner.borders/invalid-lines`; an invalid colour
  throws the `limner.core` classes."
  [box-lines color & {:keys [border-style]}]
  (validate-lines box-lines)
  (let [glyph-set (if border-style
                    (set (get-border-chars border-style))
                    all-border-glyphs)
        last-idx (dec (count box-lines))]
    (vec (map-indexed
          (fn [idx line]
            (let [units (text/clusters line)
                  flags (if (or (zero? idx) (= idx last-idx))
                          (border-row-flags units glyph-set)
                          (content-row-flags units))]
              (colorize-line line flags color)))
          box-lines))))

;; ────────────────────── Effects and composition ──────────────────────

(defn add-shadow
  "Add a one-column drop shadow to the right and bottom edges.

  `:shadow-char` defaults to `░`; `:shadow-color` colours the shadow."
  [box-lines & options]
  (apply effects/add-shadow box-lines options))

(defn add-heavy-shadow
  "Add a two-column drop shadow to the right and bottom edges."
  [box-lines & options]
  (apply effects/add-heavy-shadow box-lines options))

(defn indent-lines
  "Indent every line with `n` spaces; a negative `n` means no indent."
  [lines n]
  (effects/indent-lines lines n))

(defn nest-box
  "Wrap `inner-box-lines` in `padding` blank lines and `padding` spaces."
  [inner-box-lines padding]
  (effects/nest-box inner-box-lines padding))

(defn side-by-side
  "Place two boxes side by side with `spacing` columns between them."
  [left-box-lines right-box-lines spacing]
  (effects/side-by-side left-box-lines right-box-lines spacing))
