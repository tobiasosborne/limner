(ns limner.core
  "Compatibility utilities for ANSI styling, width, and terminal controls.

  Styled composition and Unicode columns delegate to `limner.text`, whose
  semantics are fixed by ADRs 0001 through 0003. The constructors here retain
  the legacy string-returning API; new layout code should keep styled values
  as data until its output boundary."
  (:require [limner.ansi :as ansi]
            [limner.text :as text]))

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

(defn- color-spec->style
  "Convert the compatibility color vocabulary to an ADR 0003 style map."
  [color-spec]
  (cond
    (= :reset color-spec) {}
    (#{:bold :dim :italic} color-spec) {color-spec true}
    (= :underline color-spec) {:underline :single}
    (and (keyword? color-spec) (contains? basic-colors color-spec))
    (let [value (name color-spec)]
      (if (.startsWith ^String value "bg-")
        {:bg (keyword (subs value 3))}
        {:fg color-spec}))

    (and (map? color-spec) (= :rgb (:type color-spec)))
    {:fg {:rgb [(:r color-spec) (:g color-spec) (:b color-spec)]}}

    (and (map? color-spec) (= :bg-rgb (:type color-spec)))
    {:bg {:rgb [(:r color-spec) (:g color-spec) (:b color-spec)]}}

    (and (map? color-spec) (= :256 (:type color-spec)))
    {:fg {:index (:code color-spec)}}

    (and (map? color-spec) (= :bg-256 (:type color-spec)))
    {:bg {:index (:code color-spec)}}

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

   Nested calls compose through `limner.text`: an inner colour does not clear
   an outer attribute after the inner text.

   Invalid specifications retain the legacy warning-and-unchanged behavior.
   Nil input throws `ex-info`."
  [color-spec s]
  (when-not s
    (throw (ex-info "Cannot color nil string" {:color-spec color-spec :string s})))
  (if-let [style-map (color-spec->style color-spec)]
    (text/to-ansi (text/style style-map s))
    (do
      (when (not= color-spec :reset)
        (binding [*out* *err*]
          (println "Warning: Invalid color specification:" color-spec)))
      s)))

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

;; ────────────────────── Unicode Width Calculation ──────────────────────

(defn visible-width
  "Return Unicode 17 display columns for an ANSI string or styled-text value.

   This compatibility name delegates to `limner.text/width`; new code should
   call that function directly when it also needs slicing or padding."
  [s]
  (text/width s))

(defn visible-length
  "Calculate visible length of string, stripping ANSI codes

   DEPRECATED: Use visible-width instead for accurate Unicode handling.

   This function strips ECMA-48 controls and counts Unicode code points. It is
   kept for callers needing a character count, but it does not measure columns
   or grapheme clusters."
  [s]
  (let [^String clean (ansi/strip s)]
    (.codePointCount clean 0 (.length clean))))

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
