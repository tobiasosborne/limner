(ns limner.unicode.width-test
  "Width-policy tests restated independently from the raw Unicode 17 data.

  The exhaustive oracle compiles only the properties named by ADR 0001 into
  test-local byte arrays. It does not call the production generator or Unicode
  lookups. The JVM checks every code point; babashka checks a fixed 65,536-point
  cross-plane sample to keep the portable suite bounded."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [limner.unicode.width :as width])
  (:import [java.util Arrays]))

(def unicode-root "reference/unicode/17.0.0/ucd")
(def code-point-count 0x110000)

(def eaw-codes {"N" 0, "A" 1, "F" 2, "H" 3, "Na" 4, "W" 5})
(def category-codes
  {"Mn" 1, "Me" 2, "Mc" 3, "Cf" 4, "Cc" 5,
   "Zl" 6, "Zp" 7, "Cs" 8, "Co" 9, "Cn" 10})
(def gcb-codes {"V" 1, "T" 2})

(defn- parse-range [field]
  (let [[start end] (str/split (str/trim field) #"\.\.")
        start (Long/parseLong start 16)]
    [start (if end (Long/parseLong end 16) start)]))

(defn- records [path]
  (->> (str/split-lines (slurp path))
       (keep (fn [line]
               (let [content (str/trim (first (str/split line #"#" 2)))]
                 (when (seq content)
                   (let [[range-field & fields]
                         (mapv str/trim (str/split content #";"))
                         [start end] (parse-range range-field)]
                     {:start start :end end :fields fields})))))
       vec))

(defn- apply-byte-range! [table start end value]
  (loop [cp start]
    (when (<= cp end)
      (aset-byte table cp (byte value))
      (recur (inc cp)))))

(def raw-eaw
  (delay
    (let [path (str unicode-root "/EastAsianWidth.txt")
          table (byte-array code-point-count)]
      (doseq [line (str/split-lines (slurp path))]
        (when-let [[_ start end]
                   (re-find #"U\+([0-9A-F]+)\.\.U\+([0-9A-F]+)" line)]
          (apply-byte-range! table (Long/parseLong start 16)
                             (Long/parseLong end 16) 5)))
      (doseq [{:keys [start end fields]} (records path)]
        (apply-byte-range! table start end (get eaw-codes (first fields))))
      table)))

(def raw-categories
  (delay
    (let [table (byte-array code-point-count)]
      (doseq [{:keys [start end fields]}
              (records (str unicode-root "/extracted/DerivedGeneralCategory.txt"))]
        (when-some [code (get category-codes (first fields))]
          (apply-byte-range! table start end code)))
      table)))

(def raw-gcb
  (delay
    (let [table (byte-array code-point-count)]
      (doseq [{:keys [start end fields]}
              (records (str unicode-root "/auxiliary/GraphemeBreakProperty.txt"))]
        (when-some [code (get gcb-codes (first fields))]
          (apply-byte-range! table start end code)))
      table)))

(def raw-default-ignorable
  (delay
    (let [table (byte-array code-point-count)]
      (doseq [{:keys [start end fields]}
              (records (str unicode-root "/DerivedCoreProperties.txt"))]
        (when (= "Default_Ignorable_Code_Point" (first fields))
          (apply-byte-range! table start end 1)))
      table)))

(defn- raw-code-point-width [cp ambiguous-width]
  (let [category (aget ^bytes @raw-categories cp)
        eaw (aget ^bytes @raw-eaw cp)
        gcb (aget ^bytes @raw-gcb cp)]
    (cond
      (= category 8) :surrogate
      (or (= category 5) (= category 6) (= category 7)) 0
      (= 1 (aget ^bytes @raw-default-ignorable cp)) 0
      (or (= category 1) (= category 2) (= category 4)) 0
      (or (= gcb 1) (= gcb 2)) 0
      (or (= eaw 2) (= eaw 5)) 2
      (= eaw 1) ambiguous-width
      :else 1)))

(def exhaustive-code-points
  (if (System/getProperty "babashka.version")
    (->> (concat [0 0xAD 0x3000 0xD800 0xE000 0x10FFFF 0x3FFFD]
                 (map #(mod (* % 104729) code-point-count) (range 65536)))
         distinct)
    (range code-point-count)))

(defn- production-result [cp]
  (try
    (width/code-point-width cp {})
    (catch clojure.lang.ExceptionInfo _ :surrogate)))

(defn- singleton-cluster-result [cp]
  (try
    (width/cluster-width (String. (Character/toChars cp)) {})
    (catch clojure.lang.ExceptionInfo _ :surrogate)))

(defn- raw-singleton-cluster-width [cp]
  (let [scalar-width (raw-code-point-width cp 1)
        category (aget ^bytes @raw-categories cp)]
    (if (= :surrogate scalar-width)
      :surrogate
      (if (zero? scalar-width)
        (if (and (or (= category 1) (= category 2))
                 (not= 1 (aget ^bytes @raw-default-ignorable cp)))
          1
          0)
        scalar-width))))

(deftest code-point-width-matches-raw-ucd-restatement
  (let [{:keys [count samples]}
        (reduce (fn [{:keys [count samples]} cp]
                  (let [expected (raw-code-point-width cp 1)
                        actual (production-result cp)]
                    (if (= expected actual)
                      {:count count :samples samples}
                      {:count (inc count)
                       :samples (cond-> samples
                                  (< (clojure.core/count samples) 20)
                                  (conj {:code-point cp
                                         :expected expected
                                         :actual actual}))})))
                {:count 0 :samples []}
                exhaustive-code-points)]
    (is (zero? count)
        (str count " Unicode width mismatches; first: " (pr-str samples)))))

(deftest singleton-cluster-width-matches-amended-raw-ucd-restatement
  (let [{:keys [count samples]}
        (reduce (fn [{:keys [count samples]} cp]
                  (let [expected (raw-singleton-cluster-width cp)
                        actual (singleton-cluster-result cp)]
                    (if (= expected actual)
                      {:count count :samples samples}
                      {:count (inc count)
                       :samples (cond-> samples
                                  (< (clojure.core/count samples) 20)
                                  (conj {:code-point cp
                                         :expected expected
                                         :actual actual}))})))
                {:count 0 :samples []}
                exhaustive-code-points)]
    (is (zero? count)
        (str count " singleton cluster-width mismatches; first: "
             (pr-str samples)))))

(deftest code-point-examples-and-policy-edges
  (testing "review probes and pinned-version differences"
    (is (= [2 2 2 2 2 1 1]
           (mapv #(width/code-point-width % {})
                 [0x3000 0x3001 0x3131 0x3220 0x338F 0x1F170 0x1F321])))
    (is (= [0 0 0]
           (mapv #(width/code-point-width % {}) [0x00AD 0x2060 0xFEFF])))
    (is (= 1 (width/code-point-width 0x1F1E6 {})))
    (is (= [2 2 2 2]
           (mapv #(width/code-point-width % {})
                 [0x2630 0x2637 0x268A 0x268F]))))
  (testing "controls, separators, marks and Hangul continuations"
    (is (= [0 0 0 0 0 0 1 2 0 0 0]
           (mapv #(width/code-point-width % {})
                 [0x00 0x09 0x0A 0x80 0x2028 0x2029 0x0903 0x16FF0
                  0x13430 0x1161 0x11A8]))))
  (testing "surrogates fail; unassigned, noncharacters and private use use UCD defaults"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"surrogate"
                          (width/code-point-width 0xD800 {})))
    (is (= [1 2 1 1]
           (mapv #(width/code-point-width % {})
                 [0x0378 0x3FFFD 0x10FFFF 0xE000])))
    (is (= 2 (width/code-point-width 0xE000 {:ambiguous-width 2})))
    (doseq [cp [-1 0x110000]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unicode code point"
                            (width/code-point-width cp {}))))))

(deftest ambiguous-width-is-an-explicit-option
  (is (= 1 (width/code-point-width 0x2605 {})))
  (is (= 2 (width/code-point-width 0x2605 {:ambiguous-width 2})))
  (is (= 2 (width/cluster-width "★" {:ambiguous-width 2})))
  (doseq [options [{:ambiguous-width 0} {:ambiguous-width 3}
                   {:unknown true} nil]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"options"
                          (width/code-point-width 0x2605 options)))))

(deftest cluster-width-examples
  (is (= 2 (width/cluster-width "❤️" {})))
  (is (= 1 (width/cluster-width "❤︎" {})))
  (is (= 2 (width/cluster-width "🇩🇪" {})))
  (is (= 2 (width/cluster-width "👍🏽" {})))
  (is (= 2 (width/cluster-width "👨‍👩‍👧‍👦" {})))
  (is (= 2 (width/cluster-width "1️⃣" {})))
  (is (= 1 (width/cluster-width "é" {})))
  (is (= 2 (width/cluster-width "각" {})))
  (is (= 1 (width/cluster-width "́" {})))
  (is (= 1 (width/cluster-width "🇦" {})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"non-empty"
                        (width/cluster-width "" {}))))

(deftest all-zero-clusters-distinguish-marks-from-invisible-content
  (testing "invisible and structural singleton clusters do not advance"
    (is (= [0 0 0 0 0 0 0]
           (mapv #(width/cluster-width % {})
                 ["\u200B" "\n" "\t" "\uFEFF" "\u00AD" "\u200D" "\uFE0F"]))))
  (testing "an isolated nonspacing mark retains the visible fallback"
    (is (= 1 (width/cluster-width "\u0301" {})))))
