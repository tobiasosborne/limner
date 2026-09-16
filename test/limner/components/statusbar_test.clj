(ns limner.components.statusbar-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [limner.ansi :as ansi]
            [limner.component-support :as component]
            [limner.components.statusbar :as statusbar]
            [limner.core :as core]
            [limner.text :as text]))

;; ────────────────────── Statusbar Creation Tests ──────────────────────
(deftest test-statusbar-creation
  (testing "Create basic statusbar"
    (let [bar (statusbar/statusbar :left "File: test.clj"
                                   :center "Ready"
                                   :right "Ctrl+C")]
      (is (map? bar))
      (is (= "File: test.clj" (:left bar)))
      (is (= "Ready" (:center bar)))
      (is (= "Ctrl+C" (:right bar)))))

  (testing "Create statusbar with only left section"
    (let [bar (statusbar/statusbar :left "Status")]
      (is (= "Status" (:left bar)))
      (is (or (nil? (:center bar)) (= "" (:center bar))))
      (is (or (nil? (:right bar)) (= "" (:right bar))))))

  (testing "Create statusbar with custom width"
    (let [bar (statusbar/statusbar :left "Test" :width 50)]
      (is (= 50 (:width bar)))))

  (testing "Create statusbar with background color"
    (let [bar (statusbar/statusbar :left "Test" :bg-color :blue)]
      (is (= :blue (:bg-color bar))))))

;; ────────────────────── Three-Section Layout Tests ──────────────────────
(deftest test-three-section-layout
  (testing "Three sections distribute width correctly"
    (let [bar (statusbar/statusbar :left "Left"
                                   :center "Center"
                                   :right "Right"
                                   :width 80)
          rendered (statusbar/render bar)
          visible (core/visible-length rendered)]
      (is (= 80 visible))))

  (testing "Left section aligns to left"
    (let [bar (statusbar/statusbar :left "LEFT" :width 20)
          rendered (statusbar/render bar)
          ;; Strip ANSI codes to check layout
          plain (ansi/strip rendered)]
      (is (str/starts-with? plain "LEFT"))))

  (testing "Right section aligns to right"
    (let [bar (statusbar/statusbar :right "RIGHT" :width 20)
          rendered (statusbar/render bar)
          plain (ansi/strip rendered)]
      (is (str/ends-with? plain "RIGHT"))))

  (testing "Center section is centered"
    (let [bar (statusbar/statusbar :center "MID" :width 20)
          rendered (statusbar/render bar)
          plain (ansi/strip rendered)
          center-pos (.indexOf plain "MID")
          expected-pos (/ (- 20 3) 2)] ; 3 is length of "MID"
      ;; Center should be roughly in the middle (allowing for rounding)
      (is (< (if (neg? (- center-pos expected-pos))
               (- (- center-pos expected-pos))
               (- center-pos expected-pos))
             2)))))

;; ────────────────────── Overflow and Truncation Tests ──────────────────────
(deftest test-overflow-truncation
  (testing "Long left section truncates gracefully"
    (let [bar (statusbar/statusbar :left "This is a very long status message that exceeds width"
                                   :width 20)
          rendered (statusbar/render bar)
          visible (core/visible-length rendered)]
      (is (<= visible 20))))

  (testing "Long right section truncates gracefully"
    (let [bar (statusbar/statusbar :right "This is a very long right section"
                                   :width 20)
          rendered (statusbar/render bar)
          visible (core/visible-length rendered)]
      (is (<= visible 20))))

  (testing "All three sections when too long truncate gracefully"
    (let [bar (statusbar/statusbar :left "Long left section here"
                                   :center "Long center"
                                   :right "Long right section"
                                   :width 30)
          rendered (statusbar/render bar)
          visible (core/visible-length rendered)]
      (is (<= visible 30))))

  (testing "Truncation adds an ellipsis"
    (let [bar (statusbar/statusbar :left "This is a very long message"
                                   :width 15)
          rendered (statusbar/render bar)
          plain (ansi/strip rendered)]
      (is (str/includes? plain "…")))))

(deftest caller-styles-and-wide-sections
  (testing "A caller foreground survives composition"
    (component/assert-lines
     ["\u001B[31mX\u001B[39m   "]
     [(statusbar/render (statusbar/statusbar
                         :left (core/color :red "X")
                         :width 4 :bg-color nil :fg-color nil))]))
  (testing "A caller foreground wins over the bar's default foreground"
    (let [spans (text/from-ansi
                 (statusbar/render
                  (statusbar/statusbar :left (core/color :red "Error")
                                       :width 20 :bg-color nil :fg-color :white)))
          red (first (filter #(= "Error" (:text %)) spans))]
      (is (= :red (:fg (:style red))))))
  (testing "CJK, an emoji ZWJ sequence, and a styled section fit exactly"
    (component/assert-lines
     ["日本    👩‍🔬     \u001B[31mR\u001B[0m"]
     [(statusbar/render (statusbar/statusbar
                         :left "日本" :center "👩‍🔬"
                         :right (core/color :red "R")
                         :width 16 :bg-color nil :fg-color nil))])))

(deftest statusbar-width-property
  (testing "Every statusbar is exactly its requested width"
    (let [result (tc/quick-check
                  100
                  (prop/for-all [left component/line-gen
                                 center component/line-gen
                                 right component/line-gen
                                 width (gen/choose 1 40)]
                    (= width (text/width
                              (statusbar/render
                               (statusbar/statusbar :left left :center center
                                                    :right right :width width))))))]
      (is (:pass? result) (pr-str result)))))

(deftest statusbar-width-validation
  (testing "zero columns render as the exact empty line"
    (component/assert-lines [""]
                            [(statusbar/render (statusbar/statusbar :width 0))]))
  (testing "invalid widths throw the statusbar error class"
    (doseq [width [-1 1.5 "20"]]
      (is (= :limner.components.statusbar/invalid-width
             (:limner/error
              (try (statusbar/render (statusbar/statusbar :width width))
                   nil
                   (catch clojure.lang.ExceptionInfo e (ex-data e)))))))))

(deftest non-sgr-controls-do-not-enter-statusbar-layout
  (testing "An OSC 8 hyperlink occupies no layout columns but survives"
    (is (= "\u001B]8;;https://x\u001B\\link\u001B]8;;\u001B\\    "
           (statusbar/render
            (statusbar/statusbar
             :left "\u001B]8;;https://x\u001B\\link\u001B]8;;\u001B\\"
             :width 8 :bg-color nil :fg-color nil))))))

;; ────────────────────── Background Color Tests ──────────────────────
(deftest test-background-colors
  (testing "Default background color"
    (let [bar (statusbar/statusbar :left "Test")
          rendered (statusbar/render bar)]
      (is (string? rendered))
      ;; Default should have some background
      (is (pos? (count rendered)))))

  (testing "Custom background color applied"
    (let [bar (statusbar/statusbar :left "Test" :bg-color :blue)
          rendered (statusbar/render bar)]
      ;; Should contain background color code
      (is (str/includes? rendered "\u001B["))))

  (testing "Background fills entire width"
    (let [bar (statusbar/statusbar :left "X" :width 20)
          rendered (statusbar/render bar)
          visible (core/visible-length rendered)]
      (is (= 20 visible)))))

;; ────────────────────── Update Tests ──────────────────────
(deftest test-statusbar-updates
  (testing "Update left section"
    (let [bar (statusbar/statusbar :left "Old")
          updated (statusbar/update-section bar :left "New")]
      (is (= "New" (:left updated)))))

  (testing "Update center section"
    (let [bar (statusbar/statusbar :center "Old")
          updated (statusbar/update-section bar :center "New")]
      (is (= "New" (:center updated)))))

  (testing "Update right section"
    (let [bar (statusbar/statusbar :right "Old")
          updated (statusbar/update-section bar :right "New")]
      (is (= "New" (:right updated)))))

  (testing "Update multiple sections"
    (let [bar (statusbar/statusbar :left "L" :center "C" :right "R")
          updated (-> bar
                      (statusbar/update-section :left "Left")
                      (statusbar/update-section :right "Right"))]
      (is (= "Left" (:left updated)))
      (is (= "Right" (:right updated))))))

;; ────────────────────── Helper Function Tests ──────────────────────
(deftest test-helper-functions
  (testing "Format git branch"
    (let [formatted (statusbar/format-git-branch "main")]
      (is (string? formatted))
      (is (str/includes? formatted "main"))))

  (testing "Format file info"
    (let [formatted (statusbar/format-file-info "test.clj" 42 10)]
      (is (string? formatted))
      (is (str/includes? formatted "test.clj"))
      (is (str/includes? formatted "42"))
      (is (str/includes? formatted "10"))))

  (testing "Format timestamp"
    (let [formatted (statusbar/format-timestamp)]
      (is (string? formatted))
      (is (pos? (count formatted)))))

  (testing "Format keybinding hint"
    (let [formatted (statusbar/format-keybinding "Ctrl+C" "Quit")]
      (is (string? formatted))
      (is (str/includes? formatted "Ctrl+C"))
      (is (str/includes? formatted "Quit")))))

;; ────────────────────── Width Calculation Tests ──────────────────────
(deftest test-width-calculations
  (testing "Calculate section widths for balanced layout"
    (let [bar (statusbar/statusbar :left "Left"
                                   :center "Center"
                                   :right "Right"
                                   :width 60)]
      ;; Each section should have roughly equal space
      (is (= 60 (:width bar)))))

  (testing "Width distribution prioritizes left when space limited"
    (let [bar (statusbar/statusbar :left "Important"
                                   :center "Less"
                                   :right "OK"
                                   :width 20)
          rendered (statusbar/render bar)]
      (is (str/includes? rendered "Important"))))

  (testing "Empty sections don't consume space"
    (let [bar (statusbar/statusbar :left "Left" :right "Right" :width 30)
          rendered (statusbar/render bar)
          visible (core/visible-length rendered)]
      (is (= 30 visible)))))

;; ────────────────────── Rendering Edge Cases ──────────────────────
(deftest test-rendering-edge-cases
  (testing "Very narrow width"
    (let [bar (statusbar/statusbar :left "Test" :width 5)
          rendered (statusbar/render bar)
          visible (core/visible-length rendered)]
      (is (<= visible 5))))

  (testing "Empty statusbar"
    (let [bar (statusbar/statusbar :width 20)
          rendered (statusbar/render bar)
          visible (core/visible-length rendered)]
      (is (= 20 visible))))

  (testing "Unicode characters in sections"
    (let [bar (statusbar/statusbar :left "🔧 Tools" :center "✓ Ready" :right "⚡ Fast")
          rendered (statusbar/render bar)]
      (is (string? rendered))))

  (testing "ANSI codes in section text"
    (let [bar (statusbar/statusbar :left (core/color :red "Error"))
          rendered (statusbar/render bar)]
      (is (string? rendered))
      (is (str/includes? rendered "Error")))))

;; ────────────────────── Integration Tests ──────────────────────
(deftest test-real-world-scenarios
  (testing "Claude Code style statusbar"
    (let [bar (statusbar/statusbar
               :left (str (statusbar/format-git-branch "main")
                         " "
                         (statusbar/format-file-info "app.clj" 123 45))
               :center "Ready"
               :right (statusbar/format-keybinding "Ctrl+C" "Quit")
               :width 80
               :bg-color :blue)
          rendered (statusbar/render bar)
          visible (core/visible-length rendered)]
      (is (= 80 visible))
      (is (str/includes? rendered "main"))
      (is (str/includes? rendered "app.clj"))
      (is (str/includes? rendered "Ready"))
      (is (str/includes? rendered "Ctrl+C"))))

  (testing "Dynamic update scenario"
    (let [initial (statusbar/statusbar :left "Loading..." :width 40)
          updated (statusbar/update-section initial :left "Complete!")]
      (is (not= (:left initial) (:left updated)))
      (is (= "Complete!" (:left updated))))))

;; Run all tests
(defn run-tests []
  (clojure.test/run-tests 'limner.components.statusbar-test))
