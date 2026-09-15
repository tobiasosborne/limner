(ns limner.text-test
  "Styled-text examples and generative column-operation invariants from ADR 0003."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [limner.text :as text]
            [limner.unicode.grapheme :as grapheme]))

(def esc "\u001B")
(def st "\u001B\\")

(defn- plain [value]
  (apply str (keep :text value)))

(deftest canonical-constructors-and-fill-only-style
  (is (= [{:text "red bold" :style {:fg :red :bold true}}]
         (text/concat (text/styled "red" {:fg :red :bold true})
                      (text/styled " bold" {:bold true :fg :red}))))
  (is (= [{:text "R" :style {:bold true :fg :red}}
          {:text " after" :style {:bold true}}]
         (text/style {:bold true}
                     (text/concat (text/styled "R" {:fg :red}) " after"))))
  (is (= [{:text "x" :style {:underline :single :blink :slow}}]
         (text/styled "x" {:underline true :blink true})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"style"
                        (text/styled "x" {:unknown true}))))

(deftest parses-sgr-links-and-opaque-controls
  (let [input (str esc "[31mR" esc "[1mB" esc "[22;39m"
                   esc "[2K"
                   esc "]8;id=x;https://example.test" st "link"
                   esc "]8;;" st " end")]
    (is (= [{:text "R" :style {:fg :red}}
            {:text "B" :style {:fg :red :bold true}}
            {:raw (str esc "[2K")}
            {:text "link" :style {:link "https://example.test"}}
            {:text " end" :style {}}]
           (text/from-ansi input))))
  (is (= [{:raw (str esc "[26m")}
          {:text "x" :style {}}]
         (text/from-ansi (str esc "[26mx"))))
  (is (= 4 (text/width
            (str esc "]8;;https://example.test" st "link" esc "]8;;" st))))
  (is (= 2 (text/width (str esc "[2Khi")))))

(deftest rendering-emits-minimal-transitions-and-round-trips
  (let [value [{:text "R" :style {:bold true :fg :red}}
               {:text " after" :style {:bold true}}]]
    (is (= (str esc "[1;31mR" esc "[39m after" esc "[0m")
           (text/to-ansi value)))
    (is (= value (text/from-ansi (text/to-ansi value)))))
  (let [value [{:text "link" :style {:link "https://example.test" :underline :single}}
               {:text " plain" :style {}}]
        rendered (str esc "]8;;https://example.test" st esc "[4mlink"
                      esc "]8;;" st esc "[24m plain")]
    (is (= rendered (text/to-ansi value)))
    (is (= value (text/from-ansi rendered))))
  (let [value [{:raw (str esc "[2K")}
               {:text "x" :style {:fg {:rgb [1 2 3]}
                                    :bg {:index 17}
                                    :strike true}}]]
    (is (= value (text/from-ansi (text/to-ansi value))))))

(deftest width-is-grapheme-style-and-control-aware
  (is (= 10 (text/width "A中文é👩‍🔬🇩🇪")))
  (is (= 2 (text/width (text/concat (text/styled "e" {:fg :red})
                                    (text/styled "́" {:fg :blue})
                                    "X"))))
  (is (= 2 (text/width "★" {:ambiguous-width 2})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"options"
                        (text/width "x" {:unknown true}))))

(deftest slicing-never-emits-partial-graphemes
  (testing "a cut through a wide cluster becomes styled spaces"
    (is (= [{:text " " :style {:fg :red}}]
           (text/slice-columns (text/styled "A中B" {:fg :red}) 1 2)))
    (is (= [{:text " " :style {:fg :red}}]
           (text/slice-columns (text/styled "A中B" {:fg :red}) 2 3)))
    (is (= [{:text "中" :style {:fg :red}}]
           (text/slice-columns (text/styled "A中B" {:fg :red}) 1 3))))
  (testing "clusters and styles crossing span boundaries stay intact"
    (let [value (text/concat (text/styled "e" {:fg :red})
                             (text/styled "́" {:fg :blue})
                             (text/styled "👩‍🔬" {:bold true})
                             (text/styled "🇩🇪X" {:underline true}))]
      (is (= [{:text "e" :style {:fg :red}}
              {:text "́" :style {:fg :blue}}
              {:text "👩‍🔬" :style {:bold true}}
              {:text "🇩🇪" :style {:underline :single}}]
             (text/slice-columns value 0 5)))
      (is (= 5 (text/width (text/slice-columns value 0 5))))))
  (testing "opaque controls stay zero-width"
    (let [value (text/from-ansi (str "a" esc "[2K中b"))]
      (is (= [{:text "a" :style {}}
              {:raw (str esc "[2K")}
              {:text "中" :style {}}]
             (text/slice-columns value 0 3))))))

(deftest padding-and-centering-use-display-columns
  (is (= [{:text "中..." :style {}}]
         (text/pad-right "中" 5 ".")))
  (is (= [{:text "..👩‍🔬" :style {}}]
         (text/pad-left "👩‍🔬" 4 ".")))
  (is (= [{:text ".🇩🇪.." :style {}}]
         (text/center "🇩🇪" 5 ".")))
  (is (= [{:text " " :style {:bg :blue}}]
         (text/pad-right [] 1 (text/styled "中" {:bg :blue})))))

(deftest truncation-counts-and-safely-slices-ellipsis
  (is (= [{:text "中文…" :style {}}]
         (text/truncate "中文本" 5)))
  (is (= [{:text "👩‍🔬…" :style {}}]
         (text/truncate "👩‍🔬abc" 3)))
  (is (= [{:text " " :style {:fg :yellow}}]
         (text/truncate "abcdef" 1
                        :ellipsis (text/styled "中" {:fg :yellow}))))
  (is (= [] (text/truncate "abc" 0))))

(deftest lines-preserve-empty-lines-and-active-style
  (is (= [[{:text "a" :style {:fg :red}}]
          [{:text "b" :style {:fg :red}}]
          []]
         (text/lines (str esc "[31ma\nb\n" esc "[0m"))))
  (is (= [[] [{:text "x" :style {}}]]
         (text/lines "\nx"))))

(deftest wrapping-preserves-newlines-and-hard-breaks-long-words
  (is (= [[{:text "one" :style {}}]
          [{:text "two" :style {}}]]
         (text/wrap "one two" 4)))
  (is (= [[{:text "中文" :style {}}]
          [{:text "本" :style {}}]]
         (text/wrap "中文本" 4)))
  (is (= [[{:text "👩‍🔬" :style {:bold true}}]
          [{:text "🇩🇪" :style {:bold true}}]
          [{:text "é" :style {:bold true}}]]
         (text/wrap (text/styled "👩‍🔬 🇩🇪\né" {:bold true}) 2)))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"positive"
                        (text/wrap "x" 0))))

(def display-clusters ["a" " " "é" "中" "👩‍🔬" "🇩🇪" "★"])
(def style-values [{} {:fg :red} {:bold true} {:underline :double}
                   {:fg {:index 42} :bg {:rgb [1 2 3]}}
                   {:link "https://example.test"}])

(def styled-text-gen
  (gen/fmap (fn [parts]
              (apply text/concat
                     (map (fn [[cluster style]] (text/styled cluster style))
                          parts)))
            (gen/vector (gen/tuple (gen/elements display-clusters)
                                   (gen/elements style-values))
                        0 30)))

(defn- check-property [seed property]
  (let [result (tc/quick-check 250 property :seed seed)]
    (is (:pass? result) (pr-str (dissoc result :result-data)))))

(defn- well-formed-utf16? [^String value]
  (let [length (.length value)]
    (loop [index 0]
      (if (= index length)
        true
        (let [unit (.charAt value index)]
          (cond
            (Character/isHighSurrogate unit)
            (and (< (inc index) length)
                 (Character/isLowSurrogate (.charAt value (inc index)))
                 (recur (+ index 2)))
            (Character/isLowSurrogate unit) false
            :else (recur (inc index))))))))

(deftest column-operation-properties
  (testing "truncation never exceeds its target"
    (check-property
     316001
     (prop/for-all [value styled-text-gen, n (gen/choose 0 40)]
       (<= (text/width (text/truncate value n)) n))))
  (testing "right padding reaches its target when padding is needed"
    (check-property
     316002
     (prop/for-all [value styled-text-gen, extra (gen/choose 0 20)]
       (let [target (+ (text/width value) extra)]
         (= target (text/width (text/pad-right value target " ")))))))
  (testing "slicing neither emits lone surrogates nor breaks known clusters"
    (check-property
     316003
     (prop/for-all [value styled-text-gen, start (gen/choose 0 40)
                    length (gen/choose 0 40)]
       (let [result (plain (text/slice-columns value start (+ start length)))
             units (grapheme/clusters result)]
         (and (well-formed-utf16? result)
              (= result (apply str units))
              (every? (set (conj display-clusters "" "." " ")) units)))))
  (testing "rendering and parsing preserve canonical spans"
    (check-property
     316004
     (prop/for-all [value styled-text-gen]
       (= value (text/from-ansi (text/to-ansi value))))))
  (testing "boundary-aligned slices reconstruct display text"
    (check-property
     316005
     (prop/for-all [parts (gen/vector (gen/elements display-clusters) 0 30)
                    cut-index (gen/choose 0 30)]
       (let [cut-index (min cut-index (count parts))
             value (apply text/concat parts)
             cut (text/width (apply text/concat (take cut-index parts)))
             left (text/slice-columns value 0 cut)
             right (text/slice-columns value cut (text/width value))]
         (= (plain value) (str (plain left) (plain right)))))))
  (testing "concatenation width is additive"
    (check-property
     316006
     (prop/for-all [left styled-text-gen, right styled-text-gen]
       (= (text/width (text/concat left right))
          (+ (text/width left) (text/width right))))))))
