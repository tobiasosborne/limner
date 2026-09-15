(import '[java.text BreakIterator])
(defn segs [s] (let [bi (BreakIterator/getCharacterInstance)] (.setText bi s)
  (loop [st (.first bi) en (.next bi) acc []] (if (= en BreakIterator/DONE) acc (recur en (.next bi) (conj acc (subs s st en)))))))
(let [lines (->> (clojure.string/split-lines (slurp (or (first *command-line-args*) "GraphemeBreakTest.txt"))) (remove #(or (clojure.string/blank? %) (.startsWith % "#"))))
      res (for [l lines :let [spec (clojure.string/trim (first (clojure.string/split l #"#")))
                              toks (clojure.string/split spec #"\s+")
                              ;; toks alternate ÷/× and hex
                              expected (loop [ts (rest toks) cur "" acc []]
                                         (if (empty? ts) acc
                                           (let [cp (Integer/parseInt (first ts) 16) s (str cur (String. (Character/toChars cp)))
                                                 brk (second ts)]
                                             (if (= brk "÷") (recur (drop 2 ts) "" (conj acc s)) (recur (drop 2 ts) s acc)))))
                              input (apply str expected)]]
            (= expected (segs input)))]
  (doseq [[l ok] (map vector lines res) :when (not ok)] (println "FAIL:" (second (clojure.string/split l #"#" 2)))) (println "JDK" (System/getProperty "java.version") "GraphemeBreakTest-17.0.0 pass:" (count (filter true? res)) "/" (count res)))
