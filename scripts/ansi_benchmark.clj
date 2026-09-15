(ns ansi-benchmark
  "Fixed-corpus throughput measurements for the ECMA-48 tokenizer."
  (:require [clojure.string :as str]
            [limner.ansi :as ansi]))

(def sgr-pattern #"\u001B\[[0-9;]*m")

(def corpora
  [[:plain (apply str (repeat 120 "The quick brown fox jumps over 13 lazy dogs.\n"))]
   [:sgr (apply str (repeat 160 "\u001B[1;31mred bold\u001B[0m plain "))]
   [:mixed (apply str
                  (repeat 100
                          "text \u001B[2K\u001B[38:2::1:2:3mcolor\u001B[0m \u001B]8;;https://x\u001B\\link\u001B]8;;\u001B\\\n"))]])

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

(defn- megabytes-per-second [f text iterations]
  (let [seconds (median (repeatedly 3 #(elapsed-seconds f text iterations)))]
    (/ (* iterations (.length ^String text)) seconds 1000000.0)))

(defn- legacy-strip [text]
  (str/replace text sgr-pattern ""))

(defn -main [& _]
  (let [babashka? (boolean (System/getProperty "babashka.version"))
        iterations (if babashka? 100 1000)]
    (println (str "runtime=" (if babashka? "babashka" "JVM")
                  " clojure=" (clojure-version)
                  " java=" (System/getProperty "java.version")
                  (when babashka?
                    (str " babashka=" (System/getProperty "babashka.version")))
                  " iterations=" iterations " median-rounds=3"))
    (doseq [[name text] corpora]
      (println
       (format "%s utf16=%d tokenize=%.3f MB/s strip=%.3f MB/s legacy-sgr-strip=%.3f MB/s"
               (clojure.core/name name)
               (.length ^String text)
               (megabytes-per-second ansi/tokenize text iterations)
               (megabytes-per-second ansi/strip text iterations)
               (megabytes-per-second legacy-strip text iterations))))))
