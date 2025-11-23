(ns limner.components.list-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.components.list :as list]
            [clojure.string :as str]))

;; ────────────────────── List Creation Tests ──────────────────────
(deftest test-list-creation
  (testing "Basic list creation"
    (let [l (list/list-component :items ["A" "B" "C"])]
      (is (= ["A" "B" "C"] (:items l)))
      (is (= 0 (:selected l)))
      (is (= 10 (:height l)))
      (is (false? (:multi-select l)))))

  (testing "List with all options"
    (let [l (list/list-component
             :items ["Item 1" "Item 2"]
             :selected 1
             :multi-select true
             :height 5
             :width 30
             :label "My List"
             :show-indices true)]
      (is (= ["Item 1" "Item 2"] (:items l)))
      (is (= 1 (:selected l)))
      (is (true? (:multi-select l)))
      (is (= 5 (:height l)))
      (is (= 30 (:width l)))
      (is (= "My List" (:label l)))
      (is (true? (:show-indices l))))))

;; ────────────────────── Item Normalization Tests ──────────────────────
(deftest test-item-normalization
  (testing "Normalize string item"
    (let [item (list/normalize-item "Hello")]
      (is (= "Hello" (:label item)))
      (is (= "Hello" (:value item)))))

  (testing "Normalize map item"
    (let [item (list/normalize-item {:label "Display" :value 42})]
      (is (= "Display" (:label item)))
      (is (= 42 (:value item)))))

  (testing "Get item label"
    (is (= "Test" (list/item-label "Test")))
    (is (= "Label" (list/item-label {:label "Label" :value "val"}))))

  (testing "Get item value"
    (is (= "Test" (list/item-value "Test")))
    (is (= "val" (list/item-value {:label "Label" :value "val"})))))

;; ────────────────────── Selection Management Tests ──────────────────────
(deftest test-selection-movement
  (testing "Select next item"
    (let [l (list/list-component :items ["A" "B" "C"])
          next (list/select-next l 1)]
      (is (= 1 (:selected next)))))

  (testing "Select previous item"
    (let [l (list/list-component :items ["A" "B" "C"] :selected 2)
          prev (list/select-prev l 1)]
      (is (= 1 (:selected prev)))))

  (testing "Select multiple steps"
    (let [l (list/list-component :items ["A" "B" "C" "D" "E"])
          next (list/select-next l 3)]
      (is (= 3 (:selected next)))))

  (testing "Select first item"
    (let [l (list/list-component :items ["A" "B" "C"] :selected 2)
          first-item (list/select-first l)]
      (is (= 0 (:selected first-item)))))

  (testing "Select last item"
    (let [l (list/list-component :items ["A" "B" "C"])
          last-item (list/select-last l)]
      (is (= 2 (:selected last-item)))))

  (testing "Select item by index"
    (let [l (list/list-component :items ["A" "B" "C" "D"])
          selected (list/select-item l 2)]
      (is (= 2 (:selected selected))))))

;; ────────────────────── Boundary Wrapping Tests ──────────────────────
(deftest test-boundary-wrapping
  (testing "Wrap forward at end"
    (let [l (list/list-component :items ["A" "B" "C"] :selected 2 :wrap true)
          next (list/select-next l 1)]
      (is (= 0 (:selected next)))))

  (testing "Wrap backward at start"
    (let [l (list/list-component :items ["A" "B" "C"] :selected 0 :wrap true)
          prev (list/select-prev l 1)]
      (is (= 2 (:selected prev)))))

  (testing "No wrap forward at end"
    (let [l (list/list-component :items ["A" "B" "C"] :selected 2 :wrap false)
          next (list/select-next l 1)]
      (is (= 2 (:selected next)))))

  (testing "No wrap backward at start"
    (let [l (list/list-component :items ["A" "B" "C"] :selected 0 :wrap false)
          prev (list/select-prev l 1)]
      (is (= 0 (:selected prev))))))

;; ────────────────────── Multi-Select Tests ──────────────────────
(deftest test-multi-select
  (testing "Toggle selection"
    (let [l (list/list-component :items ["A" "B" "C"] :multi-select true :selected 0)
          toggled (list/toggle-selection l)]
      (is (contains? (:selected-items toggled) 0))))

  (testing "Toggle selection off"
    (let [l (list/list-component :items ["A" "B" "C"]
                                :multi-select true
                                :selected 0
                                :selected-items #{0})
          toggled (list/toggle-selection l)]
      (is (not (contains? (:selected-items toggled) 0)))))

  (testing "Select all items"
    (let [l (list/list-component :items ["A" "B" "C"] :multi-select true)
          all (list/select-all l)]
      (is (= #{0 1 2} (:selected-items all)))))

  (testing "Deselect all items"
    (let [l (list/list-component :items ["A" "B" "C"]
                                :multi-select true
                                :selected-items #{0 1 2})
          none (list/deselect-all l)]
      (is (= #{} (:selected-items none)))))

  (testing "Get selected items"
    (let [l (list/list-component :items ["A" "B" "C"]
                                :multi-select true
                                :selected-items #{0 2})
          selected (list/get-selected-items l)]
      (is (= ["A" "C"] selected))))

  (testing "Multi-select disabled for single-select lists"
    (let [l (list/list-component :items ["A" "B" "C"] :multi-select false :selected 0)
          toggled (list/toggle-selection l)]
      (is (= #{} (:selected-items toggled))))))

;; ────────────────────── Filtering Tests ──────────────────────
(deftest test-filtering
  (testing "Filter items"
    (let [l (list/list-component :items ["Apple" "Banana" "Cherry" "Apricot"])
          filtered (list/set-filter l "ap")]
      (is (= ["Apple" "Apricot"] (list/filtered-items filtered)))))

  (testing "Case-insensitive filtering"
    (let [l (list/list-component :items ["Apple" "BANANA" "cherry"])
          filtered (list/set-filter l "AN")]
      (is (= ["BANANA"] (list/filtered-items filtered)))))

  (testing "Clear filter"
    (let [l (list/list-component :items ["A" "B" "C"] :filter-text "B")
          cleared (list/clear-filter l)]
      (is (= "" (:filter-text cleared)))
      (is (= ["A" "B" "C"] (list/filtered-items cleared)))))

  (testing "Empty filter shows all items"
    (let [l (list/list-component :items ["A" "B" "C"] :filter-text "")]
      (is (= ["A" "B" "C"] (list/filtered-items l)))))

  (testing "Filter resets selection"
    (let [l (list/list-component :items ["A" "B" "C"] :selected 2)
          filtered (list/set-filter l "B")]
      (is (= 0 (:selected filtered))))))

;; ────────────────────── Filtered Selection Tests ──────────────────────
(deftest test-filtered-selection
  (testing "Selection works in filtered list"
    (let [l (list/list-component :items ["Apple" "Banana" "Apricot"])
          filtered (list/set-filter l "ap")
          next (list/select-next filtered 1)]
      (is (= 1 (:selected next)))
      (is (= ["Apple" "Apricot"] (list/filtered-items next)))))

  (testing "Multi-select in filtered list"
    (let [l (list/list-component :items ["Apple" "Banana" "Apricot" "Cherry"]
                                :multi-select true)
          filtered (list/set-filter l "ap")
          toggled (list/toggle-selection filtered)]
      ;; First filtered item is Apple (index 0 in original)
      (is (contains? (:selected-items toggled) 0))))

  (testing "Get filtered item indices"
    (let [l (list/list-component :items ["Apple" "Banana" "Apricot" "Cherry"])
          filtered (list/set-filter l "ap")
          indices (list/filtered-item-indices filtered)]
      (is (= [0 2] indices)))))

;; ────────────────────── Scrolling Tests ──────────────────────
(deftest test-scrolling
  (testing "Adjust scroll to keep selection visible"
    (let [l (list/list-component :items (vec (range 20)) :height 5 :selected 7)
          adjusted (list/adjust-scroll l)]
      (is (<= (:scroll-offset adjusted) 7))
      (is (>= (+ (:scroll-offset adjusted) 5) 8))))

  (testing "Scroll follows selection down"
    (let [l (list/list-component :items (vec (range 20)) :height 5 :selected 0)
          next (-> l
                   (list/select-item 6)
                   list/adjust-scroll)]
      (is (>= (:scroll-offset next) 2))))

  (testing "Scroll follows selection up"
    (let [l (list/list-component :items (vec (range 20)) :height 5 :selected 10 :scroll-offset 6)
          prev (-> l
                   (list/select-item 3)
                   list/adjust-scroll)]
      (is (<= (:scroll-offset prev) 3))))

  (testing "Visible items in viewport"
    (let [l (list/list-component :items (vec (range 20)) :height 5 :scroll-offset 3)
          visible (list/visible-items l)]
      (is (= [3 4 5 6 7] visible)))))

;; ────────────────────── Get Current Item Tests ──────────────────────
(deftest test-get-current-item
  (testing "Get current item"
    (let [l (list/list-component :items ["A" "B" "C"] :selected 1)
          current (list/get-current-item l)]
      (is (= "B" current))))

  (testing "Get current item with map items"
    (let [l (list/list-component :items [{:label "One" :value 1}
                                        {:label "Two" :value 2}]
                                :selected 1)
          current (list/get-current-item l)]
      (is (= {:label "Two" :value 2} current))))

  (testing "Get current item from filtered list"
    (let [l (list/list-component :items ["Apple" "Banana" "Apricot"])
          filtered (list/set-filter l "ap")
          current (list/get-current-item filtered)]
      (is (= "Apple" current)))))

;; ────────────────────── Rendering Tests ──────────────────────
(deftest test-basic-rendering
  (testing "Render simple list"
    (let [l (list/list-component :items ["A" "B" "C"] :border false :height 3)
          rendered (list/render l)]
      (is (vector? rendered))
      (is (= 3 (count rendered)))))

  (testing "Render with border"
    (let [l (list/list-component :items ["A" "B" "C"] :border true :height 3)
          rendered (list/render l)]
      (is (> (count rendered) 3))))

  (testing "Render with label"
    (let [l (list/list-component :items ["A" "B"] :label "Options" :height 3)
          rendered (list/render l)
          output (str/join rendered)]
      (is (str/includes? output "Options"))))

  (testing "Selected item is highlighted"
    (let [l (list/list-component :items ["A" "B" "C"] :selected 1 :border false :height 3)
          rendered (list/render l)
          line2 (get rendered 1)]
      (is (str/includes? line2 ">"))))

  (testing "Multi-select shows checkboxes"
    (let [l (list/list-component :items ["A" "B"] :multi-select true :height 3 :border false)
          rendered (list/render l)
          output (str/join rendered)]
      (is (or (str/includes? output "☐") (str/includes? output "☑"))))))

;; ────────────────────── Helper Function Tests ──────────────────────
(deftest test-helper-functions
  (testing "list? predicate"
    (let [l (list/list-component :items ["A" "B"])]
      (is (true? (list/list? l)))
      (is (false? (list/list? "not a list")))
      (is (false? (list/list? nil)))))

  (testing "empty? predicate"
    (let [empty-list (list/list-component :items [])
          non-empty (list/list-component :items ["A"])]
      (is (true? (list/empty? empty-list)))
      (is (false? (list/empty? non-empty)))))

  (testing "filtered-empty? predicate"
    (let [l (list/list-component :items ["Apple" "Banana"])
          filtered (list/set-filter l "Cherry")]
      (is (true? (list/filtered-empty? filtered)))))

  (testing "item-count"
    (let [l (list/list-component :items ["A" "B" "C"])]
      (is (= 3 (list/item-count l)))))

  (testing "filtered-item-count"
    (let [l (list/list-component :items ["Apple" "Banana" "Apricot"])
          filtered (list/set-filter l "ap")]
      (is (= 2 (list/filtered-item-count filtered)))))

  (testing "has-filter? predicate"
    (let [l (list/list-component :items ["A" "B"])
          filtered (list/set-filter l "A")]
      (is (false? (list/has-filter? l)))
      (is (true? (list/has-filter? filtered)))))

  (testing "multi-select? predicate"
    (let [single (list/list-component :items ["A" "B"])
          multi (list/list-component :items ["A" "B"] :multi-select true)]
      (is (false? (list/multi-select? single)))
      (is (true? (list/multi-select? multi)))))

  (testing "selection-count"
    (let [l (list/list-component :items ["A" "B" "C"]
                                :multi-select true
                                :selected-items #{0 2})]
      (is (= 2 (list/selection-count l)))))

  (testing "all-selected? predicate"
    (let [l (list/list-component :items ["A" "B" "C"] :multi-select true)
          all (list/select-all l)
          some (list/toggle-selection l)]
      (is (true? (list/all-selected? all)))
      (is (false? (list/all-selected? some))))))

;; ────────────────────── Convenience Function Tests ──────────────────────
(deftest test-convenience-functions
  (testing "set-items replaces items"
    (let [l (list/list-component :items ["A" "B"])
          updated (list/set-items l ["X" "Y" "Z"])]
      (is (= ["X" "Y" "Z"] (:items updated)))))

  (testing "add-item appends item"
    (let [l (list/list-component :items ["A" "B"])
          updated (list/add-item l "C")]
      (is (= ["A" "B" "C"] (:items updated)))))

  (testing "remove-item deletes item"
    (let [l (list/list-component :items ["A" "B" "C"])
          updated (list/remove-item l 1)]
      (is (= ["A" "C"] (:items updated)))))

  (testing "remove-item clamps selection"
    (let [l (list/list-component :items ["A" "B" "C"] :selected 2)
          updated (list/remove-item l 2)]
      (is (= 1 (:selected updated)))))

  (testing "toggle-filter"
    (let [l (list/list-component :items ["A"] :show-filter false)
          toggled (list/toggle-filter l)]
      (is (true? (:show-filter toggled)))
      (is (false? (:show-filter (list/toggle-filter toggled)))))))

;; ────────────────────── Integration Tests ──────────────────────
(deftest test-navigation-workflow
  (testing "Complete navigation workflow"
    (let [l (list/list-component :items ["A" "B" "C" "D" "E"] :height 3)]
      ;; Start at first item
      (is (= 0 (:selected l)))

      ;; Move down twice
      (let [l2 (-> l
                   (list/select-next 1)
                   (list/select-next 1))]
        (is (= 2 (:selected l2)))

        ;; Move up once
        (let [l3 (list/select-prev l2 1)]
          (is (= 1 (:selected l3)))

          ;; Jump to last
          (let [l4 (list/select-last l3)]
            (is (= 4 (:selected l4)))

            ;; Jump to first
            (let [l5 (list/select-first l4)]
              (is (= 0 (:selected l5))))))))))

(deftest test-multi-select-workflow
  (testing "Complete multi-select workflow"
    (let [l (list/list-component :items ["A" "B" "C" "D"] :multi-select true)]
      ;; Select first item
      (let [l1 (list/toggle-selection l)]
        (is (= #{0} (:selected-items l1)))

        ;; Move to second and select
        (let [l2 (-> l1
                     (list/select-next 1)
                     list/toggle-selection)]
          (is (= #{0 1} (:selected-items l2)))

          ;; Move to fourth and select
          (let [l3 (-> l2
                       (list/select-item 3)
                       list/toggle-selection)]
            (is (= #{0 1 3} (:selected-items l3)))

            ;; Deselect second
            (let [l4 (-> l3
                         (list/select-item 1)
                         list/toggle-selection)]
              (is (= #{0 3} (:selected-items l4)))

              ;; Get selected items
              (let [selected (list/get-selected-items l4)]
                (is (= ["A" "D"] selected))))))))))

(deftest test-filter-and-select-workflow
  (testing "Filter and selection workflow"
    (let [l (list/list-component :items ["Apple" "Banana" "Apricot" "Cherry" "Avocado"])]
      ;; Apply filter
      (let [filtered (list/set-filter l "a")]
        (is (= 4 (list/filtered-item-count filtered)))

        ;; More specific filter
        (let [filtered2 (list/set-filter l "ap")]
          (is (= 2 (list/filtered-item-count filtered2)))
          (is (= "Apple" (list/get-current-item filtered2)))

          ;; Navigate in filtered list
          (let [filtered3 (list/select-next filtered2 1)]
            (is (= "Apricot" (list/get-current-item filtered3)))

            ;; Clear filter
            (let [cleared (list/clear-filter filtered3)]
              (is (= 5 (list/item-count cleared)))
              (is (= 0 (:selected cleared))))))))))

;; Run all tests
(defn run-tests []
  (clojure.test/run-tests 'limner.components.list-test))
