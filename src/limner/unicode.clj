(ns limner.unicode
  "Versioned Unicode properties for terminal text algorithms.

  The values in this namespace come from the unmodified Unicode Character
  Database 17.0.0 files under `reference/unicode/17.0.0`. A deterministic
  generator compiles their explicit ranges, `@missing` defaults, and East
  Asian Width's header-defined unassigned-CJK defaults into primitive tables.
  A load-time packed BMP table gives hot paths O(1) lookup; supplementary
  planes use small page indexes into the generated ranges.

  General Category exposes the width-relevant categories Mn, Me, Mc, Cf, Cc,
  Zl, Zp, Cs, Co and Cn. Other assigned categories return `:other`; retaining
  categories that terminal width does not distinguish would enlarge startup
  data without changing a later width decision. Generated tables are never
  edited directly: run `bb gen-unicode`, which also embeds source checksums.

  An invalid code point throws `ex-info` with
  `:limner.unicode/invalid-code-point` (ADR 0004)."
  (:require [limner.unicode.tables :as tables]))

(def max-code-point 0x10FFFF)

(defn version
  "Return the pinned Unicode Character Database version."
  []
  tables/unicode-version)

(defn- validate-code-point [code-point]
  (when-not (and (integer? code-point) (<= 0 code-point max-code-point))
    (throw (ex-info "Unicode code point must be an integer from 0 through 0x10FFFF"
                    {:limner/error :limner.unicode/invalid-code-point
                     :code-point code-point
                     :valid-range [0 max-code-point]})))
  code-point)

(def ^:private page-shift 8)
(def ^:private page-count 0x1100)

(defn- advance-range-offset [^ints ranges offset code-point]
  (loop [offset offset]
    (if (and (< offset (alength ranges))
             (< (aget ranges (inc offset)) code-point))
      (recur (+ offset 3))
      offset)))

(defn- page-index [^ints ranges]
  (let [index (int-array page-count)]
    (loop [page 0, offset 0]
      (if (= page page-count)
        index
        (let [offset (advance-range-offset ranges offset
                                           (bit-shift-left page page-shift))]
          (aset-int index page offset)
          (recur (inc page) offset))))))

(defn- indexed-range-value-code
  [^ints ranges ^ints index code-point default-code]
  (loop [offset (aget index (bit-shift-right code-point page-shift))]
    (if (>= offset (alength ranges))
      default-code
      (let [start (aget ranges offset), end (aget ranges (inc offset))]
        (cond
          (< code-point start) default-code
          (<= code-point end) (aget ranges (+ offset 2))
          :else (recur (+ offset 3)))))))

(def ^:private eaw-shift 0)
(def ^:private gcb-shift 3)
(def ^:private incb-shift 7)
(def ^:private extended-pictographic-bit 9)
(def ^:private emoji-presentation-bit 10)
(def ^:private emoji-modifier-bit 11)
(def ^:private default-ignorable-bit 12)
(def ^:private category-shift 13)
(def ^:private scalar-width-shift 17)
(def ^:private ambiguous-bit 19)

(defn- apply-ranges! [^ints packed ^ints ranges shift]
  (loop [offset 0]
    (when (< offset (alength ranges))
      (let [start (aget ranges offset), end (min 0xFFFF (aget ranges (inc offset)))
            value (bit-shift-left (aget ranges (+ offset 2)) shift)]
        (when (<= start 0xFFFF)
          (loop [code-point start]
            (when (<= code-point end)
              (aset-int packed code-point (bit-or (aget packed code-point) value))
              (recur (inc code-point)))))
        (recur (+ offset 3))))))

(defn- scalar-width-code [packed]
  (let [eaw (bit-and 7 packed)
        gcb (bit-and 15 (bit-shift-right packed gcb-shift))
        category (bit-and 15 (bit-shift-right packed category-shift))]
    (cond
      (#{1 2 4 5 6 7} category) 0
      (bit-test packed default-ignorable-bit) 0
      (#{9 10} gcb) 0
      (#{2 5} eaw) 2
      :else 1)))

(defn- add-width-bits! [^ints packed]
  (dotimes [code-point 0x10000]
    (let [value (aget packed code-point)
          width (scalar-width-code value)
          value (bit-or value (bit-shift-left width scalar-width-shift))
          value (if (= 1 (bit-and 7 value)) (bit-set value ambiguous-bit) value)]
      (aset-int packed code-point value))))

(def ^:private bmp-properties
  (let [packed (int-array 0x10000)]
    (doseq [[ranges shift]
            [[tables/east-asian-width-ranges eaw-shift]
             [tables/grapheme-break-ranges gcb-shift]
             [tables/incb-ranges incb-shift]
             [tables/extended-pictographic-ranges extended-pictographic-bit]
             [tables/emoji-presentation-ranges emoji-presentation-bit]
             [tables/emoji-modifier-ranges emoji-modifier-bit]
             [tables/default-ignorable-ranges default-ignorable-bit]
             [tables/general-category-ranges category-shift]]]
      (apply-ranges! packed ranges shift))
    (add-width-bits! packed)
    packed))

(def ^:private eaw-pages (page-index tables/east-asian-width-ranges))
(def ^:private gcb-pages (page-index tables/grapheme-break-ranges))
(def ^:private incb-pages (page-index tables/incb-ranges))
(def ^:private pictographic-pages (page-index tables/extended-pictographic-ranges))
(def ^:private presentation-pages (page-index tables/emoji-presentation-ranges))
(def ^:private modifier-pages (page-index tables/emoji-modifier-ranges))
(def ^:private ignorable-pages (page-index tables/default-ignorable-ranges))
(def ^:private category-pages (page-index tables/general-category-ranges))

(defn- supplementary-properties [code-point]
  (let [eaw (indexed-range-value-code tables/east-asian-width-ranges eaw-pages code-point 0)
        gcb (indexed-range-value-code tables/grapheme-break-ranges gcb-pages code-point 0)
        incb (indexed-range-value-code tables/incb-ranges incb-pages code-point 0)
        category (indexed-range-value-code tables/general-category-ranges category-pages code-point 0)
        packed (bit-or eaw (bit-shift-left gcb gcb-shift)
                       (bit-shift-left incb incb-shift)
                       (bit-shift-left category category-shift))
        packed (cond-> packed
                 (= 1 (indexed-range-value-code tables/extended-pictographic-ranges pictographic-pages code-point 0))
                 (bit-set extended-pictographic-bit)
                 (= 1 (indexed-range-value-code tables/emoji-presentation-ranges presentation-pages code-point 0))
                 (bit-set emoji-presentation-bit)
                 (= 1 (indexed-range-value-code tables/emoji-modifier-ranges modifier-pages code-point 0))
                 (bit-set emoji-modifier-bit)
                 (= 1 (indexed-range-value-code tables/default-ignorable-ranges ignorable-pages code-point 0))
                 (bit-set default-ignorable-bit))
        width (scalar-width-code packed)]
    (cond-> (bit-or packed (bit-shift-left width scalar-width-shift))
      (= 1 eaw) (bit-set ambiguous-bit))))

(defn packed-properties
  "Return the internal packed property word used by grapheme and width loops.

  Bits 0..2 are EAW, 3..6 GCB, 7..8 InCB, 9..12 boolean emoji/default
  properties, 13..16 General Category, 17..18 scalar width, and bit 19 marks
  EAW Ambiguous. BMP values use a load-time flat table; supplementary values
  use a page index into the generated ranges."
  [code-point]
  (validate-code-point code-point)
  (if (< code-point 0x10000)
    (aget ^ints bmp-properties code-point)
    (supplementary-properties code-point)))

(defn grapheme-properties
  "Return the GCB/InCB/Extended_Pictographic subset of the packed word.

  This avoids width-only supplementary lookups in the segmentation hot loop."
  [code-point]
  (validate-code-point code-point)
  (if (< code-point 0x10000)
    (aget ^ints bmp-properties code-point)
    (let [gcb (indexed-range-value-code tables/grapheme-break-ranges gcb-pages code-point 0)
          incb (indexed-range-value-code tables/incb-ranges incb-pages code-point 0)]
      (cond-> (bit-or (bit-shift-left gcb gcb-shift)
                      (bit-shift-left incb incb-shift))
        (= 1 (indexed-range-value-code tables/extended-pictographic-ranges pictographic-pages code-point 0))
        (bit-set extended-pictographic-bit)))))

(defn east-asian-width
  "Return the UCD East_Asian_Width keyword for code-point."
  [code-point]
  (nth tables/east-asian-width-values
       (bit-and 7 (packed-properties code-point))))

(defn grapheme-break
  "Return the UCD Grapheme_Cluster_Break keyword for code-point."
  [code-point]
  (nth tables/grapheme-break-values
       (bit-and 15 (bit-shift-right (packed-properties code-point) gcb-shift))))

(defn extended-pictographic?
  "Return whether code-point has the Extended_Pictographic property."
  [code-point]
  (bit-test (packed-properties code-point) extended-pictographic-bit))

(defn emoji-presentation?
  "Return whether code-point has the Emoji_Presentation property."
  [code-point]
  (bit-test (packed-properties code-point) emoji-presentation-bit))

(defn emoji-modifier?
  "Return whether code-point has the Emoji_Modifier property."
  [code-point]
  (bit-test (packed-properties code-point) emoji-modifier-bit))

(defn default-ignorable?
  "Return whether code-point has Default_Ignorable_Code_Point."
  [code-point]
  (bit-test (packed-properties code-point) default-ignorable-bit))

(defn incb
  "Return the Indic_Conjunct_Break keyword for code-point."
  [code-point]
  (nth tables/incb-values
       (bit-and 3 (bit-shift-right (packed-properties code-point) incb-shift))))

(defn general-category
  "Return a width-relevant General_Category keyword, or `:other`."
  [code-point]
  (nth tables/general-category-values
       (bit-and 15 (bit-shift-right (packed-properties code-point) category-shift))))
