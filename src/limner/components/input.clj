(ns limner.components.input
  "Input component - interactive text input fields"
  (:require [clojure.string :as str]
            [limner.borders :as borders]
            [limner.core :as core]))

;; ────────────────────── Input State ──────────────────────
(defn input
  "Create an input component
   Options:
   - :value - current text value (string, default \"\")
   - :cursor - cursor position (default at end of value)
   - :multiline - enable multi-line input (default false)
   - :width - input width (default 40)
   - :height - input height for multiline (default 1)
   - :placeholder - placeholder text when empty (default \"\")
   - :masked - mask input (for passwords, default false)
   - :mask-char - character to use for masking (default \"*\")
   - :validator - validation function (value -> boolean, default nil)
   - :invalid - whether current value is invalid (default false)
   - :disabled - whether input is disabled (default false)
   - :history - command history vector (default [])
   - :history-index - current position in history (default nil)
   - :max-length - maximum input length (default nil)
   - :border - show border around input (default true)
   - :border-style - border style (default :single)
   - :label - input label (default nil)"
  [& {:keys [value cursor multiline width height placeholder masked mask-char
             validator invalid disabled history history-index max-length
             border border-style label]
      :or {value ""
           multiline false
           width 40
           height 1
           placeholder ""
           masked false
           mask-char "*"
           invalid false
           disabled false
           history []
           max-length nil
           border true
           border-style :single}}]
  (let [cursor-pos (if (nil? cursor) (count value) cursor)]
    {:value value
     :cursor cursor-pos
     :multiline multiline
     :width width
     :height height
     :placeholder placeholder
     :masked masked
     :mask-char mask-char
     :validator validator
     :invalid invalid
     :disabled disabled
     :history (vec history)
     :history-index history-index
     :max-length max-length
     :border border
     :border-style border-style
     :label label}))

;; ────────────────────── Text Manipulation ──────────────────────
(defn insert-char
  "Insert character at cursor position"
  [input-state ch]
  (let [{:keys [value cursor max-length]} input-state]
    (if (and max-length (>= (count value) max-length))
      input-state
      (let [before (subs value 0 cursor)
            after (subs value cursor)
            new-value (str before ch after)]
        (assoc input-state
               :value new-value
               :cursor (inc cursor))))))

(defn delete-char
  "Delete character at cursor position (Delete key)"
  [input-state]
  (let [{:keys [value cursor]} input-state]
    (if (>= cursor (count value))
      input-state
      (let [before (subs value 0 cursor)
            after (subs value (inc cursor))
            new-value (str before after)]
        (assoc input-state :value new-value)))))

(defn backspace
  "Delete character before cursor (Backspace key)"
  [input-state]
  (let [{:keys [value cursor]} input-state]
    (if (<= cursor 0)
      input-state
      (let [before (subs value 0 (dec cursor))
            after (subs value cursor)
            new-value (str before after)]
        (assoc input-state
               :value new-value
               :cursor (dec cursor))))))

(defn clear
  "Clear input value"
  [input-state]
  (assoc input-state
         :value ""
         :cursor 0))

;; ────────────────────── Cursor Movement ──────────────────────
(defn move-cursor
  "Move cursor to position (clamped to valid range)"
  [input-state pos]
  (let [{:keys [value]} input-state
        clamped (max 0 (min pos (count value)))]
    (assoc input-state :cursor clamped)))

(defn move-left
  "Move cursor left by n positions"
  [input-state n]
  (move-cursor input-state (- (:cursor input-state) n)))

(defn move-right
  "Move cursor right by n positions"
  [input-state n]
  (move-cursor input-state (+ (:cursor input-state) n)))

(defn move-home
  "Move cursor to start of line"
  [input-state]
  (assoc input-state :cursor 0))

(defn move-end
  "Move cursor to end of line"
  [input-state]
  (assoc input-state :cursor (count (:value input-state))))

;; ────────────────────── History Navigation ──────────────────────
(defn add-to-history
  "Add current value to history"
  [input-state]
  (let [{:keys [value history]} input-state]
    (if (and (not (str/blank? value))
             (not= value (last history)))
      (assoc input-state
             :history (conj history value)
             :history-index nil)
      input-state)))

(defn history-prev
  "Navigate to previous history entry (up arrow)"
  [input-state]
  (let [{:keys [history history-index]} input-state]
    (when-not (empty? history)
      (let [current-idx (or history-index (count history))
            new-idx (max 0 (dec current-idx))]
        (if (< new-idx (count history))
          (let [historic-value (get history new-idx)]
            (assoc input-state
                   :value historic-value
                   :cursor (count historic-value)
                   :history-index new-idx))
          input-state)))))

(defn history-next
  "Navigate to next history entry (down arrow)"
  [input-state]
  (let [{:keys [history history-index]} input-state]
    (if (nil? history-index)
      input-state
      (let [new-idx (inc history-index)]
        (if (>= new-idx (count history))
          (assoc input-state
                 :value ""
                 :cursor 0
                 :history-index nil)
          (let [historic-value (get history new-idx)]
            (assoc input-state
                   :value historic-value
                   :cursor (count historic-value)
                   :history-index new-idx)))))))

;; ────────────────────── Validation ──────────────────────
(defn validate
  "Validate current value using validator function"
  [input-state]
  (let [{:keys [validator value]} input-state]
    (if validator
      (assoc input-state :invalid (not (validator value)))
      input-state)))

(defn valid?
  "Check if current value is valid"
  [input-state]
  (not (:invalid input-state)))

;; ────────────────────── Display Formatting ──────────────────────
(defn format-display-value
  "Format value for display (handle masking)"
  [{:keys [value masked mask-char placeholder]}]
  (cond
    (and (str/blank? value) (not (str/blank? placeholder)))
    (core/color :cyan placeholder)

    masked
    (apply str (repeat (count value) mask-char))

    :else
    value))

(defn format-line-with-cursor
  "Format a line with cursor indicator"
  [text cursor-pos width show-cursor?]
  (let [visible-start (max 0 (- cursor-pos (- width 5)))
        visible-end (min (count text) (+ visible-start width))
        visible-text (subs text visible-start visible-end)
        cursor-in-view (- cursor-pos visible-start)

        ;; Pad to width
        padded (if (< (core/visible-length visible-text) width)
                 (str visible-text (apply str (repeat (- width (core/visible-length visible-text)) " ")))
                 visible-text)

        ;; Add cursor if in view
        display-text (if (and show-cursor?
                            (>= cursor-in-view 0)
                            (<= cursor-in-view (count visible-text)))
                      (let [before (subs padded 0 cursor-in-view)
                            cursor-char (if (< cursor-in-view (count visible-text))
                                         (subs padded cursor-in-view (inc cursor-in-view))
                                         " ")
                            after (if (< (inc cursor-in-view) (count padded))
                                   (subs padded (inc cursor-in-view))
                                   "")]
                        (str before (core/color :reset (str "\u001B[7m" cursor-char "\u001B[27m")) after))
                      padded)]
    display-text))

;; ────────────────────── Rendering ──────────────────────
(defn render-single-line
  "Render single-line input"
  [{:keys [width disabled invalid label] :as input-state}]
  (let [display-value (format-display-value input-state)
        cursor-pos (:cursor input-state)
        show-cursor? (not disabled)
        content-line (format-line-with-cursor display-value cursor-pos (- width 2) show-cursor?)

        ;; Add status indicator
        status-char (cond
                     disabled " ⊘"
                     invalid (core/color :red " ✗")
                     :else "")

        final-line (str " " content-line status-char)]

    (if label
      [(str label ":") final-line]
      [final-line])))

(defn render-multiline
  "Render multi-line input (text area)"
  [{:keys [width height disabled invalid] :as input-state}]
  (let [value (:value input-state)
        lines (str/split-lines value)
        cursor-pos (:cursor input-state)

        ;; Calculate which line the cursor is on
        chars-before-cursor (subs value 0 cursor-pos)
        cursor-line-idx (count (str/split-lines chars-before-cursor))
        cursor-line (dec cursor-line-idx)
        cursor-col (- cursor-pos (count (str/join "\n" (take cursor-line lines))) (if (pos? cursor-line) 1 0))

        ;; Pad lines to height
        padded-lines (take height (concat lines (repeat "")))

        ;; Format each line with cursor if applicable
        formatted-lines (map-indexed
                         (fn [idx line]
                           (if (and (= idx cursor-line) (not disabled))
                             (str " " (format-line-with-cursor line cursor-col (- width 4) true) " ")
                             (str " " line (apply str (repeat (- width (count line) 4) " ")) " ")))
                         padded-lines)

        ;; Add status indicator to last line
        status (cond
                disabled (core/color :cyan " [DISABLED]")
                invalid (core/color :red " [INVALID]")
                :else "")]

    (vec (concat formatted-lines [status]))))

(defn render
  "Render input component to vector of strings"
  [{:keys [multiline border border-style label] :as input-state}]
  (let [content-lines (if multiline
                       (render-multiline input-state)
                       (render-single-line input-state))

        ;; Add border if requested
        final-lines (if border
                     (if label
                       (borders/draw-titled-box label content-lines
                                               :border-style border-style
                                               :title-pos :left)
                       (borders/draw-box content-lines :border-style border-style))
                     content-lines)]

    (vec final-lines)))

(defn render-to-string
  "Render input to a single string with newlines"
  [input-state]
  (str/join "\n" (render input-state)))

;; ────────────────────── Input Helpers ──────────────────────
(defn input?
  "Check if value is an input component"
  [x]
  (and (map? x)
       (contains? x :cursor)
       (contains? x :value)))

(defn empty?
  "Check if input value is empty"
  [input-state]
  (str/blank? (:value input-state)))

(defn disabled?
  "Check if input is disabled"
  [input-state]
  (:disabled input-state))

(defn multiline?
  "Check if input is multiline"
  [input-state]
  (:multiline input-state))

(defn at-start?
  "Check if cursor is at start"
  [input-state]
  (zero? (:cursor input-state)))

(defn at-end?
  "Check if cursor is at end"
  [input-state]
  (= (:cursor input-state) (count (:value input-state))))

;; ────────────────────── Convenience Functions ──────────────────────
(defn set-value
  "Set input value and move cursor to end"
  [input-state value]
  (assoc input-state
         :value value
         :cursor (count value)))

(defn toggle-disabled
  "Toggle disabled state"
  [input-state]
  (update input-state :disabled not))

(defn focus
  "Focus input (move cursor to end)"
  [input-state]
  (move-end input-state))
