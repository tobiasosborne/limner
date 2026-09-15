;; Compare three width sources over U+2600..U+27BF:
;;   1. UCD EastAsianWidth.txt (W/F => 2, everything else => 1), pinned version given on the command line
;;   2. glibc wcwidth as posted by pmonks on limner issue #2 (2026-09-14), saved as glibc-wcwidth-U2600-U27BF.tsv
;;   3. limner.core/visible-width at the current commit
;;
;; Usage (repo root):
;;   curl -sSfLO https://www.unicode.org/Public/17.0.0/ucd/EastAsianWidth.txt
;;   clojure -M reviews/2026-09-15/evidence/width-compare.clj EastAsianWidth.txt
(require '[clojure.string :as str] '[limner.core :as core])

(defn parse-eaw [path]
  (into {}
        (for [line (str/split-lines (slurp path))
              :let [data (str/trim (or (first (str/split line #"#")) ""))]
              :when (seq data)
              :let [[rng cls] (map str/trim (str/split data #";"))
                    [lo hi] (str/split rng #"\.\.")
                    lo (Integer/parseInt lo 16)
                    hi (if hi (Integer/parseInt hi 16) lo)]
              cp (range lo (inc hi))]
          [cp cls])))

(let [eaw (parse-eaw (or (first *command-line-args*) "EastAsianWidth.txt"))
      glibc (into (sorted-map)
                  (for [line (str/split-lines (slurp "reviews/2026-09-15/evidence/glibc-wcwidth-U2600-U27BF.tsv"))
                        :let [[cp w] (str/split line #" ")]]
                    [(Integer/parseInt cp 16) (Integer/parseInt w)]))
      ucd-width (fn [cp] (if (#{"W" "F"} (eaw cp)) 2 1))
      limner-width (fn [cp] (core/visible-width (String. (Character/toChars (int cp)))))
      classes (frequencies (map #(eaw % "N") (range 0x2600 0x27C0)))
      report (fn [label pairs]
               (println label (count pairs))
               (doseq [[cp a b] pairs] (println (format "  U+%04X  %d vs %d" cp a b))))]
  (println "EAW classes in U+2600..27BF:" classes)
  (report "glibc vs UCD disagreements:"
          (for [[cp w] glibc :when (not= w (ucd-width cp))] [cp w (ucd-width cp)]))
  (report "limner vs glibc disagreements:"
          (for [[cp w] glibc :when (not= w (limner-width cp))] [cp (limner-width cp) w]))
  (report "limner vs UCD disagreements:"
          (for [cp (range 0x2600 0x27C0) :when (not= (limner-width cp) (ucd-width cp))]
            [cp (limner-width cp) (ucd-width cp)])))
