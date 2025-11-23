(ns limner.components.panel
  "Panel component - content containers with titles and borders"
  (:require [clojure.string :as str]
            [limner.borders :as borders]
            [limner.core :as core]))

;; ────────────────────── Panel State ──────────────────────
(defn panel
  "Create a panel component
   Options:
   - :title - panel title (displayed in border)
   - :title-pos - title position (:left :center :right, default :left)
   - :border-style - border style (default :single)
   - :content - content lines (string or vector of strings)
   - :width - fixed width (auto-sizes if not specified)
   - :height - fixed height (auto-sizes if not specified)
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
  "Convert content to vector of lines"
  [content]
  (cond
    (string? content) (str/split-lines content)
    (vector? content) content
    (nil? content) []
    :else [(str content)]))

(defn add-padding
  "Add padding to content lines"
  [lines padding]
  (if (pos? padding)
    (let [pad-str (apply str (repeat padding " "))
          padded (map #(str pad-str % pad-str) lines)
          empty-line (apply str (repeat (+ (* 2 padding) (core/visible-length (first lines))) " "))
          vertical-padding (repeat padding empty-line)]
      (vec (concat vertical-padding padded vertical-padding)))
    lines))

(defn clip-content
  "Clip content to visible area considering scroll offset"
  [lines scroll-offset visible-height]
  (let [start scroll-offset
        end (+ scroll-offset visible-height)]
    (->> lines
         (drop start)
         (take visible-height)
         vec)))

;; ────────────────────── Scrollbar ──────────────────────
(defn scrollbar-indicator
  "Calculate scrollbar position and size
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
  "Add scrollbar indicator to the right edge of content lines"
  [lines scrollbar-info]
  (if-not (:show? scrollbar-info)
    lines
    (let [{:keys [position size]} scrollbar-info
          scrollbar-positions (set (range position (+ position size)))]
      (vec (map-indexed
            (fn [idx line]
              (let [indicator (if (contains? scrollbar-positions idx) "█" "│")]
                (str line " " (core/color :cyan indicator))))
            lines)))))

;; ────────────────────── Rendering ──────────────────────
(defn render-collapsed
  "Render a collapsed panel (just the title bar)"
  [{:keys [title border-style width]}]
  (let [collapse-indicator " [+] "
        title-with-indicator (str title collapse-indicator)
        content-lines [(str (apply str (repeat (or width 20) " ")))]]
    (if title
      (borders/draw-titled-box title-with-indicator content-lines
                               :border-style border-style
                               :title-pos :left)
      (borders/draw-box content-lines :border-style border-style))))

(defn render-expanded
  "Render an expanded panel with content"
  [{:keys [title title-pos border-style content width height padding
           scrollable scroll-offset collapsible] :as panel-state}]
  (let [;; Process content
        lines (content-lines content)
        padded (if (pos? padding)
                (add-padding lines padding)
                lines)

        ;; Calculate dimensions
        content-height (count padded)
        visible-height (or height content-height)

        ;; Handle scrolling
        scrollbar-info (when scrollable
                        (scrollbar-indicator content-height visible-height scroll-offset))
        clipped (if scrollable
                 (clip-content padded scroll-offset visible-height)
                 (take visible-height padded))

        ;; Add scrollbar if needed
        with-scrollbar (if (and scrollable (:show? scrollbar-info))
                        (add-scrollbar clipped scrollbar-info)
                        clipped)

        ;; Ensure minimum content
        final-content (if (empty? with-scrollbar)
                       [""]
                       with-scrollbar)

        ;; Add collapse indicator to title if collapsible
        final-title (if (and title collapsible)
                     (str title " [-]")
                     title)]

    ;; Render border
    (if final-title
      (borders/draw-titled-box final-title final-content
                               :border-style border-style
                               :title-pos title-pos)
      (borders/draw-box final-content :border-style border-style))))

(defn render
  "Render a panel to a vector of strings"
  [panel-state]
  (vec
   (if (:collapsed panel-state)
     (render-collapsed panel-state)
     (render-expanded panel-state))))

(defn render-to-string
  "Render a panel to a single string with newlines"
  [panel-state]
  (str/join "\n" (render panel-state)))

;; ────────────────────── Panel Operations ──────────────────────
(defn toggle-collapse
  "Toggle panel collapsed state"
  [panel-state]
  (if (:collapsible panel-state)
    (assoc panel-state :collapsed (not (:collapsed panel-state)))
    panel-state))

(defn scroll-to
  "Scroll panel to specific offset"
  [panel-state offset]
  (if (:scrollable panel-state)
    (let [lines (content-lines (:content panel-state))
          max-offset (max 0 (- (count lines) (or (:height panel-state) (count lines))))]
      (assoc panel-state :scroll-offset (min max-offset (max 0 offset))))
    panel-state))

(defn scroll-up
  "Scroll panel up by n lines"
  [panel-state n]
  (scroll-to panel-state (- (:scroll-offset panel-state) n)))

(defn scroll-down
  "Scroll panel down by n lines"
  [panel-state n]
  (scroll-to panel-state (+ (:scroll-offset panel-state) n)))

(defn set-content
  "Update panel content"
  [panel-state content]
  (assoc panel-state :content content))

;; ────────────────────── Nested Panels ──────────────────────
(defn nest-panels
  "Nest child panels inside a parent panel
   Children can be a single panel or vector of panels"
  [parent-panel children & {:keys [spacing] :or {spacing 1}}]
  (let [child-panels (if (vector? children) children [children])
        rendered-children (map render child-panels)
        ;; Add spacing lines between panels
        with-spacing (if (> (count rendered-children) 1)
                      (interpose (vec (repeat spacing "")) rendered-children)
                      rendered-children)
        ;; Flatten all lines from all panels
        all-lines (apply concat with-spacing)
        ;; Combine into vector for content
        combined-content (vec all-lines)]
    (set-content parent-panel combined-content)))

;; ────────────────────── Panel Helpers ──────────────────────
(defn panel?
  "Check if value is a panel"
  [x]
  (and (map? x)
       (contains? x :border-style)))

(defn scrollable?
  "Check if panel is scrollable"
  [panel-state]
  (:scrollable panel-state))

(defn collapsible?
  "Check if panel is collapsible"
  [panel-state]
  (:collapsible panel-state))

(defn collapsed?
  "Check if panel is currently collapsed"
  [panel-state]
  (:collapsed panel-state))

(defn can-scroll-up?
  "Check if panel can scroll up"
  [panel-state]
  (and (scrollable? panel-state)
       (pos? (:scroll-offset panel-state))))

(defn can-scroll-down?
  "Check if panel can scroll down"
  [panel-state]
  (and (scrollable? panel-state)
       (let [lines (content-lines (:content panel-state))
             height (or (:height panel-state) (count lines))
             max-offset (- (count lines) height)]
         (< (:scroll-offset panel-state) max-offset))))
