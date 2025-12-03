(ns limner.layout
  "Layout engine for positioning and sizing components"
  (:require [clojure.string :as str]))

;; ────────────────────── Error Handling & Validation ──────────────────────

(defn- validate-dimension
  "Validate that a dimension (width/height) is a non-negative integer"
  [dimension label]
  (when-not (and (integer? dimension) (>= dimension 0))
    (throw (ex-info (str "Invalid " label ": must be non-negative integer")
                    {:dimension dimension :label label :type (type dimension)})))
  dimension)

(defn- validate-spacing
  "Validate that spacing value is a non-negative number"
  [spacing]
  (when-not (and (number? spacing) (>= spacing 0))
    (throw (ex-info "Invalid spacing: must be non-negative number"
                    {:spacing spacing :type (type spacing)})))
  spacing)

(defn- validate-constraint
  "Validate that a constraint is properly formed"
  [constraint]
  (when-not (and (map? constraint) (:type constraint))
    (throw (ex-info "Invalid constraint: must be a map with :type key"
                    {:constraint constraint :type (type constraint)})))
  (when-not (#{:fixed :percent :flex :auto} (:type constraint))
    (throw (ex-info "Invalid constraint type: must be :fixed, :percent, :flex, or :auto"
                    {:constraint constraint :type (:type constraint)})))
  constraint)

;; ────────────────────── Box Model ──────────────────────
(defn box
  "Create a box with position and dimensions
   Options: :x :y :width :height :margin :padding :z-index

   All dimensions must be non-negative integers."
  [& {:keys [x y width height margin padding z-index]
      :or {x 0 y 0 width 0 height 0 margin 0 padding 0 z-index 0}}]
  (validate-dimension x "x")
  (validate-dimension y "y")
  (validate-dimension width "width")
  (validate-dimension height "height")
  (validate-dimension margin "margin")
  (validate-dimension padding "padding")
  (validate-dimension z-index "z-index")
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
  "Fixed size constraint in characters/lines

   Value must be a non-negative integer."
  [value]
  {:pre [(integer? value) (>= value 0)]}
  {:type :fixed :value value})

(defn percent
  "Percentage of parent size (0-100)

   Value must be a number between 0 and 100."
  [value]
  {:pre [(number? value)]}
  {:type :percent :value (min 100 (max 0 value))})

(defn flex
  "Flexible size, shares remaining space with other flex items
   Higher values get proportionally more space

   Value must be a non-negative number."
  [value]
  {:pre [(number? value) (>= value 0)]}
  {:type :flex :value value})

(defn auto
  "Automatically size to content"
  []
  {:type :auto})

(defn resolve-constraint
  "Resolve a constraint to an actual size given available space and content size

   All values must be non-negative integers. Returns 0 for invalid constraints."
  [constraint available-space content-size]
  {:pre [(>= available-space 0) (>= content-size 0)]}
  (try
    (validate-constraint constraint)
    (max 0 (case (:type constraint)
             :fixed   (:value constraint)
             :percent (int (* available-space (/ (:value constraint) 100.0)))
             :auto    content-size
             :flex    0)) ; flex is resolved separately
    (catch Exception e
      (binding [*out* *err*]
        (println "Warning: Error resolving constraint:" (.getMessage e)))
      0)))

(defn resolve-flex-constraints
  "Distribute remaining space among flex items

   Handles edge cases:
   - Empty flex-items: returns empty sequence
   - Zero total flex value: distributes equally
   - Negative remaining space: returns zeros"
  [flex-items remaining-space]
  {:pre [(>= remaining-space 0)]}
  (cond
    (empty? flex-items)
    []

    (neg? remaining-space)
    (repeat (count flex-items) 0)

    :else
    (let [total-flex (reduce + (map :value flex-items))]
      (if (pos? total-flex)
        (for [item flex-items]
          (max 0 (int (* remaining-space (/ (:value item) total-flex)))))
        ;; If total flex is 0, distribute equally
        (let [per-item (int (/ remaining-space (count flex-items)))]
          (repeat (count flex-items) per-item))))))

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
   - :padding - padding around all items (default 0)

   Throws ex-info if components is empty or contains invalid constraints."
  [components & {:keys [spacing padding] :or {spacing 0 padding 0}}]
  (when (empty? components)
    (throw (ex-info "Cannot create stack with empty components"
                    {:components components})))
  (validate-spacing spacing)
  (validate-spacing padding)
  (doseq [{:keys [constraint]} components]
    (when constraint
      (validate-constraint constraint)))
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
  "Calculate actual positions and sizes for stacked components

   Validates dimensions and handles edge cases gracefully."
  [stack-layout total-width total-height]
  {:pre [(>= total-width 0) (>= total-height 0)]}
  (try
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
              height (max 0 (first remaining-heights))
              box (merge (box :x padding
                             :y y
                             :width (max 0 content-width)
                             :height height)
                        {:content (:content component)})]
          (recur (rest remaining)
                 (rest remaining-heights)
                 (+ y height spacing)
                 (conj result box))))))
    (catch Exception e
      (binding [*out* *err*]
        (println "Error in layout-stack:" (.getMessage e)))
      [])))

;; ────────────────────── Horizontal Split Layout ──────────────────────
(defn hsplit
  "Split space horizontally (left to right)
   Each component can have a width constraint (fixed, percent, flex, auto)

   Options:
   - :spacing - horizontal space between items (default 0)
   - :padding - padding around all items (default 0)

   Throws ex-info if components is empty or contains invalid constraints."
  [components & {:keys [spacing padding] :or {spacing 0 padding 0}}]
  (when (empty? components)
    (throw (ex-info "Cannot create hsplit with empty components"
                    {:components components})))
  (validate-spacing spacing)
  (validate-spacing padding)
  (doseq [{:keys [constraint]} components]
    (when constraint
      (validate-constraint constraint)))
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
  "Calculate actual positions and sizes for horizontally split components

   Validates dimensions and handles edge cases gracefully."
  [hsplit-layout total-width total-height]
  {:pre [(>= total-width 0) (>= total-height 0)]}
  (try
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
              width (max 0 (first remaining-widths))
              box (merge (box :x x
                             :y padding
                             :width width
                             :height (max 0 content-height))
                        {:content (:content component)})]
          (recur (rest remaining)
                 (rest remaining-widths)
                 (+ x width spacing)
                 (conj result box))))))
    (catch Exception e
      (binding [*out* *err*]
        (println "Error in layout-hsplit:" (.getMessage e)))
      [])))

;; ────────────────────── Grid Layout ──────────────────────
(defn grid
  "Arrange components in a grid

   Options:
   - :columns - number of columns (required, must be positive integer)
   - :rows - number of rows (auto-calculated if not provided)
   - :spacing - space between cells (default 0)
   - :padding - padding around grid (default 0)

   Throws ex-info if:
   - :columns is missing or invalid
   - components is empty
   - spacing or padding is negative"
  [components & {:keys [columns rows spacing padding]
                 :or {spacing 0 padding 0}
                 :as opts}]
  (when-not columns
    (throw (ex-info "Grid layout requires :columns option"
                    {:options opts})))
  (when-not (and (integer? columns) (pos? columns))
    (throw (ex-info "Grid :columns must be a positive integer"
                    {:columns columns :type (type columns)})))
  (when (empty? components)
    (throw (ex-info "Cannot create grid with empty components"
                    {:components components})))
  (when rows
    (when-not (and (integer? rows) (pos? rows))
      (throw (ex-info "Grid :rows must be a positive integer"
                      {:rows rows :type (type rows)}))))
  (validate-spacing spacing)
  (validate-spacing padding)
  (let [actual-rows (or rows (int (Math/ceil (/ (count components) columns))))]
    {:type :grid
     :components components
     :columns columns
     :rows actual-rows
     :spacing spacing
     :padding padding}))

(defn layout-grid
  "Calculate actual positions and sizes for grid components

   Validates dimensions and prevents divide-by-zero errors."
  [grid-layout total-width total-height]
  {:pre [(>= total-width 0) (>= total-height 0)]}
  (try
    (let [{:keys [components columns rows spacing padding]} grid-layout
          content-width (max 0 (- total-width (* 2 padding) (* (dec columns) spacing)))
          content-height (max 0 (- total-height (* 2 padding) (* (dec rows) spacing)))
          ;; Prevent divide-by-zero
          cell-width (if (pos? columns) (int (/ content-width columns)) 0)
          cell-height (if (pos? rows) (int (/ content-height rows)) 0)]

      (for [[idx component] (map-indexed vector components)
            :let [row (quot idx columns)
                  col (rem idx columns)
                  x (+ padding (* col (+ cell-width spacing)))
                  y (+ padding (* row (+ cell-height spacing)))]]
        (merge (box :x x
                   :y y
                   :width cell-width
                   :height cell-height)
               {:content (:content component)})))
    (catch Exception e
      (binding [*out* *err*]
        (println "Error in layout-grid:" (.getMessage e)))
      [])))

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
   Returns a sequence of positioned boxes with content

   Validates inputs and handles unknown layout types gracefully."
  [layout-spec width height]
  {:pre [(>= width 0) (>= height 0)]}
  (try
    (when-not (and (map? layout-spec) (:type layout-spec))
      (throw (ex-info "Invalid layout-spec: must be a map with :type key"
                      {:layout-spec layout-spec})))
    (case (:type layout-spec)
      :stack   (layout-stack layout-spec width height)
      :hsplit  (layout-hsplit layout-spec width height)
      :grid    (layout-grid layout-spec width height)
      :overlay (layout-overlay layout-spec width height)
      (do
        (binding [*out* *err*]
          (println "Warning: Unknown layout type:" (:type layout-spec)))
        []))
    (catch Exception e
      (binding [*out* *err*]
        (println "Error in layout:" (.getMessage e)))
      [])))

;; ────────────────────── Utility Functions ──────────────────────
(defn clip
  "Clip content to fit within box dimensions
   Returns a vector of strings, one per line

   Handles edge cases:
   - Negative or zero dimensions return empty vector
   - nil content returns empty vector
   - Validates width and height are non-negative"
  [content width height]
  {:pre [(>= width 0) (>= height 0)]}
  (try
    (cond
      (or (nil? content) (zero? width) (zero? height))
      []

      :else
      (let [lines (str/split-lines (str content))
            clipped-lines (take height lines)]
        (mapv #(subs % 0 (min (count %) width)) clipped-lines)))
    (catch Exception e
      (binding [*out* *err*]
        (println "Error in clip:" (.getMessage e)))
      [])))

(defn pad-to-box
  "Pad content to fill box dimensions

   Handles edge cases:
   - Negative or zero dimensions return empty vector
   - Empty or nil lines return vector of empty lines
   - Validates width and height are non-negative"
  [lines width height]
  {:pre [(>= width 0) (>= height 0)]}
  (try
    (cond
      (or (zero? width) (zero? height))
      []

      (or (nil? lines) (empty? lines))
      (vec (repeat height (apply str (repeat width " "))))

      :else
      (let [padded-lines (mapv #(str % (apply str (repeat (max 0 (- width (count %))) " ")))
                               lines)
            line-count (count padded-lines)
            empty-lines (repeat (max 0 (- height line-count)) (apply str (repeat width " ")))]
        (vec (concat padded-lines empty-lines))))
    (catch Exception e
      (binding [*out* *err*]
        (println "Error in pad-to-box:" (.getMessage e)))
      [])))
