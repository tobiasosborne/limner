(ns limner.test-runner
  "Portable test entry point shared by the JVM and babashka.

  Test namespaces are discovered from files rather than maintained in a list,
  which keeps both runtimes on the same suite as tests are added.  Benchmark
  support deliberately lives outside the `*_test.clj` naming convention."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as test])
  (:import [java.io PushbackReader StringWriter]))

(defn- namespace-in [file]
  (binding [*read-eval* false]
    (with-open [reader (PushbackReader. (io/reader file))]
      (let [form (read reader)]
        (when-not (and (seq? form) (= 'ns (first form)))
          (throw (ex-info "Test file must start with an ns form"
                          {:file (.getPath file)})))
        (second form)))))

(defn discover-test-namespaces
  "Find namespaces declared by every `test/**/*_test.clj` file."
  []
  (->> (file-seq (io/file "test"))
       (filter #(.isFile %))
       (filter #(re-find #"_test\.clj$" (.getName %)))
       (map namespace-in)
       sort
       vec))

(defn- selected-namespaces [args]
  (let [available (discover-test-namespaces)]
    (if (empty? args)
      available
      (let [[option value & extra] args
            selected (some #(when (= value (str %)) %) available)]
        (when (or (not= "-n" option) (nil? value) (seq extra))
          (throw (ex-info "Usage: test [-n namespace]" {:arguments args})))
        (when-not selected
          (throw (ex-info "Requested test namespace was not discovered"
                          {:namespace value :available available})))
        [selected]))))

(defn run-tests
  "Load and run the discovered suite, or one namespace selected with `-n`.

  Output is buffered while tests run. Uncaptured stderr and raw terminal escape
  bytes are reported as failures instead of reaching the invoking terminal."
  [args]
  (let [namespaces (selected-namespaces args)
        output (StringWriter.)
        errors (StringWriter.)
        result (binding [*out* output, *err* errors]
                 (apply require namespaces)
                 (apply test/run-tests namespaces))
        stdout (str output)
        stderr (str errors)
        failures (cond-> []
                   (str/includes? stdout "\u001B")
                   (conj "Test output hygiene failure: uncaptured ANSI escape byte in stdout.")

                   (seq stderr)
                   (conj (str "Test output hygiene failure: uncaptured stderr: "
                              (pr-str stderr))))]
    (print (str/replace stdout "\u001B" "\\u001B"))
    (when (and (seq stdout) (not (str/ends-with? stdout "\n")))
      (println))
    (doseq [failure failures]
      (println failure))
    (flush)
    (update result :fail (fnil + 0) (count failures))))

(defn exit!
  "Terminate the command process after its runtime has reported results."
  [code]
  (System/exit code))

(defn -main
  [& args]
  (let [result (run-tests args)]
    (exit! (if (pos? (+ (:fail result 0) (:error result 0))) 1 0))))
