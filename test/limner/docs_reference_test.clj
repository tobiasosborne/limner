(ns limner.docs-reference-test
  "Checks that documented limner-qualified vars exist under the aliases shown.

  Markdown fences use their own require forms so a convenient conventional
  alias cannot hide an incomplete example.  Prose and fences without requires
  use the project's conventional aliases.  Script examples are checked against
  aliases declared by their ns or top-level require form."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [limner.quality-gate-support :as support]))

(def conventional-aliases
  {'core 'limner.core, 'terminal 'limner.terminal, 'term 'limner.terminal
   'borders 'limner.borders, 'layout 'limner.layout, 'render 'limner.render
   'events 'limner.events, 'state 'limner.state, 'stream 'limner.streaming
   'streaming 'limner.streaming, 'syntax 'limner.syntax
   'input 'limner.components.input, 'list 'limner.components.list
   'markdown 'limner.components.markdown, 'md 'limner.components.markdown
   'panel 'limner.components.panel, 'progress 'limner.components.progress
   'statusbar 'limner.components.statusbar, 'sb 'limner.components.statusbar})

(def reference-pattern
  #"(?<![A-Za-z0-9_.-])([a-z][A-Za-z0-9_.-]*)/([A-Za-z][A-Za-z0-9*+!?<>=._-]*)")

(defn- markdown-files []
  (->> (cons (io/file "README.md")
             (file-seq (io/file "docs")))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".md"))
       (remove #(str/starts-with? (.getPath %)
                                  (str "docs" java.io.File/separator "archive"
                                       java.io.File/separator)))
       (sort-by #(.getPath %))))

(defn- example-files []
  (->> (.listFiles (io/file "examples"))
       (filter #(and (.isFile %) (str/ends-with? (.getName %) ".clj")))
       (sort-by #(.getPath %))))

(defn- references [text first-line]
  (let [matcher (re-matcher reference-pattern text)]
    (loop [found []]
      (if (.find matcher)
        (let [line (+ first-line (count (re-seq #"\n" (subs text 0 (.start matcher)))))]
          (recur (conj found {:line line
                              :alias (symbol (.group matcher 1))
                              :sym (symbol (.group matcher 2))})))
        found))))

(defn- limner-namespace? [namespace]
  (str/starts-with? (str namespace) "limner"))

(defn- resolves? [namespace sym]
  (try
    (binding [*err* (java.io.StringWriter.)]
      (require namespace))
    (boolean (ns-resolve namespace sym))
    (catch Throwable _ false)))

(defn- missing-in [text first-line aliases conventional-fallback?]
  (keep (fn [{:keys [alias sym] :as reference}]
          (let [target (if conventional-fallback?
                         (get conventional-aliases alias)
                         (get aliases alias))]
            (when (and target
                       (limner-namespace? target)
                       (not (resolves? target sym)))
              reference)))
        (references text first-line)))

(defn- prose-regions [markdown]
  (loop [lines (str/split-lines markdown), line 1, fenced? false, regions []]
    (if-let [text (first lines)]
      (let [fence? (boolean (re-matches #"\s*```.*" text))]
        (recur (rest lines) (inc line) (if fence? (not fenced?) fenced?)
               (if (or fenced? fence?) regions (conj regions [text line]))))
      regions)))

(defn- markdown-missing [file]
  (let [markdown (slurp file)
        blocks (support/markdown-clojure-blocks markdown)
        prose (prose-regions markdown)]
    (concat
     (mapcat (fn [[text line]]
               (missing-in text line conventional-aliases true))
             prose)
     (mapcat (fn [{:keys [code line]}]
               (let [aliases (support/require-aliases (support/readable-forms code))]
                 (missing-in code (inc line) aliases (empty? aliases))))
             blocks))))

(defn- example-missing [file]
  (let [source (slurp file)
        aliases (support/require-aliases (support/read-forms source))]
    (missing-in source 1 aliases false)))

(defn- display-path [file]
  (str/replace (.getPath file) (re-pattern (str "^\\." java.io.File/separator)) ""))

(defn unresolved-references []
  (let [occurrences (concat
                     (mapcat #(map (fn [reference] [% reference])
                                  (markdown-missing %))
                             (markdown-files))
                     (mapcat #(map (fn [reference] [% reference])
                                  (example-missing %))
                             (example-files)))]
    (->> occurrences
         (group-by (fn [[file {:keys [alias sym]}]]
                     [(display-path file) alias sym]))
         (map (fn [[[file alias sym] entries]]
                (str file ":" (apply min (map (comp :line second) entries))
                     " " alias "/" sym)))
         sort
         vec)))

(defn- baseline []
  (edn/read-string (slurp (io/resource "limner/baselines/doc_references.edn"))))

(defn reference-baseline-key
  "Remove the diagnostic line number to keep baseline identities stable."
  [reference]
  (str/replace reference #"^(.+):[0-9]+ " "$1 "))

(deftest reference-baseline-keys-ignore-moving-line-numbers
  (is (= "docs/tutorial.md core/read-char"
         (reference-baseline-key "docs/tutorial.md:736 core/read-char"))))

(deftest documented-references-match-the-shrinking-baseline
  (let [expected (set (baseline))
        actual-by-key (into {} (map (juxt reference-baseline-key identity))
                            (unresolved-references))
        actual (set (keys actual-by-key))
        new (sort (map actual-by-key (remove expected actual)))
        stale (sort (remove actual expected))]
    (is (empty? new)
        (str "New unresolved documentation references:\n" (str/join "\n" new)))
    (is (empty? stale)
        (str "Resolved references remain in the shrinking baseline; remove:\n"
             (str/join "\n" stale)))))
