(ns limner.unicode
  "Versioned Unicode properties for terminal text algorithms.

  The values in this namespace come from the unmodified Unicode Character
  Database 17.0.0 files under `reference/unicode/17.0.0`. A deterministic
  generator compiles their explicit ranges, `@missing` defaults, and East
  Asian Width's header-defined unassigned-CJK defaults into flat primitive
  tables. Binary search keeps each lookup bounded and allocation-free apart
  from exceptional invalid input.

  General Category exposes the width-relevant categories Mn, Me, Mc, Cf, Cc,
  Zl, Zp, Cs, Co and Cn. Other assigned categories return `:other`; retaining
  categories that terminal width does not distinguish would enlarge startup
  data without changing a later width decision. Generated tables are never
  edited directly: run `bb gen-unicode`, which also embeds source checksums."
  (:require [limner.unicode.tables :as tables]))

(def max-code-point 0x10FFFF)

(defn version
  "Return the pinned Unicode Character Database version."
  []
  tables/unicode-version)

(defn- validate-code-point [code-point]
  (when-not (and (integer? code-point) (<= 0 code-point max-code-point))
    (throw (ex-info "Unicode code point must be an integer from 0 through 0x10FFFF"
                    {:code-point code-point
                     :valid-range [0 max-code-point]})))
  code-point)

(defn- range-value-code [^ints ranges code-point default-code]
  (loop [low 0, high (dec (quot (alength ranges) 3))]
    (if (> low high)
      default-code
      (let [middle (quot (+ low high) 2)
            offset (* middle 3)
            start (aget ranges offset)
            end (aget ranges (inc offset))]
        (cond
          (< code-point start) (recur low (dec middle))
          (> code-point end) (recur (inc middle) high)
          :else (aget ranges (+ offset 2)))))))

(defn- property-value [ranges values default-code code-point]
  (validate-code-point code-point)
  (nth values (range-value-code ranges code-point default-code)))

(defn east-asian-width
  "Return the UCD East_Asian_Width keyword for code-point."
  [code-point]
  (property-value tables/east-asian-width-ranges
                  tables/east-asian-width-values 0 code-point))

(defn grapheme-break
  "Return the UCD Grapheme_Cluster_Break keyword for code-point."
  [code-point]
  (property-value tables/grapheme-break-ranges
                  tables/grapheme-break-values 0 code-point))

(defn extended-pictographic?
  "Return whether code-point has the Extended_Pictographic property."
  [code-point]
  (= :Yes (property-value tables/extended-pictographic-ranges
                          tables/extended-pictographic-values 0 code-point)))

(defn emoji-presentation?
  "Return whether code-point has the Emoji_Presentation property."
  [code-point]
  (= :Yes (property-value tables/emoji-presentation-ranges
                          tables/emoji-presentation-values 0 code-point)))

(defn default-ignorable?
  "Return whether code-point has Default_Ignorable_Code_Point."
  [code-point]
  (= :Yes (property-value tables/default-ignorable-ranges
                          tables/default-ignorable-values 0 code-point)))

(defn incb
  "Return the Indic_Conjunct_Break keyword for code-point."
  [code-point]
  (property-value tables/incb-ranges tables/incb-values 0 code-point))

(defn general-category
  "Return a width-relevant General_Category keyword, or `:other`."
  [code-point]
  (property-value tables/general-category-ranges
                  tables/general-category-values 0 code-point))
