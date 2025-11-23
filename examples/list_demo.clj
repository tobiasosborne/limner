#!/usr/bin/env bb
;; List Component Demo
;; Demonstrates all list features: selection, multi-select, filtering, scrolling

(ns list-demo
  (:require [limner.components.list :as list]
            [limner.core :as core]
            [clojure.string :as str]))

(defn print-list [l & [description]]
  (when description
    (println (core/color :cyan (str "\n" description))))
  (println (list/render-to-string l))
  (println))

;; ──────────────── Demo 1: Basic Lists ────────────────
(defn demo-basic-lists []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 1: Basic Lists"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [simple (list/list-component
                :items ["Apple" "Banana" "Cherry" "Date" "Elderberry"]
                :label "Fruits"
                :height 5)]
    (print-list simple "Simple list with 5 items:"))

  (let [with-selection (list/list-component
                        :items ["Red" "Green" "Blue" "Yellow"]
                        :label "Colors"
                        :selected 2
                        :height 4)]
    (print-list with-selection "List with item 2 selected:"))

  (let [no-border (list/list-component
                   :items ["Item 1" "Item 2" "Item 3"]
                   :border false
                   :height 3)]
    (print-list no-border "List without border:")))

;; ──────────────── Demo 2: Navigation ────────────────
(defn demo-navigation []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 2: Keyboard Navigation"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [l (list/list-component
           :items ["Item 1" "Item 2" "Item 3" "Item 4" "Item 5"]
           :label "Navigate Me"
           :height 5)]

    (print-list l "Original (first item selected):")

    (let [next1 (list/select-next l 1)]
      (print-list next1 "After pressing Down arrow (↓):"))

    (let [next2 (list/select-next l 2)]
      (print-list next2 "After pressing Down 2 times (or 'j' in vim):"))

    (let [last-item (list/select-last l)]
      (print-list last-item "After jumping to last (End key):")

      (let [prev (list/select-prev last-item 2)]
        (print-list prev "After pressing Up 2 times (↑ or 'k'):")))

    (let [first-item (list/select-first l)]
      (print-list first-item "After jumping to first (Home key):"))))

;; ──────────────── Demo 3: Selection Wrapping ────────────────
(defn demo-wrapping []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 3: Selection Wrapping"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [wrap-enabled (list/list-component
                      :items ["A" "B" "C"]
                      :label "Wrap: Enabled"
                      :selected 2
                      :wrap true
                      :height 3)]

    (print-list wrap-enabled "At last item with wrap enabled:")

    (let [wrapped (list/select-next wrap-enabled 1)]
      (print-list wrapped "After Down arrow (wraps to first):")))

  (let [wrap-disabled (list/list-component
                       :items ["A" "B" "C"]
                       :label "Wrap: Disabled"
                       :selected 2
                       :wrap false
                       :height 3)]

    (print-list wrap-disabled "At last item with wrap disabled:")

    (let [no-wrap (list/select-next wrap-disabled 1)]
      (print-list no-wrap "After Down arrow (stays at last):"))))

;; ──────────────── Demo 4: Multi-Select ────────────────
(defn demo-multi-select []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 4: Multi-Select with Checkboxes"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [l (list/list-component
           :items ["Option 1" "Option 2" "Option 3" "Option 4"]
           :label "Select Multiple"
           :multi-select true
           :height 5)]

    (print-list l "Multi-select list (notice checkboxes):")

    (let [selected1 (list/toggle-selection l)]
      (print-list selected1 "After pressing Space on first item:"))

    (let [selected2 (-> l
                        (list/select-next 2)
                        list/toggle-selection)]
      (print-list selected2 "After selecting third item:"))

    (let [multiple (-> l
                       list/toggle-selection
                       (list/select-next 1)
                       list/toggle-selection
                       (list/select-next 1)
                       list/toggle-selection)]
      (print-list multiple "After selecting first three items:")
      (println (core/color :yellow "Selected items:")
               (str/join ", " (list/get-selected-items multiple))))

    (let [all-selected (list/select-all l)]
      (print-list all-selected "After 'Select All' command:"))))

;; ──────────────── Demo 5: Filtering ────────────────
(defn demo-filtering []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 5: Search/Filter"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [l (list/list-component
           :items ["Apple" "Apricot" "Banana" "Cherry" "Date" "Elderberry"]
           :label "Fruits"
           :height 6)]

    (print-list l "Original list (6 items):")

    (let [filtered1 (list/set-filter l "a")]
      (print-list filtered1 "After filtering with 'a' (case-insensitive):")
      (println (core/color :yellow (str "  Showing " (list/filtered-item-count filtered1) " of " (list/item-count filtered1) " items\n"))))

    (let [filtered2 (list/set-filter l "ap")]
      (print-list filtered2 "After filtering with 'ap':")
      (println (core/color :yellow (str "  Showing " (list/filtered-item-count filtered2) " of " (list/item-count filtered2) " items\n"))))

    (let [filtered3 (list/set-filter l "err")]
      (print-list filtered3 "After filtering with 'err':")
      (println (core/color :yellow (str "  Showing " (list/filtered-item-count filtered3) " of " (list/item-count filtered3) " items\n"))))

    (let [no-match (list/set-filter l "xyz")]
      (print-list no-match "After filtering with 'xyz' (no matches):"))))

;; ──────────────── Demo 6: Filter with Visible Input ────────────────
(defn demo-filter-input []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 6: Filter with Visible Input"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [l (list/list-component
           :items ["JavaScript" "Java" "Python" "Ruby" "Rust" "Go"]
           :label "Languages"
           :show-filter true
           :height 7)]

    (print-list l "List with filter input shown:")

    (let [filtered (-> l
                       (list/set-filter "ja")
                       (assoc :show-filter true))]
      (print-list filtered "After typing 'ja' in filter:"))))

;; ──────────────── Demo 7: Scrolling ────────────────
(defn demo-scrolling []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 7: Scrolling Long Lists"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [long-list (vec (map #(str "Item " %) (range 1 21)))
        l (list/list-component
           :items long-list
           :label "Long List (20 items)"
           :height 5)]

    (print-list l "Viewing items 1-5 (scroll offset: 0):")

    (let [scrolled (-> l
                       (list/select-item 7)
                       list/adjust-scroll)]
      (print-list scrolled "After selecting item 8 (auto-scroll):"))

    (let [at-bottom (-> l
                        list/select-last
                        list/adjust-scroll)]
      (print-list at-bottom "At bottom of list (items 16-20):"))))

;; ──────────────── Demo 8: Map Items with Values ────────────────
(defn demo-map-items []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 8: Map Items (Label + Value)"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [items [{:label "Small (10 items)" :value 10}
               {:label "Medium (50 items)" :value 50}
               {:label "Large (100 items)" :value 100}
               {:label "Extra Large (500 items)" :value 500}]
        l (list/list-component
           :items items
           :label "Choose Size"
           :selected 1
           :height 4)]

    (print-list l "List with map items:")

    (let [selected (list/get-current-item l)]
      (println (core/color :yellow "Selected item:"))
      (println "  Label:" (:label selected))
      (println "  Value:" (:value selected))
      (println))))

;; ──────────────── Demo 9: Custom Item Renderer ────────────────
(defn demo-custom-renderer []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 9: Custom Item Rendering"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [items [{:name "error.log" :size "2.3 MB" :type "log"}
               {:name "data.json" :size "156 KB" :type "json"}
               {:name "app.clj" :size "45 KB" :type "clojure"}
               {:name "README.md" :size "8 KB" :type "markdown"}]

        custom-renderer (fn [item selected?]
                         (let [name (:name item)
                               size (format "%-8s" (:size item))
                               type (:type item)
                               colored-type (condp = type
                                             "log" (core/color :red type)
                                             "json" (core/color :yellow type)
                                             "clojure" (core/color :green type)
                                             "markdown" (core/color :cyan type)
                                             type)]
                           (str name "  " size "  [" colored-type "]")))

        l (list/list-component
           :items items
           :label "Files"
           :item-renderer custom-renderer
           :height 5
           :width 50)]

    (print-list l "List with custom item rendering:")))

;; ──────────────── Demo 10: Show Item Indices ────────────────
(defn demo-indices []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 10: Show Item Indices"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [l (list/list-component
           :items ["First" "Second" "Third" "Fourth" "Fifth"]
           :label "Numbered List"
           :show-indices true
           :height 5
           :width 35)]

    (print-list l "List with indices shown:")))

;; ──────────────── Demo 11: Different Border Styles ────────────────
(defn demo-border-styles []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 11: Different Border Styles"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [single (list/list-component
                :items ["Option 1" "Option 2" "Option 3"]
                :label "Single Border"
                :border-style :single
                :height 3)]
    (print-list single "Single-line border:"))

  (let [double (list/list-component
                :items ["Option 1" "Option 2" "Option 3"]
                :label "Double Border"
                :border-style :double
                :height 3)]
    (print-list double "Double-line border:"))

  (let [rounded (list/list-component
                 :items ["Option 1" "Option 2" "Option 3"]
                 :label "Rounded Border"
                 :border-style :rounded
                 :height 3)]
    (print-list rounded "Rounded border:"))

  (let [thick (list/list-component
               :items ["Option 1" "Option 2" "Option 3"]
               :label "Thick Border"
               :border-style :thick
               :height 3)]
    (print-list thick "Thick border:")))

;; ──────────────── Demo 12: Multi-Select with Filtering ────────────────
(defn demo-multi-select-filter []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 12: Multi-Select + Filtering"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [l (list/list-component
           :items ["TypeScript" "JavaScript" "Python" "Java" "Ruby" "Rust"]
           :label "Select Languages"
           :multi-select true
           :height 6)]

    (print-list l "Select from all languages:")

    (let [selected (-> l
                       list/toggle-selection
                       (list/select-next 2)
                       list/toggle-selection
                       (list/select-next 1)
                       list/toggle-selection)]
      (print-list selected "Selected TypeScript, Python, Java:")
      (println (core/color :yellow "Selected:")
               (str/join ", " (list/get-selected-items selected)))
      (println))

    (let [filtered (-> l
                       (list/set-filter "script")
                       (assoc :show-filter true))]
      (print-list filtered "After filtering 'script':")
      (println (core/color :yellow "  Note: Only matching items shown\n")))))

;; ──────────────── Demo 13: Helper Functions ────────────────
(defn demo-helpers []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 13: Helper Functions"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [l (list/list-component
           :items ["Apple" "Banana" "Cherry"]
           :multi-select true
           :selected 1
           :selected-items #{0 2})]

    (println (core/color :yellow "\nList properties:"))
    (println "  list? ->" (list/list? l))
    (println "  empty? ->" (list/empty? l))
    (println "  item-count ->" (list/item-count l))
    (println "  multi-select? ->" (list/multi-select? l))
    (println "  selection-count ->" (list/selection-count l))
    (println "  has-filter? ->" (list/has-filter? l))

    (let [filtered (list/set-filter l "an")]
      (println (core/color :yellow "\nAfter filtering 'an':"))
      (println "  filtered-item-count ->" (list/filtered-item-count filtered))
      (println "  has-filter? ->" (list/has-filter? filtered))
      (println "  filtered-empty? ->" (list/filtered-empty? filtered)))

    (let [all (list/select-all l)]
      (println (core/color :yellow "\nAfter selecting all:"))
      (println "  selection-count ->" (list/selection-count all))
      (println "  all-selected? ->" (list/all-selected? all)))))

;; ──────────────── Demo 14: Item Management ────────────────
(defn demo-item-management []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 14: Dynamic Item Management"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [l (list/list-component
           :items ["Task 1" "Task 2"]
           :label "Todo List"
           :height 4)]

    (print-list l "Initial list with 2 items:")

    (let [with-new (list/add-item l "Task 3")]
      (print-list with-new "After adding 'Task 3':"))

    (let [removed (list/remove-item l 0)]
      (print-list removed "After removing first item:"))

    (let [replaced (list/set-items l ["New 1" "New 2" "New 3" "New 4"])]
      (print-list replaced "After replacing all items:"))))

;; ──────────────── Demo 15: Complex Example ────────────────
(defn demo-complex []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 15: Complex Example"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [tasks [{:label "Review pull request #42" :value {:id 42 :priority "high"}}
               {:label "Update documentation" :value {:id 15 :priority "medium"}}
               {:label "Fix bug in login flow" :value {:id 38 :priority "high"}}
               {:label "Refactor database queries" :value {:id 21 :priority "low"}}
               {:label "Add unit tests" :value {:id 55 :priority "medium"}}
               {:label "Deploy to staging" :value {:id 63 :priority "high"}}]

        custom-renderer (fn [item selected?]
                         (let [label (:label item)
                               priority (get-in item [:value :priority])
                               priority-color (condp = priority
                                               "high" :red
                                               "medium" :yellow
                                               "low" :cyan
                                               :reset)
                               badge (str "[" (str/upper-case priority) "]")]
                           (str label " " (core/color priority-color badge))))

        l (list/list-component
           :items tasks
           :label "Sprint Tasks (Filter & Select)"
           :multi-select true
           :show-filter true
           :height 8
           :item-renderer custom-renderer
           :width 55)]

    (print-list l "Task list with priorities:")

    (let [high-priority (list/set-filter l "high")]
      (print-list (assoc high-priority :show-filter true)
                  "Filtered to 'high' priority tasks:"))))

;; ──────────────── Main ────────────────
(defn -main []
  (println (core/color :bright-green "\n"))
  (println (core/color :bright-green "╔═══════════════════════════════════════════════════╗"))
  (println (core/color :bright-green "║                                                   ║"))
  (println (core/color :bright-green "║        LIMNER LIST COMPONENT SHOWCASE             ║"))
  (println (core/color :bright-green "║                                                   ║"))
  (println (core/color :bright-green "╚═══════════════════════════════════════════════════╝"))

  (demo-basic-lists)
  (demo-navigation)
  (demo-wrapping)
  (demo-multi-select)
  (demo-filtering)
  (demo-filter-input)
  (demo-scrolling)
  (demo-map-items)
  (demo-custom-renderer)
  (demo-indices)
  (demo-border-styles)
  (demo-multi-select-filter)
  (demo-helpers)
  (demo-item-management)
  (demo-complex)

  (println (core/color :bright-green "\n✓ List component demo complete!\n"))
  (println "Features demonstrated:")
  (println "  • Scrollable, selectable item lists")
  (println "  • Keyboard navigation (arrow keys, j/k vim-style)")
  (println "  • Selection wrapping at boundaries")
  (println "  • Multi-select with checkboxes")
  (println "  • Search/filter capability")
  (println "  • Filtered list preserves selection")
  (println "  • Scroll viewport follows selection")
  (println "  • Map items with separate labels and values")
  (println "  • Custom item rendering")
  (println "  • Show/hide item indices")
  (println "  • Different border styles")
  (println "  • Dynamic item management (add, remove, replace)")
  (println "  • Helper functions for state queries")
  (println))

(when (or (System/getProperty "babashka.version")
          (= *file* (System/getProperty "babashka.file")))
  (-main))
