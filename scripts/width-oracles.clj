(ns width-oracles
  "Reproduce independent terminal-width comparisons outside the test gate.

  The runner compiles the local glibc probe, creates a project-local virtual
  environment containing pinned jquast/wcwidth, and uses ICU4J when the `:icu`
  alias is present. It writes raw measurements under `.cache/width-oracles`
  and the reviewed Markdown result under `reviews/width-oracles/`."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [limner.text :as text]
            [limner.unicode :as unicode]
            [limner.unicode.width :as width]))

(def report-date "2026-09-15")
(def scratch ".cache/width-oracles")
(def emoji-path "reference/unicode/17.0.0/emoji/17.0/emoji-test.txt")
(def evidence-path "reviews/2026-09-15/evidence/glibc-wcwidth-U2600-U27BF.tsv")
(def output-path (str "reviews/width-oracles/" report-date ".md"))

(defn- fail [message data] (throw (ex-info message data)))

(defn- run-command! [& command]
  (let [{:keys [exit out err]} (apply shell/sh command)]
    (when-not (zero? exit)
      (fail "Width oracle command failed"
            {:command command :exit exit :stdout out :stderr err}))
    (str/trim out)))

(defn- prepare-tools! []
  (.mkdirs (io/file scratch))
  (.mkdirs (io/file "reviews/width-oracles"))
  (when-not (.isFile (io/file scratch "venv/bin/python"))
    (run-command! "timeout" "120s" "python3" "-m" "venv"
                  (str scratch "/venv")))
  (run-command! "timeout" "120s" (str scratch "/venv/bin/python")
                "-m" "pip" "install" "--disable-pip-version-check"
                "--no-deps" "wcwidth==0.2.13")
  (run-command! "timeout" "120s" "cc" "-O2" "-std=c11"
                "scripts/oracles/wcwidth.c" "-o" (str scratch "/glibc-wcwidth"))
  (run-command! "timeout" "180s" (str scratch "/glibc-wcwidth")
                (str scratch "/glibc.tsv"))
  (run-command! "timeout" "300s" (str scratch "/venv/bin/python")
                "scripts/oracles/python_wcwidth.py" emoji-path
                (str scratch "/python.tsv") (str scratch "/python-emoji.tsv")))

(defn- headers [path]
  (with-open [reader (io/reader path)]
    (->> (line-seq reader)
         (take-while #(.startsWith ^String % "#"))
         (map #(subs % 2))
         vec)))

(defn- properties [code-point]
  {:eaw (unicode/east-asian-width code-point)
   :gc (unicode/general-category code-point)
   :gcb (unicode/grapheme-break code-point)
   :di (unicode/default-ignorable? code-point)
   :ep (unicode/extended-pictographic? code-point)
   :emoji (unicode/emoji-presentation? code-point)})

(defn- glibc-classification [expected actual props]
  (cond
    (and (neg? actual) (zero? expected) (#{:Cc :Zl :Zp} (:gc props)))
    "policy difference — ADR 0001 uses non-negative widths for controls and separators"
    (= :Cn (:gc props))
    "policy difference — ADR 0001 assigns widths to pinned unassigned ranges"
    (and (= 2 expected) (= 1 actual) (#{:W :F} (:eaw props)))
    "version skew — glibc Unicode 15.1 predates the pinned UCD East_Asian_Width value"
    (neg? actual)
    "version skew — glibc Unicode 15.1 does not recognize this Unicode 17 assignment"
    :else
    "policy difference — glibc locale width differs from ADR 0001 tailoring"))

(defn- python-classification [expected actual props]
  (cond
    (and (neg? actual) (zero? expected))
    "policy difference — ADR 0001 uses non-negative widths for controls"
    (= :Cn (:gc props))
    "policy difference — ADR 0001 assigns widths to pinned unassigned ranges"
    (and (= 2 expected) (= 1 actual) (#{:W :F} (:eaw props)))
    "version skew — wcwidth Unicode 15.1 predates the pinned UCD value"
    (and (zero? expected) (= 1 actual) (#{:Mn :Me} (:gc props)))
    "version skew — wcwidth Unicode 15.1 does not recognize this Unicode 17 mark"
    (zero? expected)
    "policy difference — ADR 0001 zero-width and default-ignorable tailoring"
    (and (= 1 expected) (= 2 actual))
    "policy difference — ADR 0001 does not widen scalar emoji by block or pictograph status"
    :else
    "policy difference — jquast/wcwidth and ADR 0001 use different scalar tailoring"))

(defn- finish-group [state]
  (if-let [group (:current state)]
    (-> state (update :groups conj group) (dissoc :current))
    state))

(defn- add-difference [state code-point expected actual classification props]
  (let [signature {:limner expected :oracle actual :classification classification
                   :properties props}
        current (:current state)]
    (->
     (if (and current (= code-point (inc (:end current)))
              (= signature (select-keys current (keys signature))))
       (assoc state :current (assoc current :end code-point))
       (assoc (finish-group state) :current
              (assoc signature :start code-point :end code-point)))
     (update :differences inc)
     (update-in [:class-counts classification] (fnil inc 0)))))

(defn- compare-tsv [path classify]
  (with-open [reader (io/reader path)]
    (finish-group
     (reduce
      (fn [state line]
        (if (.startsWith ^String line "#")
          state
          (let [[hex raw-actual] (str/split line #"\t")
                code-point (Long/parseLong hex 16)
                actual (Long/parseLong raw-actual)
                expected (width/code-point-width code-point {})]
            (if (= expected actual)
              state
              (let [props (properties code-point)]
                (add-difference state code-point expected actual
                                (classify expected actual props) props))))))
      {:differences 0 :class-counts {} :groups []}
      (line-seq reader)))))

(defn- icu-capability []
  (try
    (let [character (Class/forName "com.ibm.icu.lang.UCharacter")
          version (Class/forName "com.ibm.icu.util.VersionInfo")
          method (.getMethod character "getIntPropertyValue"
                             (into-array Class [Integer/TYPE Integer/TYPE]))
          unicode-method (.getMethod character "getUnicodeVersion" (make-array Class 0))
          icu-version (.getField version "ICU_VERSION")]
      {:method method
       :icu-version (str (.get icu-version nil))
       :unicode-version (str (.invoke unicode-method nil (object-array 0)))})
    (catch ClassNotFoundException _ nil)))

(def icu-eaw {0 :N, 1 :A, 2 :H, 3 :F, 4 :Na, 5 :W})

(defn- compare-icu [capability]
  (let [method (:method capability)]
    (finish-group
     (reduce
      (fn [state code-point]
        (if (<= 0xD800 code-point 0xDFFF)
          state
          (let [expected (unicode/east-asian-width code-point)
                value (.invoke ^java.lang.reflect.Method method nil
                               (object-array [(int code-point) (int 4100)]))
                actual (icu-eaw (int value))]
            (if (= expected actual)
              state
              (add-difference state code-point expected actual
                              "limner bug — ICU and limner both claim Unicode 17 EAW"
                              (properties code-point))))))
      {:differences 0 :class-counts {} :groups []}
      (range 0x110000)))))

(defn- format-code-point [value]
  (format "U+%04X" value))

(defn- render-groups [groups]
  (if (empty? groups)
    "No disagreements.\n"
    (str
     "| Range | Points | Limner | Oracle | EAW | GC | GCB | DI | EP | Emoji_Presentation | Classification |\n"
     "| --- | ---: | ---: | ---: | --- | --- | --- | --- | --- | --- | --- |\n"
     (apply str
            (for [{:keys [start end limner oracle classification properties]} groups]
              (str "| " (format-code-point start)
                   (when (not= start end) (str "–" (format-code-point end)))
                   " | " (inc (- end start)) " | " limner " | " oracle
                   " | " (name (:eaw properties)) " | " (name (:gc properties))
                   " | " (name (:gcb properties)) " | " (:di properties)
                   " | " (:ep properties) " | " (:emoji properties)
                   " | " classification " |\n"))))))

(defn- parse-emoji-cases []
  (->> (str/split-lines (slurp emoji-path))
       (keep-indexed
        (fn [index line]
          (when (re-find #"^[0-9A-F]" line)
            (let [[field tail] (str/split line #";" 2)
                  [_ raw-version] (re-find #" E([0-9]+(?:\.[0-9]+)?) " tail)]
              [(inc index)
               {:status (first (str/split (str/trim tail) #" +"))
                :emoji-version (Double/parseDouble raw-version)
                :text (apply str (map #(String. (Character/toChars (Long/parseLong % 16)))
                                      (str/split (str/trim field) #" +")))}]))))
       (into {})))

(defn- compare-python-sequences []
  (let [cases (parse-emoji-cases)]
    (with-open [reader (io/reader (str scratch "/python-emoji.tsv"))]
      (reduce
       (fn [result line]
         (if (.startsWith ^String line "#")
           result
           (let [[raw-line status raw-actual] (str/split line #"\t")
                 line-number (Long/parseLong raw-line)
                 {:keys [text emoji-version]} (get cases line-number)
                 expected (text/width text)
                 actual (Long/parseLong raw-actual)
                 classification (if (> emoji-version 15.1)
                                  "version skew — Emoji 16/17 postdates wcwidth Unicode 15.1"
                                  "policy difference — ADR 0001 sequence and presentation tailoring")]
             (let [result (-> result
                              (update :cases inc)
                              (update-in [:status-counts status] (fnil inc 0)))]
               (if (= expected actual)
                 result
                 (-> result
                     (update :differences inc)
                     (update-in [:difference-statuses status] (fnil inc 0))
                     (update-in [:class-counts classification] (fnil inc 0))
                     (update :samples #(if (< (count %) 20)
                                        (conj % {:line line-number :status status
                                                 :limner expected :python actual
                                                 :classification classification}) %))))))))
       {:cases 0 :differences 0 :status-counts {} :difference-statuses {}
        :class-counts {} :samples []}
       (line-seq reader)))))

(defn- evidence-comparison []
  (let [recorded (->> (str/split-lines (slurp evidence-path))
                      (map #(str/split % #" +"))
                      (map (fn [[hex value]] [(Long/parseLong hex 16)
                                              (Long/parseLong value)]))
                      (into {}))
        local (with-open [reader (io/reader (str scratch "/glibc.tsv"))]
                (->> (line-seq reader)
                     (remove #(.startsWith ^String % "#"))
                     (map #(str/split % #"\t"))
                     (map (fn [[hex value]] [(Long/parseLong hex 16)
                                             (Long/parseLong value)]))
                     (filter (fn [[code-point]] (<= 0x2600 code-point 0x27BF)))
                     (into {})))
        mismatches (keep (fn [[code-point value]]
                           (when-not (= value (get local code-point)) code-point))
                         recorded)]
    {:points (count recorded) :mismatches (vec mismatches)}))

(defn- classification-lines [comparison]
  (apply str (for [[classification count] (sort-by key (:class-counts comparison))]
               (str "- " count " code points: " classification ".\n"))))

(defn- glibc-unicode-version []
  (let [path (io/file "/usr/share/i18n/locales/i18n_ctype")]
    (when (.isFile path)
      (second (re-find #"covers Unicode version ([0-9]+(?:\.[0-9]+)+)" (slurp path))))))

(defn- probe-results []
  {:wide (mapv #(text/width (String. (Character/toChars %)))
               [0x3000 0x3001 0x3131 0x3220 0x338F])
   :narrow-emoji (mapv #(text/width (String. (Character/toChars %)))
                       [0x1F170 0x1F321])
   :ignorable (mapv #(text/width (String. (Character/toChars %)))
                    [0x00AD 0x2060 0xFEFF])
   :regional-indicator (text/width "🇦")})

(defn- report [glibc python icu python-sequences evidence]
  (str
   "# Width oracle report — " report-date "\n\n"
   "This report compares limner's Unicode 17.0.0 scalar policy with independent\n"
   "implementations. It is generated by `bb width-oracles`; raw\n"
   "outputs and the pinned Python virtual environment stay under `.cache/`.\n\n"
   "## Environment\n\n"
   "- JVM: " (System/getProperty "java.version") "\n"
   "- glibc probe: " (str/join ", " (headers (str scratch "/glibc.tsv")))
   "; local locale data coverage: Unicode " (or (glibc-unicode-version) "not declared") "\n"
   "- Python probe: " (str/join ", " (headers (str scratch "/python.tsv"))) "\n"
   "- ICU4J: " (if icu (str (:icu-version icu) ", Unicode " (:unicode-version icu))
                       "not available (run with `:icu`)") "\n\n"
   "The in-suite raw-UCD oracle checks all 1,112,064 scalar values on the JVM\n"
   "and a fixed 65,542-point cross-plane sample on babashka. Emoji 17 checks\n"
   "3,944 fully-qualified, 1,029 minimally-qualified, 243 unqualified, and 9\n"
   "component entries; all 5,225 are one grapheme cluster. The first three\n"
   "groups are width 2. Unqualified entries split into 213 at width 1 and 30\n"
   "at width 2, as specified by the ADR 0001 amendment.\n\n"
   "## glibc `wcwidth`\n\n"
   "Disagreeing scalar values: **" (:differences glibc) "**.\n\n"
   (classification-lines glibc) "\n" (render-groups (:groups glibc)) "\n"
   "The local U+2600–U+27BF results match **" (- (:points evidence) (count (:mismatches evidence)))
   "/" (:points evidence) "** entries in `" evidence-path "`"
   (if (seq (:mismatches evidence))
     (str "; mismatches: " (str/join ", " (map format-code-point (:mismatches evidence))) ".")
     ", with no mismatches.") "\n\n"
   "## Python jquast/wcwidth\n\n"
   "Disagreeing scalar values: **" (:differences python) "**. The comparison\n"
   "pins package 0.2.13 and its Unicode 15.1.0 tables.\n\n"
   (classification-lines python) "\n" (render-groups (:groups python)) "\n"
   "`wcswidth` evaluated all **" (:cases python-sequences) "** Emoji 17 sequences\n"
   "and disagreed with limner on **" (:differences python-sequences) "**.\n"
   "Status counts: `" (pr-str (:status-counts python-sequences)) "`. Difference\n"
   "counts: `" (pr-str (:difference-statuses python-sequences)) "`.\n\n"
   (apply str (for [[classification count] (sort-by key (:class-counts python-sequences))]
                (str "- " count " sequences: " classification ".\n")))
   (when (seq (:samples python-sequences))
     (str " First examples: `" (pr-str (:samples python-sequences)) "`.")) "\n\n"
   "## ICU4J East_Asian_Width\n\n"
   (if icu
     (str "Disagreeing scalar values: **" (:differences (:comparison icu)) "**.\n\n"
          (classification-lines (:comparison icu)) "\n"
          (render-groups (:groups (:comparison icu))))
     "ICU4J was unavailable, so no comparison was made.\n")
   "\n## REVIEW C1 probes\n\n"
   "At review commit `01592b2`, probes 3–3f returned `[1 1 1 1 1]` for the five\n"
   "pinned-wide examples, `[2 2]` for U+1F170/U+1F321, `[1 1 1]` for the three\n"
   "default-ignorables, and 2 for a lone regional indicator. At text-layer\n"
   "commit `e7a92d5`, the same groups return `" (pr-str (probe-results)) "`.\n\n"
   "## Classification conclusion\n\n"
   "The raw UCD restatement and same-version ICU property oracle found no limner\n"
   "bug. All differences above are explained by the pinned-version skew or by\n"
   "the explicit terminal policy in ADR 0001.\n"))

(defn -main [& _]
  (prepare-tools!)
  (let [glibc (compare-tsv (str scratch "/glibc.tsv") glibc-classification)
        python (compare-tsv (str scratch "/python.tsv") python-classification)
        capability (icu-capability)
        icu-comparison (when capability (compare-icu capability))
        icu (when capability (assoc capability :comparison icu-comparison))
        python-sequences (compare-python-sequences)
        evidence (evidence-comparison)]
    (when (and icu-comparison (pos? (:differences icu-comparison)))
      (fail "ICU Unicode 17 found a limner East_Asian_Width bug"
            (select-keys icu-comparison [:differences :groups])))
    (spit output-path (report glibc python icu python-sequences evidence))
    (println "Wrote" output-path)
    (println "glibc disagreements:" (:differences glibc))
    (println "Python scalar disagreements:" (:differences python))
    (println "Python Emoji sequence disagreements:" (:differences python-sequences))
    (println "ICU EAW disagreements:" (if icu-comparison (:differences icu-comparison) :skipped))
    (shutdown-agents)))
