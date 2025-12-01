(ns limner.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.core :as core]))

;; ──────────────────────── Color Validation Tests ──────────────────────

(deftest test-rgb-validation
  (testing "Valid RGB values"
    (is (= {:type :rgb :r 255 :g 128 :b 0}
           (core/rgb 255 128 0)))
    (is (= {:type :rgb :r 0 :g 0 :b 0}
           (core/rgb 0 0 0))))

  (testing "Invalid RGB values throw assertion"
    (is (thrown? AssertionError (core/rgb -1 0 0)))
    (is (thrown? AssertionError (core/rgb 256 0 0)))
    (is (thrown? AssertionError (core/rgb 0 -1 0)))
    (is (thrown? AssertionError (core/rgb 0 256 0)))
    (is (thrown? AssertionError (core/rgb 0 0 -1)))
    (is (thrown? AssertionError (core/rgb 0 0 256)))))

(deftest test-256-color-validation
  (testing "Valid 256-color codes"
    (is (= {:type :256 :code 0} (core/color-256 0)))
    (is (= {:type :256 :code 255} (core/color-256 255)))
    (is (= {:type :256 :code 196} (core/color-256 196))))

  (testing "Invalid 256-color codes throw assertion"
    (is (thrown? AssertionError (core/color-256 -1)))
    (is (thrown? AssertionError (core/color-256 256)))
    (is (thrown? AssertionError (core/color-256 1000)))))

(deftest test-bg-color-functions
  (testing "Background RGB"
    (is (= {:type :bg-rgb :r 100 :g 150 :b 200}
           (core/bg-rgb 100 150 200))))

  (testing "Background 256-color"
    (is (= {:type :bg-256 :code 42}
           (core/bg-256 42)))))

;; ──────────────────────── Color Application Tests ──────────────────────

(deftest test-basic-color-keywords
  (testing "Basic colors apply ANSI codes"
    (let [result (core/color :red "Hello")]
      (is (clojure.string/includes? result "\u001B[31m"))
      (is (clojure.string/includes? result "Hello"))
      (is (clojure.string/includes? result "\u001B[0m"))))

  (testing "Bright colors work"
    (let [result (core/color :bright-green "Success")]
      (is (clojure.string/includes? result "\u001B[92m"))
      (is (clojure.string/includes? result "Success"))))

  (testing "Background colors work"
    (let [result (core/color :bg-blue "Highlighted")]
      (is (clojure.string/includes? result "\u001B[44m"))
      (is (clojure.string/includes? result "Highlighted")))))

(deftest test-256-color-application
  (testing "256-color foreground"
    (let [result (core/color (core/color-256 196) "Red-ish")]
      (is (clojure.string/includes? result "\u001B[38;5;196m"))
      (is (clojure.string/includes? result "Red-ish"))))

  (testing "256-color background"
    (let [result (core/color (core/bg-256 17) "Dark bg")]
      (is (clojure.string/includes? result "\u001B[48;5;17m"))
      (is (clojure.string/includes? result "Dark bg")))))

(deftest test-rgb-color-application
  (testing "RGB foreground"
    (let [result (core/color (core/rgb 255 128 0) "Orange")]
      (is (clojure.string/includes? result "\u001B[38;2;255;128;0m"))
      (is (clojure.string/includes? result "Orange"))))

  (testing "RGB background"
    (let [result (core/color (core/bg-rgb 0 0 128) "Navy bg")]
      (is (clojure.string/includes? result "\u001B[48;2;0;0;128m"))
      (is (clojure.string/includes? result "Navy bg")))))

(deftest test-invalid-color-handling
  (testing "Invalid color keyword returns string uncolored"
    (let [result (core/color :nonexistent-color "Text")]
      (is (= "Text" result))))

  (testing "Invalid color type returns string uncolored"
    (let [result (core/color "not-a-color" "Text")]
      (is (= "Text" result)))))

(deftest test-color-nesting
  (testing "Colors can be nested"
    (let [result (core/color :bold (core/color :red "Bold Red"))]
      (is (clojure.string/includes? result "\u001B[1m"))
      (is (clojure.string/includes? result "\u001B[31m"))
      (is (clojure.string/includes? result "Bold Red")))))

;; ──────────────────────── Helper Function Tests ──────────────────────

(deftest test-available-colors
  (testing "Returns list of color keywords"
    (let [colors (core/available-colors)]
      (is (seq colors))
      (is (every? keyword? colors))
      (is (some #{:red} colors))
      (is (some #{:blue} colors))
      (is (some #{:bright-green} colors)))))

(deftest test-color-predicate
  (testing "Recognizes valid color specifications"
    (is (core/color? :red))
    (is (core/color? :bright-green))
    (is (core/color? (core/rgb 255 0 0)))
    (is (core/color? (core/color-256 42)))
    (is (core/color? (core/bg-rgb 0 0 0)))
    (is (core/color? (core/bg-256 100))))

  (testing "Rejects invalid color specifications"
    (is (not (core/color? :invalid-color)))
    (is (not (core/color? "red")))
    (is (not (core/color? 42)))
    (is (not (core/color? {:type :invalid})))))

(deftest test-color-presets
  (testing "Color presets are defined"
    (is (= :red (core/colors :error)))
    (is (= :green (core/colors :success)))
    (is (= :yellow (core/colors :warning)))
    (is (= :cyan (core/colors :info)))))

;; ──────────────────────── Visible Length Tests (Legacy) ──────────────────────

(deftest test-visible-length
  (testing "Plain strings"
    (is (= 5 (core/visible-length "Hello"))))

  (testing "Strings with ANSI codes"
    (is (= 5 (core/visible-length (core/color :red "Hello"))))
    (is (= 11 (core/visible-length (core/color :blue "Hello World")))))

  (testing "Nested ANSI codes"
    (is (= 8 (core/visible-length (core/color :bold (core/color :red "Bold Red"))))))

  (testing "Empty string"
    (is (= 0 (core/visible-length ""))))

  (testing "String with only ANSI codes"
    (is (= 0 (core/visible-length "\u001B[31m\u001B[0m")))))

;; ──────────────────────── Unicode Width Tests ──────────────────────

(deftest test-visible-width-ascii
  (testing "Plain ASCII strings"
    (is (= 5 (core/visible-width "Hello")))
    (is (= 13 (core/visible-width "Hello, World!")))
    (is (= 0 (core/visible-width ""))))

  (testing "ASCII with ANSI codes"
    (is (= 5 (core/visible-width (core/color :red "Hello"))))
    (is (= 11 (core/visible-width (core/color :blue "Hello World")))))

  (testing "Nested ANSI codes"
    (is (= 8 (core/visible-width (core/color :bold (core/color :red "Bold Red")))))))

(deftest test-visible-width-cjk
  (testing "Chinese characters (width 2 each)"
    (is (= 6 (core/visible-width "你好世")))  ; 3 chars × 2 = 6
    (is (= 4 (core/visible-width "日本"))))    ; 2 chars × 2 = 4

  (testing "Japanese Hiragana (width 2 each)"
    (is (= 10 (core/visible-width "こんにちは"))))  ; 5 chars × 2 = 10

  (testing "Japanese Katakana (width 2 each)"
    (is (= 8 (core/visible-width "カタカナ"))))  ; 4 chars × 2 = 8

  (testing "Korean Hangul syllables (width 2 each)"
    (is (= 10 (core/visible-width "안녕하세요"))))  ; 5 chars × 2 = 10

  (testing "Mixed ASCII and CJK"
    (is (= 9 (core/visible-width "Hello你好")))  ; 5 + 4 = 9
    (is (= 10 (core/visible-width "Test日本語"))))  ; 4 + 6 = 10

  (testing "CJK with ANSI colors"
    (is (= 4 (core/visible-width (core/color :red "日本"))))))

(deftest test-visible-width-emoji
  (testing "True emoji (width 2)"
    (is (= 2 (core/visible-width "😀")))
    (is (= 2 (core/visible-width "🎉")))
    (is (= 2 (core/visible-width "🎈")))
    (is (= 2 (core/visible-width "🚀"))))

  (testing "Symbol characters (width 1, not emoji)"
    (is (= 1 (core/visible-width "✓")))
    (is (= 1 (core/visible-width "✗")))
    (is (= 1 (core/visible-width "⚠")))
    (is (= 1 (core/visible-width "❤"))))

  (testing "Multiple emoji"
    (is (= 6 (core/visible-width "😀🎉🎈"))))

  (testing "Emoji with ASCII"
    (is (= 7 (core/visible-width "Test 😀"))))  ; 4 + 1 + 2 = 7

  (testing "Symbols with ANSI colors"
    (is (= 1 (core/visible-width (core/color :green "✓"))))))

(deftest test-visible-width-combining
  (testing "Combining diacritical marks (width 0)"
    ;; e + combining acute accent
    (is (= 1 (core/visible-width "é")))  ; NFC form
    (is (= 1 (core/visible-width "e\u0301"))))  ; NFD form with combining acute

  (testing "Zero-width characters"
    ;; Zero Width Space
    (is (= 10 (core/visible-width "Hello\u200BWorld")))  ; Hello=5, ZWS=0, World=5 = 10
    ;; Zero Width Non-Joiner
    (is (= 4 (core/visible-width "test\u200C"))))

  (testing "String with variation selectors (width 0)"
    ;; Text style variation selector
    (is (= 1 (core/visible-width "©\uFE0E")))))  ; Copyright symbol + text variation

(deftest test-visible-width-fullwidth
  (testing "Fullwidth ASCII forms (width 2)"
    ;; Fullwidth Latin letters
    (is (= 10 (core/visible-width "ＡＢＣＤＥ")))  ; 5 fullwidth chars × 2
    (is (= 6 (core/visible-width "１２３"))))  ; 3 fullwidth digits × 2

  (testing "Mixed halfwidth and fullwidth"
    (is (= 7 (core/visible-width "ABC１２")))))  ; 3 + 4 = 7

(deftest test-visible-width-control-chars
  (testing "Control characters (width 0)"
    (is (= 5 (core/visible-width "Hello")))
    (is (= 10 (core/visible-width "Hello\nWorld")))  ; newline = 0 width
    (is (= 10 (core/visible-width "Hello\tWorld")))  ; tab = 0 width (for display width)
    (is (= 10 (core/visible-width "Hello\rWorld")))))  ; CR = 0 width

(deftest test-visible-width-edge-cases
  (testing "Nil handling"
    (is (thrown? clojure.lang.ExceptionInfo (core/visible-width nil))))

  (testing "Empty string"
    (is (= 0 (core/visible-width ""))))

  (testing "String with only ANSI codes"
    (is (= 0 (core/visible-width "\u001B[31m\u001B[0m"))))

  (testing "Very long strings"
    (let [long-str (apply str (repeat 10000 "A"))]
      (is (= 10000 (core/visible-width long-str)))))

  (testing "Very long CJK strings"
    (let [long-cjk (apply str (repeat 1000 "中"))]
      (is (= 2000 (core/visible-width long-cjk))))))

(deftest test-visible-width-vs-length
  (testing "visible-width gives different results than visible-length for CJK"
    (is (= 2 (core/visible-width "日")))
    (is (= 1 (core/visible-length "日")))
    (is (not= (core/visible-width "日本語")
              (core/visible-length "日本語"))))

  (testing "visible-width same as visible-length for ASCII"
    (is (= (core/visible-width "Hello")
           (core/visible-length "Hello")))
    (is (= (core/visible-width "Test 123")
           (core/visible-length "Test 123")))))

(deftest test-visible-width-practical
  (testing "Practical UI examples"
    ;; Status indicators with symbols (width 1 each)
    (is (= 8 (core/visible-width "✓ Passed")))  ; 1 + 1 + 6 = 8
    (is (= 8 (core/visible-width "✗ Failed")))  ; 1 + 1 + 6 = 8

    ;; Mixed content
    (is (= 18 (core/visible-width "User: 张三 (Admin)")))  ; 6 + 4 + 8 = 18

    ;; Progress indicators (block elements are width 1)
    (is (= 6 (core/visible-width "█████░")))

    ;; Real emoji with text
    (is (= 13 (core/visible-width "✓ Success! 🎉")))))

;; ──────────────────────── Edge Cases ──────────────────────

(deftest test-edge-cases
  (testing "Empty string coloring"
    (is (string? (core/color :red ""))))

  (testing "Nil handling - should throw ex-info"
    (is (thrown? clojure.lang.ExceptionInfo (core/color :red nil))))

  (testing "Very long strings"
    (let [long-str (apply str (repeat 10000 "A"))
          result (core/color :red long-str)]
      (is (= 10000 (core/visible-length result)))))

  (testing "Special characters"
    (let [result (core/color :green "Hello\nWorld\tTab")]
      (is (clojure.string/includes? result "Hello\nWorld\tTab")))))

;; ──────────────────────── ANSI Code Format Tests ──────────────────────

(deftest test-ansi-code-formats
  (testing "Standard color format"
    (is (re-find #"\u001B\[3[0-7]m" (core/color :red "x"))))

  (testing "Bright color format"
    (is (re-find #"\u001B\[9[0-7]m" (core/color :bright-red "x"))))

  (testing "256-color format"
    (is (re-find #"\u001B\[38;5;\d+m" (core/color (core/color-256 42) "x"))))

  (testing "RGB format"
    (is (re-find #"\u001B\[38;2;\d+;\d+;\d+m" (core/color (core/rgb 255 128 0) "x"))))

  (testing "Background color format"
    (is (re-find #"\u001B\[4[0-7]m" (core/color :bg-red "x"))))

  (testing "All codes end with reset"
    (is (clojure.string/ends-with? (core/color :red "x") "\u001B[0m"))))

;; ──────────────────────── Performance Tests ──────────────────────

(deftest test-color-performance
  (testing "Color application is reasonably fast"
    (let [start (System/nanoTime)
          _ (dotimes [_ 10000]
              (core/color :red "Test"))
          elapsed (/ (- (System/nanoTime) start) 1000000.0)]
      ;; Should complete 10k iterations in under 100ms
      (is (< elapsed 100)
          (str "Color application too slow: " elapsed "ms")))))
