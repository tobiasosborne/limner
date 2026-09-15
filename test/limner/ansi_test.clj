(ns limner.ansi-test
  "ECMA-48 grammar, xterm SGR, helper, and total-input properties."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [limner.ansi :as ansi]))

(def esc "\u001B")

(deftest tokenizes-csi-esc-and-controls
  (is (= [{:type :text :text "text[" :raw "text["}
          {:type :csi :params "?25" :intermediates ""
           :final \l :raw "\u001B[?25l"}
          {:type :csi :params "31" :intermediates ""
           :final \m :raw "\u009B31m"}]
         (ansi/tokenize "text[\u001B[?25l\u009B31m")))
  (is (= [{:type :csi :params "1" :intermediates ""
           :final \@ :raw "\u001B[1@"}
          {:type :csi :params "2" :intermediates ""
           :final \~ :raw "\u001B[2~"}]
         (ansi/tokenize "\u001B[1@\u001B[2~")))
  (is (= [{:type :esc :intermediates "" :final \c :raw "\u001Bc"}
          {:type :esc :intermediates "(" :final \B :raw "\u001B(B"}]
         (ansi/tokenize "\u001Bc\u001B(B")))
  (is (= [{:type :text :text "a\n\tb" :raw "a\n\tb"}
          {:type :c0 :char \u0007 :raw "\u0007"}
          {:type :c1 :char \u0084 :raw "\u0084"}]
         (ansi/tokenize "a\n\tb\u0007\u0084"))))

(deftest tokenizes-control-strings-and-every-introducer
  (let [cases [["\u001B]title\u0007" :osc "title" :bel]
               ["\u009Dtitle\u009C" :osc "title" :st]
               ["\u001BPdata\u001B\\" :dcs "data" :st]
               ["\u0090data\u009C" :dcs "data" :st]
               ["\u001B_payload\u001B\\" :apc "payload" :st]
               ["\u009Fpayload\u009C" :apc "payload" :st]
               ["\u001B^private\u001B\\" :pm "private" :st]
               ["\u009Eprivate\u009C" :pm "private" :st]
               ["\u001BXstring\u001B\\" :sos "string" :st]
               ["\u0098string\u009C" :sos "string" :st]]]
    (doseq [[raw type data terminator] cases]
      (is (= [{:type type :data data :terminator terminator :raw raw}]
             (ansi/tokenize raw))
          (str "introducer case " type " " (pr-str raw))))
    (is (= [{:type :csi :params "1" :intermediates ""
             :final \m :raw "\u001B[1m"}
            {:type :osc :data "8;;https://example.test" :terminator :st
             :raw "\u001B]8;;https://example.test\u001B\\"}
            {:type :text :text "link" :raw "link"}
            {:type :osc :data "8;;" :terminator :st
             :raw "\u001B]8;;\u001B\\"}]
           (ansi/tokenize
            "\u001B[1m\u001B]8;;https://example.test\u001B\\link\u001B]8;;\u001B\\")))))

(deftest incomplete-sequences-preserve-all-input
  (doseq [[raw kind] [["\u001B" :esc]
                      ["\u001B[31" :csi]
                      ["\u009Dunterminated" :osc]
                      ["\u001BPunterminated" :dcs]]]
    (is (= [{:type :incomplete :kind kind :raw raw}]
           (ansi/tokenize raw)))))

(deftest strip-keeps-only-text
  (is (= "a\n\tb"
         (ansi/strip "a\u001B[2K\n\t\u001B]8;;https://x\u0007b"))))

(def expected-simple-sgr
  {0 [:all :default]
   1 [:intensity :bold]
   2 [:intensity :faint]
   3 [:italic true]
   4 [:underline :single]
   5 [:blink :slow]
   7 [:inverse true]
   8 [:hidden true]
   9 [:crossed-out true]
   21 [:underline :double]
   22 [:intensity :normal]
   23 [:italic false]
   24 [:underline :none]
   25 [:blink :none]
   27 [:inverse false]
   28 [:hidden false]
   29 [:crossed-out false]
   39 [:foreground :default]
   49 [:background :default]})

(deftest parses-xterm-sgr-attributes-and-resets
  (doseq [[parameter [attribute value]] expected-simple-sgr]
    (let [token (first (ansi/tokenize (str esc "[" parameter "m")))]
      (is (= [{:attribute attribute :value value :raw (str parameter)}]
             (ansi/sgr-params token))
          (str "xterm SGR parameter " parameter))))
  (doseq [[parameter attribute index bright?]
          [[30 :foreground 0 false] [37 :foreground 7 false]
           [40 :background 0 false] [47 :background 7 false]
           [90 :foreground 0 true] [97 :foreground 7 true]
           [100 :background 0 true] [107 :background 7 true]]]
    (let [token (first (ansi/tokenize (str esc "[" parameter "m")))]
      (is (= [{:attribute attribute
               :value {:mode :ansi :index index :bright bright?}
               :raw (str parameter)}]
             (ansi/sgr-params token)))))
  (is (= [{:attribute :unknown :value 26 :raw "26"}
          {:attribute :unknown :value 777 :raw "777"}]
         (ansi/sgr-params (first (ansi/tokenize "\u001B[26;777m")))))
  (is (= [{:attribute :all :value :default :raw ""}]
         (ansi/sgr-params (first (ansi/tokenize "\u001B[m")))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"SGR"
                        (ansi/sgr-params
                         (first (ansi/tokenize "\u001B[2K"))))))

(deftest parses-indexed-and-rgb-sgr-forms
  (is (= [{:attribute :foreground
           :value {:mode :indexed :index 42}
           :raw "38;5;42"}
          {:attribute :background
           :value {:mode :rgb :r 1 :g 2 :b 3}
           :raw "48;2;1;2;3"}]
         (ansi/sgr-params
          (first (ansi/tokenize "\u001B[38;5;42;48;2;1;2;3m")))))
  (is (= [{:attribute :foreground
           :value {:mode :rgb :r 4 :g 5 :b 6}
           :raw "38:2::4:5:6"}
          {:attribute :background
           :value {:mode :indexed :index 7}
           :raw "48:5:7"}]
         (ansi/sgr-params
          (first (ansi/tokenize "\u001B[38:2::4:5:6;48:5:7m"))))))

(deftest extracts-osc-8-hyperlinks
  (is (= {:params "id=docs" :uri "https://example.test/a;b"}
         (ansi/hyperlink
          (first (ansi/tokenize
                  "\u001B]8;id=docs;https://example.test/a;b\u001B\\")))))
  (is (= {:params "" :uri nil}
         (ansi/hyperlink (first (ansi/tokenize "\u001B]8;;\u0007"))))))

(def biased-unit-gen
  (gen/frequency
   [[8 (gen/fmap char (gen/choose 0 0xFFFF))]
    [7 (gen/elements [\u001B \[ \] \u0007 \; \: \0 \1 \9
                      \u0090 \u0098 \u009B \u009C \u009D \u009E \u009F])]]))

(def biased-string-gen
  (gen/fmap #(apply str %) (gen/vector biased-unit-gen 0 120)))

(deftest tokenizer-totality-properties
  (let [result
        (tc/quick-check
         1000
         (prop/for-all [text biased-string-gen]
           (let [tokens (ansi/tokenize text)]
             (and (= text (apply str (map :raw tokens)))
                  (not (str/includes? (ansi/strip text) esc)))))
         :seed 480083)]
    (is (:pass? result) (pr-str (dissoc result :result-data)))))
