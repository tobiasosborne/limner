(ns limner.unicode.width
  "Terminal cell width as specified by ADR 0001.

  Unicode 17.0 properties are inputs, not a complete terminal algorithm: UAX
  #11 explicitly requires tailoring, while UTS #51 defines presentation and
  emoji sequence structure without assigning terminal columns. ADR 0001 makes
  that tailoring explicit for controls, marks, ambiguous characters, emoji
  presentation, and malformed scalar input.

  `code-point-width` is the scalar rule. `cluster-width` adds presentation and
  sequence adjustments to a string the caller asserts is one grapheme cluster;
  it intentionally does not segment. Both functions take an options map so
  ambiguous-width policy stays pure and injectable. See
  `docs/adr/0001-terminal-width-semantics.md` and the pinned sources under
  `reference/specs/` and `reference/unicode/17.0.0/`.

  Invalid input throws `ex-info` with an ADR 0004 class:
  `:limner.unicode.width/invalid-code-point`,
  `:limner.unicode.width/invalid-options`,
  `:limner.unicode.width/invalid-cluster`, or
  `:limner.unicode.width/invalid-string`."
  (:require [limner.unicode :as unicode]
            [limner.unicode.width.string :as string-width]))

(def ^:private valid-option-keys #{:ambiguous-width})
(def ^:private category-shift 13)
(def ^:private scalar-width-shift 17)
(def ^:private ambiguous-bit 19)

(defn- ambiguous-width [options]
  (when-not (map? options)
    (throw (ex-info "Width options must be a map"
                    {:limner/error :limner.unicode.width/invalid-options
                     :options options :allowed valid-option-keys})))
  (let [unknown (seq (remove valid-option-keys (keys options)))
        value (get options :ambiguous-width 1)]
    (when unknown
      (throw (ex-info "Width options contain unsupported keys"
                      {:limner/error :limner.unicode.width/invalid-options
                       :options options :unsupported (vec unknown)
                       :allowed valid-option-keys})))
    (when-not (#{1 2} value)
      (throw (ex-info "Width options require :ambiguous-width to be 1 or 2"
                      {:limner/error :limner.unicode.width/invalid-options
                       :options options :value value :allowed [1 2]})))
    value))

(defn- packed-code-point [code-point]
  (when-not (and (integer? code-point) (<= 0 code-point 0x10FFFF))
    (throw (ex-info "Width requires a Unicode code point from 0 through 0x10FFFF"
                    {:limner/error :limner.unicode.width/invalid-code-point
                     :code-point code-point :valid-range [0 0x10FFFF]})))
  (let [packed (unicode/packed-properties code-point)]
    (when (= 8 (bit-and 15 (bit-shift-right packed category-shift)))
    (throw (ex-info "Width is undefined for a UTF-16 surrogate code point"
                    {:limner/error :limner.unicode.width/invalid-code-point
                     :code-point code-point
                     :action "Pass a Unicode scalar value, not a UTF-16 code unit"})))
    packed))

(defn- eaw-width [code-point ambiguous]
  (case (unicode/east-asian-width code-point)
    (:W :F) 2
    :A ambiguous
    1))

(defn- packed-width [packed ambiguous]
  (if (and (= 2 ambiguous) (bit-test packed ambiguous-bit))
    2
    (bit-and 3 (bit-shift-right packed scalar-width-shift))))

(defn- code-point-width* [code-point ambiguous]
  (packed-width (packed-code-point code-point) ambiguous))

(defn code-point-width
  "Return the ADR 0001 terminal width of code-point.

  Options must be a map. `:ambiguous-width` defaults to 1 and may be 1 or 2.
  Invalid integers and surrogate code points throw `ex-info`."
  [code-point options]
  (code-point-width* code-point (ambiguous-width options)))

(defn- code-points [^String text]
  (vec (.toArray (.codePoints text))))

(defn- keycap-sequence? [points]
  (and (= 3 (count points))
       (or (<= 0x30 (first points) 0x39)
           (#{0x23 0x2A} (first points)))
       (= [0xFE0F 0x20E3] (subvec points 1))))

(defn- regional-indicator-pair? [points]
  (and (= 2 (count points))
       (every? #(= :Regional_Indicator (unicode/grapheme-break %)) points)))

(defn- emoji-modifier-sequence? [points]
  (and (unicode/extended-pictographic? (first points))
       (some unicode/emoji-modifier? (rest points))))

(defn- emoji-zwj-sequence? [points]
  (and (some #{0x200D} points)
       (<= 2 (count (filter unicode/extended-pictographic? points)))))

(defn- presentation-base [points selector]
  (some (fn [[base following]]
          (when (= selector following) base))
        (partition 2 1 points)))

(defn- emoji-base? [code-point]
  (or (unicode/extended-pictographic? code-point)
      (unicode/emoji-presentation? code-point)))

(defn- isolated-mark-width [first-code-point]
  (let [packed (unicode/packed-properties first-code-point)
        category (bit-and 15 (bit-shift-right packed category-shift))]
    (if (and (#{1 2} category) (not (bit-test packed 12))) 1 0)))

(defn cluster-width
  "Return the ADR 0001 width of one caller-asserted grapheme cluster.

  This function does not segment or verify the one-cluster precondition. An
  empty string, non-string value, invalid options, or unpaired surrogate fails
  with `ex-info`."
  [cluster options]
  (when-not (and (string? cluster) (seq cluster))
    (throw (ex-info "Cluster width requires a non-empty string"
                    {:limner/error :limner.unicode.width/invalid-cluster
                     :cluster cluster :action "Pass exactly one grapheme cluster"})))
  (let [ambiguous (ambiguous-width options)
        points (code-points cluster)
        widths (mapv #(code-point-width* % ambiguous) points)
        base-width (or (some #(when (pos? %) %) widths)
                       (isolated-mark-width (first points)))
        text-base (presentation-base points 0xFE0E)
        emoji-base (presentation-base points 0xFE0F)]
    (cond
      (and text-base (emoji-base? text-base)) (eaw-width text-base ambiguous)
      (and emoji-base (emoji-base? emoji-base)) 2
      (regional-indicator-pair? points) 2
      (emoji-modifier-sequence? points) 2
      (emoji-zwj-sequence? points) 2
      (keycap-sequence? points) 2
      :else base-width)))

(defn string-width
  "Return ADR 0001 width for a complete plain string.

  The implementation delegates to the allocation-free, one-pass scanner in
  `limner.unicode.width.string`; this function owns public option validation."
  [text options]
  (when-not (string? text)
    (throw (ex-info "String width requires a string"
                    {:limner/error :limner.unicode.width/invalid-string :text text})))
  (string-width/width text (ambiguous-width options)))
