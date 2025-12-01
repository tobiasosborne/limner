(ns limner.core
  "Terminal and ANSI escape sequence utilities"
  (:require [clojure.string :as str]))

;; ────────────────────── ANSI Escape Codes ──────────────────────

;; Basic 16-color palette (8 colors + 8 bright variants)
(def ^:private basic-colors
  "Basic ANSI color codes (3-bit and 4-bit color)"
  {:reset "\u001B[0m"
   :bold "\u001B[1m"
   :dim "\u001B[2m"
   :italic "\u001B[3m"
   :underline "\u001B[4m"

   ;; Standard colors (30-37)
   :black "\u001B[30m"
   :red "\u001B[31m"
   :green "\u001B[32m"
   :yellow "\u001B[33m"
   :blue "\u001B[34m"
   :magenta "\u001B[35m"
   :cyan "\u001B[36m"
   :white "\u001B[37m"

   ;; Bright colors (90-97)
   :bright-black "\u001B[90m"
   :bright-red "\u001B[91m"
   :bright-green "\u001B[92m"
   :bright-yellow "\u001B[93m"
   :bright-blue "\u001B[94m"
   :bright-magenta "\u001B[95m"
   :bright-cyan "\u001B[96m"
   :bright-white "\u001B[97m"

   ;; Background colors (40-47)
   :bg-black "\u001B[40m"
   :bg-red "\u001B[41m"
   :bg-green "\u001B[42m"
   :bg-yellow "\u001B[43m"
   :bg-blue "\u001B[44m"
   :bg-magenta "\u001B[45m"
   :bg-cyan "\u001B[46m"
   :bg-white "\u001B[47m"

   ;; Bright background colors (100-107)
   :bg-bright-black "\u001B[100m"
   :bg-bright-red "\u001B[101m"
   :bg-bright-green "\u001B[102m"
   :bg-bright-yellow "\u001B[103m"
   :bg-bright-blue "\u001B[104m"
   :bg-bright-magenta "\u001B[105m"
   :bg-bright-cyan "\u001B[106m"
   :bg-bright-white "\u001B[107m"})

;; Color validation and conversion

(defn- valid-rgb?
  "Check if RGB values are in valid range [0-255]"
  [r g b]
  (and (int? r) (int? g) (int? b)
       (>= r 0) (<= r 255)
       (>= g 0) (<= g 255)
       (>= b 0) (<= b 255)))

(defn- valid-256?
  "Check if 256-color palette index is valid [0-255]"
  [n]
  (and (int? n) (>= n 0) (<= n 255)))

(defn rgb
  "Create RGB/truecolor specification
   Usage: (rgb 255 128 0) => {:type :rgb :r 255 :g 128 :b 0}

   RGB colors require terminal truecolor support (most modern terminals)"
  [r g b]
  {:pre [(valid-rgb? r g b)]}
  {:type :rgb :r r :g g :b b})

(defn color-256
  "Create 256-color palette specification
   Usage: (color-256 196) => {:type :256 :code 196}

   256-color palette:
   - 0-15: Basic 16 colors
   - 16-231: 6×6×6 RGB cube
   - 232-255: Grayscale ramp

   See: https://en.wikipedia.org/wiki/ANSI_escape_code#8-bit"
  [n]
  {:pre [(valid-256? n)]}
  {:type :256 :code n})

(defn bg-rgb
  "Create RGB background color specification"
  [r g b]
  {:pre [(valid-rgb? r g b)]}
  {:type :bg-rgb :r r :g g :b b})

(defn bg-256
  "Create 256-color background specification"
  [n]
  {:pre [(valid-256? n)]}
  {:type :bg-256 :code n})

;; ANSI code generation

(defn- color-spec->ansi
  "Convert color specification to ANSI escape sequence"
  [color-spec]
  (cond
    ;; Keyword lookup in basic colors
    (keyword? color-spec)
    (get basic-colors color-spec)

    ;; RGB truecolor: ESC[38;2;R;G;Bm
    (and (map? color-spec) (= :rgb (:type color-spec)))
    (str "\u001B[38;2;" (:r color-spec) ";" (:g color-spec) ";" (:b color-spec) "m")

    ;; RGB background: ESC[48;2;R;G;Bm
    (and (map? color-spec) (= :bg-rgb (:type color-spec)))
    (str "\u001B[48;2;" (:r color-spec) ";" (:g color-spec) ";" (:b color-spec) "m")

    ;; 256-color: ESC[38;5;Nm
    (and (map? color-spec) (= :256 (:type color-spec)))
    (str "\u001B[38;5;" (:code color-spec) "m")

    ;; 256-color background: ESC[48;5;Nm
    (and (map? color-spec) (= :bg-256 (:type color-spec)))
    (str "\u001B[48;5;" (:code color-spec) "m")

    ;; Unknown/invalid
    :else
    nil))

(defn color
  "Apply color to string with ANSI escape codes

   Supports multiple color formats:

   1. Basic colors (keywords):
      (color :red \"text\")
      (color :bright-green \"text\")
      (color :bg-blue \"text\")  ; background color

   2. 256-color palette:
      (color (color-256 196) \"text\")       ; bright red
      (color (bg-256 17) \"text\")           ; dark blue background

   3. RGB/truecolor:
      (color (rgb 255 128 0) \"text\")       ; orange
      (color (bg-rgb 0 0 128) \"text\")      ; dark blue background

   Multiple colors can be combined:
      (color :bold (color :red \"text\"))

   Returns string uncolored if color specification is invalid (with warning)
   Returns empty string if input string is nil"
  [color-spec s]
  (when-not s
    (throw (ex-info "Cannot color nil string" {:color-spec color-spec :string s})))
  (if-let [ansi-code (color-spec->ansi color-spec)]
    (str ansi-code s "\u001B[0m")
    (do
      (when (not= color-spec :reset)
        (binding [*out* *err*]
          (println "Warning: Invalid color specification:" color-spec)))
      s))) ; Return string uncolored if invalid

;; Helper functions

(defn available-colors
  "List all available basic color keywords"
  []
  (sort (keys basic-colors)))

(defn color?
  "Check if a value is a valid color specification"
  [spec]
  (or (and (keyword? spec) (contains? basic-colors spec))
      (and (map? spec) (#{:rgb :bg-rgb :256 :bg-256} (:type spec)))))

;; Common color presets

(def colors
  "Convenient color presets for common use cases"
  {:error :red
   :success :green
   :warning :yellow
   :info :cyan
   :muted :bright-black
   :primary :blue
   :secondary :magenta})

(defn visible-length
  "Calculate visible length of string, stripping ANSI codes

   Note: This strips ANSI codes but does NOT handle:
   - Wide characters (CJK) - counted as 1 but display as 2
   - Combining characters - counted but may not display width
   - Emoji with ZWJ - may count incorrectly

   For accurate Unicode width, see: visible-width (TODO)"
  [s]
  (count (str/replace s #"\u001B\[[0-9;]*m" "")))

;; ────────────────────── Terminal Control ──────────────────────
(defn hide-cursor
  "Hide terminal cursor"
  []
  (print "\u001B[?25l")
  (flush))

(defn show-cursor
  "Show terminal cursor"
  []
  (print "\u001B[?25h")
  (flush))

(defn clear-line
  "Clear current line"
  []
  (print "\r\u001B[K")
  (flush))
