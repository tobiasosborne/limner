(ns limner.component-support-test
  "Checks that shared component properties exercise the Unicode width classes
  named by the R6 acceptance criteria, rather than accidentally sampling only
  ASCII strings."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.generators :as gen]
            [limner.component-support :as component]
            [limner.text :as text]))

(deftest fragment-generators-have-their-stated-width-classes
  (testing "wide samples contain a width-two grapheme"
    (is (every? #(>= (text/width %) 2)
                (gen/sample component/wide-fragment-gen 20))))
  (testing "zero-width samples are non-empty text with no terminal columns"
    (let [samples (gen/sample component/zero-width-fragment-gen 20)]
      (is (every? seq samples))
      (is (every? #(zero? (text/width %)) samples)))))
