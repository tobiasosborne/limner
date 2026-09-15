(ns limner.unicode-test
  "Independent checks from raw Unicode 17.0 data to generated lookups.

  The parser here is intentionally small and separate from the generator. It
  scans the upstream range records directly, including the East Asian Width
  header defaults, so generator and lookup bugs cannot simply agree with one
  another."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [limner.unicode :as unicode]))

(def generate-source
  (do
    (load-file "scripts/gen-unicode-tables.clj")
    (ns-resolve 'gen-unicode-tables 'generate-source)))

(def unicode-root "reference/unicode/17.0.0")

(def source-paths
  {:eaw "ucd/EastAsianWidth.txt"
   :emoji "ucd/emoji/emoji-data.txt"
   :gcb "ucd/auxiliary/GraphemeBreakProperty.txt"
   :core "ucd/DerivedCoreProperties.txt"
   :category "ucd/extracted/DerivedGeneralCategory.txt"})

(def width-categories #{:Mn :Me :Mc :Cf :Cc :Zl :Zp :Cs :Co :Cn})

(defn- source-file [key]
  (str unicode-root "/" (source-paths key)))

(defn- parse-range [token]
  (let [[start end] (str/split (str/trim token) #"\.\.")
        start (Long/parseLong start 16)]
    [start (if end (Long/parseLong end 16) start)]))

(defn- records [key]
  (->> (str/split-lines (slurp (source-file key)))
       (keep (fn [line]
               (let [content (str/trim (first (str/split line #"#" 2)))]
                 (when (seq content)
                   (let [[range-field & fields] (mapv str/trim
                                                     (str/split content #";"))
                         [start end] (parse-range range-field)]
                     {:start start :end end :fields fields})))))
       vec))

(defn- missing-default [key property]
  (some (fn [line]
          (when-let [[_ body] (re-matches #"# @missing: (.+)" line)]
            (let [[_ & fields] (mapv str/trim (str/split body #";"))]
              (when (or (nil? property) (= property (first fields)))
                (keyword (last fields))))))
        (str/split-lines (slurp (source-file key)))))

(defn- value-at [ranges cp default]
  (or (some (fn [{:keys [start end value]}]
              (when (<= start cp end) value))
            ranges)
      default))

(def raw-eaw
  (delay (mapv (fn [{:keys [start end fields]}]
                 {:start start :end end :value (keyword (first fields))})
               (records :eaw))))

(def raw-eaw-implicit-wide
  (delay
    (->> (str/split-lines (slurp (source-file :eaw)))
         (keep (fn [line]
                 (when-let [[_ start end]
                            (re-find #"U\+([0-9A-F]+)\.\.U\+([0-9A-F]+)" line)]
                   {:start (Long/parseLong start 16)
                    :end (Long/parseLong end 16)
                    :value :W})))
         vec)))

(def raw-gcb
  (delay (mapv (fn [{:keys [start end fields]}]
                 {:start start :end end :value (keyword (first fields))})
               (records :gcb))))

(def raw-emoji
  (delay (records :emoji)))

(def raw-core
  (delay (records :core)))

(def raw-extended-pictographic
  (delay (filterv #(= "Extended_Pictographic" (first (:fields %)))
                  @raw-emoji)))

(def raw-emoji-presentation
  (delay (filterv #(= "Emoji_Presentation" (first (:fields %)))
                  @raw-emoji)))

(def raw-default-ignorable
  (delay (filterv #(= "Default_Ignorable_Code_Point" (first (:fields %)))
                  @raw-core)))

(def raw-incb
  (delay (filterv #(= "InCB" (first (:fields %))) @raw-core)))

(def raw-category
  (delay (mapv (fn [{:keys [start end fields]}]
                 (let [category (keyword (first fields))]
                   {:start start :end end
                    :value (if (width-categories category) category :other)}))
               (records :category))))

(def eaw-default (delay (missing-default :eaw nil)))
(def gcb-default (delay (missing-default :gcb nil)))
(def incb-default (delay (missing-default :core "InCB")))

(defn- eaw-at [cp]
  (value-at @raw-eaw cp
            (value-at @raw-eaw-implicit-wide cp
                      @eaw-default)))

(defn- gcb-at [cp]
  (value-at @raw-gcb cp @gcb-default))

(defn- binary-property-at [ranges cp]
  (boolean
   (some (fn [{:keys [start end]}]
           (when (<= start cp end) true))
         ranges)))

(defn- incb-at [cp]
  (or (some (fn [{:keys [start end fields]}]
              (when (<= start cp end)
                (keyword (second fields))))
            @raw-incb)
      @incb-default))

(defn- category-at [cp]
  (value-at @raw-category cp :other))

(def sample-code-points
  (let [random (java.util.Random. 170017)]
    (->> (concat
          [0 0x378 0x3000 0x4DBE 0xD800 0xE000 0x10FFFF 0x3FFFD]
          (for [plane (range 17)
                _ (range 24)]
            (+ (* plane 0x10000) (.nextInt random 0x10000))))
         distinct
         vec)))

(deftest generated-tables-have-not-drifted
  (is (true? (= (generate-source unicode-root)
                (slurp "src/limner/unicode/tables.clj")))
      "src/limner/unicode/tables.clj differs from `bb gen-unicode` output"))

(deftest lookups-match-independent-raw-data-sample
  (is (>= (count sample-code-points) 400))
  (doseq [cp sample-code-points]
    (testing (format "U+%04X" cp)
      (is (= (eaw-at cp) (unicode/east-asian-width cp)))
      (is (= (gcb-at cp) (unicode/grapheme-break cp)))
      (is (= (binary-property-at @raw-extended-pictographic cp)
             (unicode/extended-pictographic? cp)))
      (is (= (binary-property-at @raw-emoji-presentation cp)
             (unicode/emoji-presentation? cp)))
      (is (= (boolean
              (some (fn [{:keys [start end fields]}]
                      (when (and (= "Emoji_Modifier" (first fields))
                                 (<= start cp end)) true))
                    @raw-emoji))
             (unicode/emoji-modifier? cp)))
      (is (= (binary-property-at @raw-default-ignorable cp)
             (unicode/default-ignorable? cp)))
      (is (= (incb-at cp) (unicode/incb cp)))
      (is (= (category-at cp) (unicode/general-category cp))))))

(deftest documented-property-examples
  (is (= "17.0.0" (unicode/version)))
  (is (= [:F :W :N :A]
         (mapv unicode/east-asian-width [0x3000 0x2630 0x2713 0x1F170])))
  (is (= [true false]
         (mapv unicode/emoji-presentation? [0x26A1 0x2764])))
  (is (= [true false]
         (mapv unicode/emoji-modifier? [0x1F3FD 0x1F44D])))
  (is (= [true true]
         (mapv unicode/default-ignorable? [0x00AD 0xFEFF])))
  (is (= [:ZWJ :Regional_Indicator]
         (mapv unicode/grapheme-break [0x200D 0x1F1E6])))
  (is (= :Linker (unicode/incb 0x094D))))

(deftest defaults-and-domain-edges
  (testing "missing defaults and Unicode edge classes"
    (is (= :N (unicode/east-asian-width 0x0378)))
    (is (= :W (unicode/east-asian-width 0x3FFFD)))
    (is (= :N (unicode/east-asian-width 0x10FFFF)))
    (is (= :Cs (unicode/general-category 0xD800)))
    (is (= :Co (unicode/general-category 0xE000)))
    (is (= :Cn (unicode/general-category 0x3FFFD))))
  (testing "invalid code points fail loudly"
    (doseq [cp [-1 0x110000 nil 1.5]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Unicode code point"
                            (unicode/east-asian-width cp))))))
