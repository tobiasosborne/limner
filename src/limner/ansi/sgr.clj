(ns limner.ansi.sgr
  "Structured SGR operations from xterm's character-attributes table.

  ECMA-48 Section 8.3.117 defines the base attributes and resets. The xterm
  table in `reference/specs/xterm-ctlseqs.txt` adds bright colors and the
  ISO-8613-6 colon color forms plus compatible semicolon forms. Unknown or
  incomplete parameters remain explicit operations rather than disappearing.

  A token that is not a CSI sequence with final byte `m` throws `ex-info`
  with `:limner.ansi.sgr/invalid-token` (ADR 0004)."
  (:require [clojure.string :as str]))

(defn- split-on [^String value separator]
  (let [length (.length value)
        separator-code (int separator)]
    (loop [start 0, index 0, parts (transient [])]
      (if (= index length)
        (persistent! (conj! parts (subs value start index)))
        (if (= separator-code (int (.charAt value index)))
          (recur (inc index) (inc index) (conj! parts (subs value start index)))
          (recur start (inc index) parts))))))

(defn- digits? [^String value]
  (and (pos? (.length value))
       (loop [index 0]
         (or (= index (.length value))
             (let [code (int (.charAt value index))]
               (and (<= 0x30 code 0x39) (recur (inc index))))))))

(defn- number [value]
  (when (digits? value)
    (let [value (bigint value)]
      (if (<= value Long/MAX_VALUE) (long value) value))))

(defn- operation [attribute value raw]
  {:attribute attribute :value value :raw raw})

(defn- unknown [raw]
  (operation :unknown (or (number raw) raw) raw))

(defn- simple-operation [raw]
  (let [value (if (empty? raw) 0 (number raw))]
    (cond
      (nil? value) (unknown raw)
      (= 0 value) (operation :all :default raw)
      (= 1 value) (operation :intensity :bold raw)
      (= 2 value) (operation :intensity :faint raw)
      (= 3 value) (operation :italic true raw)
      (= 4 value) (operation :underline :single raw)
      (= 5 value) (operation :blink :slow raw)
      (= 7 value) (operation :inverse true raw)
      (= 8 value) (operation :hidden true raw)
      (= 9 value) (operation :crossed-out true raw)
      (= 21 value) (operation :underline :double raw)
      (= 22 value) (operation :intensity :normal raw)
      (= 23 value) (operation :italic false raw)
      (= 24 value) (operation :underline :none raw)
      (= 25 value) (operation :blink :none raw)
      (= 27 value) (operation :inverse false raw)
      (= 28 value) (operation :hidden false raw)
      (= 29 value) (operation :crossed-out false raw)
      (= 39 value) (operation :foreground :default raw)
      (= 49 value) (operation :background :default raw)
      (<= 30 value 37)
      (operation :foreground {:mode :ansi :index (- value 30) :bright false} raw)
      (<= 40 value 47)
      (operation :background {:mode :ansi :index (- value 40) :bright false} raw)
      (<= 90 value 97)
      (operation :foreground {:mode :ansi :index (- value 90) :bright true} raw)
      (<= 100 value 107)
      (operation :background {:mode :ansi :index (- value 100) :bright true} raw)
      :else (unknown raw))))

(defn- byte-value? [value]
  (and (number? value) (<= 0 value 255)))

(defn- color-operation [attribute mode values raw]
  (case mode
    5 (when (and (= 1 (count values)) (byte-value? (first values)))
        (operation attribute {:mode :indexed :index (first values)} raw))
    2 (when (and (= 3 (count values)) (every? byte-value? values))
        (operation attribute {:mode :rgb
                              :r (nth values 0)
                              :g (nth values 1)
                              :b (nth values 2)} raw))
    nil))

(defn- colon-color [raw]
  (let [parts (split-on raw \:)
        attribute (case (first parts) "38" :foreground "48" :background nil)
        mode (number (get parts 1 ""))
        values (case mode
                 5 (if (= 3 (count parts))
                     [(number (get parts 2 ""))]
                     [])
                 2 (let [components (case (count parts)
                                      5 (subvec parts 2 5)
                                      6 (subvec parts 3 6)
                                      [])]
                     (mapv number components))
                 [])]
    (or (and attribute (color-operation attribute mode values raw))
        (unknown raw))))

(defn- semicolon-color [parts index attribute]
  (let [mode (number (get parts (inc index) ""))
        needed (case mode 5 3, 2 5, 2)
        end (min (count parts) (+ index needed))
        raw (str/join ";" (subvec parts index end))
        values (case mode
                 5 [(number (get parts (+ index 2) ""))]
                 2 (mapv #(number (get parts % ""))
                         (range (+ index 2) (+ index 5)))
                 [])]
    [(or (color-operation attribute mode values raw) (unknown raw)) end]))

(defn parse [token]
  (when-not (and (map? token) (= :csi (:type token)) (= \m (:final token)))
    (throw (ex-info "SGR parsing requires a CSI token with final byte m"
                    {:limner/error :limner.ansi.sgr/invalid-token
                     :token token :action "Pass an SGR token from limner.ansi/tokenize"})))
  (let [parts (split-on (:params token) \;)]
    (loop [index 0, operations (transient [])]
      (if (= index (count parts))
        (persistent! operations)
        (let [part (nth parts index)]
          (cond
            (not= -1 (.indexOf ^String part ":"))
            (recur (inc index) (conj! operations (colon-color part)))

            (= "38" part)
            (let [[operation end] (semicolon-color parts index :foreground)]
              (recur end (conj! operations operation)))

            (= "48" part)
            (let [[operation end] (semicolon-color parts index :background)]
              (recur end (conj! operations operation)))

            :else
            (recur (inc index) (conj! operations (simple-operation part)))))))))
