(ns limner.text.columns
  "Grapheme-safe column operations over canonical styled spans.

  ADR 0003 requires segmentation across style boundaries, not once per span.
  These functions therefore build one display-text stream, retain source
  events at UTF-16 offsets, and attach them to Unicode 17 grapheme units. Raw
  controls never contribute width. Public callers use `limner.text`.

  Invalid input throws `ex-info` with an ADR 0004 class:
  `:limner.text/invalid-input`, `:limner.text/invalid-options`,
  `:limner.text/invalid-bounds`, `:limner.text/invalid-target`,
  `:limner.text/invalid-fill`, or `:limner.text/invalid-width`."
  (:refer-clojure :exclude [lines])
  (:require [limner.ansi :as ansi]
            [limner.text.style :as style]
            [limner.unicode.grapheme :as grapheme]
            [limner.unicode.width :as unicode-width]))

(def ^:private option-keys #{:ambiguous-width})

(defn- fail [error-class message data]
  (throw (ex-info message (assoc data :limner/error error-class))))

(defn options [value]
  (when-not (map? value)
    (fail :limner.text/invalid-options "Text width options must be a map"
          {:options value :allowed option-keys}))
  (when-let [unknown (seq (remove option-keys (keys value)))]
    (fail :limner.text/invalid-options "Text width options contain unsupported keys"
          {:options value :unsupported (vec unknown) :allowed option-keys}))
  (let [ambiguous (get value :ambiguous-width 1)]
    (when-not (#{1 2} ambiguous)
      (fail :limner.text/invalid-options "Text width options require :ambiguous-width to be 1 or 2"
            {:options value :value ambiguous}))
    {:ambiguous-width ambiguous}))

(defn- source [input]
  (let [spans (style/value input), plain (StringBuilder.)]
    (let [events
          (loop [remaining spans, offset 0, events []]
            (if-let [span (first remaining)]
              (if-let [text (:text span)]
                (do (.append plain ^String text)
                    (recur (next remaining) (+ offset (.length ^String text))
                           (conj events {:kind :text :start offset
                                         :end (+ offset (.length ^String text))
                                         :text text :style (:style span)})))
                (recur (next remaining) offset
                       (conj events {:kind :raw :offset offset :raw (:raw span)})))
              events))]
      {:events events :plain (str plain)})))

(defn- content-between [events start end]
  (reduce
   (fn [result event]
     (case (:kind event)
       :raw (if (and (<= start (:offset event)) (< (:offset event) end))
              (style/append-span result {:raw (:raw event)}) result)
       :text (let [from (max start (:start event)), to (min end (:end event))]
               (if (< from to)
                 (style/append-span
                  result {:text (subs ^String (:text event)
                                      (- from (:start event)) (- to (:start event)))
                          :style (:style event)})
                 result))))
   [] events))

(defn- raw-at-end [events offset]
  (reduce (fn [result event]
            (if (and (= :raw (:kind event)) (= offset (:offset event)))
              (style/append-span result {:raw (:raw event)}) result))
          [] events))

(defn- units [input width-options]
  (let [{:keys [events plain]} (source input)
        boundaries (grapheme/cluster-boundaries plain)
        ranges (partition 2 1 boundaries)]
    (loop [remaining ranges, column 0, result []]
      (if-let [[start end] (first remaining)]
        (let [cluster (subs ^String plain start end)
              cluster-width (unicode-width/cluster-width cluster width-options)]
          (recur (next remaining) (+ column cluster-width)
                 (conj result {:column column :width cluster-width :plain cluster
                               :content (content-between events start end)})))
        (let [trailing (raw-at-end events (.length ^String plain))]
          (cond-> result (seq trailing)
            (conj {:column column :width 0 :plain "" :content trailing})))))))

(defn- printable-ascii-width [^String input]
  (when (every? #(<= 0x20 (int %) 0x7E) input)
    (count input)))

(defn- simple-wide-width [^String input]
  (when (and (seq input)
             (every? (fn [unit]
                       (let [unit (int unit)]
                         (or (<= 0x3400 unit 0x4DBF)
                             (<= 0x4E00 unit 0x9FFF)
                             (<= 0xAC00 unit 0xD7A3))))
                     input))
    (* 2 (count input))))

(defn default-width [input]
  (if (string? input)
    (or (printable-ascii-width input)
        (simple-wide-width input)
        (unicode-width/string-width (ansi/strip input) {:ambiguous-width 1}))
    (do
      (when-not (vector? input)
        (fail :limner.text/invalid-input
              "Text width requires a string or styled-text vector"
              {:input input}))
      (let [{:keys [plain]} (source input)]
        (unicode-width/string-width plain {:ambiguous-width 1})))))

(defn width [input width-options]
  (when-not (or (string? input) (vector? input))
    (fail :limner.text/invalid-input
          "Text width requires a string or styled-text vector" {:input input}))
  (let [width-options (options width-options)]
    (if (string? input)
      (or (printable-ascii-width input)
          (simple-wide-width input)
          (unicode-width/string-width (ansi/strip input) width-options))
      (let [{:keys [plain]} (source input)]
        (unicode-width/string-width plain width-options)))))

(defn- validate-bounds [start end]
  (when-not (and (integer? start) (integer? end) (<= 0 start end))
    (fail :limner.text/invalid-bounds "Column slice requires non-negative ordered integer bounds"
          {:start start :end end})))

(defn- zero-anchor-in-slice? [column start end]
  (and (< start end)
       (or (and (zero? column) (zero? start))
           (and (< start column) (<= column end)))))

(defn- partial-content [content covered]
  (loop [remaining content, replaced? false, result []]
    (if-let [span (first remaining)]
      (if-let [raw (:raw span)]
        (recur (next remaining) replaced? (style/append-span result {:raw raw}))
        (if replaced?
          (recur (next remaining) true result)
          (recur (next remaining) true
                 (style/append-span result {:text (apply str (repeat covered " "))
                                            :style (:style span)}))))
      result)))

(defn slice-columns [input start end width-options]
  (validate-bounds start end)
  (let [width-options (options width-options)]
    (reduce
     (fn [result {:keys [column width content]}]
       (if (zero? width)
         (if (zero-anchor-in-slice? column start end)
           (reduce style/append-span result content) result)
         (let [covered (- (min end (+ column width)) (max start column))]
           (cond
             (not (pos? covered)) result
             (= covered width) (reduce style/append-span result content)
             :else (reduce style/append-span result (partial-content content covered))))))
     [] (units input width-options))))

(defn- target-width [target]
  (when-not (and (integer? target) (<= 0 target))
    (fail :limner.text/invalid-target "Target width must be a non-negative integer"
          {:target target})))

(defn- fill-info [fill width-options]
  (let [fill (style/value fill), fill-units (units fill width-options)
        display-units (filter #(pos? (:width %)) fill-units)]
    (when-not (and (= 1 (count display-units))
                   (= (count fill-units) (count display-units)))
      (fail :limner.text/invalid-fill
            "Padding fill must be exactly one positive-width grapheme cluster"
            {:fill fill}))
    {:value fill :width (:width (first display-units))}))

(defn- padding [amount fill width-options]
  (if (zero? amount)
    []
    (let [{fill-value :value fill-width :width} (fill-info fill width-options)
          repetitions (quot (+ amount (dec fill-width)) fill-width)]
      (slice-columns (apply style/concat (repeat repetitions fill-value))
                     0 amount width-options))))

(defn pad-right [input target fill width-options]
  (target-width target)
  (let [width-options (options width-options), value (style/value input)
        current (width value width-options)]
    (if (<= target current) value
        (style/concat value (padding (- target current) fill width-options)))))

(defn pad-left [input target fill width-options]
  (target-width target)
  (let [width-options (options width-options), value (style/value input)
        current (width value width-options)]
    (if (<= target current) value
        (style/concat (padding (- target current) fill width-options) value))))

(defn center [input target fill width-options]
  (target-width target)
  (let [width-options (options width-options), value (style/value input)
        current (width value width-options), total (max 0 (- target current))
        left (quot total 2), right (- total left)]
    (if (zero? total) value
        (style/concat (padding left fill width-options) value
                      (padding right fill width-options)))))

(defn truncate [input target ellipsis width-options]
  (target-width target)
  (let [width-options (options width-options), value (style/value input)
        current (width value width-options)]
    (if (<= current target)
      value
      (let [ellipsis (style/value ellipsis), ellipsis-width (width ellipsis width-options)]
        (if (< target ellipsis-width)
          (slice-columns ellipsis 0 target width-options)
          (style/concat (slice-columns value 0 (- target ellipsis-width) width-options)
                        ellipsis))))))

(defn lines [input]
  (let [all-units (units input {:ambiguous-width 1})]
    (loop [remaining all-units, current [], result []]
      (if-let [{:keys [plain content]} (first remaining)]
        (if (#{"\n" "\r\n"} plain)
          (recur (next remaining) []
                 (conj result (reduce style/append-span current (filter :raw content))))
          (recur (next remaining) (reduce style/append-span current content) result))
        (conj result current)))))

(defn- whitespace-unit? [{:keys [plain]}]
  (and (seq plain) (every? #(Character/isWhitespace ^char %) plain)))

(defn- unit-runs [line width-options]
  (reduce (fn [runs unit]
            (let [space? (whitespace-unit? unit), previous (peek runs)]
              (if (and previous (= space? (:space? previous)))
                (conj (pop runs) (-> previous (update :units conj unit)
                                      (update :width + (:width unit))))
                (conj runs {:space? space? :units [unit] :width (:width unit)}))))
          [] (units line width-options)))

(defn- units-content [values]
  (reduce (fn [result unit] (reduce style/append-span result (:content unit))) [] values))

(defn- split-word [word-units limit]
  (loop [remaining word-units, current [], current-width 0, chunks []]
    (if-let [unit (first remaining)]
      (let [next-width (+ current-width (:width unit))]
        (if (and (seq current) (> next-width limit))
          (recur remaining [] 0 (conj chunks (units-content current)))
          (recur (next remaining) (conj current unit) next-width chunks)))
      (cond-> chunks (seq current) (conj (units-content current))))))

(defn- wrap-line [line limit width-options]
  (let [runs (unit-runs line width-options)]
    (loop [remaining runs, current [], current-width 0, pending nil, result []]
      (if-let [run (first remaining)]
        (if (:space? run)
          (recur (next remaining) current current-width run result)
          (let [space-width (if (seq current) (or (:width pending) 0) 0)
                fits? (<= (+ current-width space-width (:width run)) limit)]
            (cond
              fits?
              (recur (next remaining)
                     (if (and (seq current) pending)
                       (style/concat current (units-content (:units pending))
                                     (units-content (:units run)))
                       (style/concat current (units-content (:units run))))
                     (+ current-width space-width (:width run)) nil result)

              (<= (:width run) limit)
              (recur (next remaining) (units-content (:units run)) (:width run) nil
                     (cond-> result (seq current) (conj current)))

              :else
              (let [chunks (split-word (:units run) limit), prefix (cond-> result (seq current) (conj current))]
                (recur (next remaining) (or (peek chunks) [])
                       (if (seq chunks) (width (peek chunks) width-options) 0) nil
                       (into prefix (butlast chunks)))))))
        (cond-> result (or (seq current) (empty? result)) (conj current))))))

(defn wrap [input limit width-options]
  (when-not (and (integer? limit) (pos? limit))
    (fail :limner.text/invalid-width "Wrap width must be a positive integer" {:width limit}))
  (let [width-options (options width-options)]
    (vec (mapcat #(wrap-line % limit width-options) (lines input)))))
