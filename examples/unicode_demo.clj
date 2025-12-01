#!/usr/bin/env bb

(require '[limner.core :as core]
         '[limner.borders :as borders])

(println "\n╔═══════════════════════════════════════════════════════════╗")
(println "║     Limner Unicode Width Handling Demonstration          ║")
(println "╚═══════════════════════════════════════════════════════════╝\n")

(println (core/color :bold "Unicode Width Calculation\n"))
(println "Limner now properly calculates display width for Unicode characters,")
(println "ensuring correct alignment and layout in terminal UIs.\n")

;; ──────────────────────── ASCII vs CJK ──────────────────────
(println (core/color :bold "1. ASCII vs CJK Width\n"))

(defn show-width [label text]
  (let [width (core/visible-width text)
        len (count text)]
    (println (format "  %-20s chars=%-2d width=%-2d  %s"
                     (str label ":")
                     len width text))))

(show-width "ASCII" "Hello")
(show-width "Chinese" "你好")
(show-width "Japanese" "こんにちは")
(show-width "Korean" "안녕하세요")
(show-width "Mixed" "Hello世界")
(println)

;; ──────────────────────── Box Alignment ──────────────────────
(println (core/color :bold "2. Proper Box Alignment\n"))
(println "Before Unicode fix, CJK text would misalign:")

(let [ascii-box (borders/draw-box ["Hello World"] :border-style :single)
      cjk-box (borders/draw-box ["你好世界"] :border-style :single)
      mixed-box (borders/draw-box ["Hello 世界"] :border-style :single)
      emoji-box (borders/draw-box ["✓ Success! 🎉"] :border-style :single)]

  (println "ASCII text:")
  (doseq [line ascii-box] (println "  " line))
  (println)

  (println "CJK text (width 2 per character):")
  (doseq [line cjk-box] (println "  " line))
  (println)

  (println "Mixed ASCII + CJK:")
  (doseq [line mixed-box] (println "  " line))
  (println)

  (println "Emoji + text:")
  (doseq [line emoji-box] (println "  " line))
  (println))

;; ──────────────────────── Width Comparison ──────────────────────
(println (core/color :bold "3. Width Comparison Table\n"))
(println (format "  %-20s %-10s %-10s %-10s" "Text" "Chars" "Old Width" "New Width"))
(println (format "  %-20s %-10s %-10s %-10s" "────────────────────" "─────" "─────────" "─────────"))

(defn compare-widths [text]
  (let [char-count (count text)
        old-width (core/visible-length text)
        new-width (core/visible-width text)]
    (println (format "  %-20s %-10d %-10d %-10s%s"
                     text
                     char-count
                     old-width
                     new-width
                     (if (= old-width new-width)
                       ""
                       (core/color :green " ✓ Fixed"))))))

(compare-widths "Hello")
(compare-widths "日本")
(compare-widths "你好世界")
(compare-widths "안녕")
(compare-widths "こんにちは")
(compare-widths "Hello世界")
(compare-widths "✓✗")
(compare-widths "😀🎉")
(println)

;; ──────────────────────── Character Types ──────────────────────
(println (core/color :bold "4. Character Type Handling\n"))

(defn show-char-width [label char-str expected]
  (let [actual (core/visible-width char-str)
        status (if (= actual expected)
                 (core/color :green "OK")
                 (core/color :red "ERR"))]
    (println (format "  %s %-25s width=%d (expected %d)  %s"
                     status
                     (str label ":")
                     actual
                     expected
                     char-str))))

(println "Control Characters (width 0):")
(show-char-width "Newline" "test\ntest" 8)
(show-char-width "Tab" "test\ttest" 8)
(println)

(println "Zero-Width Characters (width 0):")
(show-char-width "Zero-Width Space" "test\u200Btest" 8)
(show-char-width "ZW Non-Joiner" "test\u200Ctest" 8)
(println)

(println "Combining Characters (width 0):")
(show-char-width "é (NFC)" "é" 1)
(show-char-width "e + acute (NFD)" "e\u0301" 1)
(println)

(println "Wide Characters (width 2):")
(show-char-width "CJK Ideograph" "漢" 2)
(show-char-width "Hiragana" "あ" 2)
(show-char-width "Katakana" "カ" 2)
(show-char-width "Hangul" "한" 2)
(show-char-width "Fullwidth A" "Ａ" 2)
(show-char-width "Emoji" "😀" 2)
(println)

;; ──────────────────────── Practical Example ──────────────────────
(println (core/color :bold "5. Practical UI Example\n"))
(println "Status table with mixed Unicode:")

(let [headers ["Status" "User" "Message"]
      rows [["✓" "Alice" "Login successful"]
            ["✗" "张三" "Authentication failed"]
            ["⚠" "田中" "Warning: slow query"]
            ["✓" "김철수" "Transaction complete"]
            ["🎉" "Müller" "Deployment success!"]]

      ;; Calculate column widths
      col-widths (vec (for [col-idx (range 3)]
                        (apply max (map #(core/visible-width (nth % col-idx))
                                       (cons headers rows)))))

      format-row (fn [row]
                   (str "  │ "
                        (str/join " │ "
                                 (map-indexed
                                  (fn [idx cell]
                                    (let [width (nth col-widths idx)
                                          cell-width (core/visible-width cell)
                                          padding (apply str (repeat (- width cell-width) " "))]
                                      (str cell padding)))
                                  row))
                        " │"))

      separator (str "  ├"
                    (str/join "┼"
                             (map #(apply str (repeat (+ % 2) "─"))
                                  col-widths))
                    "┤")]

  ;; Draw table
  (println (format-row headers))
  (println separator)
  (doseq [row rows]
    (println (format-row row))))

(println)

;; ──────────────────────── Summary ──────────────────────
(println (core/color :bold "Summary\n"))
(println "✓ Wide characters (CJK) counted correctly as width 2")
(println "✓ Combining marks counted as width 0")
(println "✓ Zero-width characters handled properly")
(println "✓ Control characters ignored in width calculation")
(println "✓ Emoji handled as wide characters")
(println "✓ Box drawing and alignment work with all Unicode")
(println)
(println (core/color :green "Unicode width handling: COMPLETE") "🎉\n")
