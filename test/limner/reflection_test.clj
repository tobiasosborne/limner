(ns limner.reflection-test
  "Guards the JVM compiler's zero-reflection-warning target.

  Every source namespace is reloaded with reflection diagnostics enabled and
  stderr captured. The checked-in baseline is deliberately empty: a new
  diagnostic fails the gate rather than being recorded, and a non-empty
  baseline is itself a failure. Babashka has no equivalent JVM compiler
  diagnostic and reports that explicitly."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]])
  (:import [java.io PushbackReader StringWriter]))

(defn- namespace-in [file]
  (binding [*read-eval* false]
    (with-open [reader (PushbackReader. (io/reader file))]
      (second (read reader)))))

(defn- source-namespaces []
  (->> (file-seq (io/file "src"))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".clj"))
       (sort-by #(.getPath %))
       (map namespace-in)
       vec))

(defn- babashka? []
  (boolean (System/getProperty "babashka.version")))

(defn reflection-warnings []
  (let [namespaces (source-namespaces)
        root-prefix (str (.getCanonicalPath (io/file ".")) java.io.File/separator)
        writer (StringWriter.)]
    (doseq [namespace namespaces]
      (require namespace))
    (binding [*warn-on-reflection* true
              *err* writer]
      (doseq [namespace namespaces]
        (require namespace :reload)))
    (->> (str/split-lines (str writer))
         (filter #(str/includes? % "Reflection warning"))
         (map #(str/replace % root-prefix ""))
         distinct
         sort
         vec)))

(defn- baseline []
  (edn/read-string (slurp (io/resource "limner/baselines/reflection_warnings.edn"))))

(defn warning-baseline-key
  "Remove volatile source coordinates while retaining warning multiplicity."
  [warning]
  (if-let [[_ file message]
           (re-matches #"^Reflection warning, (.+?):[0-9]+:[0-9]+ - (.+)$"
                       warning)]
    (str file " | " message)
    warning))

(defn- unmatched-actual [actual expected]
  (loop [warnings actual, remaining (frequencies expected), unmatched []]
    (if-let [warning (first warnings)]
      (let [key (warning-baseline-key warning)]
        (if (pos? (get remaining key 0))
          (recur (rest warnings) (update remaining key dec) unmatched)
          (recur (rest warnings) remaining (conj unmatched warning))))
      unmatched)))

(defn- missing-baseline-entries [actual expected]
  (let [actual-counts (frequencies (map warning-baseline-key actual))]
    (loop [entries expected, remaining actual-counts, missing []]
      (if-let [entry (first entries)]
        (if (pos? (get remaining entry 0))
          (recur (rest entries) (update remaining entry dec) missing)
          (recur (rest entries) remaining (conj missing entry)))
        missing))))

(deftest warning-baseline-keys-ignore-moving-source-lines
  (is (= "limner/render.clj | call to method indexOf can't be resolved."
         (warning-baseline-key
          "Reflection warning, limner/render.clj:80:35 - call to method indexOf can't be resolved."))))

(deftest source-reflection-warnings-match-the-shrinking-baseline
  (if (babashka?)
    (do
      (println "Reflection gate: not applicable under babashka; it has no JVM reflection warnings.")
      (is true "Reflection gate does not apply under babashka's SCI runtime."))
    (let [expected (baseline)
          actual (reflection-warnings)
          new (sort (unmatched-actual actual expected))
          stale (sort (missing-baseline-entries actual expected))]
      (is (empty? expected)
          (str "The reflection baseline must stay empty (PLAN 2.6); fix the "
               "warning instead of recording it. Entries: " (pr-str expected)))
      (is (empty? new)
          (str "JVM reflection warnings must be fixed, not baselined:\n" (str/join "\n" new)))
      (is (empty? stale)
          (str "Fixed reflection warnings remain in the baseline; remove:\n"
               (str/join "\n" stale))))))
