(ns limner.error-gates-test
  "Static gates for ADR 0004.

  No library namespace outside the documented terminal-output functions may
  print, and no `catch Exception`/`catch Throwable` may appear outside an
  allow-listed boundary. Each allow-list entry carries its reason.

  The scan reads source forms rather than text, so a comment or docstring that
  mentions `println` does not trip the gate, and it is not fooled by renaming
  the surrounding function."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [limner.quality-gate-support :as support]))

;; ────────────────────── Allowed printing ──────────────────────

(def ^:private allowed-printing
  "Top-level vars allowed to write to stdout/stderr, with why."
  {["src/limner.clj" 'limner/live-display]
   "Deprecated demo namespace; live-display is a self-contained stdout example."

   ["src/limner.clj" 'limner/-main]
   "Deprecated demo entry point; writes the example output to stdout."})

;; ────────────────────── Allowed catches ──────────────────────

(def ^:private allowed-catches
  "Top-level vars allowed to contain `catch Exception`/`catch Throwable`, with why."
  {["src/limner/render.clj" 'limner.render/create-render-loop]
   "ADR 0004 render-loop boundary; limner-2-3 finalises its shape."

   ["src/limner/render.clj" 'limner.render/report-render-error!]
   "ADR 0004 render-loop boundary helper; limner-2-3."

   ["src/limner/render.clj" 'limner.render/terminal-size-from-system]
   "Terminal-size capability probe: a failed stty/tput selects the documented default."

   ["src/limner/render.clj" 'limner.render/get-terminal-size]
   "Terminal-size capability fallback: unknown size becomes 80x24 (ADR 0004)."

   ["src/limner/events.clj" 'limner.events/call-handler-with-timeout]
   "ADR 0004 async event-processor boundary; limner-2-5."

   ["src/limner/events.clj" 'limner.events/create-async-event-processor]
   "ADR 0004 async event-processor boundary; limner-2-5."})

;; ────────────────────── Source scanning ──────────────────────

(defn- source-files []
  (->> (file-seq (io/file "src"))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".clj"))
       (sort-by #(.getPath %))))

(defn- relative-path [file]
  (str/replace (.getPath ^java.io.File file) #"^\./" ""))

(defn- declared-name [ns-sym form]
  (when (and (seq? form) (symbol? (first form)))
    (let [head (name (first form))
          declared (second form)]
      (when (and (#{"defn" "defn-" "def" "defmacro"} head)
                 (symbol? declared))
        (symbol (str ns-sym) (str declared))))))

(defn- top-level-entries []
  (for [file (source-files)
        :let [forms (support/read-forms (slurp file))
              ns-sym (second (first forms))
              path (relative-path file)]
        form forms
        :let [name (declared-name ns-sym form)]
        :when name]
    {:path path :form form :name name}))

(defn- walk [form]
  (tree-seq coll? seq form))

(defn- printing-form? [form]
  (boolean
   (some (fn [node]
           (or (and (symbol? node) (#{"println" "prn"} (name node)))
               (and (seq? node)
                    (= 'binding (first node))
                    (vector? (second node))
                    (some #(= '*err* %) (second node)))))
         (walk form))))

(defn- caught-classes [form]
  (->> (walk form)
       (filter #(and (seq? %) (= 'catch (first %))))
       (keep second)
       (filter symbol?)
       (map name)
       set))

(defn- catch-boundary? [form]
  (boolean (some #{"Exception" "Throwable"} (caught-classes form))))

;; ────────────────────── Gates ──────────────────────

(deftest no-stray-printing-in-src
  (testing "every println/binding *err* in src is allow-listed with a reason"
    (doseq [{:keys [path form name]} (top-level-entries)
            :when (printing-form? form)]
      (is (contains? allowed-printing [path name])
          (str path " / " name " prints; add it to allowed-printing with a reason "
               "or route the message to a callback (ADR 0004).")))))

(deftest catches-are-only-at-documented-boundaries
  (testing "every catch Exception/Throwable in src is allow-listed with a reason"
    (doseq [{:keys [path form name]} (top-level-entries)
            :when (catch-boundary? form)]
      (is (contains? allowed-catches [path name])
          (str path " / " name " catches Exception/Throwable; add it to "
               "allowed-catches with a reason or let the error propagate (ADR 0004).")))))
