(ns limner.table-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [limner.component-support :as component]
            [limner.core :as core]
            [limner :as legacy]
            [limner.table :as table]
            [limner.text :as text]))

(deftest ascii-table-golden
  (testing "Columns are sized by display width and padded on both sides"
    (component/assert-lines
     ["┌───────┬─────┐"
      "│ Name  │ Age │"
      "├───────┼─────┤"
      "│ Alice │ 30  │"
      "│ Bob   │ 7   │"
      "└───────┴─────┘"]
     (table/table [["Name" "Age"] ["Alice" "30"] ["Bob" "7"]]))))

(deftest cjk-table-golden
  (testing "Wide clusters size the column by columns, not UTF-16 units"
    (component/assert-lines
     ["┌──────┬───┐"
      "│ 名前 │ x │"
      "├──────┼───┤"
      "│ ab   │ y │"
      "└──────┴───┘"]
     (table/table [["名前" "x"] ["ab" "y"]]))))

(deftest emoji-table-golden
  (testing "An emoji ZWJ sequence counts as one two-column cell"
    (component/assert-lines
     ["┌────┬────┐"
      "│ a  │ 👩‍🔬 │"
      "├────┼────┤"
      "│ bb │ c  │"
      "└────┴────┘"]
     (table/table [["a" "👩‍🔬"] ["bb" "c"]]))))

(deftest styled-table-golden
  (testing "A styled cell keeps its style inside the row"
    (component/assert-lines
     ["┌───┐"
      "│ \u001B[31mR\u001B[39m │"
      "└───┘"]
     (table/table [[(core/color :red "R")]]))))

(deftest alignment-goldens
  (testing "A single alignment applies to every column"
    (component/assert-lines
     ["┌─────┬────┐"
      "│   a │ bb │"
      "├─────┼────┤"
      "│ ccc │  d │"
      "└─────┴────┘"]
     (table/table [["a" "bb"] ["ccc" "d"]] :align :right)))
  (testing "A per-column alignment vector wins over the default"
    (component/assert-lines
     ["┌─────┬────┐"
      "│ a   │ bb │"
      "├─────┼────┤"
      "│ ccc │  d │"
      "└─────┴────┘"]
     (table/table [["a" "bb"] ["ccc" "d"]] :align [:left :right])))
  (testing "Centering puts an odd spare column on the right"
    (component/assert-lines
     ["┌─────┬────┐"
      "│  a  │ bb │"
      "├─────┼────┤"
      "│ ccc │ d  │"
      "└─────┴────┘"]
     (table/table [["a" "bb"] ["ccc" "d"]] :align [:center :left]))))

(deftest header-option
  (testing "header? false drops the separator row"
    (component/assert-lines
     ["┌───┬───┐"
      "│ a │ b │"
      "│ c │ d │"
      "└───┴───┘"]
     (table/table [["a" "b"] ["c" "d"]] :header? false)))
  (testing "A single-row table has no separator"
    (component/assert-lines
     ["┌───┐"
      "│ a │"
      "└───┘"]
     (table/table [["a"]]))))

(deftest deprecated-table-preserves-its-string-contract
  (testing "The compatibility wrapper remains newline-terminated and right-aligned"
    (is (= (str "┌─────┬────┐\n"
                "│   a │ bb │\n"
                "├─────┼────┤\n"
                "│ ccc │  d │\n"
                "└─────┴────┘\n")
           (legacy/table [["a" "bb"] ["ccc" "d"]]))))
  (testing "header? false renders each input row once"
    (is (= (str "┌───┐\n"
                "│ a │\n"
                "│ b │\n"
                "└───┘\n")
           (legacy/table [["a"] ["b"]] :header? false)))))

(deftest ragged-rows-throw
  (testing "A row with the wrong number of cells throws with a hint"
    (let [data (try
                 (table/table [["a" "b"] ["c"]])
                 nil
                 (catch clojure.lang.ExceptionInfo e (ex-data e)))]
      (is (= :limner.table/ragged-rows (:limner/error data)))
      (is (= 1 (:row-index data)))
      (is (= 2 (:expected data)))
      (is (= 1 (:actual data)))
      (is (string? (:limner/hint data))))))

(deftest invalid-input-throws
  (is (= :limner.table/empty-rows
         (:limner/error (try (table/table []) nil
                             (catch clojure.lang.ExceptionInfo e (ex-data e))))))
  (is (= :limner.table/invalid-alignment
         (:limner/error (try (table/table [["a"]] :align :sideways) nil
                             (catch clojure.lang.ExceptionInfo e (ex-data e))))))
  (is (= :limner.table/invalid-row
         (:limner/error (try (table/table ["not-a-row"]) nil
                             (catch clojure.lang.ExceptionInfo e (ex-data e)))))))

(deftest every-table-line-has-one-width
  (testing "Generated tables keep one exact display width per line"
    (let [result (tc/quick-check
                  100
                  (prop/for-all [rows (gen/vector (gen/vector component/line-gen 1 4) 1 4)]
                    (let [columns (count (first rows))
                          rows (mapv #(vec (take columns (concat % (repeat "")))) rows)
                          rendered (table/table rows :header? false)]
                      (component/equal-line-width? rendered))))]
      (is (:pass? result) (pr-str result)))))

(deftest every-table-line-width-matches-columns
  (testing "Every rendered line equals the sum of column, padding, and rule widths"
    (let [result (tc/quick-check
                  100
                  (prop/for-all [rows (gen/vector (gen/vector component/line-gen 1 4) 1 4)]
                    (let [columns (count (first rows))
                          rows (mapv #(vec (take columns (concat % (repeat "")))) rows)
                          widths (mapv (fn [col]
                                         (apply max (map #(text/width (nth % col)) rows)))
                                       (range columns))
                          expected (+ (inc (count widths))
                                      (reduce + 0 (map #(+ % 2) widths)))
                          rendered (table/table rows :header? false)]
                      (every? #(= expected %)
                              (component/widths rendered)))))]
      (is (:pass? result) (pr-str result)))))
