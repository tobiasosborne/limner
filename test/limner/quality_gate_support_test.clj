(ns limner.quality-gate-support-test
  "Executable specifications for the parsers used by the documentation gates.

  The gates must understand aliases from both namespace declarations and
  top-level require forms, and must preserve enough Markdown context to make a
  failure actionable without opening the scanner implementation."
  (:require [clojure.test :refer [deftest is testing]]
            [limner.quality-gate-support :as support]))

(deftest require-aliases-follow-the-code
  (testing "namespace require clauses"
    (is (= {'core 'limner.core, 'str 'clojure.string}
           (support/require-aliases
            (support/read-forms
             "(ns sample (:require [limner.core :as core]\n[clojure.string :as str]))")))))
  (testing "top-level require forms used by script examples"
    (is (= {'borders 'limner.borders}
           (support/require-aliases
            (support/read-forms
             "#!/usr/bin/env bb\n(require '[limner.borders :as borders])"))))))

(deftest readable-forms-preserve-the-valid-prefix
  (is (= '[(require '[limner.core :as core])]
         (support/readable-forms
          "(require '[limner.core :as core])\n(println \"unfinished"))))

(deftest markdown-blocks-retain-execution-metadata
  (let [markdown (str "## Runnable\n\n```clojure\n(+ 1 2)\n```\n\n"
                      "## Loop\n<!-- not-executed: runs forever -->\n"
                      "```clj\n(loop [] (recur))\n```\n")]
    (is (= [{:heading "Runnable", :line 3, :code "(+ 1 2)"}
            {:heading "Loop", :line 9, :code "(loop [] (recur))",
             :skip-reason "runs forever"}]
           (support/markdown-clojure-blocks markdown)))))
