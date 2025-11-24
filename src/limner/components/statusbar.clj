(ns limner.components.statusbar
  "Status bar component with left/center/right sections, like Claude Code"
  (:require [clojure.string :as str]
            [limner.core :as core]))

;; ────────────────────── ANSI Background Colors ──────────────────────
(def ^:private bg-colors
  "ANSI background color codes"
  {:black "\u001B[40m"
   :red "\u001B[41m"
   :green "\u001B[42m"
   :yellow "\u001B[43m"
   :blue "\u001B[44m"
   :magenta "\u001B[45m"
   :cyan "\u001B[46m"
   :white "\u001B[47m"
   :bright-black "\u001B[100m"
   :bright-red "\u001B[101m"
   :bright-green "\u001B[102m"
   :bright-yellow "\u001B[103m"
   :bright-blue "\u001B[104m"
   :bright-magenta "\u001B[105m"
   :bright-cyan "\u001B[106m"
   :bright-white "\u001B[107m"})

;; ────────────────────── Statusbar Component ──────────────────────
(defn statusbar
  "Create a statusbar component with left/center/right sections
   Options:
   - :left - left section content (default \"\")
   - :center - center section content (default \"\")
   - :right - right section content (default \"\")
   - :width - total width of statusbar (default 80)
   - :bg-color - background color keyword (default :bright-black)
   - :fg-color - foreground/text color (default :white)"
  [& {:keys [left center right width bg-color fg-color]
      :or {left ""
           center ""
           right ""
           width 80
           bg-color :bright-black
           fg-color :white}}]
  {:type :statusbar
   :left (or left "")
   :center (or center "")
   :right (or right "")
   :width width
   :bg-color bg-color
   :fg-color fg-color})

(defn update-section
  "Update a specific section of the statusbar"
  [statusbar section content]
  (assoc statusbar section content))

;; ────────────────────── Text Truncation ──────────────────────
(defn- truncate
  "Truncate text to max-length, adding ellipsis if needed"
  [text max-length]
  (let [visible (core/visible-length text)]
    (if (<= visible max-length)
      text
      ;; Strip ANSI codes, truncate, add ellipsis
      (let [plain (str/replace text #"\u001B\[[0-9;]*m" "")
            truncated (if (> max-length 3)
                       (str (subs plain 0 (- max-length 3)) "...")
                       (subs plain 0 max-length))]
        truncated))))

;; ────────────────────── Layout Calculation ──────────────────────
(defn- calculate-section-widths
  "Calculate widths for left/center/right sections based on content and total width"
  [left center right total-width]
  (let [left-len (core/visible-length left)
        center-len (core/visible-length center)
        right-len (core/visible-length right)
        total-content (+ left-len center-len right-len)]

    (cond
      ;; All content fits - calculate spacing
      (<= total-content total-width)
      (let [;; For center to be truly centered, we need space on both sides
            center-start (max left-len (/ (- total-width center-len) 2))
            right-start (- total-width right-len)]
        {:left-width left-len
         :center-start (int center-start)
         :center-width center-len
         :right-start (int right-start)
         :right-width right-len})

      ;; Content too large - need to truncate
      :else
      (let [;; Allocate roughly 40% left, 20% center, 40% right
            left-alloc (int (* total-width 0.4))
            center-alloc (int (* total-width 0.2))
            right-alloc (- total-width left-alloc center-alloc)

            ;; But don't truncate if section is empty or shorter than allocation
            left-final (min left-len left-alloc)
            right-final (min right-len right-alloc)
            center-final (min center-len (- total-width left-final right-final))

            center-start (+ left-final (int (/ (- total-width left-final right-final center-final) 2)))]
        {:left-width left-final
         :center-start center-start
         :center-width center-final
         :right-start (- total-width right-final)
         :right-width right-final}))))

;; ────────────────────── Rendering ──────────────────────
(defn- apply-fg-color
  "Apply foreground color to text"
  [color text]
  (if (and color text (not= text ""))
    (core/color color text)
    text))

(defn render
  "Render statusbar to string with background color and proper spacing"
  [{:keys [left center right width bg-color fg-color] :as statusbar}]
  (let [;; Strip any existing ANSI codes from content for accurate measurement
        left-plain (str/replace left #"\u001B\[[0-9;]*m" "")
        center-plain (str/replace center #"\u001B\[[0-9;]*m" "")
        right-plain (str/replace right #"\u001B\[[0-9;]*m" "")

        ;; Calculate layout
        layout (calculate-section-widths left-plain center-plain right-plain width)

        ;; Truncate sections if needed
        left-text (truncate left-plain (:left-width layout))
        center-text (truncate center-plain (:center-width layout))
        right-text (truncate right-plain (:right-width layout))

        ;; Build the statusbar with proper spacing
        ;; Create array of spaces, then place sections
        chars (vec (repeat width " "))

        ;; Place left section
        chars-with-left (reduce
                         (fn [acc i]
                           (if (< i (count left-text))
                             (assoc acc i (str (nth left-text i)))
                             acc))
                         chars
                         (range (count left-text)))

        ;; Place center section
        chars-with-center (reduce
                           (fn [acc i]
                             (let [char-idx (+ (:center-start layout) i)]
                               (if (and (< i (count center-text))
                                       (< char-idx width))
                                 (assoc acc char-idx (str (nth center-text i)))
                                 acc)))
                           chars-with-left
                           (range (count center-text)))

        ;; Place right section
        chars-with-right (reduce
                          (fn [acc i]
                            (let [char-idx (+ (:right-start layout) i)]
                              (if (and (< i (count right-text))
                                      (< char-idx width))
                                (assoc acc char-idx (str (nth right-text i)))
                                acc)))
                          chars-with-center
                          (range (count right-text)))

        ;; Join characters
        content (apply str chars-with-right)

        ;; Apply foreground color if specified
        colored-content (if fg-color
                         (apply-fg-color fg-color content)
                         content)

        ;; Apply background color
        bg-code (get bg-colors bg-color "")
        reset "\u001B[0m"]

    (str bg-code colored-content reset)))

;; ────────────────────── Helper Functions ──────────────────────
(defn format-git-branch
  "Format git branch name with icon"
  [branch-name]
  (str " " branch-name))

(defn format-file-info
  "Format file information (name, line, column)"
  [filename line column]
  (str filename " " line ":" column))

(defn format-timestamp
  "Format current timestamp"
  ([]
   (format-timestamp (java.time.LocalTime/now)))
  ([time]
   (let [hour (.getHour time)
         minute (.getMinute time)
         second (.getSecond time)]
     (format "%02d:%02d:%02d" hour minute second))))

(defn format-keybinding
  "Format a keybinding hint"
  [key description]
  (str key " " description))
