(ns limner.check
  "Serial orchestration for the repository's local quality gates.

  The check command delegates to the public JVM and babashka test commands so
  it exercises the same entry points contributors use.  Optional lint is only
  attempted when its executable is present on PATH."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [limner.terminal :as terminal]))

(defn run-command
  "Run a command synchronously and retain its exit status and output."
  [command]
  (apply shell/sh command))

(defn command-available?
  "Return true when an executable file with this name is present on PATH."
  [command]
  (let [path (or (terminal/get-env "PATH") "")]
    (boolean
     (some (fn [directory]
             (let [candidate (io/file directory command)]
               (and (.isFile candidate) (.canExecute candidate))))
           (str/split path (re-pattern (java.util.regex.Pattern/quote
                                        java.io.File/pathSeparator)))))))

(defn- forward-output [{:keys [out err]}]
  (when (seq out)
    (print out)
    (flush))
  (when (seq err)
    (binding [*out* *err*]
      (print err)
      (flush))))

(defn- run-gate [label command]
  (let [{:keys [exit] :as result} (run-command command)]
    (forward-output result)
    (if (zero? exit)
      (println (str "[PASS] " label))
      (println (str "[FAIL] " label " (exit " exit ")")))
    exit))

(defn run-check
  "Run JVM tests, babashka tests and available lint in fail-fast order."
  []
  (loop [[[label command] & remaining]
         [["JVM tests" ["clojure" "-M:test"]]
          ["babashka tests" ["bb" "test"]]]]
    (if label
      (let [exit (run-gate label command)]
        (if (zero? exit) (recur remaining) exit))
      (if (command-available? "clj-kondo")
        (run-gate "clj-kondo" ["clj-kondo" "--lint" "src" "test"])
        (do (println "[SKIP] clj-kondo (not installed)") 0)))))

(defn -main [& _]
  (System/exit (or (run-check) 1)))
