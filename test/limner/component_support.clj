(ns limner.component-support
  "Shared helpers for component golden and property tests.

  Components render terminal strings. A failure that prints the two strings
  raw is unreadable, because the escape that matters is invisible; `visible`
  makes every ESC explicit and `assert-lines` puts it in the assertion
  message. The generators cover the three text classes the audit found
  broken: CJK wide clusters, emoji ZWJ sequences, and text that already
  carries an ANSI style."
  (:require [clojure.string :as str]
            [clojure.test :refer [is]]
            [clojure.test.check.generators :as gen]
            [limner.core :as core]
            [limner.text :as text]))

(def esc "\u001B")

(defn visible
  "Make control bytes visible so an exact-string failure is readable."
  [value]
  (-> (str value)
      (str/replace esc "<ESC>")))

(defn assert-lines
  "Assert rendered `actual` lines equal `expected` exactly, escape-visible."
  [expected actual]
  (is (= (vec expected) (vec actual))
      (str "\nexpected:\n" (str/join "\n" (map visible expected))
           "\nactual:\n" (str/join "\n" (map visible actual)))))

(defn widths
  "Display width of every line, in order."
  [lines]
  (mapv text/width lines))

(defn equal-line-width?
  "True when every line has the same display width."
  [lines]
  (apply = (widths lines)))

(def ^:private cjk-fragments
  ["日本" "中文" "한국어" "日本語タイトル" "名前" "、。"])

(def ^:private emoji-fragments
  ["😀" "👩‍🔬" "🇩🇪" "⚡" "🎉" "🅰"])

(def fragment-gen
  "A short user-visible fragment drawn from the audit's problem classes."
  (gen/one-of
   [(gen/elements (concat cjk-fragments emoji-fragments
                          ["hello" "a b" "x" "" "  spaced  "]))
    (gen/fmap #(core/color :red %) (gen/elements ["Red" "赤" "🔴"]))
    (gen/fmap #(core/color :bold %) (gen/elements ["bold" "太字"]))
    (gen/fmap #(core/color :underline %)
              (gen/elements ["link" "リンク"]))]))

(def line-gen
  "A content line that may mix styled and plain fragments."
  (gen/fmap #(apply str %) (gen/vector fragment-gen 0 3)))

(def lines-gen
  "A non-empty vector of content lines."
  (gen/vector line-gen 1 4))

(def title-gen
  "A title that may mix styled and plain fragments."
  (gen/fmap #(apply str %) (gen/vector fragment-gen 0 2)))
