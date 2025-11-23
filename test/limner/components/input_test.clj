(ns limner.components.input-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.components.input :as input]
            [clojure.string :as str]))

;; ────────────────────── Input Creation Tests ──────────────────────
(deftest test-input-creation
  (testing "Basic input creation"
    (let [i (input/input)]
      (is (= "" (:value i)))
      (is (= 0 (:cursor i)))
      (is (= 40 (:width i)))
      (is (false? (:multiline i)))
      (is (false? (:masked i)))))

  (testing "Input with initial value"
    (let [i (input/input :value "Hello")]
      (is (= "Hello" (:value i)))
      (is (= 5 (:cursor i)))))

  (testing "Input with all options"
    (let [i (input/input
             :value "test"
             :cursor 2
             :width 50
             :height 5
             :multiline true
             :placeholder "Enter text"
             :masked true
             :mask-char "•"
             :max-length 100
             :label "Password")]
      (is (= "test" (:value i)))
      (is (= 2 (:cursor i)))
      (is (= 50 (:width i)))
      (is (= 5 (:height i)))
      (is (true? (:multiline i)))
      (is (= "Enter text" (:placeholder i)))
      (is (true? (:masked i)))
      (is (= "•" (:mask-char i)))
      (is (= 100 (:max-length i)))
      (is (= "Password" (:label i))))))

;; ────────────────────── Text Manipulation Tests ──────────────────────
(deftest test-insert-char
  (testing "Insert character at cursor"
    (let [i (input/input :value "Hllo" :cursor 1)
          updated (input/insert-char i "e")]
      (is (= "Hello" (:value updated)))
      (is (= 2 (:cursor updated)))))

  (testing "Insert at start"
    (let [i (input/input :value "ello" :cursor 0)
          updated (input/insert-char i "H")]
      (is (= "Hello" (:value updated)))
      (is (= 1 (:cursor updated)))))

  (testing "Insert at end"
    (let [i (input/input :value "Hell")
          updated (input/insert-char i "o")]
      (is (= "Hello" (:value updated)))
      (is (= 5 (:cursor updated)))))

  (testing "Respect max-length"
    (let [i (input/input :value "ABC" :max-length 3)
          updated (input/insert-char i "D")]
      (is (= "ABC" (:value updated)))
      (is (= 3 (:cursor updated))))))

(deftest test-delete-char
  (testing "Delete character at cursor"
    (let [i (input/input :value "Hello" :cursor 1)
          updated (input/delete-char i)]
      (is (= "Hllo" (:value updated)))
      (is (= 1 (:cursor updated)))))

  (testing "Delete at end does nothing"
    (let [i (input/input :value "Hello" :cursor 5)
          updated (input/delete-char i)]
      (is (= "Hello" (:value updated))))))

(deftest test-backspace
  (testing "Backspace deletes character before cursor"
    (let [i (input/input :value "Hello" :cursor 2)
          updated (input/backspace i)]
      (is (= "Hllo" (:value updated)))
      (is (= 1 (:cursor updated)))))

  (testing "Backspace at start does nothing"
    (let [i (input/input :value "Hello" :cursor 0)
          updated (input/backspace i)]
      (is (= "Hello" (:value updated)))
      (is (= 0 (:cursor updated))))))

(deftest test-clear
  (testing "Clear input value"
    (let [i (input/input :value "Hello World" :cursor 5)
          cleared (input/clear i)]
      (is (= "" (:value cleared)))
      (is (= 0 (:cursor cleared))))))

;; ────────────────────── Cursor Movement Tests ──────────────────────
(deftest test-move-cursor
  (testing "Move cursor to valid position"
    (let [i (input/input :value "Hello")
          moved (input/move-cursor i 3)]
      (is (= 3 (:cursor moved)))))

  (testing "Clamp cursor to valid range (too low)"
    (let [i (input/input :value "Hello")
          moved (input/move-cursor i -5)]
      (is (= 0 (:cursor moved)))))

  (testing "Clamp cursor to valid range (too high)"
    (let [i (input/input :value "Hello")
          moved (input/move-cursor i 100)]
      (is (= 5 (:cursor moved))))))

(deftest test-move-left-right
  (testing "Move cursor left"
    (let [i (input/input :value "Hello" :cursor 3)
          moved (input/move-left i 1)]
      (is (= 2 (:cursor moved)))))

  (testing "Move cursor right"
    (let [i (input/input :value "Hello" :cursor 2)
          moved (input/move-right i 2)]
      (is (= 4 (:cursor moved)))))

  (testing "Move left beyond start"
    (let [i (input/input :value "Hello" :cursor 2)
          moved (input/move-left i 10)]
      (is (= 0 (:cursor moved)))))

  (testing "Move right beyond end"
    (let [i (input/input :value "Hello" :cursor 3)
          moved (input/move-right i 10)]
      (is (= 5 (:cursor moved))))))

(deftest test-move-home-end
  (testing "Move to home"
    (let [i (input/input :value "Hello World" :cursor 7)
          moved (input/move-home i)]
      (is (= 0 (:cursor moved)))))

  (testing "Move to end"
    (let [i (input/input :value "Hello World" :cursor 5)
          moved (input/move-end i)]
      (is (= 11 (:cursor moved))))))

;; ────────────────────── History Navigation Tests ──────────────────────
(deftest test-add-to-history
  (testing "Add value to history"
    (let [i (input/input :value "command1")
          updated (input/add-to-history i)]
      (is (= ["command1"] (:history updated)))
      (is (nil? (:history-index updated)))))

  (testing "Don't add empty values"
    (let [i (input/input :value "")
          updated (input/add-to-history i)]
      (is (= [] (:history updated)))))

  (testing "Don't add duplicate of last entry"
    (let [i (input/input :value "command1" :history ["command1"])
          updated (input/add-to-history i)]
      (is (= ["command1"] (:history updated))))))

(deftest test-history-navigation
  (testing "Navigate to previous history entry"
    (let [i (input/input :value "" :history ["cmd1" "cmd2" "cmd3"])
          prev (input/history-prev i)]
      (is (= "cmd3" (:value prev)))
      (is (= 2 (:history-index prev)))))

  (testing "Navigate through multiple previous entries"
    (let [i (input/input :value "" :history ["cmd1" "cmd2" "cmd3"])
          prev1 (input/history-prev i)
          prev2 (input/history-prev prev1)]
      (is (= "cmd2" (:value prev2)))
      (is (= 1 (:history-index prev2)))))

  (testing "Navigate to next history entry"
    (let [i (input/input :value "cmd1" :history ["cmd1" "cmd2" "cmd3"] :history-index 0)
          next (input/history-next i)]
      (is (= "cmd2" (:value next)))
      (is (= 1 (:history-index next)))))

  (testing "Next at end of history clears value"
    (let [i (input/input :value "cmd3" :history ["cmd1" "cmd2" "cmd3"] :history-index 2)
          next (input/history-next i)]
      (is (= "" (:value next)))
      (is (nil? (:history-index next)))))

  (testing "Next with no history index does nothing"
    (let [i (input/input :value "test" :history ["cmd1"])
          next (input/history-next i)]
      (is (= "test" (:value next))))))

;; ────────────────────── Validation Tests ──────────────────────
(deftest test-validation
  (testing "Valid input with validator"
    (let [i (input/input :value "hello" :validator #(> (count %) 3))
          validated (input/validate i)]
      (is (false? (:invalid validated)))
      (is (true? (input/valid? validated)))))

  (testing "Invalid input with validator"
    (let [i (input/input :value "hi" :validator #(> (count %) 3))
          validated (input/validate i)]
      (is (true? (:invalid validated)))
      (is (false? (input/valid? validated)))))

  (testing "No validator always valid"
    (let [i (input/input :value "anything")
          validated (input/validate i)]
      (is (false? (:invalid validated)))
      (is (true? (input/valid? validated))))))

;; ────────────────────── Display Formatting Tests ──────────────────────
(deftest test-format-display-value
  (testing "Normal value display"
    (let [i (input/input :value "Hello")
          display (input/format-display-value i)]
      (is (= "Hello" display))))

  (testing "Masked value display"
    (let [i (input/input :value "secret" :masked true)
          display (input/format-display-value i)]
      (is (= "******" display))))

  (testing "Custom mask character"
    (let [i (input/input :value "pass" :masked true :mask-char "•")
          display (input/format-display-value i)]
      (is (= "••••" display))))

  (testing "Placeholder when empty"
    (let [i (input/input :value "" :placeholder "Enter text")
          display (input/format-display-value i)]
      (is (str/includes? display "Enter text")))))

;; ────────────────────── Rendering Tests ──────────────────────
(deftest test-basic-rendering
  (testing "Render single-line input"
    (let [i (input/input :value "Hello" :border false)
          rendered (input/render i)]
      (is (vector? rendered))
      (is (> (count rendered) 0))))

  (testing "Render with border"
    (let [i (input/input :value "Test" :border true)
          rendered (input/render i)]
      (is (> (count rendered) 2))))

  (testing "Render with label"
    (let [i (input/input :value "Test" :label "Username" :border true)
          rendered (input/render i)
          output (str/join rendered)]
      (is (str/includes? output "Username"))))

  (testing "Render multiline input"
    (let [i (input/input :value "Line 1\nLine 2" :multiline true :height 3)
          rendered (input/render i)]
      (is (vector? rendered))
      (is (>= (count rendered) 3)))))

;; ────────────────────── Helper Function Tests ──────────────────────
(deftest test-helper-functions
  (testing "input? predicate"
    (let [i (input/input :value "test")]
      (is (true? (input/input? i)))
      (is (false? (input/input? "not an input")))
      (is (false? (input/input? nil)))))

  (testing "empty? predicate"
    (let [empty-input (input/input :value "")
          non-empty (input/input :value "text")]
      (is (true? (input/empty? empty-input)))
      (is (false? (input/empty? non-empty)))))

  (testing "disabled? predicate"
    (let [disabled (input/input :disabled true)
          enabled (input/input :disabled false)]
      (is (true? (input/disabled? disabled)))
      (is (false? (input/disabled? enabled)))))

  (testing "multiline? predicate"
    (let [multiline (input/input :multiline true)
          single-line (input/input :multiline false)]
      (is (true? (input/multiline? multiline)))
      (is (false? (input/multiline? single-line)))))

  (testing "at-start? predicate"
    (let [at-start (input/input :value "Hello" :cursor 0)
          not-at-start (input/input :value "Hello" :cursor 3)]
      (is (true? (input/at-start? at-start)))
      (is (false? (input/at-start? not-at-start)))))

  (testing "at-end? predicate"
    (let [at-end (input/input :value "Hello")
          not-at-end (input/input :value "Hello" :cursor 2)]
      (is (true? (input/at-end? at-end)))
      (is (false? (input/at-end? not-at-end))))))

;; ────────────────────── Convenience Function Tests ──────────────────────
(deftest test-convenience-functions
  (testing "set-value updates value and cursor"
    (let [i (input/input :value "old" :cursor 1)
          updated (input/set-value i "new value")]
      (is (= "new value" (:value updated)))
      (is (= 9 (:cursor updated)))))

  (testing "toggle-disabled changes disabled state"
    (let [i (input/input :disabled false)
          toggled (input/toggle-disabled i)]
      (is (true? (:disabled toggled)))
      (is (false? (:disabled (input/toggle-disabled toggled))))))

  (testing "focus moves cursor to end"
    (let [i (input/input :value "Hello" :cursor 2)
          focused (input/focus i)]
      (is (= 5 (:cursor focused))))))

;; ────────────────────── Integration Tests ──────────────────────
(deftest test-text-editing-workflow
  (testing "Complete text editing workflow"
    (let [i (input/input)
          ;; Type "Hello"
          i1 (-> i
                 (input/insert-char "H")
                 (input/insert-char "e")
                 (input/insert-char "l")
                 (input/insert-char "l")
                 (input/insert-char "o"))]
      (is (= "Hello" (:value i1)))
      (is (= 5 (:cursor i1)))

      ;; Move to start and insert "Oh "
      (let [i2 (-> i1
                   input/move-home
                   (input/insert-char "O")
                   (input/insert-char "h")
                   (input/insert-char " "))]
        (is (= "Oh Hello" (:value i2)))
        (is (= 3 (:cursor i2)))

        ;; Move to end and delete last character
        (let [i3 (-> i2
                     input/move-end
                     input/backspace)]
          (is (= "Oh Hell" (:value i3)))
          (is (= 7 (:cursor i3))))))))

(deftest test-history-workflow
  (testing "Complete history workflow"
    (let [i (input/input)
          ;; Enter first command
          i1 (-> i
                 (input/set-value "first command")
                 input/add-to-history
                 input/clear)
          ;; Enter second command
          i2 (-> i1
                 (input/set-value "second command")
                 input/add-to-history
                 input/clear)]

      (is (= ["first command" "second command"] (:history i2)))

      ;; Navigate back through history
      (let [i3 (input/history-prev i2)]
        (is (= "second command" (:value i3)))

        (let [i4 (input/history-prev i3)]
          (is (= "first command" (:value i4)))

          ;; Navigate forward
          (let [i5 (input/history-next i4)]
            (is (= "second command" (:value i5)))))))))

;; Run all tests
(defn run-tests []
  (clojure.test/run-tests 'limner.components.input-test))
