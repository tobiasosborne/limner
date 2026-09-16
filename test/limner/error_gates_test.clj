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
    "src/limner/components/statusbar.clj"
    "src/limner/components/panel.clj"})

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

   ["src/limner/layout.clj" 'limner.layout/resolve-flex-constraints 'count]
   "Counts flex-item maps when distributing numeric space."
   ["src/limner/layout.clj" 'limner.layout/resolve-flex-constraints 'nth]
   "Indexes numeric floor and exact-share vectors."
   ["src/limner/layout.clj" 'limner.layout/stack 'count]
   "Counts component maps and indexes the resolved numeric height vector."
   ["src/limner/layout.clj" 'limner.layout/stack 'nth]
   "Indexes the resolved numeric height vector."
   ["src/limner/layout.clj" 'limner.layout/hsplit 'count]
   "Counts component maps when reserving inter-component spacing."
   ["src/limner/layout.clj" 'limner.layout/hsplit 'nth]
   "Indexes the resolved numeric width vector."
   ["src/limner/layout.clj" 'limner.layout/grid 'count]
   "Counts component maps to derive the grid row count."
   ["src/limner/layout.clj" 'limner.layout/pad-to-box 'count]
   "Counts already-rendered rows when padding height."

   ["src/limner/table.clj" 'limner.table/normalize-rows 'count]
   "Counts row cells to enforce rectangular structure."
   ["src/limner/table.clj" 'limner.table/normalize-aligns 'count]
   "Counts alignment entries against the numeric column count."
   ["src/limner/table.clj" 'limner.table/render-row 'nth]
   "Indexes per-column width and alignment vectors."
   ["src/limner/table.clj" 'limner.table/table 'count]
   "Counts rows and columns; cell strings are measured by limner.text."
   ["src/limner/table.clj" 'limner.table/table 'nth]
   "Indexes cells by numeric column; cell strings are measured by limner.text."

   ["src/limner/components/list.clj" 'limner.components.list/filtered-item-indices 'count]
   "Counts item records to construct their numeric indexes."
   ["src/limner/components/list.clj" 'limner.components.list/clamp-selection 'count]
   "Counts filtered item records to clamp a numeric selection."
   ["src/limner/components/list.clj" 'limner.components.list/select-item 'count]
   "Counts filtered item records to validate an index."
   ["src/limner/components/list.clj" 'limner.components.list/select-next 'count]
   "Counts filtered item records for selection navigation."
   ["src/limner/components/list.clj" 'limner.components.list/select-prev 'count]
   "Counts filtered item records for selection navigation."
   ["src/limner/components/list.clj" 'limner.components.list/select-last 'count]
   "Counts filtered item records to select the last index."
   ["src/limner/components/list.clj" 'limner.components.list/get-current-item 'count]
   "Counts filtered item records to validate a selection index."
   ["src/limner/components/list.clj" 'limner.components.list/adjust-scroll 'count]
   "Counts item records to clamp a viewport offset."
   ["src/limner/components/list.clj" 'limner.components.list/prefix-columns 'count]
   "Counts item records only to size the largest decimal index."
   ["src/limner/components/list.clj" 'limner.components.list/render-list 'count]
   "Counts rendered item rows when padding viewport height."
   ["src/limner/components/list.clj" 'limner.components.list/item-count 'count]
   "Returns the number of item records."
   ["src/limner/components/list.clj" 'limner.components.list/filtered-item-count 'count]
   "Returns the number of filtered item records."
   ["src/limner/components/list.clj" 'limner.components.list/selection-count 'count]
   "Counts selected numeric indexes."
   ["src/limner/components/list.clj" 'limner.components.list/remove-item 'count]
   "Counts item records to validate a removal index."

   ["src/limner/components/input.clj" 'limner.components.input/cluster-count 'count]
   "Counts grapheme records already segmented by limner.text."
   ["src/limner/components/input.clj" 'limner.components.input/history-prev 'count]
   "Counts history entries, not text."
   ["src/limner/components/input.clj" 'limner.components.input/history-next 'count]
   "Counts history entries, not text."
   ["src/limner/components/input.clj" 'limner.components.input/format-line-with-cursor 'count]
   "Counts grapheme records already segmented by limner.text."
   ["src/limner/components/input.clj" 'limner.components.input/format-line-with-cursor 'nth]
   "Indexes a grapheme record vector produced by limner.text."

   ["src/limner/components/progress.clj" 'limner.components.progress/tick 'count]
   "Counts animation frame records."
   ["src/limner/components/progress.clj" 'limner.components.progress/render-spinner 'nth]
   "Indexes an animation frame vector."
   ["src/limner/components/progress.clj" 'limner.components.progress/render-step-indicator 'count]
   "Counts label records before numeric indexing."
   ["src/limner/components/progress.clj" 'limner.components.progress/render-step-indicator 'nth]
   "Indexes the current label record."

   ["src/limner/components/panel.clj" 'limner.components.panel/render-expanded 'count]
   "Counts padded content rows for scrolling."
   ["src/limner/components/panel.clj" 'limner.components.panel/scroll-to 'count]
   "Counts content rows to clamp the viewport offset."
   ["src/limner/components/panel.clj" 'limner.components.panel/nest-panels 'count]
   "Counts child panel results before inserting separator rows."
   ["src/limner/components/panel.clj" 'limner.components.panel/can-scroll-down? 'count]
   "Counts content rows to compute the final viewport offset."})

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
            use (distinct (keep banned-use (walk form)))]
      (is (contains? allowed-component-uses [path name (symbol use)])
          (str path " / " name " uses " use "; route it through limner.text or "
               "add it to allowed-component-uses with a reason.")))))

(deftest component-text-allow-list-is-var-scoped
  (testing "every exception names one exact source var and one operation"
    (doseq [[allow-key reason] allowed-component-uses]
      (is (= 3 (count allow-key))
          (str "Over-broad text-operation exception " (pr-str allow-key)
               " could hide a future visible-text regression: " reason)))))
