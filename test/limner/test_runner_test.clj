(ns limner.test-runner-test
  "Checks the portable runner's discovery and namespace-selection contract.

  The local gates rely on the JVM and babashka selecting the same files, while
  focused development runs retain the documented `-n namespace` interface."
  (:require [clojure.test :refer [deftest is testing]]
            [limner.test-runner :as runner]))

(deftest discovers-tests-but-not-benchmarks
  (let [namespaces (set (runner/discover-test-namespaces))]
    (is (contains? namespaces 'limner.core-test))
    (is (contains? namespaces 'limner.test-runner-test))
    (is (not (contains? namespaces 'limner.benchmarks)))
    (is (every? #(re-find #"-test$" (str %)) namespaces))))

(deftest selects-one-namespace-with-the-documented-option
  (testing "-n selects one discovered namespace"
    (let [called (atom nil)]
      (with-redefs [runner/discover-test-namespaces
                    (constantly '[limner.core-test limner.layout-test])
                    clojure.core/require (fn [& namespaces]
                                           (reset! called namespaces))
                    clojure.test/run-tests (fn [& namespaces]
                                             {:test 0 :pass 0 :fail 0 :error 0
                                              :namespaces namespaces})]
        (is (= '[limner.layout-test]
               (:namespaces (runner/run-tests ["-n" "limner.layout-test"]))))
        (is (= '(limner.layout-test) @called))))))

(deftest main-exits-explicitly-after-a-green-suite
  (let [exit-code (atom nil)]
    (with-redefs [runner/run-tests (constantly {:fail 0 :error 0})
                  runner/exit! #(reset! exit-code %)]
      (runner/-main))
    (is (= 0 @exit-code))))
