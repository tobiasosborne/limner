(ns limner.components.panel
  "Content panels with a title, border, padding, and optional scrolling.

  Invariants: when `:width` is set, every rendered line is exactly `:width`
  display columns wide, including the border. Content and title are measured,
  truncated, and padded through `limner.text`, so CJK, emoji, and content that
  already carries ANSI keep their true columns and styles. The title is cut to
  the inner width before it is drawn, so it cannot widen the border.

  `:width` must leave room for the border; anything below four columns throws
  `:limner.components.panel/invalid-width` (ADR 0004).
  Omitting `:width` auto-sizes the panel to its widest content line."
  (:require [clojure.string :as str]
            [limner.borders :as borders]
            [limner.text :as text]))

;; ────────────────────── Panel State ──────────────────────
(defn panel
  "Create a panel component.
   Options:
   - :title - panel title (displayed in the border)
   - :title-pos - title position (:left :center :right, default :left)
   - :border-style - border style (default :single)
   - :content - content lines (string or vector of strings)
   - :width - total rendered width including the border (auto if omitted)
   - :height - visible height (auto if omitted)
   - :padding - internal padding (default 0)
   - :scrollable - enable scrolling (default false)
   - :scroll-offset - current scroll position (default 0)
   - :collapsed - whether panel is collapsed (default false)
   - :collapsible - whether panel can be collapsed (default false)"
  [& {:keys [title title-pos border-style content width height padding
             scrollable scroll-offset collapsed collapsible]
      :or {title-pos :left
           border-style :single
           padding 0
           scroll-offset 0
           collapsed false
           collapsible false
           scrollable false}}]
  {:title title
   :title-pos title-pos
   :border-style border-style
   :content content
   :width width
   :height height
   :padding padding
   :scrollable scrollable
   :scroll-offset scroll-offset
   :collapsed collapsed
   :collapsible collapsible})

;; ────────────────────── Content Processing ──────────────────────
(defn content-lines
  "Convert content to a vector of lines."
  [content]
  (cond
    (string? content) (str/split-lines content)
    (vector? content) content
    (nil? content) []
    :else [(str content)]))

(defn add-padding
  "Add `padding` spaces to both sides of every line and blank rows around it."
  [lines padding]
  (if (pos? padding)
    (let [pad (apply str (repeat padding " "))
          padded (mapv #(text/to-ansi (text/concat pad % pad)) lines)
          blank (apply str (repeat (* 2 padding) " "))]
      (vec (concat (repeat padding blank) padded (repeat padding blank))))
    (vec lines)))

(defn clip-content
  "Keep the lines in the viewport starting at `scroll-offset`."
  [lines scroll-offset visible-height]
  (->> lines
       (drop scroll-offset)
       (take visible-height)
       vec))

;; ────────────────────── Scrollbar ──────────────────────
(defn scrollbar-indicator
  "Calculate scrollbar position and size.
   Returns {:show? bool :position int :size int}"
  [content-height visible-height scroll-offset]
  (if (<= content-height visible-height)
    {:show? false}
    (let [viewport-ratio (/ visible-height content-height)
          scrollbar-size (max 1 (int (* visible-height viewport-ratio)))
          max-scroll (- content-height visible-height)
          scroll-ratio (if (pos? max-scroll)
                         (/ scroll-offset max-scroll)
                         0)
          scrollbar-pos (int (* (- visible-height scrollbar-size) scroll-ratio))]
      {:show? true
       :position scrollbar-pos
       :size scrollbar-size})))

(defn add-scrollbar
  "Append a coloured scrollbar indicator to the right edge of content lines."
  [lines scrollbar-info]
  (if-not (:show? scrollbar-info)
    lines
    (let [{:keys [position size]} scrollbar-info
          scrollbar-positions (set (range position (+ position size)))]
      (vec (map-indexed
            (fn [idx line]
              (let [indicator (if (contains? scrollbar-positions idx) "█" "│")]
                (text/to-ansi (text/concat line " " (text/style {:fg :cyan} indicator)))))
            lines)))))

;; ────────────────────── Width handling ──────────────────────
(defn- validate-width! [{:keys [width]}]
  (when (and width (integer? width) (< width 4))
    (throw (ex-info "Panel width must leave room for its border and content"
                    {:limner/error :limner.components.panel/invalid-width
                     :width width
                     :limner/hint "Use a width of at least 4, or omit :width to auto-size"})))
  (when (and width (not (integer? width)))
    (throw (ex-info "Panel width must be an integer"
                    {:limner/error :limner.components.panel/invalid-width
                     :width width :type (type width)}))))

(defn- fit-line
  "Truncate `line` to `columns` and pad it back out to exactly `columns`."
  [line columns]
  (text/to-ansi (text/pad-right (text/truncate line columns) columns)))

;; ────────────────────── Rendering ──────────────────────
(defn render-collapsed
  "Render a collapsed panel: just the title bar."
  [{:keys [title border-style width] :as panel-state}]
  (validate-width! panel-state)
  (let [columns (if width (- width 4) 16)
        title-with-indicator (when title (str title " [+]"))
        top-title (when title-with-indicator
                    (if width
                      (text/to-ansi (text/truncate title-with-indicator (max 0 columns)))
                      title-with-indicator))
        content-lines [(apply str (repeat (max 0 columns) " "))]]
    (if top-title
      (borders/draw-titled-box top-title content-lines
                               :border-style border-style
                               :title-pos :left)
      (borders/draw-box content-lines :border-style border-style))))

(defn render-expanded
  "Render an expanded panel with content."
  [{:keys [title title-pos border-style content width height padding
           scrollable scroll-offset collapsible] :as panel-state}]
  (validate-width! panel-state)
  (let [columns (when width (- width 4))
        lines (content-lines content)
        padded (add-padding lines padding)
        content-height (count padded)
        visible-height (or height content-height)
        scrollbar-info (when scrollable
                         (scrollbar-indicator content-height visible-height scroll-offset))
        show-scrollbar? (boolean (and scrollable (:show? scrollbar-info)))
        item-columns (when columns (max 0 (- columns (if show-scrollbar? 2 0))))
        clipped (if scrollable
                  (clip-content padded scroll-offset visible-height)
                  (vec (take visible-height padded)))
        fitted (if item-columns
                 (mapv #(fit-line % item-columns) clipped)
                 (mapv text/to-ansi clipped))
        base (if (empty? fitted)
               [(apply str (repeat (or item-columns columns 0) " "))]
               fitted)
        with-scrollbar (if show-scrollbar?
                         (add-scrollbar base scrollbar-info)
                         base)
        final-title (if (and title collapsible) (str title " [-]") title)
        title-text (when final-title
                     (if width
                       (text/to-ansi (text/truncate final-title (max 0 columns)))
                       final-title))]
    (if title-text
      (borders/draw-titled-box title-text with-scrollbar
                               :border-style border-style
                               :title-pos title-pos)
      (borders/draw-box with-scrollbar :border-style border-style))))

(defn render
  "Render a panel to a vector of strings."
  [panel-state]
  (vec
   (if (:collapsed panel-state)
     (render-collapsed panel-state)
     (render-expanded panel-state))))

(defn render-to-string
  "Render a panel to a single string with newlines."
  [panel-state]
  (str/join "\n" (render panel-state)))

;; ────────────────────── Panel Operations ──────────────────────
(defn toggle-collapse
  "Toggle panel collapsed state."
  [panel-state]
  (if (:collapsible panel-state)
    (assoc panel-state :collapsed (not (:collapsed panel-state)))
    panel-state))

(defn scroll-to
  "Scroll panel to specific offset."
  [panel-state offset]
  (if (:scrollable panel-state)
    (let [lines (content-lines (:content panel-state))
          max-offset (max 0 (- (count lines) (or (:height panel-state) (count lines))))]
      (assoc panel-state :scroll-offset (min max-offset (max 0 offset))))
    panel-state))

(defn scroll-up
  "Scroll panel up by n lines."
  [panel-state n]
  (scroll-to panel-state (- (:scroll-offset panel-state) n)))

(defn scroll-down
  "Scroll panel down by n lines."
  [panel-state n]
  (scroll-to panel-state (+ (:scroll-offset panel-state) n)))

(defn set-content
  "Update panel content."
  [panel-state content]
  (assoc panel-state :content content))

;; ────────────────────── Nested Panels ──────────────────────
(defn nest-panels
  "Nest child panels inside a parent panel.
   Children can be a single panel or vector of panels."
  [parent-panel children & {:keys [spacing] :or {spacing 1}}]
  (let [child-panels (if (vector? children) children [children])
        rendered-children (map render child-panels)
        with-spacing (if (> (count rendered-children) 1)
                       (interpose (vec (repeat spacing "")) rendered-children)
                       rendered-children)
        all-lines (apply concat with-spacing)]
    (set-content parent-panel (vec all-lines))))

;; ────────────────────── Panel Helpers ──────────────────────
(defn panel?
  "Check if value is a panel."
  [x]
  (and (map? x) (contains? x :border-style)))

(defn scrollable?
  "Check if panel is scrollable."
  [panel-state]
  (:scrollable panel-state))

(defn collapsible?
  "Check if panel is collapsible."
  [panel-state]
  (:collapsible panel-state))

(defn collapsed?
  "Check if panel is currently collapsed."
  [panel-state]
  (:collapsed panel-state))

(defn can-scroll-up?
  "Check if panel can scroll up."
  [panel-state]
  (and (scrollable? panel-state)
       (pos? (:scroll-offset panel-state))))

(defn can-scroll-down?
  "Check if panel can scroll down."
  [panel-state]
  (and (scrollable? panel-state)
       (let [lines (content-lines (:content panel-state))
             height (or (:height panel-state) (count lines))
             max-offset (- (count lines) height)]
         (< (:scroll-offset panel-state) max-offset))))
