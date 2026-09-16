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

;; ────────────────────── Component text-operation gate ──────────────────────

(def ^:private component-sources
  "Standalone text-rendering namespaces; extended as components migrate."
  #{"src/limner/borders.clj"
    "src/limner/borders/internal.clj"
    "src/limner/borders/effects.clj"
    "src/limner/layout.clj"
    "src/limner/table.clj"
    "src/limner/components/list.clj"
    "src/limner/components/input.clj"
    "src/limner/components/progress.clj"
    "src/limner/components/statusbar.clj"})

(def ^:private banned-text-symbols
  "Symbols whose UTF-16 semantics must not appear in a component namespace."
  #{"subs" "nth" "count"})

(def ^:private banned-interop-methods
  "Java string methods that split or count UTF-16 code units."
  #{".substring" ".length" ".codePointCount"})

(def ^:private allowed-component-uses
  "Component uses of a banned symbol that are not about user-visible text,
  each with the reason it is safe."
  {["src/limner/borders.clj" 'limner.borders/valid-custom-style? 'count]
   "Counts the six elements of a custom-style vector, not text."

   ["src/limner/borders.clj" 'limner.borders/colorize-border 'count]
   "Counts box rows to find the last one; never measures text."

   ["src/limner/borders/effects.clj" 'limner.borders.effects/side-by-side 'count]
   "Counts box rows to pad the shorter column; never measures text."

   ["src/limner/layout.clj" 'count]
   "Layout counts rows, cells, and flex items; text is measured by limner.text."

   ["src/limner/layout.clj" 'nth]
   "Layout indexes numeric share vectors; text is measured by limner.text."

   ["src/limner/table.clj" 'count]
   "Table counts rows and columns; cells are measured by limner.text."

   ["src/limner/table.clj" 'nth]
   "Table indexes its per-column width vector; cells use limner.text."

   ["src/limner/components/list.clj" 'count]
   "List counts items and viewport rows; labels are measured by limner.text."

   ["src/limner/components/input.clj" 'count]
   "Input counts grapheme units and history entries; boundaries come from limner.text."

   ["src/limner/components/input.clj" 'nth]
   "Input indexes its own grapheme-unit vector; text is sliced by limner.text."

   ["src/limner/components/progress.clj" 'count]
   "Progress counts frames and labels; rendered text is built by limner.text."

   ["src/limner/components/progress.clj" 'nth]
   "Progress indexes its own frame and label vectors, never text columns."})

(defn- banned-use [form]
  (cond
    (and (seq? form) (symbol? (first form)))
    (let [head (first form), name (clojure.core/name head)]
      (cond
        (contains? banned-text-symbols name) name
        (contains? banned-interop-methods name) name
        (and (= "format" name)
             (string? (second form))
             (re-find #"%[-+ 0-9.#]*[sS]" (second form)))
        "format"
        :else nil))
    (instance? java.util.regex.Pattern form)
    (when (re-find #"\\u001[bB]|\\u001B|\\x1[bB]|\\033|\\e\[" (str form))
      "ansi-regex")
    :else nil))

(defn- component-entries []
  (for [file (source-files)
        :let [path (relative-path file)]
        :when (contains? component-sources path)
        :let [forms (support/read-forms (slurp file))
              ns-sym (second (first forms))]
        form forms
        :let [name (declared-name ns-sym form)]
        :when name]
    {:path path :form form :name name}))

(deftest components-use-the-text-layer
  (testing "no component measures or slices user-visible text with UTF-16 tools"
    (doseq [{:keys [path form name]} (component-entries)
            :let [use (some banned-use (walk form))]
            :when use]
      (is (or (contains? allowed-component-uses [path name (symbol use)])
              (contains? allowed-component-uses [path (symbol use)]))
          (str path " / " name " uses " use "; route it through limner.text or "
               "add it to allowed-component-uses with a reason.")))))
