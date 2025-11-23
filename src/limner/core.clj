(ns limner.core
  "Terminal and ANSI escape sequence utilities"
  (:require [clojure.string :as str]))

;; ────────────────────── ANSI Escape Codes ──────────────────────
(def ^:private ansi
  "ANSI color and style codes"
  {:reset "\u001B[0m"
   :bold "\u001B[1m"
   :red "\u001B[31m"
   :green "\u001B[32m"
   :yellow "\u001B[33m"
   :blue "\u001B[34m"
   :cyan "\u001B[36m"
   :bright-green "\u001B[92m"})

(defn color
  "Wrap string s in ANSI color code c"
  [c s]
  (str (ansi c "") s (ansi :reset)))

(defn visible-length
  "Calculate visible length of string, stripping ANSI codes"
  [s]
  (count (str/replace s #"\u001B\[[0-9;]*m" "")))

;; ────────────────────── Terminal Control ──────────────────────
(defn hide-cursor
  "Hide terminal cursor"
  []
  (print "\u001B[?25l")
  (flush))

(defn show-cursor
  "Show terminal cursor"
  []
  (print "\u001B[?25h")
  (flush))

(defn clear-line
  "Clear current line"
  []
  (print "\r\u001B[K")
  (flush))
