(ns limner.components.list
  "List component - scrollable, selectable item lists"
  (:require [clojure.string :as str]
            [limner.borders :as borders]
            [limner.core :as core]))

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
  "Set filter text and reset selection"
  [list-state filter-text]
  (let [new-state (assoc list-state :filter-text filter-text)]
    (if (empty? (filtered-items new-state))
      (assoc new-state :selected 0)
      (assoc new-state :selected 0 :scroll-offset 0))))

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

(defn select-next
  "Move selection down by n items"
  [list-state n]
  (let [items (filtered-items list-state)
        item-count (count items)
        {:keys [selected wrap]} list-state]
    (if (zero? item-count)
      list-state
      (let [new-selected (+ selected n)]
        (cond
          (< new-selected item-count)
          (assoc list-state :selected new-selected)

          wrap
          (assoc list-state :selected (mod new-selected item-count))

          :else
          (assoc list-state :selected (dec item-count)))))))

(defn select-prev
  "Move selection up by n items"
  [list-state n]
  (let [items (filtered-items list-state)
        item-count (count items)
        {:keys [selected wrap]} list-state]
    (if (zero? item-count)
      list-state
      (let [new-selected (- selected n)]
        (cond
          (>= new-selected 0)
          (assoc list-state :selected new-selected)

          wrap
          (assoc list-state :selected (mod new-selected item-count))

          :else
          (assoc list-state :selected 0))))))

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
  "Adjust scroll offset to keep selection visible"
  [{:keys [selected scroll-offset height] :as list-state}]
  (let [new-offset (cond
                    ;; Selection above viewport
                    (< selected scroll-offset)
                    selected

                    ;; Selection below viewport
                    (>= selected (+ scroll-offset height))
                    (- selected height -1)

                    ;; Selection in viewport
                    :else
                    scroll-offset)]
    (assoc list-state :scroll-offset new-offset)))

(defn visible-items
  "Get items visible in current viewport"
  [{:keys [scroll-offset height] :as list-state}]
  (let [items (filtered-items list-state)]
    (vec (take height (drop scroll-offset items)))))

;; ────────────────────── Rendering ──────────────────────
(defn render-item
  "Render a single item with selection indicator"
  [{:keys [cursor-char checkbox-checked checkbox-unchecked show-indices
           item-renderer multi-select selected-items width filter-text]
    :as list-state}
   item original-idx display-idx is-selected?]
  (let [;; Get item label
        label (if item-renderer
               (item-renderer item is-selected?)
               (item-label item))

        ;; Multi-select checkbox
        checkbox (when multi-select
                  (if (contains? selected-items original-idx)
                    (str checkbox-checked " ")
                    (str checkbox-unchecked " ")))

        ;; Selection cursor
        cursor (if is-selected?
                (str cursor-char " ")
                "  ")

        ;; Index prefix
        index-str (when show-indices
                   (format "%3d. " display-idx))

        ;; Combine parts
        prefix (str cursor checkbox index-str)
        max-label-width (- width (core/visible-length prefix) 2)

        ;; Truncate or pad label
        truncated-label (if (> (core/visible-length label) max-label-width)
                         (str (subs label 0 (- max-label-width 3)) "...")
                         label)

        padded-label (if (< (core/visible-length truncated-label) max-label-width)
                      (str truncated-label
                           (apply str (repeat (- max-label-width (core/visible-length truncated-label)) " ")))
                      truncated-label)

        ;; Highlight if selected
        final-label (if is-selected?
                     (core/color :cyan padded-label)
                     padded-label)]

    (str " " prefix final-label " ")))

(defn render-filter-line
  "Render filter input line"
  [{:keys [filter-text width]}]
  (let [prefix "Filter: "
        max-width (- width 4)
        available (- max-width (count prefix))
        display-text (if (> (count filter-text) available)
                      (str "..." (subs filter-text (- (count filter-text) (- available 3))))
                      filter-text)
        padding (apply str (repeat (- available (count display-text)) " "))]
    (str " " prefix display-text padding " ")))

(defn render-list
  "Render list content (without border)"
  [{:keys [selected scroll-offset show-filter height] :as list-state}]
  (let [items (filtered-items list-state)
        indices (filtered-item-indices list-state)
        visible (visible-items list-state)

        ;; Render items
        item-lines (map-indexed
                    (fn [idx item]
                      (let [display-idx (+ scroll-offset idx)
                            original-idx (get indices display-idx)
                            is-selected? (= display-idx selected)]
                        (render-item list-state item original-idx display-idx is-selected?)))
                    visible)

        ;; Add empty lines if needed
        empty-lines (repeat (- height (count item-lines)) (str " " (apply str (repeat (- (:width list-state) 2) " ")) " "))
        all-lines (concat item-lines empty-lines)

        ;; Add filter line if enabled
        final-lines (if show-filter
                     (cons (render-filter-line list-state) all-lines)
                     all-lines)]

    (vec final-lines)))

(defn render
  "Render list component to vector of strings"
  [{:keys [border border-style label] :as list-state}]
  (let [content-lines (render-list list-state)
        final-lines (if border
                     (if label
                       (borders/draw-titled-box label content-lines
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
