(ns limner.text.style
  "Canonical styled spans and semantic ECMA-48 rendering.

  This is the stateful half of ADR 0003: ANSI tokens update one style map,
  while unsupported controls remain opaque spans. Rendering derives every SGR
  parameter from that map, so no stored reset string can clobber an enclosing
  style. Public callers use `limner.text`; this namespace exposes the small
  internal seam used by column operations.

  Invalid input throws `ex-info` with an ADR 0004 class:
  `:limner.text.style/invalid-color`, `:limner.text.style/invalid-style`,
  `:limner.text.style/invalid-span`, or `:limner.text.style/invalid-input`."
  (:refer-clojure :exclude [concat])
  (:require [clojure.string :as str]
            [limner.ansi :as ansi]))

(def named-colors
  [:black :red :green :yellow :blue :magenta :cyan :white
   :bright-black :bright-red :bright-green :bright-yellow
   :bright-blue :bright-magenta :bright-cyan :bright-white])

(def ^:private named-color-set (set named-colors))
(def ^:private style-keys
  #{:fg :bg :bold :dim :italic :underline :blink :inverse :hidden :strike :link})
(def ^:private boolean-keys #{:bold :dim :italic :inverse :hidden :strike})

(defn- fail [error-class message data]
  (throw (ex-info message (assoc data :limner/error error-class))))

(defn- byte? [value]
  (and (integer? value) (<= 0 value 255)))

(defn- normalize-color [key value]
  (cond
    (named-color-set value) value
    (and (map? value) (= #{:index} (set (keys value))) (byte? (:index value))) value
    (and (map? value) (= #{:rgb} (set (keys value)))
         (vector? (:rgb value)) (= 3 (count (:rgb value))) (every? byte? (:rgb value))) value
    :else (fail :limner.text.style/invalid-color
                "Styled text has an invalid color value"
                {:style-key key :value value
                 :action "Use a named color, {:index 0..255}, or {:rgb [r g b]}"})))

(defn normalize-style [style]
  (when-not (map? style)
    (fail :limner.text.style/invalid-style "Styled text requires a style map" {:style style}))
  (when-let [unknown (seq (remove style-keys (keys style)))]
    (fail :limner.text.style/invalid-style
          "Styled text contains unsupported style keys"
          {:style style :unsupported (vec unknown) :allowed (sort style-keys)}))
  (reduce-kv
   (fn [result key value]
     (cond
       (nil? value) result
       (boolean-keys key)
       (cond (= true value) (assoc result key true)
             (= false value) result
             :else (fail :limner.text.style/invalid-style
                         "Styled text boolean style must be true, false, or absent"
                         {:style-key key :value value}))
       (#{:fg :bg} key) (assoc result key (normalize-color key value))
       (= :underline key)
       (case value true (assoc result key :single)
         false result, :single (assoc result key value), :double (assoc result key value)
         (fail :limner.text.style/invalid-style
               "Styled text underline must be :single or :double"
               {:style-key key :value value}))
       (= :blink key)
       (case value true (assoc result key :slow)
         false result, :slow (assoc result key value)
         (fail :limner.text.style/invalid-style
               "Styled text blink must be :slow"
               {:style-key key :value value}))
       (= :link key)
       (if (and (string? value) (seq value))
         (assoc result key value)
         (fail :limner.text.style/invalid-style
               "Styled text link must be a non-empty URI string"
               {:style-key key :value value}))))
   {}
   style))

(defn append-span [spans span]
  (cond
    (contains? span :text)
    (let [value (:text span), style (normalize-style (:style span))]
      (when-not (string? value)
        (fail :limner.text.style/invalid-span
              "Styled text span requires string :text" {:span span}))
      (if (empty? value)
        spans
        (let [span {:text value :style style}, previous (peek spans)]
          (if (and (contains? previous :text) (= style (:style previous)))
            (conj (pop spans) (update previous :text str value))
            (conj spans span)))))

    (contains? span :raw)
    (let [value (:raw span)]
      (when-not (string? value)
        (fail :limner.text.style/invalid-span
              "Opaque styled-text span requires string :raw" {:span span}))
      (if (empty? value) spans (conj spans {:raw value})))

    :else (fail :limner.text.style/invalid-span
                "Styled text span requires :text or :raw" {:span span})))

(defn canonical [spans]
  (when-not (vector? spans)
    (fail :limner.text.style/invalid-input
          "Styled text must be a vector of spans" {:value spans}))
  (reduce append-span [] spans))

(defn- ansi-color [value]
  (case (:mode value)
    :ansi (nth named-colors (+ (:index value) (if (:bright value) 8 0)))
    :indexed {:index (:index value)}
    :rgb {:rgb [(:r value) (:g value) (:b value)]}))

(defn- apply-operation [style {:keys [attribute value]}]
  (case attribute
    :all (select-keys style [:link])
    :intensity (case value :bold (assoc style :bold true)
                 :faint (assoc style :dim true)
                 :normal (dissoc style :bold :dim))
    :italic (if value (assoc style :italic true) (dissoc style :italic))
    :underline (if (= :none value) (dissoc style :underline)
                   (assoc style :underline value))
    :blink (if (= :none value) (dissoc style :blink)
               (assoc style :blink value))
    :inverse (if value (assoc style :inverse true) (dissoc style :inverse))
    :hidden (if value (assoc style :hidden true) (dissoc style :hidden))
    :crossed-out (if value (assoc style :strike true) (dissoc style :strike))
    :foreground (if (= :default value) (dissoc style :fg)
                    (assoc style :fg (ansi-color value)))
    :background (if (= :default value) (dissoc style :bg)
                    (assoc style :bg (ansi-color value)))
    style))

(defn from-ansi [input]
  (when-not (string? input)
    (fail :limner.text.style/invalid-input
          "ANSI styled-text parsing requires a string" {:text input}))
  (loop [tokens (seq (ansi/tokenize input)), style {}, spans []]
    (if-let [token (first tokens)]
      (cond
        (= :text (:type token))
        (recur (next tokens) style (append-span spans {:text (:text token) :style style}))

        (and (= :csi (:type token)) (= \m (:final token)) (empty? (:intermediates token)))
        (let [operations (ansi/sgr-params token)
              style (reduce apply-operation style operations)
              spans (reduce (fn [result operation]
                              (if (= :unknown (:attribute operation))
                                (append-span result {:raw (str "\u001B[" (:raw operation) "m")})
                                result))
                            spans operations)]
          (recur (next tokens) style spans))

        (= :osc (:type token))
        (if-let [{:keys [uri]} (ansi/hyperlink token)]
          (recur (next tokens) (if uri (assoc style :link uri) (dissoc style :link)) spans)
          (recur (next tokens) style (append-span spans {:raw (:raw token)})))

        :else (recur (next tokens) style (append-span spans {:raw (:raw token)})))
      spans)))

(defn value [input]
  (cond (string? input) (from-ansi input)
        (vector? input) (canonical input)
        :else (fail :limner.text.style/invalid-input
                    "Text operation requires a string or styled-text vector"
                    {:value input})))

(defn concat [& values]
  (reduce (fn [result input] (reduce append-span result (value input))) [] values))

(defn style [outer input]
  (let [outer (normalize-style outer)]
    (reduce (fn [result span]
              (append-span result (if (contains? span :text)
                                    (update span :style #(merge outer %))
                                    span)))
            [] (value input))))

(defn styled [input style-map]
  (when-not (string? input)
    (fail :limner.text.style/invalid-input "styled requires a string" {:text input}))
  (style style-map (from-ansi input)))

(def ^:private color-indices (zipmap named-colors (range)))

(defn- color-parameter [base bright-base extended-base value]
  (cond
    (keyword? value) (let [index (color-indices value)]
                       (str (+ (if (< index 8) base bright-base) (mod index 8))))
    (:index value) (str extended-base ";5;" (:index value))
    (:rgb value) (str extended-base ";2;" (str/join ";" (:rgb value)))))

(defn- intensity-params [current desired]
  (let [old (select-keys current [:bold :dim]), new (select-keys desired [:bold :dim])]
    (cond (= old new) []
          (every? #(contains? new %) (keys old))
          (cond-> [] (and (:bold new) (not (:bold old))) (conj "1")
                     (and (:dim new) (not (:dim old))) (conj "2"))
          :else (cond-> ["22"] (:bold new) (conj "1") (:dim new) (conj "2")))))

(defn- attribute-param [current desired key on-values off]
  (when (not= (get current key) (get desired key))
    (if-let [value (get desired key)] (get on-values value) off)))

(defn- sgr-transition [current desired]
  (let [params (into (intensity-params current desired)
                     (keep identity
                           [(attribute-param current desired :italic {true "3"} "23")
                            (attribute-param current desired :underline {:single "4" :double "21"} "24")
                            (attribute-param current desired :blink {:slow "5"} "25")
                            (attribute-param current desired :inverse {true "7"} "27")
                            (attribute-param current desired :hidden {true "8"} "28")
                            (attribute-param current desired :strike {true "9"} "29")
                            (when (not= (:fg current) (:fg desired))
                              (if-let [color (:fg desired)] (color-parameter 30 90 38 color) "39"))
                            (when (not= (:bg current) (:bg desired))
                              (if-let [color (:bg desired)] (color-parameter 40 100 48 color) "49"))]))]
    (when (seq params) (str "\u001B[" (str/join ";" params) "m"))))

(defn- link-transition [current desired]
  (when (not= (:link current) (:link desired))
    (str (when (:link current) "\u001B]8;;\u001B\\")
         (when-let [uri (:link desired)] (str "\u001B]8;;" uri "\u001B\\")))))

(defn to-ansi [input]
  (let [spans (value input), output (StringBuilder.)]
    (loop [remaining (seq spans), current {}]
      (if-let [span (first remaining)]
        (if-let [raw (:raw span)]
          (do (.append output ^String raw) (recur (next remaining) current))
          (let [desired (:style span)]
            (when-let [transition (link-transition current desired)] (.append output ^String transition))
            (when-let [transition (sgr-transition current desired)] (.append output ^String transition))
            (.append output ^String (:text span))
            (recur (next remaining) desired)))
        (do
          (when (:link current) (.append output "\u001B]8;;\u001B\\"))
          (when (seq (dissoc current :link)) (.append output "\u001B[0m"))
          (str output))))))
