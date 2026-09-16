(ns limner.components.statusbar
  "Sectioned status-bar rendering.

  The bar is one line of exactly the configured `:width` display columns.
  Left, centre, and right sections are measured, truncated, and padded through
  `limner.text`, so CJK, emoji, and text that already carries ANSI occupy
  their true columns. Caller styles survive: the section spans keep their own
  foreground and attributes, and `:bg-color`/`:fg-color` fill only the keys a
  span does not set (REVIEW F9/B2/B3).

  There is no private background-colour table or ANSI stripper; the colour
  vocabulary is `limner.text.style`. An unsupported `:bg-color` or `:fg-color`
  throws the `limner.text.style/invalid-color` class, and an invalid width
  throws `:limner.components.statusbar/invalid-width` (ADR 0004)."
  (:require [limner.text :as text]))

;; ────────────────────── Statusbar Component ──────────────────────
(defn statusbar
  "Create a statusbar component with left/center/right sections.
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
  "Update a specific section of the statusbar."
  [statusbar section content]
  (assoc statusbar section content))

;; ────────────────────── Layout ──────────────────────
(defn- layout-regions
  "Return `[left-area center-area right-area]` widths that tile `:width`.

  When the content fits, the centre keeps all the space between the left and
  right sections and centres within it. When it does not fit, the left and
  right sections are capped at two fifths of the bar each so the centre keeps
  a visible region instead of being squeezed out."
  [width left-width center-width right-width]
  (if (<= (+ left-width center-width right-width) width)
    (let [left-area (min left-width width)
          right-area (min right-width (- width left-area))]
      [left-area (- width left-area right-area) right-area])
    (let [cap (quot (* width 2) 5)
          left-area (min left-width cap)
          right-area (min right-width (min cap (- width left-area)))]
      [left-area (max 0 (- width left-area right-area)) right-area])))

(defn- base-style [{:keys [bg-color fg-color]}]
  (cond-> {}
    bg-color (assoc :bg bg-color)
    fg-color (assoc :fg fg-color)))

(defn render
  "Render the statusbar as one string of exactly `:width` display columns.

  Section text may be a string or a styled value. The base `:bg-color` and
  `:fg-color` fill only the keys a section span does not already set, so a
  caller's foreground and attributes survive composition."
  [{:keys [left center right width] :as statusbar}]
  (when-not (and (integer? width) (<= 0 width))
    (throw (ex-info "Statusbar width must be a non-negative integer"
                    {:limner/error :limner.components.statusbar/invalid-width
                     :width width :type (type width)})))
  (let [left-value (or left "")
        center-value (or center "")
        right-value (or right "")
        [left-area center-area right-area]
        (layout-regions width
                        (text/width left-value)
                        (text/width center-value)
                        (text/width right-value))
        left-region (text/pad-right (text/truncate left-value left-area) left-area)
        center-region (text/center (text/truncate center-value center-area) center-area)
        right-region (text/pad-left (text/truncate right-value right-area) right-area)
        line (text/concat left-region center-region right-region)]
    (text/to-ansi (text/style (base-style statusbar) line))))

;; ────────────────────── Helper Functions ──────────────────────
(defn format-git-branch
  "Format git branch name with icon."
  [branch-name]
  (str " " branch-name))

(defn format-file-info
  "Format file information (name, line, column)."
  [filename line column]
  (str filename " " line ":" column))

(defn format-timestamp
  "Format current timestamp."
  ([]
   (format-timestamp (java.time.LocalTime/now)))
  ([^java.time.LocalTime time]
   (let [hour (.getHour time)
         minute (.getMinute time)
         second (.getSecond time)]
     (format "%02d:%02d:%02d" hour minute second))))

(defn format-keybinding
  "Format a keybinding hint."
  [key description]
  (str key " " description))
