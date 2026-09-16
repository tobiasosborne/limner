(ns limner.components.input
  "Editable text input state and rendering.

  The cursor is a grapheme index, not a UTF-16 offset, so moving left or right
  and inserting or deleting never splits an emoji or a combining sequence.
  Editing uses `limner.text/clusters` for boundaries and `limner.text` spans
  for rendering, so a placeholder keeps its colour while the inverse-video
  cursor is a real style attribute rather than an escape embedded in a
  substring (REVIEW F1/B1/B3).

  Input values are plain text. `:width` is the total rendered width including
  the border; every rendered line is exactly that many display columns."
  (:refer-clojure :exclude [empty?])
  (:require [clojure.string :as str]
            [limner.borders :as borders]
            [limner.core :as core]
            [limner.text :as text]))

(defn- cluster-count
  "Number of grapheme clusters in plain text."
  [s]
  (count (text/clusters s)))

(defn- grapheme-slice
  "Plain text from grapheme index `start` (inclusive) to `end` (exclusive)."
  [s start end]
  (apply str (map :cluster (subvec (text/clusters s) start end))))

;; ────────────────────── Input State ──────────────────────
(defn input
  "Create an input component.
   Options:
   - :value - current text value (string, default \"\")
   - :cursor - cursor position in grapheme clusters (default at end of value)
   - :multiline - enable multi-line input (default false)
   - :width - total rendered width (default 40)
   - :height - input height for multiline (default 1)
   - :placeholder - placeholder text when empty (default \"\")
   - :masked - mask input (for passwords, default false)
   - :mask-char - character to use for masking (default \"*\")
   - :validator - validation function (value -> boolean, default nil)
   - :invalid - whether current value is invalid (default false)
   - :disabled - whether input is disabled (default false)
   - :history - command history vector (default [])
   - :history-index - current position in history (default nil)
   - :max-length - maximum input length in grapheme clusters (default nil)
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
  (let [cursor-pos (if (nil? cursor) (cluster-count value) cursor)]
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
  "Insert `ch` (one grapheme or more) at the cursor, bounded by `:max-length`."
  [input-state ch]
  (let [{:keys [value cursor max-length]} input-state
        total (cluster-count value)
        cursor (max 0 (min cursor total))]
    (if (and max-length (>= total max-length))
      input-state
      (assoc input-state
             :value (str (grapheme-slice value 0 cursor) ch (grapheme-slice value cursor total))
             :cursor (+ cursor (cluster-count ch))))))

(defn delete-char
  "Delete the grapheme cluster at the cursor (Delete key)."
  [input-state]
  (let [{:keys [value cursor]} input-state
        total (cluster-count value)
        cursor (max 0 (min cursor total))]
    (if (>= cursor total)
      input-state
      (assoc input-state
             :value (str (grapheme-slice value 0 cursor)
                         (grapheme-slice value (inc cursor) total))))))

(defn backspace
  "Delete the grapheme cluster before the cursor (Backspace key)."
  [input-state]
  (let [{:keys [value cursor]} input-state
        total (cluster-count value)
        cursor (max 0 (min cursor total))]
    (if (<= cursor 0)
      input-state
      (assoc input-state
             :value (str (grapheme-slice value 0 (dec cursor))
                         (grapheme-slice value cursor total))
             :cursor (dec cursor)))))

(defn clear
  "Clear input value."
  [input-state]
  (assoc input-state :value "" :cursor 0))

;; ────────────────────── Cursor Movement ──────────────────────
(defn move-cursor
  "Move cursor to `pos`, clamped to the grapheme count."
  [input-state pos]
  (assoc input-state :cursor (max 0 (min pos (cluster-count (:value input-state))))))

(defn move-left
  "Move cursor left by `n` grapheme clusters."
  [input-state n]
  (move-cursor input-state (- (:cursor input-state) n)))

(defn move-right
  "Move cursor right by `n` grapheme clusters."
  [input-state n]
  (move-cursor input-state (+ (:cursor input-state) n)))

(defn move-home
  "Move cursor to the start of the line."
  [input-state]
  (assoc input-state :cursor 0))

(defn move-end
  "Move cursor past the last grapheme cluster."
  [input-state]
  (assoc input-state :cursor (cluster-count (:value input-state))))

;; ────────────────────── History Navigation ──────────────────────
(defn add-to-history
  "Add the current value to history, ignoring blanks and repeats."
  [input-state]
  (let [{:keys [value history]} input-state]
    (if (and (not (str/blank? value))
             (not= value (last history)))
      (assoc input-state :history (conj history value) :history-index nil)
      input-state)))

(defn history-prev
  "Navigate to the previous history entry; an empty history is unchanged."
  [input-state]
  (let [{:keys [history history-index]} input-state]
    (if (clojure.core/empty? history)
      input-state
      (let [current-idx (or history-index (count history))
            new-idx (max 0 (dec current-idx))]
        (if (< new-idx (count history))
          (let [historic-value (get history new-idx)]
            (assoc input-state
                   :value historic-value
                   :cursor (cluster-count historic-value)
                   :history-index new-idx))
          input-state)))))

(defn history-next
  "Navigate to the next history entry."
  [input-state]
  (let [{:keys [history history-index]} input-state]
    (if (nil? history-index)
      input-state
      (let [new-idx (inc history-index)]
        (if (>= new-idx (count history))
          (assoc input-state :value "" :cursor 0 :history-index nil)
          (let [historic-value (get history new-idx)]
            (assoc input-state
                   :value historic-value
                   :cursor (cluster-count historic-value)
                   :history-index new-idx)))))))

;; ────────────────────── Validation ──────────────────────
(defn validate
  "Validate the current value with the validator, setting `:invalid`."
  [input-state]
  (let [{:keys [validator value]} input-state]
    (if validator
      (assoc input-state :invalid (not (validator value)))
      input-state)))

(defn valid?
  "True when the input is not marked invalid."
  [input-state]
  (not (:invalid input-state)))

;; ────────────────────── Display Formatting ──────────────────────
(defn format-display-value
  "Format the value for display: a coloured placeholder or a mask.

  Returns a string, as before, so existing callers are unaffected. Rendering
  parses it back into spans through `limner.text`."
  [{:keys [value masked mask-char placeholder]}]
  (cond
    (and (str/blank? value) (not (str/blank? placeholder)))
    (core/color :cyan placeholder)

    masked
    (apply str (repeat (cluster-count value) mask-char))

    :else value))

(defn format-line-with-cursor
  "Render one line of `display` in `width` columns with a cursor at `cursor-pos`.

  The cursor is the grapheme at that index tinted with the inverse attribute;
  it inherits the grapheme's colour, and it is a span, never an escape spliced
  into the middle of another escape. A wide cursor that would cross the right
  edge becomes an inverse space. `display` may be an ANSI string or a styled
  value. The result is exactly `width` display columns."
  [display cursor-pos width show-cursor?]
  (let [width (max 0 width)
        units (text/clusters display)
        n (count units)]
    (if-not show-cursor?
      (text/to-ansi (text/pad-right (text/slice-columns display 0 width) width))
      (let [cursor (max 0 (min cursor-pos n))
            cursor-col (reduce + 0 (map :width (subvec units 0 cursor)))
            start-col (if (>= cursor-col width) (max 0 (- cursor-col (dec width))) 0)
            sliced (text/slice-columns display start-col (+ start-col width))
            sliced-width (text/width sliced)
            rel (- cursor-col start-col)
            cursor-unit (when (< cursor n) (nth units cursor))
            cursor-width (or (:width cursor-unit) 1)
            cursor-cluster (or (:cluster cursor-unit) " ")
            fits? (<= (+ rel cursor-width) width)
            before (text/slice-columns sliced 0 (min rel sliced-width))
            after-start (min (if fits? (+ rel cursor-width) rel) sliced-width)
            after (text/slice-columns sliced after-start sliced-width)
            cursor-cell (text/concat
                         [{:text (if fits? cursor-cluster " ")
                           :style (assoc (or (:style cursor-unit) {}) :inverse true)}])
            body (text/concat before cursor-cell after)]
        (text/to-ansi (text/pad-right body width))))))

;; ────────────────────── Rendering ──────────────────────
(defn- content-columns [input-state]
  (max 0 (- (:width input-state) (if (:border input-state) 4 0))))

(defn- status-text [{:keys [disabled invalid]}]
  (cond
    disabled (core/color :cyan " ⊘")
    invalid (core/color :red " ✗")
    :else ""))

(defn- pad-line [line columns]
  (text/to-ansi (text/pad-right (text/slice-columns line 0 columns) columns)))

(defn render-single-line
  "Render a single-line input as one line of the configured width."
  [{:keys [border label] :as input-state}]
  (let [columns (content-columns input-state)
        status (status-text input-state)
        prefix (if (and label (not border)) (str label ": ") "")
        field (max 0 (- columns 1 (text/width prefix) (text/width status)))
        cursor-line (format-line-with-cursor (format-display-value input-state)
                                             (:cursor input-state)
                                             field
                                             (not (:disabled input-state)))]
    [(text/to-ansi (text/concat " " prefix cursor-line status))]))

(defn- cursor-location
  "Return `[line grapheme-column]` for grapheme cursor `cursor` in `value`."
  [value cursor]
  (loop [units (seq (text/clusters value)), idx 0, line 0, col 0]
    (if (or (nil? units) (= idx cursor))
      [line col]
      (let [cluster (:cluster (first units))]
        (if (#{"\n" "\r\n"} cluster)
          (recur (next units) (inc idx) (inc line) 0)
          (recur (next units) (inc idx) line (inc col)))))))

(defn render-multiline
  "Render a multiline input as `:height` rows, scrolling to the cursor row.

  A trailing newline opens a new empty row with the cursor at its start; it
  does not add a second stray row. A status row is only emitted when the input
  is disabled or invalid."
  [{:keys [disabled height] :as input-state}]
  (let [columns (content-columns input-state)
        value (:value input-state)
        lines (vec (text/lines value))
        [cursor-line cursor-col] (cursor-location value (:cursor input-state))
        offset (max 0 (- cursor-line (dec (max 1 height))))
        padded (vec (take height (drop offset (concat lines (repeat "")))))
        rendered (map-indexed
                  (fn [idx line]
                    (if (and (= (+ offset idx) cursor-line) (not disabled))
                      (format-line-with-cursor line cursor-col columns true)
                      (pad-line line columns)))
                  padded)
        status (cond
                 disabled (core/color :cyan "⊘ disabled")
                 (:invalid input-state) (core/color :red "✗ invalid")
                 :else nil)
        status-line (when status (pad-line status columns))]
    (vec (concat rendered (when status-line [status-line])))))

(defn render
  "Render an input component to a vector of strings.

  Every returned line is exactly `:width` display columns wide, including the
  border. A label is drawn in the border when there is one, and inline
  otherwise."
  [{:keys [multiline border border-style label] :as input-state}]
  (let [content-lines (if multiline
                        (render-multiline input-state)
                        (render-single-line input-state))
        final-lines (if border
                      (if label
                        (borders/draw-titled-box label content-lines
                                                :border-style border-style
                                                :title-pos :left)
                        (borders/draw-box content-lines :border-style border-style))
                      content-lines)]
    (vec final-lines)))

(defn render-to-string
  "Render input to a single string with newlines."
  [input-state]
  (str/join "\n" (render input-state)))

;; ────────────────────── Input Helpers ──────────────────────
(defn input?
  "Check if value is an input component."
  [x]
  (and (map? x) (contains? x :cursor) (contains? x :value)))

(defn empty?
  "Check if input value is empty."
  [input-state]
  (str/blank? (:value input-state)))

(defn disabled?
  "Check if input is disabled."
  [input-state]
  (:disabled input-state))

(defn multiline?
  "Check if input is multiline."
  [input-state]
  (:multiline input-state))

(defn at-start?
  "Check if cursor is at start."
  [input-state]
  (zero? (:cursor input-state)))

(defn at-end?
  "Check if cursor is at the end."
  [input-state]
  (= (:cursor input-state) (cluster-count (:value input-state))))

;; ────────────────────── Convenience Functions ──────────────────────
(defn set-value
  "Set input value and move cursor to the end."
  [input-state value]
  (assoc input-state :value value :cursor (cluster-count value)))

(defn toggle-disabled
  "Toggle disabled state."
  [input-state]
  (update input-state :disabled not))

(defn focus
  "Focus input (move cursor to end)."
  [input-state]
  (move-end input-state))
