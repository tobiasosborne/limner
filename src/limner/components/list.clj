(ns limner.components.list
  "Scrollable, selectable item lists.

  Rendering measures, truncates, and pads every item through `limner.text`, so
  CJK and emoji items occupy their true display columns and an item that
  already carries ANSI keeps its style. `:width` is the total rendered width
  including the border; a width too small for the item prefix throws
  `:limner.components.list/invalid-width` instead of failing inside a
  substring operation (REVIEW F2/B3).

  Selection, filtering, and scrolling are pure state transitions. Selecting an
  item adjusts the scroll offset so the selection stays visible, and changing
  the filter text resets both the selection and the scroll offset."
  (:refer-clojure :exclude [list? empty?])
  (:require [clojure.string :as str]
            [limner.borders :as borders]
            [limner.text :as text]))

;; ────────────────────── List State ──────────────────────
(defn list-component
  "Create a list component
   Options:
   - :items - vector of items (strings or maps with :label and :value)
   - :selected - index of selected item (default 0)
   - :multi-select - enable multi-selection with checkboxes (default false)
   - :selected-items - set of selected indices for multi-select (default #{})
   - :height - visible height (default 10)
   - :width - list width (default 40)
   - :scroll-offset - scroll position (default 0)
   - :filter-text - current filter text (default \"\")
   - :show-filter - show filter input (default false)
   - :wrap - wrap selection at boundaries (default true)
   - :item-renderer - custom rendering function (default nil)
   - :border - show border (default true)
   - :border-style - border style (default :single)
   - :label - list label/title (default nil)
   - :show-indices - show item indices (default false)
   - :cursor-char - character for selected item (default \">\")
   - :checkbox-checked - character for checked items (default \"☑\")
   - :checkbox-unchecked - character for unchecked items (default \"☐\")"
  [& {:keys [items selected multi-select selected-items height width
             scroll-offset filter-text show-filter wrap item-renderer
             border border-style label show-indices cursor-char
             checkbox-checked checkbox-unchecked]
      :or {items []
           selected 0
           multi-select false
           selected-items #{}
           height 10
           width 40
           scroll-offset 0
           filter-text ""
           show-filter false
           wrap true
           border true
           border-style :single
           show-indices false
           cursor-char ">"
           checkbox-checked "☑"
           checkbox-unchecked "☐"}}]
  {:items (vec items)
   :selected selected
   :multi-select multi-select
   :selected-items (set selected-items)
   :height height
   :width width
   :scroll-offset scroll-offset
   :filter-text filter-text
   :show-filter show-filter
   :wrap wrap
   :item-renderer item-renderer
   :border border
   :border-style border-style
   :label label
   :show-indices show-indices
   :cursor-char cursor-char
   :checkbox-checked checkbox-checked
   :checkbox-unchecked checkbox-unchecked})

;; ────────────────────── Item Normalization ──────────────────────
(defn normalize-item
  "Convert item to {:label :value} map"
  [item]
  (cond
    (map? item) item
    (string? item) {:label item :value item}
    :else {:label (str item) :value item}))

(defn item-label
  "Get display label for item"
  [item]
  (:label (normalize-item item)))

(defn item-value
  "Get value for item"
  [item]
  (:value (normalize-item item)))

;; ────────────────────── Filtering ──────────────────────
(defn matches-filter?
  "Check if item matches filter text"
  [item filter-text]
  (if (str/blank? filter-text)
    true
    (let [label (str/lower-case (item-label item))
          filter (str/lower-case filter-text)]
      (str/includes? label filter))))

(defn filtered-items
  "Get filtered items based on filter-text"
  [{:keys [items filter-text]}]
  (if (str/blank? filter-text)
    items
    (vec (filter #(matches-filter? % filter-text) items))))

(defn filtered-item-indices
  "Get original indices of filtered items"
  [{:keys [items filter-text] :as list-state}]
  (if (str/blank? filter-text)
    (vec (range (count items)))
    (vec (keep-indexed
          (fn [idx item]
            (when (matches-filter? item filter-text) idx))
          items))))

(defn set-filter
  "Set filter text and reset selection and scroll to the top.

  Resetting the scroll in every branch matters: a filter that matches nothing
  still moves the viewport back to the first row, so it cannot stay scrolled
  to an item that is no longer visible."
  [list-state filter-text]
  (assoc list-state :filter-text filter-text :selected 0 :scroll-offset 0))

(defn clear-filter
  "Clear filter text"
  [list-state]
  (set-filter list-state ""))

;; ────────────────────── Selection Management ──────────────────────
(defn clamp-selection
  "Clamp selection to valid range"
  [list-state]
  (let [items (filtered-items list-state)
        item-count (count items)]
    (if (zero? item-count)
      (assoc list-state :selected 0)
      (update list-state :selected #(max 0 (min % (dec item-count)))))))

(defn select-item
  "Select item at index"
  [list-state index]
  (let [items (filtered-items list-state)
        item-count (count items)]
    (if (and (>= index 0) (< index item-count))
      (assoc list-state :selected index)
      list-state)))

(declare adjust-scroll)

(defn select-next
  "Move selection down by n items, keeping it inside the viewport."
  [list-state n]
  (let [items (filtered-items list-state)
        item-count (count items)
        {:keys [selected wrap]} list-state]
    (if (zero? item-count)
      list-state
      (let [new-selected (+ selected n)
            moved (cond
                    (< new-selected item-count)
                    (assoc list-state :selected new-selected)

                    wrap
                    (assoc list-state :selected (mod new-selected item-count))

                    :else
                    (assoc list-state :selected (dec item-count)))]
        (adjust-scroll moved)))))

(defn select-prev
  "Move selection up by n items, keeping it inside the viewport."
  [list-state n]
  (let [items (filtered-items list-state)
        item-count (count items)
        {:keys [selected wrap]} list-state]
    (if (zero? item-count)
      list-state
      (let [new-selected (- selected n)
            moved (cond
                    (>= new-selected 0)
                    (assoc list-state :selected new-selected)

                    wrap
                    (assoc list-state :selected (mod new-selected item-count))

                    :else
                    (assoc list-state :selected 0))]
        (adjust-scroll moved)))))

(defn select-first
  "Select first item"
  [list-state]
  (assoc list-state :selected 0 :scroll-offset 0))

(defn select-last
  "Select last item"
  [list-state]
  (let [items (filtered-items list-state)
        last-idx (max 0 (dec (count items)))]
    (assoc list-state :selected last-idx)))

;; ────────────────────── Multi-Select ──────────────────────
(defn toggle-selection
  "Toggle selection of current item (multi-select)"
  [{:keys [selected selected-items filter-text] :as list-state}]
  (if-not (:multi-select list-state)
    list-state
    (let [indices (filtered-item-indices list-state)
          actual-idx (get indices selected)]
      (if actual-idx
        (let [new-selected (if (contains? selected-items actual-idx)
                            (disj selected-items actual-idx)
                            (conj selected-items actual-idx))]
          (assoc list-state :selected-items new-selected))
        list-state))))

(defn select-all
  "Select all items (multi-select)"
  [list-state]
  (if-not (:multi-select list-state)
    list-state
    (let [indices (filtered-item-indices list-state)]
      (assoc list-state :selected-items (set indices)))))

(defn deselect-all
  "Deselect all items (multi-select)"
  [list-state]
  (assoc list-state :selected-items #{}))

(defn get-selected-items
  "Get list of selected items (for multi-select)"
  [{:keys [items selected-items multi-select]}]
  (if multi-select
    (vec (map #(get items %) (sort selected-items)))
    []))

(defn get-current-item
  "Get currently selected item"
  [{:keys [selected] :as list-state}]
  (let [items (filtered-items list-state)]
    (when (and (>= selected 0) (< selected (count items)))
      (get items selected))))

;; ────────────────────── Scrolling ──────────────────────
(defn adjust-scroll
  "Adjust scroll offset so the selection stays visible and in range."
  [{:keys [selected scroll-offset height] :as list-state}]
  (let [item-count (count (filtered-items list-state))
        max-offset (max 0 (- item-count height))
        new-offset (cond
                     ;; Selection above viewport
                     (< selected scroll-offset)
                     selected

                     ;; Selection below viewport
                     (>= selected (+ scroll-offset height))
                     (- selected height -1)

                     ;; Selection in viewport
                     :else
                     scroll-offset)]
    (assoc list-state :scroll-offset (max 0 (min new-offset max-offset)))))

(defn visible-items
  "Get items visible in current viewport"
  [{:keys [scroll-offset height] :as list-state}]
  (let [items (filtered-items list-state)]
    (vec (take height (drop scroll-offset items)))))

;; ────────────────────── Rendering ──────────────────────

(defn- content-columns
  "Display columns available to item rows for this list's border setting."
  [{:keys [width border]}]
  (- width (if border 4 0)))

(defn- prefix-columns
  "Display width of the per-row prefix (cursor, checkbox, index)."
  [{:keys [cursor-char checkbox-checked checkbox-unchecked multi-select
           show-indices items]}]
  (let [cursor-width (max 2 (text/width (str cursor-char " ")))
        checkbox-width (if multi-select
                         (inc (max (text/width checkbox-checked)
                                   (text/width checkbox-unchecked)))
                         0)
        last-index (max 0 (dec (count items)))
        index-width (if show-indices
                      (+ (max 3 (text/width (str last-index))) 2)
                      0)]
    (+ cursor-width checkbox-width index-width)))

(defn- validate-render-width
  "Return the content columns, throwing when the prefix cannot fit."
  [list-state]
  (let [width (:width list-state)]
    (when-not (and (integer? width) (<= 0 width))
      (throw (ex-info "List width must be a non-negative integer"
                      {:limner/error :limner.components.list/invalid-width
                       :width width :type (type width)
                       :limner/hint "Use an integer width large enough for the configured prefix"})))
    (let [columns (content-columns list-state)
          prefix (prefix-columns list-state)
          filter-minimum (if (:show-filter list-state)
                           (+ (text/width "Filter: ") 3)
                           0)
          minimum (max (+ prefix 3) filter-minimum)]
      (when (< columns minimum)
        (throw (ex-info "List width is too small for its item prefix"
                        {:limner/error :limner.components.list/invalid-width
                         :width width
                         :border (:border list-state)
                         :content-columns columns
                         :minimum-content-columns minimum
                         :limner/hint "Increase :width, or disable :show-indices, :multi-select, or :show-filter"})))
      columns)))

(defn- item-prefix
  "Styled cursor, checkbox, and index prefix for one row."
  [{:keys [cursor-char checkbox-checked checkbox-unchecked show-indices
           multi-select selected-items]}
   original-idx display-idx is-selected?]
  (text/concat (if is-selected? (str cursor-char " ") "  ")
               (if multi-select
                 (str (if (contains? selected-items original-idx)
                        checkbox-checked checkbox-unchecked) " ")
                 "")
               (if show-indices
                 (text/concat (text/pad-left (str display-idx) 3) ". ")
                 "")))

(defn render-item
  "Render one item row at exactly the list's content width.

  `label` is measured and cut through `limner.text`; a selected row is tinted
  cyan without clearing a style the label already carries."
  [list-state item original-idx display-idx is-selected?]
  (let [columns (content-columns list-state)
        label (if-let [renderer (:item-renderer list-state)]
                (renderer item is-selected?)
                (item-label item))
        prefix (item-prefix list-state original-idx display-idx is-selected?)
        label-columns (max 0 (- columns (text/width prefix) 2))
        padded (text/pad-right (text/truncate label label-columns) label-columns)
        final (if is-selected? (text/style {:fg :cyan} padded) padded)]
    (text/to-ansi (text/concat " " prefix final " "))))

(defn render-filter-line
  "Render the filter prompt as one row of the list's content width."
  [{:keys [filter-text] :as list-state}]
  (let [columns (content-columns list-state)
        prefix "Filter: "
        available (max 0 (- columns 2 (text/width prefix)))
        display (text/truncate filter-text available)]
    (text/to-ansi (text/concat " " prefix (text/pad-right display available) " "))))

(defn render-list
  "Render list content (without border), one exact content width per row."
  [{:keys [selected scroll-offset show-filter height] :as list-state}]
  (let [columns (validate-render-width list-state)
        indices (filtered-item-indices list-state)
        visible (visible-items list-state)
        item-lines (map-indexed
                    (fn [idx item]
                      (let [display-idx (+ scroll-offset idx)
                            original-idx (get indices display-idx)
                            is-selected? (= display-idx selected)]
                        (render-item list-state item original-idx display-idx is-selected?)))
                    visible)
        empty-line (apply str (repeat columns " "))
        empty-lines (repeat (max 0 (- height (count item-lines))) empty-line)
        all-lines (concat item-lines empty-lines)
        final-lines (if show-filter
                      (cons (render-filter-line list-state) all-lines)
                      all-lines)]
    (vec final-lines)))

(defn render
  "Render list component to vector of strings.

  `:width` is the total width including the border, so every returned line is
  exactly `:width` display columns wide."
  [{:keys [border border-style label] :as list-state}]
  (let [content-lines (render-list list-state)
        title (when (and border label)
                (text/to-ansi (text/truncate label (content-columns list-state))))
        final-lines (if border
                      (if title
                        (borders/draw-titled-box title content-lines
                                                :border-style border-style
                                                :title-pos :left)
                        (borders/draw-box content-lines :border-style border-style))
                      content-lines)]
    (vec final-lines)))

(defn render-to-string
  "Render list to a single string with newlines"
  [list-state]
  (str/join "\n" (render list-state)))

;; ────────────────────── List Helpers ──────────────────────
(defn list?
  "Check if value is a list component"
  [x]
  (and (map? x)
       (contains? x :items)
       (contains? x :selected)))

(defn empty?
  "Check if list has no items"
  [list-state]
  (clojure.core/empty? (:items list-state)))

(defn filtered-empty?
  "Check if filtered list has no items"
  [list-state]
  (clojure.core/empty? (filtered-items list-state)))

(defn item-count
  "Get total number of items"
  [list-state]
  (count (:items list-state)))

(defn filtered-item-count
  "Get number of filtered items"
  [list-state]
  (count (filtered-items list-state)))

(defn has-filter?
  "Check if filter is active"
  [list-state]
  (not (str/blank? (:filter-text list-state))))

(defn multi-select?
  "Check if multi-select is enabled"
  [list-state]
  (:multi-select list-state))

(defn selection-count
  "Get number of selected items (multi-select)"
  [list-state]
  (count (:selected-items list-state)))

(defn all-selected?
  "Check if all filtered items are selected"
  [list-state]
  (and (:multi-select list-state)
       (let [indices (set (filtered-item-indices list-state))]
         (= indices (:selected-items list-state)))))

;; ────────────────────── Convenience Functions ──────────────────────
(defn set-items
  "Replace all items in list"
  [list-state items]
  (-> list-state
      (assoc :items (vec items))
      clamp-selection
      adjust-scroll))

(defn add-item
  "Add item to end of list"
  [list-state item]
  (update list-state :items conj item))

(defn remove-item
  "Remove item at index"
  [list-state index]
  (if (and (>= index 0) (< index (count (:items list-state))))
    (-> list-state
        (update :items #(vec (concat (subvec % 0 index)
                                    (subvec % (inc index)))))
        clamp-selection
        adjust-scroll)
    list-state))

(defn toggle-filter
  "Toggle filter visibility"
  [list-state]
  (update list-state :show-filter not))
