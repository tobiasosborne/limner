(ns limner.quality-gate-support
  "Shared, side-effect-free parsing support for limner's local quality gates.

  Documentation checks need source locations and namespace aliases without
  executing the documents they inspect.  These helpers read Clojure forms with
  read-time evaluation disabled and retain Markdown fence line numbers so gate
  failures point back to the text that needs correction."
  (:require [clojure.string :as str])
  (:import [java.io PushbackReader StringReader]))

(defn read-forms
  "Read every form in source without permitting reader evaluation."
  [source]
  (let [source (str/replace source #"\A#![^\n]*(?:\n|\z)" "")
        eof (Object.)]
    (binding [*read-eval* false]
      (with-open [reader (PushbackReader. (StringReader. source))]
        (loop [forms []]
          (let [form (read reader false eof)]
            (if (identical? eof form)
              forms
              (recur (conj forms form)))))))))

(defn readable-forms
  "Read the complete prefix of a possibly fragmentary documentation block."
  [source]
  (let [source (str/replace source #"\A#![^\n]*(?:\n|\z)" "")
        eof (Object.)]
    (binding [*read-eval* false]
      (with-open [reader (PushbackReader. (StringReader. source))]
        (loop [forms []]
          (let [[status form] (try
                                [:read (read reader false eof)]
                                (catch RuntimeException _ [:invalid nil]))]
            (cond
              (= :invalid status) forms
              (identical? eof form) forms
              :else (recur (conj forms form)))))))))

(defn- unquote-form [form]
  (if (and (seq? form) (= 'quote (first form)))
    (second form)
    form))

(defn- alias-pair [libspec]
  (let [libspec (unquote-form libspec)]
    (when (vector? libspec)
      (let [namespace (first libspec)
            options (apply hash-map (rest libspec))]
        (when-let [alias (:as options)]
          [alias namespace])))))

(defn- ns-libspecs [form]
  (when (and (seq? form) (= 'ns (first form)))
    (mapcat rest
            (filter #(and (seq? %) (= :require (first %)))
                    (drop 2 form)))))

(defn- require-libspecs [form]
  (when (and (seq? form) (= 'require (first form)))
    (rest form)))

(defn require-aliases
  "Return alias-to-namespace mappings declared by ns or require forms."
  [forms]
  (into {}
        (keep alias-pair)
        (mapcat #(concat (ns-libspecs %) (require-libspecs %)) forms)))

(defn markdown-clojure-blocks
  "Extract Clojure fences with their heading, opening line and opt-out reason."
  [markdown]
  (let [lines (str/split-lines markdown)
        opening? #(re-matches #"\s*```(?:clojure|clj)\s*" %)
        closing? #(re-matches #"\s*```\s*" %)
        heading #(some->> (re-matches #"\s*#{1,6}\s+(.+?)\s*" %)
                          second)
        reason #(some->> (re-matches
                          #"\s*<!--\s*not-executed:\s*(.+?)\s*-->\s*" %)
                         second)]
    (loop [remaining lines
           line-number 1
           current-heading nil
           previous nil
           blocks []]
      (if-let [line (first remaining)]
        (if (opening? line)
          (let [body (take-while (complement closing?) (rest remaining))
                consumed (+ 2 (count body))
                block (cond-> {:heading current-heading
                               :line line-number
                               :code (str/join "\n" body)}
                        (reason previous) (assoc :skip-reason (reason previous)))]
            (recur (drop (inc (count body)) (rest remaining))
                   (+ line-number consumed)
                   current-heading
                   nil
                   (conj blocks block)))
          (recur (rest remaining)
                 (inc line-number)
                 (or (heading line) current-heading)
                 line
                 blocks))
        blocks))))
