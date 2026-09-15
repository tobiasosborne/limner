(ns grapheme-benchmark
  "Fixed-corpus comparison for the portable and legacy grapheme segmenters."
  (:require [limner.unicode.grapheme :as grapheme])
  (:import [java.text BreakIterator]))

(def corpora
  [[:ascii (apply str (repeat 40 "The quick brown fox jumps over 13 lazy dogs. "))]
   [:latin-marks (apply str (repeat 80 "Café naïve Ångström. "))]
   [:cjk (apply str (repeat 120 "你好世界日本語한국어"))]
   [:emoji-zwj (apply str (repeat 100 "👨‍👩‍👧‍👦 👩‍🔬 🏳️‍🌈 "))]
   [:devanagari (apply str (repeat 160 "क्ष त्र ज्ञ श्र "))]])

(defn- legacy-clusters [^String text]
  (let [^BreakIterator iterator (doto (BreakIterator/getCharacterInstance)
                                  (.setText text))]
    (loop [start (.first iterator), result (transient [])]
      (let [end (.next iterator)]
        (if (= BreakIterator/DONE end)
          (persistent! result)
          (recur end (conj! result (subs text start end))))))))

(defn- elapsed-seconds [f text iterations]
  (dotimes [_ 100] (f text))
  (let [sink (volatile! 0)
        start (System/nanoTime)]
    (dotimes [_ iterations]
      (vswap! sink + (count (f text))))
    (when (neg? @sink)
      (throw (ex-info "Unreachable benchmark sink" {:sink @sink})))
    (/ (- (System/nanoTime) start) 1.0e9)))

(defn- median [values]
  (nth (vec (sort values)) (quot (count values) 2)))

(defn- measure [f text iterations clusters]
  (let [seconds (median (repeatedly 3 #(elapsed-seconds f text iterations)))]
    (long (/ (* iterations clusters) seconds))))

(defn -main [& _]
  (let [babashka? (boolean (System/getProperty "babashka.version"))
        iterations (if babashka? 300 3000)]
    (println (str "runtime=" (if babashka? "babashka" "JVM")
                  " clojure=" (clojure-version)
                  " java=" (System/getProperty "java.version")
                  (when babashka?
                    (str " babashka=" (System/getProperty "babashka.version")))
                  " iterations=" iterations " median-rounds=3"))
    (doseq [[name text] corpora]
      (let [cluster-count (count (grapheme/clusters text))
            portable-rate (measure grapheme/clusters text iterations cluster-count)
            legacy-rate (measure legacy-clusters text iterations cluster-count)]
        (println (format "%s clusters=%d portable=%d clusters/s legacy=%d clusters/s ratio=%.3f"
                         (clojure.core/name name) cluster-count portable-rate legacy-rate
                         (double (/ portable-rate legacy-rate))))))))
