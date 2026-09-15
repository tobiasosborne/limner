(ns limner.readme-examples-test
  "Executes README Clojure fences without touching the user's terminal.

  Every runnable fence gets a fresh namespace, including a fresh alias map,
  and bound output writers capture println, print and flush.  Long-lived or
  process-mutating snippets must opt out explicitly in the preceding Markdown
  comment; existing broken blocks are tracked in a shrinking baseline."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [limner.quality-gate-support :as support]))

(defn- throwable-messages [throwable]
  (loop [current throwable, messages []]
    (if current
      (recur (.getCause current)
             (cond-> messages (.getMessage current) (conj (.getMessage current))))
      messages)))

(defn- normalized-failure [throwable]
  (let [messages (throwable-messages throwable)
        joined (str/join "\n" messages)]
    (cond
      (str/includes? joined "Invalid height: must be non-negative integer")
      "invalid argument: Invalid height: must be non-negative integer"

      (str/includes? joined "stream/complete?")
      "unresolved symbol: stream/complete?"

      (str/includes? joined "nest-panels")
      "unresolved symbol: nest-panels"

      :else
      (str "error: " (or (last messages) (str (class throwable)))))))

(defn- evaluate-block [index {:keys [code heading line]}]
  (let [namespace-symbol (symbol (str "limner.readme-example-" index "-" (gensym)))
        namespace (create-ns namespace-symbol)
        output (java.io.StringWriter.)
        errors (java.io.StringWriter.)]
    (try
      (binding [*ns* namespace, *out* output, *err* errors]
        (refer 'clojure.core)
        (doseq [form (support/read-forms code)]
          (eval form)))
      nil
      (catch Throwable throwable
        {:heading heading
         :line line
         :failure (normalized-failure throwable)
         :captured-output (str output)
         :captured-errors (str errors)})
      (finally
        (remove-ns namespace-symbol)))))

(defn readme-failures []
  (->> (support/markdown-clojure-blocks (slurp "README.md"))
       (remove :skip-reason)
       (map-indexed evaluate-block)
       (remove nil?)
       vec))

(defn- baseline-key [{:keys [heading failure]}]
  (str heading " | " failure))

(defn- baseline []
  (edn/read-string (slurp (io/resource "limner/baselines/readme_examples.edn"))))

(deftest readme-examples-match-the-shrinking-baseline
  (let [failures (readme-failures)
        expected (set (baseline))
        actual (set (map baseline-key failures))
        new (sort (remove expected actual))
        stale (sort (remove actual expected))
        locations (into {} (map (juxt baseline-key :line)) failures)]
    (is (empty? new)
        (str "New failing README examples:\n"
             (str/join "\n" (map #(str "README.md:" (locations %) " " %) new))))
    (is (empty? stale)
        (str "Passing README examples remain in the shrinking baseline; remove:\n"
             (str/join "\n" stale)))))
