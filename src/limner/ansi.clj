(ns limner.ansi
  "ECMA-48 control-sequence tokenization for terminal text.

  The grammar comes from `reference/specs/ecma-48-5th-edition.pdf`, chiefly
  Sections 5.3, 5.4, 5.6, and the control definitions in 8.3. Xterm's concrete
  SGR and OSC practices come from `reference/specs/xterm-ctlseqs.txt`.

  `tokenize` is a total, lossless scanner: concatenating every token's `:raw`
  value always reconstructs the input. CSI parameter bytes are U+0030..U+003F,
  intermediates are U+0020..U+002F, and finals are U+0040..U+007E. OSC accepts
  xterm's BEL terminator as well as 7-bit or 8-bit ST; DCS, APC, PM, and SOS use
  ST. Unterminated prefixes become `:incomplete` instead of disappearing.

  The input is already a Clojure string, so byte provenance is unavailable.
  Limner recognizes decoded U+0080..U+009F as the 8-bit C1 controls required by
  ECMA-48. Xterm notes that raw C1 bytes are ambiguous inside UTF-8 byte
  streams; callers decoding bytes should choose that policy before calling
  this character-level tokenizer. LF and TAB remain text because downstream
  layout interprets them structurally; other C0/C1 controls are tokens.

  A compiled character-class matcher only locates the next control boundary;
  it does not parse sequences. This keeps plain-text scanning inside the Java
  regex engine while the byte-class state machine remains the single grammar.
  Fixed-corpus measurements and reproduction commands are in
  `docs/api/ansi.md` and `scripts/ansi_benchmark.clj`.

  `strip` retains only text tokens. SGR interpretation and OSC 8 extraction are
  helpers over tokens, not competing scanners."
  (:require [limner.ansi.sgr :as sgr])
  (:import [java.util.regex Pattern]))

(def ^:private esc-code 0x1B)
(def ^:private bel-code 0x07)
(def ^:private st-code 0x9C)
(def ^Pattern ^:private control-pattern
  (Pattern/compile "[\u0000-\u0008\u000B-\u001F\u007F-\u009F]"))

(defn- fail-unless-string [text]
  (when-not (string? text)
    (throw (ex-info "ANSI tokenization requires a string"
                    {:text text :action "Pass a non-nil string"}))))

(defn- raw [^String text start end]
  (subs text start end))

(defn- csi-token [^String text start body-start]
  (let [length (.length text)]
    (loop [index body-start, intermediate-start nil]
      (if (= index length)
        [{:type :incomplete :kind :csi :raw (raw text start index)} index]
        (let [code (int (.charAt text index))]
          (cond
            (and (nil? intermediate-start) (<= 0x30 code 0x3F))
            (recur (inc index) nil)

            (<= 0x20 code 0x2F)
            (recur (inc index) (or intermediate-start index))

            (<= 0x40 code 0x7E)
            (let [parameter-end (or intermediate-start index)
                  end (inc index)]
              [{:type :csi
                :params (raw text body-start parameter-end)
                :intermediates (raw text parameter-end index)
                :final (.charAt text index)
                :raw (raw text start end)}
               end])

            :else
            [{:type :incomplete :kind :csi :raw (raw text start index)}
             index]))))))

(defn- string-token [^String text start body-start kind osc?]
  (let [length (.length text)]
    (loop [index body-start]
      (if (= index length)
        [{:type :incomplete :kind kind :raw (raw text start index)} index]
        (let [code (int (.charAt text index))]
          (cond
            (and osc? (= bel-code code))
            (let [end (inc index)]
              [{:type kind :data (raw text body-start index)
                :terminator :bel :raw (raw text start end)} end])

            (= st-code code)
            (let [end (inc index)]
              [{:type kind :data (raw text body-start index)
                :terminator :st :raw (raw text start end)} end])

            (and (= esc-code code)
                 (< (inc index) length)
                 (= \\ (.charAt text (inc index))))
            (let [end (+ index 2)]
              [{:type kind :data (raw text body-start index)
                :terminator :st :raw (raw text start end)} end])

            :else
            (recur (inc index))))))))

(defn- esc-token [^String text start]
  (let [length (.length text)
        body-start (inc start)]
    (loop [index body-start]
      (if (= index length)
        [{:type :incomplete :kind :esc :raw (raw text start index)} index]
        (let [code (int (.charAt text index))]
          (cond
            (<= 0x20 code 0x2F) (recur (inc index))
            (<= 0x30 code 0x7E)
            (let [end (inc index)]
              [{:type :esc
                :intermediates (raw text body-start index)
                :final (.charAt text index)
                :raw (raw text start end)} end])
            :else
            [{:type :incomplete :kind :esc :raw (raw text start index)}
             index]))))))

(defn- c1-token [^String text index]
  [{:type :c1 :char (.charAt text index) :raw (raw text index (inc index))}
   (inc index)])

(defn- escape-introducer [^String text index]
  (let [length (.length text)]
    (if (= (inc index) length)
      [{:type :incomplete :kind :esc :raw (raw text index length)} length]
      (case (.charAt text (inc index))
        \[ (csi-token text index (+ index 2))
        \] (string-token text index (+ index 2) :osc true)
        \P (string-token text index (+ index 2) :dcs false)
        \_ (string-token text index (+ index 2) :apc false)
        \^ (string-token text index (+ index 2) :pm false)
        \X (string-token text index (+ index 2) :sos false)
        \\ [{:type :c1 :char \u009C :raw (raw text index (+ index 2))}
             (+ index 2)]
        (esc-token text index)))))

(defn- c1-introducer [^String text index]
  (case (.charAt text index)
    \u009B (csi-token text index (inc index))
    \u009D (string-token text index (inc index) :osc true)
    \u0090 (string-token text index (inc index) :dcs false)
    \u009F (string-token text index (inc index) :apc false)
    \u009E (string-token text index (inc index) :pm false)
    \u0098 (string-token text index (inc index) :sos false)
    (c1-token text index)))

(defn- control-start? [code]
  (or (= esc-code code)
      (<= 0x80 code 0x9F)
      (and (or (< code 0x20) (= code 0x7F))
           (not (#{0x09 0x0A} code)))))

(defn- text-token [^String text start]
  (let [matcher (.matcher control-pattern text)
        index (if (and (.find matcher start) (<= start (.start matcher)))
                (.start matcher)
                (.length text))
        value (raw text start index)]
    [{:type :text :text value :raw value} index]))

(defn tokenize
  "Return a lossless vector of ECMA-48 and text tokens for text."
  [text]
  (fail-unless-string text)
  (let [^String text text, length (.length text)]
    (if-not (.find (.matcher control-pattern text))
      (if (zero? length) [] [{:type :text :text text :raw text}])
      (loop [index 0, tokens (transient [])]
        (if (= index length)
          (persistent! tokens)
          (let [code (int (.charAt text index))
                [token end]
                (cond
                  (= esc-code code) (escape-introducer text index)
                  (<= 0x80 code 0x9F) (c1-introducer text index)
                  (control-start? code)
                  [{:type :c0 :char (.charAt text index)
                    :raw (raw text index (inc index))}
                   (inc index)]
                  :else (text-token text index))]
            (recur end (conj! tokens token))))))))

(defn strip
  "Return only text content, retaining structural LF and TAB characters."
  [text]
  (fail-unless-string text)
  (if-not (.find (.matcher control-pattern ^String text))
    text
    (let [result (StringBuilder.)]
      (doseq [{:keys [type text]} (tokenize text)]
        (when (= :text type)
          (.append result ^String text)))
      (str result))))

(defn sgr-params
  "Parse an SGR CSI token into ordered attribute operations."
  [token]
  (sgr/parse token))

(defn hyperlink
  "Extract `{:params string :uri string-or-nil}` from an OSC 8 token."
  [{:keys [type data]}]
  (when (and (= :osc type)
             (string? data)
             (.startsWith ^String data "8;"))
    (let [separator (.indexOf ^String data ";" 2)]
      (when (not= -1 separator)
        (let [uri (subs data (inc separator))]
          {:params (subs data 2 separator)
           :uri (when (seq uri) uri)})))))
