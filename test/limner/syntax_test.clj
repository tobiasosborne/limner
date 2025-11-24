(ns limner.syntax-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.syntax :as syntax]
            [clojure.string :as str]))

;; ────────────────────── Clojure Syntax Tests ──────────────────────
(deftest test-clojure-keywords
  (testing "Highlight Clojure keywords"
    (let [code ":keyword :another-keyword :ns/qualified"
          highlighted (syntax/highlight code :clojure)]
      (is (string? highlighted))
      (is (str/includes? highlighted "keyword"))
      ;; Should have ANSI codes
      (is (str/includes? highlighted "\u001B["))))

  (testing "Clojure def keywords"
    (let [code "(defn my-fn [] nil)"
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "defn"))
      ;; defn should be highlighted as keyword
      (is (str/includes? highlighted "\u001B["))))

  (testing "Clojure special forms"
    (let [code "(let [x 1] (if x true false))"
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "let"))
      (is (str/includes? highlighted "if")))))

(deftest test-clojure-strings
  (testing "Highlight Clojure strings"
    (let [code "\"hello world\""
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "hello world"))
      ;; String should be colored
      (is (str/includes? highlighted "\u001B["))))

  (testing "Strings with escaped quotes"
    (let [code "\"hello \\\"world\\\"\""
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "hello"))
      (is (str/includes? highlighted "world"))))

  (testing "Multiline strings"
    (let [code "\"line 1\nline 2\""
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "line 1"))
      (is (str/includes? highlighted "line 2")))))

(deftest test-clojure-comments
  (testing "Single-line comments"
    (let [code "; this is a comment\n(def x 1)"
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "this is a comment"))
      ;; Comment should be colored differently
      (is (str/includes? highlighted "\u001B["))))

  (testing "Inline comments"
    (let [code "(def x 1) ; inline comment"
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "inline comment"))))

  (testing "Multiple comments"
    (let [code "; comment 1\n; comment 2\n(def x 1)"
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "comment 1"))
      (is (str/includes? highlighted "comment 2")))))

(deftest test-clojure-symbols
  (testing "Function symbols"
    (let [code "(map inc [1 2 3])"
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "map"))
      (is (str/includes? highlighted "inc"))))

  (testing "Qualified symbols"
    (let [code "clojure.core/map"
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "clojure.core/map")))))

(deftest test-clojure-numbers
  (testing "Integer literals"
    (let [code "123 456"
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "123"))
      (is (str/includes? highlighted "456"))))

  (testing "Float literals"
    (let [code "3.14 2.718"
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "3.14"))
      (is (str/includes? highlighted "2.718")))))

;; ────────────────────── Python Syntax Tests ──────────────────────
(deftest test-python-keywords
  (testing "Python keywords"
    (let [code "def function(): return True"
          highlighted (syntax/highlight code :python)]
      (is (str/includes? highlighted "def"))
      (is (str/includes? highlighted "return"))
      (is (str/includes? highlighted "True"))))

  (testing "Python control flow"
    (let [code "if x > 0:\n    print('positive')\nelse:\n    print('negative')"
          highlighted (syntax/highlight code :python)]
      (is (str/includes? highlighted "if"))
      (is (str/includes? highlighted "else")))))

(deftest test-python-strings
  (testing "Single-quoted strings"
    (let [code "'hello world'"
          highlighted (syntax/highlight code :python)]
      (is (str/includes? highlighted "hello world"))))

  (testing "Double-quoted strings"
    (let [code "\"hello world\""
          highlighted (syntax/highlight code :python)]
      (is (str/includes? highlighted "hello world"))))

  (testing "F-strings"
    (let [code "f'Hello {name}!'"
          highlighted (syntax/highlight code :python)]
      (is (str/includes? highlighted "Hello"))
      (is (str/includes? highlighted "name"))))

  (testing "Triple-quoted strings"
    (let [code "\"\"\"multiline\nstring\"\"\""
          highlighted (syntax/highlight code :python)]
      (is (str/includes? highlighted "multiline"))
      (is (str/includes? highlighted "string")))))

(deftest test-python-decorators
  (testing "Simple decorator"
    (let [code "@decorator\ndef function():\n    pass"
          highlighted (syntax/highlight code :python)]
      (is (str/includes? highlighted "@decorator"))
      ;; Decorator should be specially highlighted
      (is (str/includes? highlighted "\u001B["))))

  (testing "Decorator with arguments"
    (let [code "@app.route('/home')\ndef home():\n    pass"
          highlighted (syntax/highlight code :python)]
      (is (str/includes? highlighted "@app.route")))))

(deftest test-python-comments
  (testing "Python comments"
    (let [code "# this is a comment\nx = 1"
          highlighted (syntax/highlight code :python)]
      (is (str/includes? highlighted "this is a comment")))))

;; ────────────────────── JavaScript Syntax Tests ──────────────────────
(deftest test-javascript-keywords
  (testing "JavaScript keywords"
    (let [code "const x = 10; let y = 20; var z = 30;"
          highlighted (syntax/highlight code :javascript)]
      (is (str/includes? highlighted "const"))
      (is (str/includes? highlighted "let"))
      (is (str/includes? highlighted "var"))))

  (testing "JavaScript control flow"
    (let [code "if (x > 0) { return true; } else { return false; }"
          highlighted (syntax/highlight code :javascript)]
      (is (str/includes? highlighted "if"))
      (is (str/includes? highlighted "return"))
      (is (str/includes? highlighted "else")))))

(deftest test-javascript-strings
  (testing "Single-quoted strings"
    (let [code "'hello world'"
          highlighted (syntax/highlight code :javascript)]
      (is (str/includes? highlighted "hello world"))))

  (testing "Double-quoted strings"
    (let [code "\"hello world\""
          highlighted (syntax/highlight code :javascript)]
      (is (str/includes? highlighted "hello world"))))

  (testing "Template literals"
    (let [code "`Hello ${name}!`"
          highlighted (syntax/highlight code :javascript)]
      (is (str/includes? highlighted "Hello"))
      ;; Template literals should be specially highlighted
      (is (str/includes? highlighted "\u001B[")))))

(deftest test-javascript-arrow-functions
  (testing "Arrow function syntax"
    (let [code "const add = (a, b) => a + b;"
          highlighted (syntax/highlight code :javascript)]
      (is (str/includes? highlighted "=>"))
      (is (str/includes? highlighted "const"))))

  (testing "Arrow function with block"
    (let [code "const fn = () => { return 42; };"
          highlighted (syntax/highlight code :javascript)]
      (is (str/includes? highlighted "=>"))
      (is (str/includes? highlighted "return")))))

(deftest test-javascript-comments
  (testing "Single-line comments"
    (let [code "// this is a comment\nconst x = 1;"
          highlighted (syntax/highlight code :javascript)]
      (is (str/includes? highlighted "this is a comment"))))

  (testing "Multi-line comments"
    (let [code "/* multi\nline\ncomment */\nconst x = 1;"
          highlighted (syntax/highlight code :javascript)]
      (is (str/includes? highlighted "multi"))
      (is (str/includes? highlighted "line")))))

;; ────────────────────── Theme Tests ──────────────────────
(deftest test-themes
  (testing "Default theme"
    (let [code "(def x 1)"
          highlighted (syntax/highlight code :clojure)]
      (is (string? highlighted))
      (is (str/includes? highlighted "\u001B["))))

  (testing "Set custom theme"
    (let [custom-theme {:keyword :red
                       :string :green
                       :comment :yellow}
          code "(def x \"hello\")"
          highlighted (syntax/highlight code :clojure :theme custom-theme)]
      (is (string? highlighted))))

  (testing "Multiple themes available"
    (let [themes (syntax/available-themes)]
      (is (coll? themes))
      (is (pos? (count themes)))
      ;; Should include at least default
      (is (some #(= :default %) themes))))

  (testing "Get theme by name"
    (let [default-theme (syntax/get-theme :default)]
      (is (map? default-theme))
      (is (:keyword default-theme))
      (is (:string default-theme))
      (is (:comment default-theme)))))

;; ────────────────────── Token-Based Highlighting Tests ──────────────────────
(deftest test-tokenization
  (testing "Tokenize Clojure code"
    (let [code "(def x 1)"
          tokens (syntax/tokenize code :clojure)]
      (is (coll? tokens))
      (is (pos? (count tokens)))
      ;; Each token should have type and value
      (is (every? #(and (:type %) (:value %)) tokens))))

  (testing "Token types are correct"
    (let [code "(def x 1)"
          tokens (syntax/tokenize code :clojure)
          types (map :type tokens)]
      ;; Should have various token types
      (is (some #(= :keyword %) types))))

  (testing "Token values are preserved"
    (let [code "(def x 1)"
          tokens (syntax/tokenize code :clojure)
          reconstructed (apply str (map :value tokens))]
      ;; Reconstructing from tokens should give original code
      (is (= code reconstructed)))))

;; ────────────────────── Edge Cases ──────────────────────
(deftest test-edge-cases
  (testing "Empty string"
    (let [highlighted (syntax/highlight "" :clojure)]
      (is (= "" highlighted))))

  (testing "Only whitespace"
    (let [code "   \n\n   "
          highlighted (syntax/highlight code :clojure)]
      (is (string? highlighted))))

  (testing "Nested strings with escapes"
    (let [code "\"outer \\\"inner\\\" outer\""
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "outer"))
      (is (str/includes? highlighted "inner"))))

  (testing "Unknown language defaults gracefully"
    (let [code "some code"
          highlighted (syntax/highlight code :unknown-lang)]
      (is (string? highlighted))
      ;; Should return original or lightly processed
      (is (str/includes? highlighted "some code"))))

  (testing "Very long code"
    (let [code (apply str (repeat 1000 "(def x 1) "))
          highlighted (syntax/highlight code :clojure)]
      (is (string? highlighted))
      (is (pos? (count highlighted)))))

  (testing "Unicode characters"
    (let [code "(def λ \"λ calculus\")"
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "λ")))))

;; ────────────────────── Language Detection ──────────────────────
(deftest test-language-detection
  (testing "Detect language from extension"
    (is (= :clojure (syntax/detect-language "file.clj")))
    (is (= :clojure (syntax/detect-language "file.cljs")))
    (is (= :python (syntax/detect-language "file.py")))
    (is (= :javascript (syntax/detect-language "file.js"))))

  (testing "Detect language from content"
    (is (= :clojure (syntax/detect-language-from-content "(defn foo [])")))
    (is (= :python (syntax/detect-language-from-content "def function():")))
    (is (= :javascript (syntax/detect-language-from-content "const x = () => {}")))))

;; ────────────────────── Integration Tests ──────────────────────
(deftest test-real-world-code
  (testing "Real Clojure function"
    (let [code "(defn factorial [n]\n  (if (<= n 1)\n    1\n    (* n (factorial (dec n)))))"
          highlighted (syntax/highlight code :clojure)]
      (is (str/includes? highlighted "defn"))
      (is (str/includes? highlighted "factorial"))
      (is (str/includes? highlighted "if"))))

  (testing "Real Python function"
    (let [code "def factorial(n):\n    if n <= 1:\n        return 1\n    return n * factorial(n - 1)"
          highlighted (syntax/highlight code :python)]
      (is (str/includes? highlighted "def"))
      (is (str/includes? highlighted "factorial"))
      (is (str/includes? highlighted "return"))))

  (testing "Real JavaScript function"
    (let [code "const factorial = (n) => {\n  if (n <= 1) return 1;\n  return n * factorial(n - 1);\n};"
          highlighted (syntax/highlight code :javascript)]
      (is (str/includes? highlighted "const"))
      (is (str/includes? highlighted "factorial"))
      (is (str/includes? highlighted "=>")))))

;; Run all tests
(defn run-tests []
  (clojure.test/run-tests 'limner.syntax-test))
