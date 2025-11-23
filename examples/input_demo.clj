#!/usr/bin/env bb
;; Input Component Demo
;; Demonstrates all input features: single-line, multi-line, validation, masking, history

(ns input-demo
  (:require [limner.components.input :as input]
            [limner.core :as core]
            [clojure.string :as str]))

(defn print-input [i & [description]]
  (when description
    (println (core/color :cyan (str "\n" description))))
  (println (input/render-to-string i))
  (println))

;; ──────────────── Demo 1: Basic Single-Line Inputs ────────────────
(defn demo-basic-inputs []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 1: Basic Single-Line Inputs"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [empty (input/input :label "Empty Input" :width 30)]
    (print-input empty "Empty input with label:"))

  (let [with-value (input/input
                    :label "With Value"
                    :value "Hello World"
                    :width 30)]
    (print-input with-value "Input with initial value:"))

  (let [with-placeholder (input/input
                          :label "Username"
                          :placeholder "Enter your username"
                          :width 30)]
    (print-input with-placeholder "Input with placeholder text:"))

  (let [no-border (input/input
                   :value "No border input"
                   :border false
                   :width 30)]
    (print-input no-border "Input without border:")))

;; ──────────────── Demo 2: Cursor Positioning ────────────────
(defn demo-cursor-positioning []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 2: Cursor Positioning"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [i (input/input :value "Hello World" :label "Original" :width 30)]
    (print-input i "Cursor at end (default):"))

  (let [i (input/input :value "Hello World" :cursor 0 :label "At Start" :width 30)]
    (print-input i "Cursor at start:"))

  (let [i (input/input :value "Hello World" :cursor 5 :label "In Middle" :width 30)]
    (print-input i "Cursor in middle:"))

  (let [i (input/input :value "Hello World" :label "After Move" :width 30)
        moved (input/move-cursor i 7)]
    (print-input moved "After moving cursor to position 7:")))

;; ──────────────── Demo 3: Text Editing Operations ────────────────
(defn demo-text-editing []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 3: Text Editing Operations"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [i (input/input :value "Hello" :label "Original" :width 30)]
    (print-input i "Original input:")

    ;; Insert character
    (let [inserted (input/insert-char i "!")]
      (print-input inserted "After inserting '!' at end:"))

    ;; Insert in middle
    (let [moved (input/move-cursor i 2)
          inserted (input/insert-char moved "X")]
      (print-input inserted "After moving to position 2 and inserting 'X':"))

    ;; Backspace
    (let [moved (input/move-cursor i 3)
          after-backspace (input/backspace moved)]
      (print-input after-backspace "After moving to position 3 and backspace:"))

    ;; Delete
    (let [moved (input/move-cursor i 2)
          after-delete (input/delete-char moved)]
      (print-input after-delete "After moving to position 2 and delete:"))

    ;; Clear
    (let [cleared (input/clear i)]
      (print-input cleared "After clearing input:"))))

;; ──────────────── Demo 4: Cursor Movement Keys ────────────────
(defn demo-cursor-movement []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 4: Cursor Movement (Arrow Keys)"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [i (input/input :value "Hello World" :cursor 5 :label "Original" :width 30)]
    (print-input i "Original (cursor at position 5):")

    (let [left (input/move-left i 2)]
      (print-input left "After moving left 2 positions:"))

    (let [right (input/move-right i 3)]
      (print-input right "After moving right 3 positions:"))

    (let [home (input/move-home i)]
      (print-input home "After Home key (move to start):"))

    (let [end (input/move-end i)]
      (print-input end "After End key (move to end):"))))

;; ──────────────── Demo 5: Password/Masked Input ────────────────
(defn demo-masked-input []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 5: Password/Masked Input"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [password (input/input
                  :label "Password"
                  :value "secret123"
                  :masked true
                  :width 30)]
    (print-input password "Masked input (default * character):"))

  (let [custom-mask (input/input
                     :label "PIN"
                     :value "1234"
                     :masked true
                     :mask-char "•"
                     :width 30)]
    (print-input custom-mask "Masked with custom character (•):")))

;; ──────────────── Demo 6: Input Validation ────────────────
(defn demo-validation []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 6: Input Validation"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [min-length-validator #(>= (count %) 5)

        valid-input (input/input
                     :label "Username (min 5 chars)"
                     :value "johndoe"
                     :validator min-length-validator
                     :width 30)
        validated-valid (input/validate valid-input)]
    (print-input validated-valid "Valid input (7 characters):"))

  (let [min-length-validator #(>= (count %) 5)

        invalid-input (input/input
                       :label "Username (min 5 chars)"
                       :value "joe"
                       :validator min-length-validator
                       :width 30)
        validated-invalid (input/validate invalid-input)]
    (print-input validated-invalid "Invalid input (3 characters, shows ✗):"))

  (let [email-validator #(str/includes? % "@")

        email-valid (input/input
                     :label "Email"
                     :value "user@example.com"
                     :validator email-validator
                     :width 30)
        validated-email (input/validate email-valid)]
    (print-input validated-email "Valid email:"))

  (let [email-validator #(str/includes? % "@")

        email-invalid (input/input
                       :label "Email"
                       :value "notanemail"
                       :validator email-validator
                       :width 30)
        validated-email (input/validate email-invalid)]
    (print-input validated-email "Invalid email (shows ✗):")))

;; ──────────────── Demo 7: Command History ────────────────
(defn demo-history []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 7: Command History"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (println (core/color :yellow "\nBuilding history:"))
  (let [i (input/input :label "Command" :width 30)

        ;; Add first command
        i1 (-> i
               (input/set-value "git status")
               input/add-to-history
               input/clear)
        _ (println "  Added: git status")

        ;; Add second command
        i2 (-> i1
               (input/set-value "git add .")
               input/add-to-history
               input/clear)
        _ (println "  Added: git add .")

        ;; Add third command
        i3 (-> i2
               (input/set-value "git commit -m \"update\"")
               input/add-to-history
               input/clear)]
    (println "  Added: git commit -m \"update\"")

    (print-input i3 "\nCurrent state (empty):")

    ;; Navigate back through history
    (let [prev1 (input/history-prev i3)]
      (print-input prev1 "After pressing Up (↑) once:"))

    (let [prev1 (input/history-prev i3)
          prev2 (input/history-prev prev1)]
      (print-input prev2 "After pressing Up (↑) twice:"))

    (let [prev1 (input/history-prev i3)
          prev2 (input/history-prev prev1)
          prev3 (input/history-prev prev2)]
      (print-input prev3 "After pressing Up (↑) three times:"))

    ;; Navigate forward
    (let [prev1 (input/history-prev i3)
          prev2 (input/history-prev prev1)
          next1 (input/history-next prev2)]
      (print-input next1 "After pressing Down (↓) once:"))))

;; ──────────────── Demo 8: Multi-line Text Area ────────────────
(defn demo-multiline []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 8: Multi-line Text Area"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [text-area (input/input
                   :label "Description"
                   :value "Line 1\nLine 2\nLine 3"
                   :multiline true
                   :width 40
                   :height 5)]
    (print-input text-area "Multi-line text area:"))

  (let [long-text (str/join "\n" ["First line"
                                   "Second line"
                                   "Third line"
                                   "Fourth line"
                                   "Fifth line"])
        text-area (input/input
                   :label "Notes"
                   :value long-text
                   :multiline true
                   :width 40
                   :height 5)]
    (print-input text-area "Multi-line with exactly height lines:")))

;; ──────────────── Demo 9: Disabled Input ────────────────
(defn demo-disabled []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 9: Disabled Input"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [enabled (input/input
                 :label "Active"
                 :value "You can edit this"
                 :width 30)]
    (print-input enabled "Enabled input:"))

  (let [disabled (input/input
                  :label "Read-only"
                  :value "You cannot edit this"
                  :disabled true
                  :width 30)]
    (print-input disabled "Disabled input (shows ⊘):"))

  (let [i (input/input :value "Toggle me" :label "State" :width 30)
        toggled (input/toggle-disabled i)]
    (print-input i "Original (enabled):")
    (print-input toggled "After toggling (disabled):")))

;; ──────────────── Demo 10: Max Length ────────────────
(defn demo-max-length []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 10: Maximum Length"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [limited (input/input
                 :label "Tweet (max 10)"
                 :value "Short"
                 :max-length 10
                 :width 30)]
    (print-input limited "Input with max-length of 10:"))

  (let [limited (input/input
                 :label "Code (max 10)"
                 :value "0123456789"
                 :max-length 10
                 :width 30)]
    (print-input limited "At maximum length (10 chars):"))

  (println (core/color :yellow "  Note: Cannot insert more characters when at max-length")))

;; ──────────────── Demo 11: Different Border Styles ────────────────
(defn demo-border-styles []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 11: Different Border Styles"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [single (input/input
                :label "Single"
                :value "Single border"
                :border-style :single
                :width 30)]
    (print-input single "Single-line border:"))

  (let [double (input/input
                :label "Double"
                :value "Double border"
                :border-style :double
                :width 30)]
    (print-input double "Double-line border:"))

  (let [rounded (input/input
                 :label "Rounded"
                 :value "Rounded border"
                 :border-style :rounded
                 :width 30)]
    (print-input rounded "Rounded border:"))

  (let [thick (input/input
               :label "Thick"
               :value "Thick border"
               :border-style :thick
               :width 30)]
    (print-input thick "Thick border:")))

;; ──────────────── Demo 12: Complex Form Example ────────────────
(defn demo-complex-form []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 12: Complex Form Example"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [username (input/input
                  :label "Username"
                  :value "johndoe"
                  :width 30
                  :validator #(>= (count %) 3))
        validated-user (input/validate username)

        email (input/input
               :label "Email"
               :value "john@example.com"
               :width 30
               :validator #(str/includes? % "@"))
        validated-email (input/validate email)

        password (input/input
                  :label "Password"
                  :value "secret123"
                  :masked true
                  :width 30
                  :validator #(>= (count %) 8))
        validated-pass (input/validate password)

        bio (input/input
             :label "Bio"
             :value "Software developer\nLoves Clojure"
             :multiline true
             :width 35
             :height 3)]

    (println (core/color :cyan "\nUser Registration Form:"))
    (println)
    (println (input/render-to-string validated-user))
    (println)
    (println (input/render-to-string validated-email))
    (println)
    (println (input/render-to-string validated-pass))
    (println)
    (println (input/render-to-string bio))
    (println)))

;; ──────────────── Demo 13: Helper Functions ────────────────
(defn demo-helpers []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 13: Helper Functions"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [i (input/input
           :value "Hello World"
           :cursor 5
           :multiline false
           :disabled false)]

    (println (core/color :yellow "\nInput properties:"))
    (println "  input? ->" (input/input? i))
    (println "  empty? ->" (input/empty? i))
    (println "  disabled? ->" (input/disabled? i))
    (println "  multiline? ->" (input/multiline? i))
    (println "  at-start? ->" (input/at-start? i))
    (println "  at-end? ->" (input/at-end? i))
    (println "  valid? ->" (input/valid? i))

    (let [at-start (input/move-home i)]
      (println (core/color :yellow "\nAfter moving to start:"))
      (println "  at-start? ->" (input/at-start? at-start)))

    (let [at-end (input/move-end i)]
      (println (core/color :yellow "\nAfter moving to end:"))
      (println "  at-end? ->" (input/at-end? at-end)))

    (let [empty-input (input/clear i)]
      (println (core/color :yellow "\nAfter clearing:"))
      (println "  empty? ->" (input/empty? empty-input)))))

;; ──────────────── Main ────────────────
(defn -main []
  (println (core/color :bright-green "\n"))
  (println (core/color :bright-green "╔═══════════════════════════════════════════════════╗"))
  (println (core/color :bright-green "║                                                   ║"))
  (println (core/color :bright-green "║        LIMNER INPUT COMPONENT SHOWCASE            ║"))
  (println (core/color :bright-green "║                                                   ║"))
  (println (core/color :bright-green "╚═══════════════════════════════════════════════════╝"))

  (demo-basic-inputs)
  (demo-cursor-positioning)
  (demo-text-editing)
  (demo-cursor-movement)
  (demo-masked-input)
  (demo-validation)
  (demo-history)
  (demo-multiline)
  (demo-disabled)
  (demo-max-length)
  (demo-border-styles)
  (demo-complex-form)
  (demo-helpers)

  (println (core/color :bright-green "\n✓ Input component demo complete!\n"))
  (println "Features demonstrated:")
  (println "  • Single-line text input with cursor")
  (println "  • Multi-line text area")
  (println "  • Cursor positioning and movement (arrows, home, end)")
  (println "  • Text editing (insert, delete, backspace, clear)")
  (println "  • Password masking with custom characters")
  (println "  • Input validation with visual feedback")
  (println "  • Command history navigation (up/down arrows)")
  (println "  • Placeholder text")
  (println "  • Disabled state")
  (println "  • Maximum length enforcement")
  (println "  • Different border styles")
  (println "  • Labels and optional borders")
  (println "  • Helper functions for state queries")
  (println))

(when (or (System/getProperty "babashka.version")
          (= *file* (System/getProperty "babashka.file")))
  (-main))
