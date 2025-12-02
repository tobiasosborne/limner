(ns limner.borders-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.borders :as borders]
            [clojure.string :as str]))

;; ────────────────────── Border Style Tests ──────────────────────
(deftest test-predefined-styles
  (testing "Single border style"
    (let [chars (borders/get-border-chars :single)]
      (is (= ["┌" "┐" "└" "┘" "─" "│"] chars))))

  (testing "Double border style"
    (let [chars (borders/get-border-chars :double)]
      (is (= ["╔" "╗" "╚" "╝" "═" "║"] chars))))

  (testing "Rounded border style"
    (let [chars (borders/get-border-chars :rounded)]
      (is (= ["╭" "╮" "╰" "╯" "─" "│"] chars))))

  (testing "Thick border style"
    (let [chars (borders/get-border-chars :thick)]
      (is (= ["┏" "┓" "┗" "┛" "━" "┃"] chars))))

  (testing "ASCII border style"
    (let [chars (borders/get-border-chars :ascii)]
      (is (= ["+" "+" "+" "+" "-" "|"] chars))))

  (testing "Default to single for unknown style"
    (let [chars (borders/get-border-chars :unknown)]
      (is (= ["┌" "┐" "└" "┘" "─" "│"] chars)))))

(deftest test-custom-style
  (testing "Custom style as vector"
    (let [custom ["<" ">" "{" "}" "=" "|"]
          chars (borders/get-border-chars custom)]
      (is (= custom chars))))

  (testing "Custom style as map"
    (let [custom {:top-left "A" :top-right "B"
                  :bottom-left "C" :bottom-right "D"
                  :horizontal "-" :vertical "|"}
          chars (borders/get-border-chars custom)]
      (is (= ["A" "B" "C" "D" "-" "|"] chars)))))

;; ────────────────────── Basic Box Drawing Tests ──────────────────────
(deftest test-draw-box
  (testing "Simple box with single line"
    (let [lines ["Hello"]
          box (borders/draw-box lines :border-style :single)]
      (is (= 3 (count box)))
      (is (str/starts-with? (first box) "┌"))
      (is (str/ends-with? (first box) "┐"))
      (is (str/starts-with? (last box) "└"))
      (is (str/ends-with? (last box) "┘"))))

  (testing "Box with multiple lines"
    (let [lines ["Line 1" "Line 2" "Line 3"]
          box (borders/draw-box lines :border-style :single)]
      (is (= 5 (count box)))
      (is (str/includes? (nth box 1) "Line 1"))
      (is (str/includes? (nth box 2) "Line 2"))
      (is (str/includes? (nth box 3) "Line 3"))))

  (testing "Box with double border style"
    (let [lines ["Content"]
          box (borders/draw-box lines :border-style :double)]
      (is (str/starts-with? (first box) "╔"))
      (is (str/ends-with? (first box) "╗")))))

(deftest test-draw-titled-box
  (testing "Box with left-aligned title"
    (let [lines ["Content"]
          box (borders/draw-titled-box "Title" lines :border-style :single :title-pos :left)]
      (is (= 3 (count box)))
      (is (str/includes? (first box) " Title "))))

  (testing "Box with center-aligned title"
    (let [lines ["Content here"]
          box (borders/draw-titled-box "Title" lines :border-style :single :title-pos :center)]
      (is (str/includes? (first box) " Title "))))

  (testing "Box with right-aligned title"
    (let [lines ["Content"]
          box (borders/draw-titled-box "Title" lines :border-style :single :title-pos :right)]
      (is (str/includes? (first box) " Title "))
      (is (str/ends-with? (first box) "┐")))))

;; ────────────────────── Shadow Tests ──────────────────────
(deftest test-shadow-effects
  (testing "Add light shadow"
    (let [lines ["┌─────┐" "│ Hi  │" "└─────┘"]
          with-shadow (borders/add-shadow lines)]
      (is (> (count with-shadow) (count lines)))
      ;; First line (top border) should NOT have shadow (light from above)
      (is (not (str/ends-with? (first with-shadow) "░")))
      ;; Middle and bottom lines should have shadow
      (is (str/ends-with? (second with-shadow) "░"))
      (is (str/ends-with? (nth with-shadow 2) "░"))))

  (testing "Add heavy shadow"
    (let [lines ["┌─────┐" "│ Hi  │" "└─────┘"]
          with-shadow (borders/add-heavy-shadow lines)]
      (is (> (count with-shadow) (count lines)))
      ;; First line (top border) should NOT have shadow (light from above)
      (is (not (str/includes? (first with-shadow) "▓")))
      ;; Middle and bottom lines should have shadow
      (is (str/includes? (second with-shadow) "▓"))
      (is (str/includes? (nth with-shadow 2) "▓")))))

;; ────────────────────── Nested Box Tests ──────────────────────
(deftest test-nested-boxes
  (testing "Indent lines"
    (let [lines ["Line 1" "Line 2"]
          indented (borders/indent-lines lines 3)]
      (is (every? #(str/starts-with? % "   ") indented))))

  (testing "Nest box with padding"
    (let [inner-lines ["┌────┐" "│ Hi │" "└────┘"]
          nested (borders/nest-box inner-lines 2)]
      (is (>= (count nested) (+ (count inner-lines) 4)))))

  (testing "Side by side boxes"
    (let [left ["┌───┐" "│ L │" "└───┘"]
          right ["┌───┐" "│ R │" "└───┘"]
          combined (borders/side-by-side left right 2)]
      (is (= (count left) (count combined)))
      (is (every? #(str/includes? % "  ") combined)))))

;; ────────────────────── Width Calculation Tests ──────────────────────
(deftest test-box-width-calculation
  (testing "Box width matches content"
    (let [lines ["Short" "Very long content line" "Med"]
          box (borders/draw-box lines)]
      ;; All lines should have same width
      (let [widths (map count box)]
        (is (apply = widths)))))

  (testing "Title doesn't overflow"
    (let [lines ["Content"]
          box (borders/draw-titled-box "Very Long Title Here" lines)]
      ;; Box should be at least as wide as the title
      (is (>= (count (first box)) 22)))))

;; ────────────────────── Edge Cases ──────────────────────
(deftest test-edge-cases
  (testing "Empty content"
    (let [lines [""]
          box (borders/draw-box lines)]
      (is (= 3 (count box)))))

  (testing "Single character content"
    (let [lines ["X"]
          box (borders/draw-box lines)]
      (is (= 3 (count box)))
      (is (str/includes? (nth box 1) "X"))))

  (testing "Very long single line"
    (let [lines [(apply str (repeat 100 "x"))]
          box (borders/draw-box lines)]
      (is (> (count (first box)) 100)))))

;; Run all tests
(defn run-tests []
  (clojure.test/run-tests 'limner.borders-test))
