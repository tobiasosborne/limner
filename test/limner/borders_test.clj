(ns limner.borders-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [limner.borders :as borders]
            [limner.component-support :as component]
            [limner.core :as core]
            [limner.text :as text]))

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

  (testing "Unknown style throws with the ADR 0004 class"
    (is (thrown? clojure.lang.ExceptionInfo
                 (borders/get-border-chars :unknown)))))

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

;; ────────────────────── Column-correct goldens ──────────────────────
(deftest draw-box-cjk-golden
  (testing "CJK content keeps every line the same display width"
    (component/assert-lines
     ["┌────────┐"
      "│ 日本語 │"
      "└────────┘"]
     (borders/draw-box ["日本語"]))))

(deftest draw-titled-box-cjk-golden
  (testing "A CJK title sizes the box by columns, not UTF-16 units"
    (component/assert-lines
     ["┌ 日本語 ┐"
      "│ x      │"
      "└────────┘"
      ]
     (borders/draw-titled-box "日本語" ["x"]))))

(deftest draw-titled-box-styled-golden
  (testing "A pre-coloured title keeps its style and the box stays square"
    (component/assert-lines
     ["┌ \u001B[31mTitle\u001B[39m ┐"
      "│ x     │"
      "└───────┘"]
     (borders/draw-titled-box (str "\u001B[31mTitle\u001B[0m") ["x"]))))

(deftest tabs-expand-to-eight-column-stops
  (testing "A tab is cursor motion, so it expands before the box is measured"
    (component/assert-lines
     ["┌───────────┐"
      "│ a       b │"
      "│ abcd      │"
      "└───────────┘"]
     (borders/draw-box ["a\tb" "abcd"]))))

(deftest colorize-border-is-structural
  (testing "Rounded border rows are coloured end to end, content is not"
    (component/assert-lines
     ["\u001B[31m╭────╮\u001B[0m"
      "\u001B[31m│\u001B[39m hi \u001B[31m│\u001B[0m"
      "\u001B[31m╰────╯\u001B[0m"]
     (borders/colorize-border (borders/draw-box ["hi"] :border-style :rounded) :red)))
  (testing "Thick border rows are coloured end to end"
    (component/assert-lines
     ["\u001B[31m┏━━━━┓\u001B[0m"
      "\u001B[31m┃\u001B[39m hi \u001B[31m┃\u001B[0m"
      "\u001B[31m┗━━━━┛\u001B[0m"]
     (borders/colorize-border (borders/draw-box ["hi"] :border-style :thick) :red)))
  (testing "A title inside the top border is not coloured"
    (component/assert-lines
     ["\u001B[31m┌\u001B[39m Hi \u001B[31m┐\u001B[0m"
      "\u001B[31m│\u001B[39m x  \u001B[31m│\u001B[0m"
      "\u001B[31m└────┘\u001B[0m"]
     (borders/colorize-border (borders/draw-titled-box "Hi" ["x"]) :red))))

(deftest colorize-border-preserves-existing-styles-and-controls
  (testing "A border-like title glyph keeps its own colour inside a coloured border"
    (component/assert-lines
     ["\u001B[31m┌\u001B[39m \u001B[34m─\u001B[39m \u001B[31m┐\u001B[0m"
      "\u001B[31m│\u001B[39m \u001B[32mx\u001B[39m \u001B[31m│\u001B[0m"
      "\u001B[31m└───┘\u001B[0m"]
     (borders/colorize-border
      (borders/draw-titled-box (core/color :blue "─")
                               [(core/color :green "x")])
      :red)))
  (testing "An OSC 8 link in content survives border colouring exactly"
    (let [link "\u001B]8;;https://x\u001B\\x\u001B]8;;\u001B\\"]
      (component/assert-lines
       ["\u001B[31m┌───┐\u001B[0m"
        "\u001B[31m│\u001B[39m \u001B]8;;https://x\u001B\\x\u001B]8;;\u001B\\ \u001B[31m│\u001B[0m"
        "\u001B[31m└───┘\u001B[0m"]
       (borders/colorize-border (borders/draw-box [link]) :red)))))

(deftest custom-border-and-shadow-glyphs-obey-column-geometry
  (testing "Custom border strings must each be one display-column grapheme"
    (doseq [style [["界" "界" "界" "界" "界" "界"]
                   ["" "x" "x" "x" "x" "x"]]]
      (is (= :limner.borders/invalid-border-style
             (:limner/error
              (try (borders/draw-box ["x"] :border-style style)
                   nil
                   (catch clojure.lang.ExceptionInfo e (ex-data e))))))))
  (testing "A wide shadow fill is sliced by columns, not repeated by occurrence"
    (let [box (borders/draw-box ["x"])]
      (component/assert-lines
       ["┌───┐" "│ x │ " "└───┘ " "  界界 "]
       (borders/add-shadow box :shadow-char "界"))
      (component/assert-lines
       ["┌───┐" "│ x │界" "└───┘界" "  界界 " "  界界 "]
       (borders/add-heavy-shadow box :shadow-char "界")))))

(deftest every-drawn-line-has-one-width
  (testing "draw-box: generated content keeps one exact display width"
    (let [result (tc/quick-check
                  100
                  (prop/for-all [lines component/lines-gen]
                    (let [rendered (borders/draw-box lines)
                          expected (+ 4 (apply max 0 (map text/width lines)))]
                      (and (= (+ 2 (count lines)) (count rendered))
                           (every? #(= expected (text/width %)) rendered)))))]
      (is (:pass? result) (pr-str result))))
  (testing "draw-titled-box: title and content share one width"
    (let [result (tc/quick-check
                  100
                  (prop/for-all [title component/title-gen
                                 lines component/lines-gen]
                    (component/equal-line-width?
                     (borders/draw-titled-box title lines))))]
      (is (:pass? result) (pr-str result)))))

;; Run all tests
(defn run-tests []
  (clojure.test/run-tests 'limner.borders-test))
