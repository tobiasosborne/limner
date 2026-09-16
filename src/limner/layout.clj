(ns limner.layout
  "Layout engine for positioning and sizing components.

  Invalid input throws `ex-info` with an ADR 0004 class:
  `:limner.layout/invalid-constraint`, `:limner.layout/invalid-dimension`,
  `:limner.layout/invalid-spacing`, `:limner.layout/unknown-layout-type`,
  `:limner.layout/empty-components`, or `:limner.layout/invalid-grid`."
  (:require [limner.text :as text]))

;; ────────────────────── Error Handling & Validation ──────────────────────

(defn- validate-dimension
  "Validate that a dimension (width/height) is a non-negative integer"
  [dimension label]
  (when-not (and (integer? dimension) (>= dimension 0))
    (throw (ex-info (str "Invalid " label ": must be non-negative integer")
                    {:limner/error :limner.layout/invalid-dimension
                     :dimension dimension :label label :type (type dimension)})))
  dimension)

(defn- validate-spacing
  "Validate that spacing value is a non-negative number"
  [spacing]
  (when-not (and (number? spacing) (>= spacing 0))
    (throw (ex-info "Invalid spacing: must be non-negative number"
                    {:limner/error :limner.layout/invalid-spacing
                     :spacing spacing :type (type spacing)})))
  spacing)

(defn- validate-constraint
  "Validate that a constraint is properly formed"
  [constraint]
  (when-not (and (map? constraint) (:type constraint))
    (throw (ex-info "Invalid constraint: must be a map with :type key"
                    {:limner/error :limner.layout/invalid-constraint
                     :constraint constraint :type (type constraint)})))
  (when-not (#{:fixed :percent :flex :auto} (:type constraint))
    (throw (ex-info "Invalid constraint type: must be :fixed, :percent, :flex, or :auto"
                    {:limner/error :limner.layout/invalid-constraint
                     :constraint constraint :type (:type constraint)})))
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

   Value must be a non-negative integer; anything else throws `ex-info` with
   `:limner.layout/invalid-dimension` (ADR 0004)."
  [value]
  (when-not (and (integer? value) (>= value 0))
    (throw (ex-info "Fixed constraint value must be a non-negative integer"
                    {:limner/error :limner.layout/invalid-dimension
                     :value value :type (type value)})))
  {:type :fixed :value value})

(defn percent
  "Percentage of parent size (0-100)

   Value must be a number between 0 and 100; anything else throws `ex-info`
   with `:limner.layout/invalid-constraint` (ADR 0004)."
  [value]
  (when-not (and (number? value) (<= 0 value 100))
    (throw (ex-info "Percent constraint value must be a number from 0 through 100"
                    {:limner/error :limner.layout/invalid-constraint
                     :value value :type (type value) :valid-range [0 100]})))
  {:type :percent :value value})

(defn flex
  "Flexible size, shares remaining space with other flex items
   Higher values get proportionally more space

   Value must be a non-negative number; anything else throws `ex-info` with
   `:limner.layout/invalid-constraint` (ADR 0004)."
  [value]
  (when-not (and (number? value) (>= value 0))
    (throw (ex-info "Flex constraint value must be a non-negative number"
                    {:limner/error :limner.layout/invalid-constraint
                     :value value :type (type value)})))
  {:type :flex :value value})

(defn auto
  "Automatically size to content"
  []
  {:type :auto})

(defn resolve-constraint
  "Resolve a constraint to an actual size given available space and content size

   All values must be non-negative integers. Invalid input throws `ex-info`
   with the class from ADR 0004 rather than returning 0."
  [constraint available-space content-size]
  (when-not (and (number? available-space) (>= available-space 0))
    (throw (ex-info "Available space must be a non-negative number"
                    {:limner/error :limner.layout/invalid-dimension
                     :available-space available-space :type (type available-space)})))
  (when-not (and (number? content-size) (>= content-size 0))
    (throw (ex-info "Content size must be a non-negative number"
                    {:limner/error :limner.layout/invalid-dimension
                     :content-size content-size :type (type content-size)})))
  (validate-constraint constraint)
  (max 0 (case (:type constraint)
           :fixed   (:value constraint)
           :percent (int (* available-space (/ (:value constraint) 100.0)))
           :auto    content-size
           :flex    0))) ; flex is resolved separately

(defn resolve-flex-constraints
  "Distribute `remaining-space` among flex items by largest remainder.

  Each item receives its exact proportional share rounded down; the leftover
  whole columns or rows then go one each to the items with the largest
  fractional share, ties broken by position. This guarantees that the
  allocated sizes sum to `remaining-space`, so a flex row in a stack never
  drops the final row (REVIEW F6). Zero total flex distributes equally."
  [flex-items remaining-space]
  (when-not (and (number? remaining-space) (>= remaining-space 0))
    (throw (ex-info "Remaining space must be a non-negative number"
                    {:limner/error :limner.layout/invalid-dimension
                     :remaining-space remaining-space :type (type remaining-space)})))
  (if (empty? flex-items)
    []
    (let [remaining (long remaining-space)
          n (count flex-items)
          total-flex (reduce + (map :value flex-items))
          weights (if (pos? total-flex)
                    (mapv #(/ (double (:value %)) total-flex) flex-items)
                    (vec (repeat n (/ 1.0 n))))
          exact (mapv #(* remaining %) weights)
          floors (mapv #(long (Math/floor %)) exact)
          allocated (reduce + floors)
          leftover (int (- remaining allocated))
          order (sort-by (fn [i] [(- (nth floors i) (nth exact i)) i]) (range n))]
      (reduce (fn [sizes i] (update sizes i inc)) floors (take leftover order)))))

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
                    {:limner/error :limner.layout/empty-components
                     :components components})))
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
                          (let [avail (available-height total-height)]
                            (when (neg? avail)
                              (throw (ex-info "Stack spacing and padding exceed the available height"
                                              {:limner/error :limner.layout/invalid-dimension
                                               :available-height avail
                                               :total-height total-height
                                               :spacing spacing :padding padding
                                               :limner/hint "Reduce :spacing or :padding, or increase the total height"})))
                            (let [;; First pass: resolve fixed, percent, and auto
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
                                  size)))))]

    {:type :stack
     :components components
     :spacing spacing
     :padding padding
     :resolve-heights resolve-heights}))

(defn layout-stack
  "Calculate actual positions and sizes for stacked components

   Validates dimensions and handles edge cases gracefully."
  [stack-layout total-width total-height]
  (validate-dimension total-width "total-width")
  (validate-dimension total-height "total-height")
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
                 (conj result box)))))))

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
                    {:limner/error :limner.layout/empty-components
                     :components components})))
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
                         (let [avail (available-width total-width)]
                           (when (neg? avail)
                             (throw (ex-info "Hsplit spacing and padding exceed the available width"
                                             {:limner/error :limner.layout/invalid-dimension
                                              :available-width avail
                                              :total-width total-width
                                              :spacing spacing :padding padding
                                              :limner/hint "Reduce :spacing or :padding, or increase the total width"})))
                           (let [first-pass (for [{:keys [constraint content]} components]
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
                                 size)))))]

    {:type :hsplit
     :components components
     :spacing spacing
     :padding padding
     :resolve-widths resolve-widths}))

(defn layout-hsplit
  "Calculate actual positions and sizes for horizontally split components

   Validates dimensions and handles edge cases gracefully."
  [hsplit-layout total-width total-height]
  (validate-dimension total-width "total-width")
  (validate-dimension total-height "total-height")
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
                 (conj result box)))))))

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
                    {:limner/error :limner.layout/invalid-grid
                     :options opts})))
  (when-not (and (integer? columns) (pos? columns))
    (throw (ex-info "Grid :columns must be a positive integer"
                    {:limner/error :limner.layout/invalid-grid
                     :columns columns :type (type columns)})))
  (when (empty? components)
    (throw (ex-info "Cannot create grid with empty components"
                    {:limner/error :limner.layout/empty-components
                     :components components})))
  (when rows
    (when-not (and (integer? rows) (pos? rows))
      (throw (ex-info "Grid :rows must be a positive integer"
                      {:limner/error :limner.layout/invalid-grid
                       :rows rows :type (type rows)}))))
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
  (validate-dimension total-width "total-width")
  (validate-dimension total-height "total-height")
  (let [{:keys [components columns rows spacing padding]} grid-layout]
    (when-not (and (integer? columns) (pos? columns))
      (throw (ex-info "Grid :columns must be a positive integer"
                      {:limner/error :limner.layout/invalid-grid
                       :columns columns :type (type columns)})))
    (when-not (and (integer? rows) (pos? rows))
      (throw (ex-info "Grid :rows must be a positive integer"
                      {:limner/error :limner.layout/invalid-grid
                       :rows rows :type (type rows)})))
    (let [content-width (max 0 (- total-width (* 2 padding) (* (dec columns) spacing)))
          content-height (max 0 (- total-height (* 2 padding) (* (dec rows) spacing)))
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
               {:content (:content component)})))))

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

   An unknown layout `:type` throws `ex-info` with
   `:limner.layout/unknown-layout-type` (ADR 0004)."
  [layout-spec width height]
  (validate-dimension width "width")
  (validate-dimension height "height")
  (when-not (and (map? layout-spec) (:type layout-spec))
    (throw (ex-info "Invalid layout-spec: must be a map with :type key"
                    {:limner/error :limner.layout/invalid-constraint
                     :layout-spec layout-spec})))
  (case (:type layout-spec)
    :stack   (layout-stack layout-spec width height)
    :hsplit  (layout-hsplit layout-spec width height)
    :grid    (layout-grid layout-spec width height)
    :overlay (layout-overlay layout-spec width height)
    (throw (ex-info (str "Unknown layout type: " (:type layout-spec))
                    {:limner/error :limner.layout/unknown-layout-type
                     :layout-spec layout-spec
                     :known [:stack :hsplit :grid :overlay]}))))

;; ────────────────────── Utility Functions ──────────────────────
(defn clip
  "Clip `content` to `width` display columns and `height` lines.

  Slicing goes through `limner.text`, so a wide cluster is never cut in half:
  a partly covered cluster becomes spaces. Callers can rely on every returned
  line being at most `width` columns wide (REVIEW B3)."
  [content width height]
  (validate-dimension width "width")
  (validate-dimension height "height")
  (cond
    (or (nil? content) (zero? width) (zero? height))
    []

    :else
    (let [lines (take height (text/lines (str content)))]
      (mapv #(text/to-ansi (text/slice-columns % 0 width)) lines))))

(defn pad-to-box
  "Pad `lines` to `width` display columns and at least `height` rows.

  Padding and measurement go through `limner.text`, so wide content is padded
  by its column width and styled lines keep their spans."
  [lines width height]
  (validate-dimension width "width")
  (validate-dimension height "height")
  (cond
    (or (zero? width) (zero? height))
    []

    (or (nil? lines) (empty? lines))
    (vec (repeat height (apply str (repeat width " "))))

    :else
    (let [padded (mapv #(text/to-ansi (text/pad-right % width)) lines)
          empty-line (apply str (repeat width " "))
          line-count (count padded)]
      (vec (concat padded (repeat (max 0 (- height line-count)) empty-line))))))
