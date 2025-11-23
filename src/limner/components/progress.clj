(ns limner.components.progress
  "Progress indicators - spinners, bars, step indicators, and pulse effects"
  (:require [clojure.string :as str]
            [limner.core :as core]))

;; ────────────────────── Spinner Styles ──────────────────────
(def spinner-styles
  "Predefined spinner animation frames"
  {:dots ["⠋" "⠙" "⠹" "⠸" "⠼" "⠴" "⠦" "⠧" "⠇" "⠏"]
   :line ["-" "\\" "|" "/"]
   :arrow ["←" "↖" "↑" "↗" "→" "↘" "↓" "↙"]
   :dots2 ["⣾" "⣽" "⣻" "⢿" "⡿" "⣟" "⣯" "⣷"]
   :dots3 ["⠋" "⠙" "⠚" "⠞" "⠖" "⠦" "⠴" "⠲" "⠳" "⠓"]
   :box ["◰" "◳" "◲" "◱"]
   :bounce ["⠁" "⠂" "⠄" "⠂"]
   :circle ["◐" "◓" "◑" "◒"]
   :square ["◰" "◳" "◲" "◱"]
   :toggle ["⊶" "⊷"]})

;; ────────────────────── Spinner Component ──────────────────────
(defn spinner
  "Create a spinner component
   Options:
   - :style - spinner style keyword (default :dots)
   - :frames - custom frame sequence for :custom style
   - :color - ANSI color for spinner (default :cyan)"
  [& {:keys [style frames color]
      :or {style :dots
           color :cyan}}]
  {:type :spinner
   :style style
   :frames (if (= style :custom) frames (get spinner-styles style))
   :frame 0
   :color color})

(defn tick
  "Advance animation to next frame"
  [component]
  (case (:type component)
    :spinner
    (let [frame-count (count (:frames component))
          next-frame (mod (inc (:frame component)) frame-count)]
      (assoc component :frame next-frame))

    :progress-bar
    (if (= :indeterminate (:bar-type component))
      (let [next-frame (mod (inc (:animation-frame component 0)) 20)]
        (assoc component :animation-frame next-frame))
      component)

    :pulse
    (let [frame-count (case (:speed component :normal)
                       :fast 8
                       :normal 12
                       :slow 16)
          next-frame (mod (inc (:frame component)) frame-count)]
      (assoc component :frame next-frame))

    ;; Default: return unchanged
    component))

;; ────────────────────── Progress Bar Component ──────────────────────
(defn progress-bar
  "Create a progress bar component
   Options:
   - :type - :determinate or :indeterminate (default :determinate)
   - :value - current value 0-100 (for determinate bars)
   - :width - bar width in characters (default 30)
   - :show-percentage - show percentage label (default true)
   - :filled-char - character for filled portion (default \"█\")
   - :empty-char - character for empty portion (default \"░\")
   - :color - color for filled portion (default :green)"
  [& {:keys [type value width show-percentage filled-char empty-char color]
      :or {type :determinate
           value 0
           width 30
           show-percentage true
           filled-char "█"
           empty-char "░"
           color :green}}]
  (let [clamped-value (max 0 (min 100 value))]
    {:type :progress-bar
     :bar-type type
     :value clamped-value
     :width width
     :show-percentage show-percentage
     :filled-char filled-char
     :empty-char empty-char
     :color color
     :animation-frame 0}))

(defn set-value
  "Update progress bar value"
  [bar value]
  (assoc bar :value (max 0 (min 100 value))))

(defn increment
  "Increment progress bar value by amount"
  [bar amount]
  (set-value bar (+ (:value bar) amount)))

;; ────────────────────── Step Indicator Component ──────────────────────
(defn step-indicator
  "Create a step indicator component
   Options:
   - :current - current step number (1-indexed)
   - :total - total number of steps
   - :labels - optional vector of step labels
   - :show-progress - show progress bar below steps (default false)"
  [& {:keys [current total labels show-progress]
      :or {current 1
           total 1
           show-progress false}}]
  (let [clamped-current (max 1 (min total current))]
    {:type :step-indicator
     :current clamped-current
     :total total
     :labels labels
     :show-progress show-progress}))

(defn step-percentage
  "Calculate percentage complete for step indicator"
  [steps]
  (if (zero? (:total steps))
    0
    (int (* 100 (/ (:current steps) (:total steps))))))

(defn next-step
  "Advance to next step"
  [steps]
  (update steps :current #(min (:total steps) (inc %))))

(defn prev-step
  "Go back to previous step"
  [steps]
  (update steps :current #(max 1 (dec %))))

;; ────────────────────── Pulse Effect Component ──────────────────────
(defn pulse
  "Create a pulsing/breathing text effect
   Options:
   - :text - text to pulse
   - :speed - :fast :normal :slow (default :normal)
   - :color - color to pulse (default :cyan)"
  [& {:keys [text speed color]
      :or {text ""
           speed :normal
           color :cyan}}]
  {:type :pulse
   :text text
   :speed speed
   :color color
   :frame 0})

(defn pulse-frame-count
  "Get total frame count for pulse speed"
  [pulse-component]
  (case (:speed pulse-component :normal)
    :fast 8
    :normal 12
    :slow 16))

;; ────────────────────── Rendering ──────────────────────
(defn- render-spinner
  "Render spinner to string"
  [{:keys [frames frame color]}]
  (let [current-frame (nth frames frame)]
    (core/color color current-frame)))

(defn- render-progress-bar-determinate
  "Render determinate progress bar"
  [{:keys [value width filled-char empty-char color show-percentage]}]
  (let [;; Calculate bar width accounting for brackets and optional percentage
        bar-width (int (if show-percentage (- width 7) (- width 2)))
        filled-width (int (* bar-width (/ value 100.0)))
        empty-width (int (- bar-width filled-width))

        ;; Build bar components
        filled (apply str (repeat filled-width filled-char))
        empty (apply str (repeat empty-width empty-char))
        bar (str "[" (core/color color filled) empty "]")

        ;; Add percentage if requested
        percentage-str (when show-percentage
                        (format " %3d%%" (int value)))]
    (str bar percentage-str)))

(defn- render-progress-bar-indeterminate
  "Render indeterminate progress bar with moving segment"
  [{:keys [width empty-char animation-frame color]}]
  (let [bar-width (int (- width 2))
        segment-width 5
        position (int (mod animation-frame bar-width))

        ;; Create bar with moving segment
        bar-chars (for [i (range bar-width)]
                   (if (and (>= i position)
                           (< i (+ position segment-width)))
                     (core/color color "█")
                     empty-char))]
    (str "[" (apply str bar-chars) "]")))

(defn- render-progress-bar
  "Render progress bar to string"
  [bar]
  (if (= :indeterminate (:bar-type bar))
    (render-progress-bar-indeterminate bar)
    (render-progress-bar-determinate bar)))

(defn- render-step-indicator
  "Render step indicator to string"
  [{:keys [current total labels show-progress]}]
  (let [;; Basic step display
        step-text (str current "/" total)

        ;; Add current step label if available
        label-text (when (and labels (>= (count labels) current))
                    (str " - " (nth labels (dec current))))

        ;; Optional progress bar
        progress-text (when show-progress
                       (let [pct (step-percentage {:current current :total total})
                             bar (progress-bar :value pct :width 20)]
                         (str "\n" (render-progress-bar bar))))]

    (str (core/color :cyan step-text) label-text progress-text)))

(defn- render-pulse
  "Render pulsing text with varying opacity"
  [{:keys [text frame speed color]}]
  (let [frame-count (case speed
                     :fast 8
                     :normal 12
                     :slow 16)
        ;; Calculate opacity based on sine wave
        phase (/ (* 2 Math/PI frame) frame-count)
        intensity (/ (+ 1 (Math/sin phase)) 2) ; 0 to 1

        ;; Map intensity to color brightness
        ;; For simplicity, alternate between normal and bright variants
        use-bright? (> intensity 0.5)]

    (if use-bright?
      (core/color color text)
      ;; Use dimmer version (could be enhanced with more ANSI codes)
      (str "\u001B[2m" (core/color color text) "\u001B[22m"))))

(defn render
  "Render any progress component to string"
  [component]
  (case (:type component)
    :spinner (render-spinner component)
    :progress-bar (render-progress-bar component)
    :step-indicator (render-step-indicator component)
    :pulse (render-pulse component)
    ""))

;; ────────────────────── Helper Functions ──────────────────────
(defn complete?
  "Check if progress indicator shows completion"
  [component]
  (case (:type component)
    :progress-bar (= 100 (:value component))
    :step-indicator (= (:current component) (:total component))
    false))

(defn component-type
  "Get the type of progress component"
  [component]
  (:type component))

;; ────────────────────── Composite Progress Display ──────────────────────
(defn combined-progress
  "Create a combined progress display with multiple indicators
   Example: spinner + progress bar + text"
  [& components]
  {:type :combined
   :components components})

(defn render-combined
  "Render combined progress display"
  [combined]
  (let [components (:components combined)
        rendered (map render components)]
    (str/join "  " rendered)))
