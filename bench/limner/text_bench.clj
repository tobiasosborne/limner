(ns limner.text-bench
  "Fixed-corpus measurements and regression budgets for `limner.text`.

  This namespace is deliberately outside `test/`. It measures warmed calls in
  five independent rounds and reports the median plus min/max spread. The
  legacy comparator is a private copy of the removed `visible-width` algorithm
  so the benchmark does not keep obsolete production code alive."
  (:require [limner.ansi :as ansi]
            [limner.text :as text]
            [limner.unicode.grapheme :as grapheme])
  (:import [java.text BreakIterator]))

(def ^:private bmp-emoji-presentation
  (set (mapcat (fn [[low high]] (range low (inc high)))
               [[0x231A 0x231B] [0x23E9 0x23EC] [0x23F0 0x23F0] [0x23F3 0x23F3]
                [0x25FD 0x25FE] [0x2614 0x2615] [0x2648 0x2653] [0x267F 0x267F]
                [0x2693 0x2693] [0x26A1 0x26A1] [0x26AA 0x26AB] [0x26BD 0x26BE]
                [0x26C4 0x26C5] [0x26CE 0x26CE] [0x26D4 0x26D4] [0x26EA 0x26EA]
                [0x26F2 0x26F3] [0x26F5 0x26F5] [0x26FA 0x26FA] [0x26FD 0x26FD]
                [0x2705 0x2705] [0x270A 0x270B] [0x2728 0x2728] [0x274C 0x274C]
                [0x274E 0x274E] [0x2753 0x2755] [0x2757 0x2757] [0x2795 0x2797]
                [0x27B0 0x27B0] [0x27BF 0x27BF] [0x2B1B 0x2B1C] [0x2B50 0x2B50]
                [0x2B55 0x2B55]])))

(defn- legacy-char-width [code-point]
  (let [code-point (int code-point), category (Character/getType code-point)]
    (cond
      (or (< code-point 0x20) (<= 0x7F code-point 0x9F)) 0
      (#{0x200B 0x200C 0x200D} code-point) 0
      (or (= category Character/NON_SPACING_MARK)
          (= category Character/ENCLOSING_MARK)
          (= category Character/COMBINING_SPACING_MARK)) 0
      (<= 0x1160 code-point 0x11FF) 0
      (or (<= 0xFE00 code-point 0xFE0F) (<= 0xE0100 code-point 0xE01EF)) 0
      (<= 0x1100 code-point 0x115F) 2
      (or (<= 0x4E00 code-point 0x9FFF) (<= 0x3400 code-point 0x4DBF)
          (<= 0x20000 code-point 0x2A6DF) (<= 0x2A700 code-point 0x2B73F)
          (<= 0x2B740 code-point 0x2B81F) (<= 0x2B820 code-point 0x2CEAF)
          (<= 0x2CEB0 code-point 0x2EBEF) (<= 0x30000 code-point 0x3134F)
          (<= 0xAC00 code-point 0xD7A3) (<= 0xF900 code-point 0xFAFF)
          (<= 0x2F800 code-point 0x2FA1F) (<= 0xFF01 code-point 0xFF60)
          (<= 0xFFE0 code-point 0xFFE6) (<= 0x3040 code-point 0x309F)
          (<= 0x30A0 code-point 0x30FF) (bmp-emoji-presentation code-point)
          (<= 0x1F300 code-point 0x1F9FF) (<= 0x1FA70 code-point 0x1FAFF)
          (<= 0x1F000 code-point 0x1F02F) (<= 0x1F0A0 code-point 0x1F0FF)
          (<= 0x1F100 code-point 0x1F2FF)) 2
      :else 1)))

(defn- legacy-clusters [^String value]
  (let [^BreakIterator iterator (doto (BreakIterator/getCharacterInstance)
                                  (.setText value))]
    (loop [start (.first iterator), result (transient [])]
      (let [end (.next iterator)]
        (if (= BreakIterator/DONE end)
          (persistent! result)
          (recur end (conj! result (subs value start end))))))))

(defn- legacy-cluster-width [^String cluster]
  (let [base (legacy-char-width (.codePointAt cluster 0))]
    (cond
      (not= -1 (.indexOf cluster "\uFE0F")) 2
      (pos? base) base
      :else (reduce max 0 (map legacy-char-width (.toArray (.codePoints cluster)))))))

(defn legacy-visible-width [value]
  (let [^String clean (ansi/strip value)]
    (if (every? #(<= 0x20 (int %) 0x7E) clean)
      (.length clean)
      (reduce + 0 (map legacy-cluster-width (legacy-clusters clean))))))

(def ascii-line (apply str (take 80 (cycle "The quick brown fox 0123456789 "))))
(def latin-marks (apply str (repeat 8 "Café naïve Ångström. ")))
(def cjk-line (apply str (repeat 40 "中")))
(def emoji-zwj (apply str (repeat 20 "👩‍🔬🇩🇪")))
(def devanagari (apply str (repeat 24 "क्ष त्र ज्ञ श्र ")))
(def mixed-line (apply str (repeat 10 "中A👩‍🔬é🇩🇪")))
(def mixed-10k
  (let [chunk (str mixed-line "\n"), chunk-bytes (alength (.getBytes ^String chunk "UTF-8"))
        repetitions (quot 10240 chunk-bytes), prefix (apply str (repeat repetitions chunk))
        remaining (- 10240 (* repetitions chunk-bytes))]
    (str prefix (apply str (repeat remaining "a")))))
(def many-spans
  (apply text/concat
         (map-indexed (fn [index value]
                        (text/styled value (case (mod index 4)
                                             0 {:fg :red}
                                             1 {:bold true}
                                             2 {:bg {:index 17}}
                                             {:underline :double})))
                      (take 160 (cycle ["A" "中" "é" "👩‍🔬" "🇩🇪"])))))

(def corpora
  [{:name :ascii :value ascii-line}
   {:name :latin-marks :value latin-marks}
   {:name :cjk :value cjk-line}
   {:name :emoji-zwj :value emoji-zwj}
   {:name :devanagari :value devanagari}
   {:name :many-spans :value many-spans}
   {:name :mixed-10kb :value mixed-10k}])

(defn- plain [value]
  (if (string? value) value (apply str (keep :text value))))

(defn- ansi-value [value]
  (if (string? value)
    (str "\u001B[1;31m" value "\u001B[0m")
    (text/to-ansi value)))

(def operations
  [{:name :width :function text/width}
   {:name :clusters :function #(grapheme/clusters (plain %))}
   {:name :from-ansi :function #(text/from-ansi (ansi-value %))}
   {:name :to-ansi :function #(text/to-ansi (if (string? %) (text/styled % {:fg :red}) %))}
   {:name :truncate :function #(text/truncate % 40)}
   {:name :wrap :function #(text/wrap % 40)}])

(defn- median [values]
  (nth (vec (sort values)) (quot (count values) 2)))

(defn- elapsed [function value iterations]
  (let [sink (volatile! nil), start (System/nanoTime)]
    (dotimes [_ iterations]
      (vreset! sink (function value)))
    (when (identical? sink @sink) (println "unreachable"))
    (/ (- (System/nanoTime) start) 1.0e9)))

(defn- iterations [value short?]
  (let [length (.length ^String (plain value)), babashka? (System/getProperty "babashka.version")]
    (if short?
      (if babashka? 80 800)
      (cond
        (> length 5000) (if babashka? 1 10)
        (> length 500) (if babashka? 12 120)
        :else (if babashka? 60 600)))))

(defn measure [function value short?]
  (let [length (.length ^String (plain value))
        iterations (iterations value short?)
        warmups (if short? 10 (cond (> length 5000) 5, (> length 500) 30, :else 100))]
    (dotimes [_ warmups] (function value))
    (let [seconds (mapv (fn [_] (elapsed function value iterations)) (range 5))
          rates (mapv #(/ iterations %) seconds)]
      {:median (median rates) :min (apply min rates) :max (apply max rates)
       :iterations iterations})))

(defn- runtime-name []
  (if (System/getProperty "babashka.version") "babashka" "JVM"))

(defn- print-suite []
  (println (str "runtime=" (runtime-name) " clojure=" (clojure-version)
                " java=" (System/getProperty "java.version")
                (when-let [version (System/getProperty "babashka.version")]
                  (str " babashka=" version)) " rounds=5"))
  (println "| Corpus | Operation | UTF-16 units | Median calls/s | Spread calls/s | MiB/s |")
  (println "| --- | --- | ---: | ---: | ---: | ---: |")
  (doseq [{:keys [name value]} corpora
          {operation :name function :function} operations]
    (let [{:keys [median min max]} (measure function value false)
          plain-value (plain value)
          length (.length ^String plain-value)
          byte-count (alength (.getBytes ^String plain-value "UTF-8"))]
      (println (format "| %s | %s | %d | %.1f | %.1f–%.1f | %.3f |"
                       (clojure.core/name name) (clojure.core/name operation) length
                       median min max (/ (* median byte-count) 1048576.0))))))

(defn- ratio [new-result old-result]
  (/ (:median old-result) (:median new-result)))

(defn- budget-results [short?]
  (let [ascii-new (measure text/width ascii-line short?)
        ascii-old (measure legacy-visible-width ascii-line short?)
        cjk-new (measure text/width cjk-line short?)
        cjk-old (measure legacy-visible-width cjk-line short?)
        mixed-new (measure text/width mixed-line short?)
        mixed-old (measure legacy-visible-width mixed-line short?)]
    {:ascii-slowdown (ratio ascii-new ascii-old)
     :cjk-speedup (/ (:median cjk-new) (:median cjk-old))
     :mixed-slowdown (ratio mixed-new mixed-old)
     :measurements {:ascii-new ascii-new :ascii-legacy ascii-old
                    :cjk-new cjk-new :cjk-legacy cjk-old
                    :mixed-new mixed-new :mixed-legacy mixed-old}}))

(defn- check-budgets []
  (let [results (budget-results true)
        babashka? (boolean (System/getProperty "babashka.version"))
        limits {:ascii-slowdown 1.50
                :cjk-speedup (if babashka? 8.0 0.0)
                :mixed-slowdown (if babashka? 2.50 3.50)}
        failures (cond-> []
                   (> (:ascii-slowdown results) (:ascii-slowdown limits))
                   (conj :ascii-slowdown)
                   (< (:cjk-speedup results) (:cjk-speedup limits))
                   (conj :cjk-speedup)
                   (> (:mixed-slowdown results) (:mixed-slowdown limits))
                   (conj :mixed-slowdown))]
    (println (runtime-name) (pr-str (dissoc results :measurements)))
    (when (seq failures)
      (throw (ex-info "Text performance budget exceeded"
                      {:runtime (runtime-name) :failures failures
                       :limits limits :results results})))))

(defn -main [& arguments]
  (if (= ["--check"] (vec arguments))
    (check-budgets)
    (do (print-suite)
        (println "budgets" (pr-str (budget-results false))))))
