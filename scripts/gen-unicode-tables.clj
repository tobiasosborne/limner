(ns gen-unicode-tables
  "Deterministically compile vendored UCD ranges into Clojure source.

  The compiler materialises each property once, applies explicit records over
  defaults declared by the file, and merges adjacent equal values. East Asian
  Width's additional unassigned-CJK defaults are read from its pinned header,
  not duplicated as remembered constants. Output contains no clock or host
  data, so identical input bytes produce identical source bytes."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util Arrays]))

(def pinned-version "17.0.0")
(def code-point-count 0x110000)

(def table-sources
  ["ucd/EastAsianWidth.txt"
   "ucd/emoji/emoji-data.txt"
   "ucd/auxiliary/GraphemeBreakProperty.txt"
   "ucd/DerivedCoreProperties.txt"
   "ucd/extracted/DerivedGeneralCategory.txt"])

(def width-categories
  #{:Mn :Me :Mc :Cf :Cc :Zl :Zp :Cs :Co :Cn})

(defn- parse-range [field]
  (let [[start end] (str/split (str/trim field) #"\.\.")
        start (Long/parseLong start 16)]
    [start (if end (Long/parseLong end 16) start)]))

(defn- records [file]
  (->> (str/split-lines (slurp file))
       (keep (fn [line]
               (let [content (str/trim (first (str/split line #"#" 2)))]
                 (when (seq content)
                   (let [[range-field & fields]
                         (mapv str/trim (str/split content #";"))
                         [start end] (parse-range range-field)]
                     {:start start :end end :fields fields})))))
       vec))

(defn- missing-default [file property]
  (some (fn [line]
          (when-let [[_ body] (re-matches #"# @missing: (.+)" line)]
            (let [[_ & fields] (mapv str/trim (str/split body #";"))]
              (when (or (nil? property) (= property (first fields)))
                (keyword (last fields))))))
        (str/split-lines (slurp file))))

(defn- eaw-implicit-wide-ranges [file]
  (let [ranges (->> (str/split-lines (slurp file))
                    (keep (fn [line]
                            (when-let [[_ start end]
                                       (re-find #"U\+([0-9A-F]+)\.\.U\+([0-9A-F]+)"
                                                line)]
                              {:start (Long/parseLong start 16)
                               :end (Long/parseLong end 16)})))
                    vec)]
    (when-not (= 5 (count ranges))
      (throw (ex-info "EastAsianWidth header has unexpected implicit-wide ranges"
                      {:file (.getPath file) :ranges ranges})))
    ranges))

(defn- code-map [values]
  (zipmap values (range)))

(defn- apply-range! [^ints table start end value]
  (loop [cp start]
    (when (<= cp end)
      (aset-int table cp value)
      (recur (inc cp)))))

(defn- compile-table [default values initial-ranges source-records value-of]
  (let [codes (code-map values)
        default-code (get codes default)
        table (int-array code-point-count)]
    (Arrays/fill table (int default-code))
    (doseq [{:keys [start end value]} initial-ranges]
      (apply-range! table start end (get codes value)))
    (doseq [{:keys [start end] :as record} source-records]
      (when-some [value (value-of record)]
        (let [code (get codes value ::unknown)]
          (when (= ::unknown code)
            (throw (ex-info "Unicode source contains an unexpected property value"
                            {:record record :value value :allowed values})))
          (apply-range! table start end code))))
    (loop [cp 1, start 0, prior (aget table 0), output (transient [])]
      (if (< cp code-point-count)
        (let [current (aget table cp)]
          (if (= prior current)
            (recur (inc cp) start prior output)
            (recur (inc cp) cp current
                   (if (= prior default-code)
                     output
                     (-> output
                         (conj! start)
                         (conj! (dec cp))
                         (conj! prior))))))
        (persistent!
         (if (= prior default-code)
           output
           (-> output
               (conj! start)
               (conj! (dec code-point-count))
               (conj! prior))))))))

(defn- expect-default [file property expected]
  (let [actual (missing-default file property)]
    (when-not (= expected actual)
      (throw (ex-info "Unicode @missing default does not match the generator contract"
                      {:file (.getPath file) :property property
                       :expected expected :actual actual})))
    actual))

(defn- compile-tables [root]
  (let [file #(io/file root %)
        eaw-file (file "ucd/EastAsianWidth.txt")
        emoji-file (file "ucd/emoji/emoji-data.txt")
        gcb-file (file "ucd/auxiliary/GraphemeBreakProperty.txt")
        core-file (file "ucd/DerivedCoreProperties.txt")
        category-file (file "ucd/extracted/DerivedGeneralCategory.txt")
        eaw-values [:N :A :F :H :Na :W]
        gcb-values [:Other :CR :LF :Control :Extend :Regional_Indicator
                    :Prepend :SpacingMark :L :V :T :ZWJ :LV :LVT]
        incb-values [:None :Linker :Consonant :Extend]
        category-values [:other :Mn :Me :Mc :Cf :Cc :Zl :Zp :Cs :Co :Cn]]
    (expect-default eaw-file nil :N)
    (expect-default gcb-file nil :Other)
    (expect-default core-file "InCB" :None)
    [{:name "east-asian-width" :values eaw-values
      :ranges (compile-table
               :N eaw-values
               (map #(assoc % :value :W) (eaw-implicit-wide-ranges eaw-file))
               (records eaw-file)
               #(keyword (first (:fields %))))}
     {:name "grapheme-break" :values gcb-values
      :ranges (compile-table :Other gcb-values [] (records gcb-file)
                             #(keyword (first (:fields %))))}
     {:name "extended-pictographic" :values [:No :Yes]
      :ranges (compile-table :No [:No :Yes] [] (records emoji-file)
                             #(when (= "Extended_Pictographic"
                                       (first (:fields %))) :Yes))}
     {:name "emoji-presentation" :values [:No :Yes]
      :ranges (compile-table :No [:No :Yes] [] (records emoji-file)
                             #(when (= "Emoji_Presentation"
                                       (first (:fields %))) :Yes))}
     {:name "emoji-modifier" :values [:No :Yes]
      :ranges (compile-table :No [:No :Yes] [] (records emoji-file)
                             #(when (= "Emoji_Modifier"
                                       (first (:fields %))) :Yes))}
     {:name "default-ignorable" :values [:No :Yes]
      :ranges (compile-table :No [:No :Yes] [] (records core-file)
                             #(when (= "Default_Ignorable_Code_Point"
                                       (first (:fields %))) :Yes))}
     {:name "incb" :values incb-values
      :ranges (compile-table :None incb-values [] (records core-file)
                             #(when (= "InCB" (first (:fields %)))
                                (keyword (second (:fields %)))))}
     {:name "general-category" :values category-values
      :ranges (compile-table
               :other category-values [] (records category-file)
               #(let [category (keyword (first (:fields %)))]
                  (when (width-categories category) category)))}]))

(defn- read-manifest [root]
  (into {}
        (map (fn [line]
               (let [[checksum path] (str/split line #"  " 2)]
                 [path checksum])))
        (remove str/blank?
                (str/split-lines (slurp (io/file root "SHA256SUMS"))))))

(defn- render-table [{:keys [name values ranges]}]
  (str "(def " name "-values " (pr-str values) ")\n\n"
       "(def ^ints " name "-ranges\n"
       "  (int-array\n"
       "   ["
       (->> (partition 3 ranges)
            (map-indexed
             (fn [index [start end value]]
               (str (when (pos? index) "    ")
                    (format "0x%X 0x%X %d" start end value))))
            (str/join "\n"))
       "]))\n"))

(defn generate-source
  "Return deterministic generated source for a vendored Unicode root."
  [root]
  (let [version (.getName (io/file root))
        manifest (read-manifest root)
        source-lines (for [path table-sources]
                       (str ";; - reference/unicode/" version "/" path
                            "  sha256=" (get manifest path)))]
    (when-not (every? #(get manifest %) table-sources)
      (throw (ex-info "SHA256SUMS lacks a generator input"
                      {:required table-sources :present (keys manifest)})))
    (str ";; AUTOGENERATED Unicode property tables.\n"
         ";; Unicode version: " version "\n"
         ";; Sources:\n" (str/join "\n" source-lines) "\n"
         ";; DO NOT EDIT; run `bb gen-unicode`.\n\n"
         "(ns limner.unicode.tables\n"
         "  \"Compact lookup data generated from the pinned Unicode Character Database.\n\n"
         "  Ranges are flat primitive int arrays of start, end and value-code\n"
         "  triples. Defaults are omitted and supplied by limner.unicode. General\n"
         "  Category retains only Mn, Me, Mc, Cf, Cc, Zl, Zp, Cs, Co and Cn; all\n"
         "  other categories map to :other because width rules do not distinguish\n"
         "  them. Flat arrays were chosen over nested triples after measuring faster\n"
         "  construction and lookup under babashka; they keep startup data small\n"
         "  without losing width input.\"\n"
         "  )\n\n"
         "(def unicode-version " (pr-str version) ")\n\n"
         (str/join "\n" (map render-table (compile-tables root))))))

(defn write-tables!
  "Generate tables from root and write them to target."
  [root target]
  (.mkdirs (.getParentFile (io/file target)))
  (spit target (generate-source root))
  (println "Generated" target "from Unicode" (.getName (io/file root))))

(defn -main [& args]
  (when (> (count args) 1)
    (throw (ex-info "Usage: bb gen-unicode [UCD-version]" {:arguments args})))
  (let [version (or (first args) pinned-version)]
    (write-tables! (str "reference/unicode/" version)
                   "src/limner/unicode/tables.clj")))
