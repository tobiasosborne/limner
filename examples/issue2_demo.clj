;; Demo for issue #2: grapheme-cluster width, column padding and style
;; composition. Run from the repo root on either runtime:
;;   bb examples/issue2_demo.clj
;;   clojure -M examples/issue2_demo.clj
(require '[limner.core :as core]
         '[limner.text :as text]
         '[clojure.string :as str])

(defn cps [& code-points]
  (let [sb (StringBuilder.)]
    (doseq [cp code-points] (.appendCodePoint sb (int cp)))
    (str sb)))

(def samples
  [[(cps 0x1F468 0x200D 0x1F469 0x200D 0x1F467 0x200D 0x1F466) "family ZWJ sequence (7 code points)"]
   [(cps 0x1F1E9 0x1F1EA)   "flag DE, regional-indicator pair"]
   [(cps 0x1F44D 0x1F3FD)   "thumbs up + skin-tone modifier"]
   [(cps 0x2764 0xFE0F)     "heart + VS16 (emoji presentation)"]
   [(cps 0x2764)            "heart alone, U+2764 (text presentation)"]
   [(cps 0x31 0xFE0F 0x20E3) "keycap sequence 1"]
   [(cps 0x1F170)           "U+1F170 A-button, EAW=A, no VS16"]
   [(cps 0x2630)            "U+2630 Yijing trigram, W since Unicode 16"]
   [(cps 0x2713)            "U+2713 check mark, N"]
   [(cps 0x2614)            "U+2614 umbrella, W"]
   [(cps 0x65E5 0x672C 0x8A9E) "CJK, three wide clusters"]
   [(cps 0x61 0xAD 0x62)    "soft hyphen U+00AD inside ab"]
   [(str (cps 0x1B) "[2Khi") "CSI erase-line + hi"]])

(println "Unicode" ((requiring-resolve 'limner.unicode/version)))
(println)
(printf "%-4s %-6s %-6s  %s%n" "cps" "count" "width" "sample")
(doseq [[s label] samples]
  (printf "%-4d %-6d %-6d  %s%n"
          (.codePointCount ^String s 0 (count s)) (count s) (core/visible-width s) label))

(println "\nRight-padding to 12 columns with limner.text (the pipes should line up):")
(doseq [[s] (take 11 samples)]
  (println (str "|" (text/to-ansi (text/pad-right s 12)) "|")))

(println "\nThe old way, format \"%-12s\" (pads in UTF-16 units):")
(doseq [[s] (take 11 samples)]
  (println (str "|" (format "%-12s" s) "|")))

(println "\nNested styles: the outer bold must survive the inner red span.")
(let [s (core/color :bold (str (core/color :red "R") " still bold"))]
  (println s)
  (println (pr-str s)))

(println "\nTruncating five CJK clusters to 5 columns never splits a wide cluster:")
(println (str "|" (text/to-ansi (text/truncate (cps 0x65E5 0x672C 0x8A9E 0x30C6 0x30AD) 5)) "|"))
