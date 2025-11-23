;; limner.clj – 100% bulletproof, no more broken borders (2025)
(ns limner
  (:require [clojure.string :as str]
            [limner.core :as core]
            [limner.borders :as borders]))

;; ────────────────────── Syntax highlighting ──────────────────────
(defn syntax [code lang]
  (let [rules [[#"(\b(def|defn|fn|let|ns|if|when)\b)" :yellow]
               [#"(;.*)"                            :cyan]
               [#"(\"[^\"]*\")"                     :green]
               [#"#?\{[^}]*\}"                      :bright-green]]]
    (reduce (fn [s [re col]]
              (str/replace s re #(core/color col (second %&))))
            code rules)))

;; ────────────────────── Panel ──────────────────────
(defn panel [title content & {:keys [border-style] :or {border-style :double}}]
  (let [lines (str/split-lines content)
        box-lines (borders/draw-titled-box title lines :border-style border-style)]
    (str (str/join "\n" box-lines) "\n")))

;; ────────────────────── Table ──────────────────────
(defn table [rows & {:keys [header?] :or {header? true}}]
  (let [widths (for [c (range (count (first rows)))]
                 (apply max (map #(core/visible-length (str (get % c ""))) rows)))
        hline  (str "├─" (str/join "─┼─" (map #(apply str (repeat % "─")) widths)) "─┤")
        rowstr (fn [r]
                 (str "│ "
                      (str/join " │ " (map-indexed #(format (str "%" (nth widths %) "s") (str %2)) r))
                      " │"))]
    (str "┌─" (str/join "─┬─" (map #(apply str (repeat % "─")) widths)) "─┐\n"
         (rowstr (first rows))
         (when header? (str "\n" hline))
         (when (next rows)
           (str "\n" (str/join "\n" (map rowstr (if header? (rest rows) rows)))))
         "\n└─" (str/join "─┴─" (map #(apply str (repeat % "─")) widths)) "─┘\n")))

;; ────────────────────── Spinner ──────────────────────
(def spinner-frames ["⠋" "⠙" "⠹" "⠸" "⠼" "⠴" "⠦" "⠧" "⠇" "⠏"])

(defn live-display [f]
  (core/hide-cursor)
  (loop [i 0]
    (print (str "\r\u001B[K" (spinner-frames (mod i 10)) " Working..."))
    (flush)
    (Thread/sleep 100)
    (when-let [res (f i)]
      (println (str "\r\u001B[K" (core/color :bright-green "Done!") " " res))
      (core/show-cursor)
      res)))

;; ────────────────────── Demo ──────────────────────
(defn -main []
  (println (panel "Claude's Response"
                  (syntax "(defn fibonacci [n]\n  (if (<= n 1)\n      n\n      (+ (fibonacci (- n 1))\n         (fibonacci (- n 2)))))" :clojure)
                  :border-style :double))

  (println (table [["File"        "Status"    "Lines"]
                   ["src/app.clj" "modified"  "42"]
                   ["README.md"   "added"     "128"]]))

  (live-display (fn [i] (when (= i 25) "fibonacci computed")))

  (println))

(when (or (System/getProperty "babashka.version")
          (= *file* (System/getProperty "babashka.file")))
  (-main))
