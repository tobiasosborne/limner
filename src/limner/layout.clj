(ns limner.layout
  "Layout engine for positioning and sizing components"
  (:require [clojure.string :as str]))

;; ────────────────────── Box Model ──────────────────────
(defn box
  "Create a box with position and dimensions
   Options: :x :y :width :height :margin :padding :z-index"
  [& {:keys [x y width height margin padding z-index]
      :or {x 0 y 0 width 0 height 0 margin 0 padding 0 z-index 0}}]
  {:x x :y y :width width :height height
   :margin margin :padding padding :z-index z-index})

(defn content-box
  "Calculate inner content box after subtracting padding"
  [{:keys [x y width height padding]}]
  {:x (+ x padding)
   :y (+ y padding)
   :width (max 0 (- width (* 2 padding)))
   :height (max 0 (- height (* 2 padding)))})

;; ────────────────────── Constraints ──────────────────────
(defn fixed
  "Fixed size constraint in characters/lines"
  [value]
  {:type :fixed :value value})

(defn percent
  "Percentage of parent size (0-100)"
  [value]
  {:type :percent :value (min 100 (max 0 value))})

(defn flex
  "Flexible size, shares remaining space with other flex items
   Higher values get proportionally more space"
  [value]
  {:type :flex :value (max 0 value)})

(defn auto
  "Automatically size to content"
  []
  {:type :auto})

(defn resolve-constraint
  "Resolve a constraint to an actual size given available space and content size"
  [constraint available-space content-size]
  (case (:type constraint)
    :fixed   (:value constraint)
    :percent (int (* available-space (/ (:value constraint) 100.0)))
    :auto    content-size
    :flex    0)) ; flex is resolved separately

(defn resolve-flex-constraints
  "Distribute remaining space among flex items"
  [flex-items remaining-space]
  (let [total-flex (reduce + (map :value flex-items))]
    (if (pos? total-flex)
      (for [item flex-items]
        (int (* remaining-space (/ (:value item) total-flex))))
      (repeat (count flex-items) 0))))

;; ────────────────────── Vertical Stack Layout ──────────────────────
(defn stack
  "Stack components vertically from top to bottom
   Each component can have a height constraint:
   - {:constraint (fixed 10) :content [...]}
   - {:constraint (percent 50) :content [...]}
   - {:constraint (flex 1) :content [...]}
   - {:constraint (auto) :content [...]}

   Options:
   - :spacing - vertical space between items (default 0)
   - :padding - padding around all items (default 0)"
  [components & {:keys [spacing padding] :or {spacing 0 padding 0}}]
  (let [available-height (fn [total-height]
                           (- total-height
                              (* 2 padding)
                              (* (dec (count components)) spacing)))

        resolve-heights (fn [total-height]
                          (let [avail (available-height total-height)
                                ;; First pass: resolve fixed, percent, and auto
                                first-pass (for [{:keys [constraint content]} components]
                                            (if (= (:type constraint) :flex)
                                              {:size nil :flex constraint}
                                              {:size (resolve-constraint constraint avail
                                                       (or (:height content) 0))
                                               :flex nil}))
                                used-space (reduce + 0 (keep :size first-pass))
                                remaining (- avail used-space)
                                flex-items (keep :flex first-pass)
                                flex-sizes (resolve-flex-constraints flex-items remaining)

                                ;; Second pass: fill in flex sizes
                                flex-iter (atom 0)]
                            (for [{:keys [size flex]} first-pass]
                              (if flex
                                (let [idx @flex-iter]
                                  (swap! flex-iter inc)
                                  (nth flex-sizes idx))
                                size))))]

    {:type :stack
     :components components
     :spacing spacing
     :padding padding
     :resolve-heights resolve-heights}))

(defn layout-stack
  "Calculate actual positions and sizes for stacked components"
  [stack-layout total-width total-height]
  (let [{:keys [components spacing padding resolve-heights]} stack-layout
        heights (resolve-heights total-height)
        content-width (- total-width (* 2 padding))]

    (loop [remaining components
           remaining-heights heights
           y padding
           result []]
      (if (empty? remaining)
        result
        (let [component (first remaining)
              height (first remaining-heights)
              box (merge (box :x padding
                             :y y
                             :width content-width
                             :height height)
                        {:content (:content component)})]
          (recur (rest remaining)
                 (rest remaining-heights)
                 (+ y height spacing)
                 (conj result box)))))))

;; ────────────────────── Horizontal Split Layout ──────────────────────
(defn hsplit
  "Split space horizontally (left to right)
   Each component can have a width constraint (fixed, percent, flex, auto)

   Options:
   - :spacing - horizontal space between items (default 0)
   - :padding - padding around all items (default 0)"
  [components & {:keys [spacing padding] :or {spacing 0 padding 0}}]
  (let [available-width (fn [total-width]
                          (- total-width
                             (* 2 padding)
                             (* (dec (count components)) spacing)))

        resolve-widths (fn [total-width]
                         (let [avail (available-width total-width)
                               first-pass (for [{:keys [constraint content]} components]
                                           (if (= (:type constraint) :flex)
                                             {:size nil :flex constraint}
                                             {:size (resolve-constraint constraint avail
                                                      (or (:width content) 0))
                                              :flex nil}))
                               used-space (reduce + 0 (keep :size first-pass))
                               remaining (- avail used-space)
                               flex-items (keep :flex first-pass)
                               flex-sizes (resolve-flex-constraints flex-items remaining)
                               flex-iter (atom 0)]
                           (for [{:keys [size flex]} first-pass]
                             (if flex
                               (let [idx @flex-iter]
                                 (swap! flex-iter inc)
                                 (nth flex-sizes idx))
                               size))))]

    {:type :hsplit
     :components components
     :spacing spacing
     :padding padding
     :resolve-widths resolve-widths}))

(defn layout-hsplit
  "Calculate actual positions and sizes for horizontally split components"
  [hsplit-layout total-width total-height]
  (let [{:keys [components spacing padding resolve-widths]} hsplit-layout
        widths (resolve-widths total-width)
        content-height (- total-height (* 2 padding))]

    (loop [remaining components
           remaining-widths widths
           x padding
           result []]
      (if (empty? remaining)
        result
        (let [component (first remaining)
              width (first remaining-widths)
              box (merge (box :x x
                             :y padding
                             :width width
                             :height content-height)
                        {:content (:content component)})]
          (recur (rest remaining)
                 (rest remaining-widths)
                 (+ x width spacing)
                 (conj result box)))))))

;; ────────────────────── Grid Layout ──────────────────────
(defn grid
  "Arrange components in a grid

   Options:
   - :columns - number of columns (required)
   - :rows - number of rows (auto-calculated if not provided)
   - :spacing - space between cells (default 0)
   - :padding - padding around grid (default 0)"
  [components & {:keys [columns rows spacing padding]
                 :or {spacing 0 padding 0}
                 :as opts}]
  (assert columns "Grid layout requires :columns option")
  (let [actual-rows (or rows (int (Math/ceil (/ (count components) columns))))]
    {:type :grid
     :components components
     :columns columns
     :rows actual-rows
     :spacing spacing
     :padding padding}))

(defn layout-grid
  "Calculate actual positions and sizes for grid components"
  [grid-layout total-width total-height]
  (let [{:keys [components columns rows spacing padding]} grid-layout
        content-width (- total-width (* 2 padding) (* (dec columns) spacing))
        content-height (- total-height (* 2 padding) (* (dec rows) spacing))
        cell-width (int (/ content-width columns))
        cell-height (int (/ content-height rows))]

    (for [[idx component] (map-indexed vector components)
          :let [row (quot idx columns)
                col (rem idx columns)
                x (+ padding (* col (+ cell-width spacing)))
                y (+ padding (* row (+ cell-height spacing)))]]
      (merge (box :x x
                 :y y
                 :width cell-width
                 :height cell-height)
             {:content (:content component)}))))

;; ────────────────────── Overlay Layout ──────────────────────
(defn overlay
  "Overlay components on top of each other (z-index ordering)
   Each component should have a :z-index in its box specification"
  [components]
  {:type :overlay
   :components components})

(defn layout-overlay
  "Calculate positions for overlaid components (sorted by z-index)"
  [overlay-layout total-width total-height]
  (let [{:keys [components]} overlay-layout]
    (sort-by :z-index
             (for [component components]
               (merge (box :x 0 :y 0 :width total-width :height total-height)
                      {:content (:content component)
                       :z-index (or (:z-index component) 0)})))))

;; ────────────────────── Layout Dispatcher ──────────────────────
(defn layout
  "Apply layout to components given total dimensions
   Returns a sequence of positioned boxes with content"
  [layout-spec width height]
  (case (:type layout-spec)
    :stack   (layout-stack layout-spec width height)
    :hsplit  (layout-hsplit layout-spec width height)
    :grid    (layout-grid layout-spec width height)
    :overlay (layout-overlay layout-spec width height)
    []))

;; ────────────────────── Utility Functions ──────────────────────
(defn clip
  "Clip content to fit within box dimensions
   Returns a vector of strings, one per line"
  [content width height]
  (let [lines (str/split-lines (str content))
        clipped-lines (take height lines)]
    (mapv #(subs % 0 (min (count %) width)) clipped-lines)))

(defn pad-to-box
  "Pad content to fill box dimensions"
  [lines width height]
  (let [padded-lines (mapv #(str % (apply str (repeat (- width (count %)) " ")))
                           lines)
        line-count (count padded-lines)
        empty-lines (repeat (- height line-count) (apply str (repeat width " ")))]
    (vec (concat padded-lines empty-lines))))
