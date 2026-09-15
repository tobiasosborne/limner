(ns limner.check-test
  "Specifies serial execution, fail-fast behavior and optional lint reporting.

  These tests isolate subprocess execution so testing the orchestrator never
  recursively launches the full suite.  They assert both command order and the
  concise status lines intended for humans running `bb check`."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [limner.check :as check]
            [limner.terminal :as terminal]))

(deftest executable-discovery-uses-the-environment-seam
  (let [requested (atom [])]
    (with-redefs [terminal/get-env
                  (fn [variable]
                    (swap! requested conj variable)
                    "/path/that/does/not/exist")]
      (is (false? (check/command-available? "not-present"))))
    (is (= ["PATH"] @requested))))

(deftest runs-runtime-gates-in-order-and-reports-missing-lint
  (let [calls (atom [])
        output (with-out-str
                 (with-redefs [check/run-command
                               (fn [command]
                                 (swap! calls conj command)
                                 {:exit 0 :out "" :err ""})
                               check/command-available? (constantly false)]
                   (is (zero? (check/run-check)))))]
    (is (= [["clojure" "-M:test"] ["bb" "test"]] @calls))
    (is (= ["[PASS] JVM tests" "[PASS] babashka tests"
            "[SKIP] clj-kondo (not installed)"]
           (str/split-lines output)))))

(deftest stops-after-the-first-failing-gate
  (let [calls (atom [])
        output (with-out-str
                 (with-redefs [check/run-command
                               (fn [command]
                                 (swap! calls conj command)
                                 {:exit 7 :out "" :err ""})]
                   (is (= 7 (check/run-check)))))]
    (is (= [["clojure" "-M:test"]] @calls))
    (is (= "[FAIL] JVM tests (exit 7)\n" output))))
