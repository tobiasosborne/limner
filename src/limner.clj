(ns limner
  "Deprecated compatibility namespace.

  Use `limner.borders` for border drawing, `limner.syntax` for syntax
  highlighting, and `limner.table` for tables. The duplicated `table` here
  now delegates to `limner.table` and is deprecated; require the focused
  namespace instead."
  (:require [clojure.string :as str]
            [limner.borders :as borders]
            [limner.core :as core]
            [limner.table :as limner-table]))

;; ────────────────────── Syntax highlighting ──────────────────────
(defn syntax [code lang]
  (let [rules [[#"(\b(def|defn|fn|let|ns|if|when)\b)" :yellow]
               [#"(;.*)"                            :cyan]
               [#"(\"[^\"]*\")"                     :green]
               [#"#?\{[^}]*\}"                      :bright-green]]]
    (reduce (fn [s [re col]]
              ;; the replacement fn gets the match string, or a vector
              ;; [match group1 ...] when the pattern has groups
              (str/replace s re (fn [m]
                                  (core/color col (if (string? m) m (first m))))))
            code rules)))

;; ────────────────────── Panel ──────────────────────
(defn panel [title content & {:keys [border-style] :or {border-style :double}}]
  (let [lines (str/split-lines content)
        box-lines (borders/draw-titled-box title lines :border-style border-style)]
    (str (str/join "\n" box-lines) "\n")))

;; ────────────────────── Table ──────────────────────
(defn table
  "DEPRECATED: use `limner.table/table`.

  Kept for compatibility: delegates to `limner.table` with right alignment,
  then returns the historical newline-terminated string. Rectangular-row
  validation is shared with the focused namespace."
  {:deprecated "0.2.0"}
  [rows & {:keys [header?] :or {header? true}}]
  (str (str/join "\n" (limner-table/table rows :header? header? :align :right))
       "\n"))

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

;; Run the demo only when this file is the script (bb src/limner.clj),
;; not when the namespace is required.
(when (= *file* (System/getProperty "babashka.file"))
  (-main))
