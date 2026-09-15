(ns limner.unicode.grapheme-test
  "Unicode 17 extended-grapheme conformance and invariant tests.

  Expected boundaries come directly from the vendored
  `GraphemeBreakTest.txt`, never from a host segmenter. Property tests include
  arbitrary UTF-16, because the public API also defines behavior for unpaired
  surrogates outside UAX #29's sequence-of-characters domain."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [limner.unicode.grapheme :as grapheme]))

(def conformance-path
  "reference/unicode/17.0.0/ucd/auxiliary/GraphemeBreakTest.txt")

(defn- code-point-string [code-point]
  (String. (Character/toChars code-point)))

(defn- parse-conformance-case [case-number line-number line]
  (let [tokens (str/split (str/trim (first (str/split line #"#" 2))) #"\s+")]
    (loop [remaining tokens
           text (StringBuilder.)
           offset 0
           boundaries []]
      (let [[marker code-point & more] remaining
            boundaries (cond-> boundaries (= "÷" marker) (conj offset))]
        (if code-point
          (let [value (Long/parseLong code-point 16)
                chars (code-point-string value)]
            (.append text chars)
            (recur more text (+ offset (.length ^String chars)) boundaries))
          {:case-number case-number
           :line-number line-number
           :line line
           :text (str text)
           :boundaries boundaries})))))

(def conformance-cases
  (->> (str/split-lines (slurp conformance-path))
       (keep-indexed (fn [index line]
                       (when (str/starts-with? (str/trim line) "÷")
                         [index line])))
       (map-indexed (fn [index [line-index line]]
                      (parse-conformance-case (inc index) (inc line-index) line)))
       vec))

(defn- cluster-hex [cluster]
  (->> (.toArray (.codePoints ^String cluster))
       (map #(format "U+%04X" %))
       (str/join " ")))

(defn- clusters-hex [value]
  (if (and (vector? value) (every? string? value))
    (mapv cluster-hex value)
    value))

(defn- clusters-from-boundaries [^String text boundaries]
  (mapv (fn [[start end]] (subs text start end))
        (partition 2 1 boundaries)))

(deftest grapheme-break-test-17-conformance
  (is (= 766 (count conformance-cases))
      "The pinned Unicode 17 conformance corpus changed unexpectedly")
  (doseq [{:keys [case-number line-number line text boundaries]}
          conformance-cases]
    (let [expected (clusters-from-boundaries text boundaries)
          actual (grapheme/clusters text)]
      (is (= expected actual)
          (str "GraphemeBreakTest case " case-number " (source line "
               line-number ") failed:\n" line "\nexpected "
               (pr-str (clusters-hex expected)) "\nactual   "
               (pr-str (clusters-hex actual)))))))

(deftest public-api-examples-and-utf16-boundaries
  (testing "ordered UAX #29 rules and UTF-16 offsets"
    (is (= ["a" "é" "\r\n" "👩‍🔬" "🇩🇪" "🇫"]
           (grapheme/clusters "aé\r\n👩‍🔬🇩🇪🇫")))
    (is (= [0 1 3 4]
           (grapheme/cluster-boundaries "a😀b"))))
  (testing "the allocation-free reducer exposes source ranges"
    (is (= [[0 1] [1 3] [3 4]]
           (grapheme/reduce-clusters (fn [ranges _text start end]
                                       (conj ranges [start end]))
                                     []
                                     "a😀b")))))

(deftest cjk-and-precomposed-hangul-fast-path-matches-general-scanner
  (let [reduce-general (ns-resolve 'limner.unicode.grapheme 'reduce-general)
        segment (fn [value]
                  (persistent!
                   (reduce-general value
                                   (fn [parts ^String source start end]
                                     (conj! parts (subs source start end)))
                                   (transient []))))]
    (doseq [value [(apply str (repeat 80 "中"))
                   (apply str (repeat 80 "한"))
                   "中文한글漢字"]]
      (is (= (segment value) (grapheme/clusters value))))))

(deftest unpaired-surrogates-are-isolated
  (let [high (String. (char-array [(char 0xD800)]))
        low (String. (char-array [(char 0xDC00)]))]
    (is (= ["a" high "́" low "b"]
           (grapheme/clusters (str "a" high "́" low "b"))))
    (is (= ["😀"] (grapheme/clusters "😀"))
        "A well-formed surrogate pair stays one code point")))

(def special-units
  ["\r" "\n" "́" "्" "क" "\u200D" "©" "😀" "🇩" "🇪"
   (String. (char-array [(char 0xD800)]))
   (String. (char-array [(char 0xDC00)]))])

(def unicode-unit-gen
  (gen/frequency
   [[7 (gen/fmap code-point-string (gen/choose 0 0x10FFFF))]
    [3 (gen/elements special-units)]]))

(def unicode-string-gen
  (gen/fmap #(apply str %) (gen/vector unicode-unit-gen 0 80)))

(def valid-code-point-gen
  (gen/one-of [(gen/choose 0 0xD7FF) (gen/choose 0xE000 0x10FFFF)]))

(def valid-string-gen
  (gen/fmap #(apply str %)
            (gen/vector
             (gen/frequency
              [[7 (gen/fmap code-point-string valid-code-point-gen)]
               [3 (gen/elements (subvec special-units 0 10))]])
             0 80)))

(defn- strictly-increasing? [values]
  (every? (fn [[left right]] (< left right)) (partition 2 1 values)))

(defn- splits-surrogate-pair? [^String text boundary]
  (and (pos? boundary)
       (< boundary (.length text))
       (Character/isHighSurrogate (.charAt text (dec boundary)))
       (Character/isLowSurrogate (.charAt text boundary))))

(deftest grapheme-properties
  (let [result
        (tc/quick-check
         500
         (prop/for-all [text unicode-string-gen]
           (let [parts (grapheme/clusters text)
                 cuts (grapheme/cluster-boundaries text)]
             (and (= text (apply str parts))
                  (every? seq parts)
                  (strictly-increasing? cuts)
                  (= 0 (first cuts))
                  (= (.length ^String text) (peek cuts))
                  (not-any? #(splits-surrogate-pair? text %) cuts))))
         :seed 290017)]
    (is (:pass? result) (pr-str (dissoc result :result-data)))))

(defn- resolve-icu [name]
  (when-not (System/getProperty "babashka.version")
    (try
      (requiring-resolve (symbol "limner.icu-grapheme-oracle" name))
      (catch Throwable _ nil))))

(deftest optional-icu4j-cross-check
  (let [icu-boundaries (resolve-icu "boundaries")
        icu-version (resolve-icu "version")]
    (if (and icu-boundaries icu-version)
      (let [conformance-mismatches
            (vec (keep (fn [{:keys [case-number text boundaries]}]
                         (when-not (= boundaries (icu-boundaries text))
                           case-number))
                       conformance-cases))
            generated-corpus (mapv #(gen/generate valid-string-gen 80 %)
                                   (range 500))
            generated-mismatches
            (vec (keep-indexed (fn [index text]
                                 (when-not (= (grapheme/cluster-boundaries text)
                                              (icu-boundaries text))
                                   index))
                               generated-corpus))]
        (println
         (if (and (empty? conformance-mismatches)
                  (empty? generated-mismatches))
           (str "ICU4J " (icu-version)
                " grapheme oracle: 766 conformance cases and 500 generated "
                "strings agree.")
           (str "ICU4J " (icu-version) " grapheme oracle found mismatches.")))
        (is (empty? conformance-mismatches)
            (str "ICU4J conformance mismatches: "
                 (pr-str (take 20 conformance-mismatches))))
        (is (empty? generated-mismatches)
            (str "ICU4J generated-corpus mismatches: "
                 (pr-str (take 20 generated-mismatches)))))
      (do
        (println "[SKIP] ICU4J grapheme oracle (run clojure -M:test:icu).")
        (is true "ICU4J is an explicit optional test capability")))))
