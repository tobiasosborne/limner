(ns limner.unicode.emoji-test
  "Emoji 17 sequence conformance for ADR 0001 and UAX #29 revision 47.

  Every record in the pinned `emoji-test.txt` is checked. Fully-qualified,
  minimally-qualified, and component entries must be one cluster of width two.
  Unqualified entries remain one cluster but follow their text-presentation
  properties, yielding the exact corpus distribution recorded in ADR 0001."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [limner.text :as text]
            [limner.unicode.grapheme :as grapheme]))

(def emoji-test-path
  "reference/unicode/17.0.0/emoji/17.0/emoji-test.txt")

(defn- code-point-string [field]
  (apply str
         (map #(String. (Character/toChars (Long/parseLong % 16)))
              (str/split (str/trim field) #" +"))))

(defn- parse-case [line-number line]
  (when (re-find #"^[0-9A-F]" line)
    (let [[code-points tail] (str/split line #";" 2)
          status (keyword (first (str/split (str/trim tail) #" +")))]
      {:line line-number :status status :text (code-point-string code-points)})))

(def emoji-cases
  (->> (str/split-lines (slurp emoji-test-path))
       (keep-indexed #(parse-case (inc %1) %2))
       vec))

(def expected
  {:fully-qualified {:count 3944 :widths {2 3944}}
   :minimally-qualified {:count 1029 :widths {2 1029}}
   :component {:count 9 :widths {2 9}}
   :unqualified {:count 243 :widths {1 213, 2 30}}})

(defn- summary [cases]
  {:count (count cases)
   :cluster-mismatches
   (vec (keep (fn [{:keys [line text]}]
                (when-not (= 1 (count (grapheme/clusters text))) line))
              cases))
   :widths (frequencies (map #(text/width (:text %)) cases))})

(deftest every-emoji-17-sequence-follows-the-status-policy
  (doseq [[status expectation] expected]
    (testing (name status)
      (let [actual (summary (filter #(= status (:status %)) emoji-cases))]
        (is (= (assoc expectation :cluster-mismatches []) actual)
            (str status " conformance mismatch: "
                 (pr-str (update actual :cluster-mismatches #(take 20 %)))))))))
