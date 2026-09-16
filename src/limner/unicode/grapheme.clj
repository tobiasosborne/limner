(ns limner.unicode.grapheme
  "Portable Unicode 17 extended grapheme clusters.

  This namespace implements UAX #29 revision 47, Section 3.1.1, from the
  pinned `reference/specs/uax29-47.html`. Boundary decisions use generated
  Unicode 17 properties through `limner.unicode`, including InCB for GB9c and
  Extended_Pictographic for GB11; neither the host JDK nor locale participates.

  The scanner walks UTF-16 by code point and keeps only the suffix state needed
  by GB9c, GB11, and RI parity. `reduce-clusters` exposes UTF-16 source ranges
  without allocating substrings, while `clusters` allocates only the returned
  cluster strings. Printable ASCII has a direct one-code-unit-per-cluster path.

  UAX #29 conformance clause C1-1 is stated for a sequence of Unicode
  characters. A Java string can also contain an unpaired UTF-16 surrogate,
  which is not such a character. Limner treats each unpaired surrogate as an
  opaque one-unit cluster and forces boundaries on both sides; a valid pair is
  decoded as one code point. This total-input extension is documented in ADR
  0002 and does not change conforming Unicode input.

  A non-string input throws `ex-info` with
  `:limner.unicode.grapheme/invalid-string`; a missing reducing function
  throws with `:limner.unicode.grapheme/invalid-reduce-fn` (ADR 0004)."
  (:require [limner.unicode :as unicode]))

(defn- grapheme-code [packed]
  (bit-and 15 (bit-shift-right packed 3)))

(defn- incb-code [packed]
  (bit-and 3 (bit-shift-right packed 7)))

(defn- fail-unless-string [text]
  (when-not (string? text)
    (throw (ex-info "Grapheme segmentation requires a string"
                    {:limner/error :limner.unicode.grapheme/invalid-string
                     :text text :action "Pass a non-nil string"}))))

(defn- paired-high-surrogate?
  [^String text index length]
  (let [unit (.charAt text index)]
    (and (Character/isHighSurrogate unit)
         (< (inc index) length)
         (Character/isLowSurrogate (.charAt text (inc index))))))

(defn- scalar-at
  [^String text index length]
  (let [unit (.charAt text index)]
    (if (paired-high-surrogate? text index length)
      (Character/toCodePoint unit (.charAt text (inc index)))
      (int unit))))

(defn- scalar-end
  [^String text index length]
  (+ index (if (paired-high-surrogate? text index length) 2 1)))

(defn- lone-surrogate?
  [^String text index length]
  (let [unit (.charAt text index)]
    (or (Character/isLowSurrogate unit)
        (and (Character/isHighSurrogate unit)
             (not (paired-high-surrogate? text index length))))))

(defn- printable-ascii?
  [^String text]
  (let [length (.length text)]
    (loop [index 0]
      (or (= index length)
          (let [unit (int (.charAt text index))]
            (and (<= 0x20 unit 0x7E)
                 (recur (inc index))))))))

(defn- simple-unit-run? [^String text]
  ;; Every value here has GCB Other, LV, or LVT. Adjacent precomposed Hangul
  ;; syllables do not match GB6–GB8, so GB999 separates every UTF-16 unit.
  (let [length (.length text)]
    (and (pos? length)
         (loop [index 0]
           (or (= index length)
               (let [unit (int (.charAt text index))]
                 (and (or (<= 0x3400 unit 0x4DBF)
                          (<= 0x4E00 unit 0x9FFF)
                          (<= 0xAC00 unit 0xD7A3))
                      (recur (inc index)))))))))

(defn- reduce-ascii
  [^String text rf init]
  (let [length (.length text)]
    (loop [index 0, result init]
      (if (= index length)
        result
        (let [next-result (rf result text index (inc index))]
          (if (reduced? next-result)
            @next-result
            (recur (inc index) next-result)))))))

(defn- initial-incb-state [incb]
  (if (= 2 incb) 1 0))

(defn- next-incb-state [state incb boundary?]
  (if boundary?
    (initial-incb-state incb)
    (case incb
      2 1
      3 (if (pos? state) state 0)
      1 (if (pos? state) 2 0)
      0)))

(defn- next-emoji-state [state grapheme-break extended-pictographic? boundary?]
  (if boundary?
    (if extended-pictographic? 1 0)
    (cond
      extended-pictographic? 1
      (= 4 grapheme-break) (if (= 1 state) 1 0)
      (= 11 grapheme-break) (if (= 1 state) 2 0)
      :else 0)))

(defn- boundary?
  [previous-break current-break previous-lone? current-lone?
   incb-state current-incb emoji-state current-extended-pictographic? ri-run]
  (cond
    (or previous-lone? current-lone?) true
    (and (= 1 previous-break) (= 2 current-break)) false
    (<= 1 previous-break 3) true
    (<= 1 current-break 3) true
    (and (= 8 previous-break) (#{8 9 12 13} current-break)) false
    (and (#{12 9} previous-break) (#{9 10} current-break)) false
    (and (#{13 10} previous-break) (= 10 current-break)) false
    (#{4 11} current-break) false
    (= 7 current-break) false
    (= 6 previous-break) false
    (and (= 2 current-incb) (= 2 incb-state)) false
    (and current-extended-pictographic? (= 2 emoji-state)) false
    (and (= 5 current-break) (odd? ri-run)) false
    :else true))

(defn- reduce-general
  [^String text rf init]
  (let [length (.length text)
        first-code-point (scalar-at text 0 length)
        first-end (scalar-end text 0 length)
        first-lone? (lone-surrogate? text 0 length)
        first-packed (when-not first-lone? (unicode/grapheme-properties first-code-point))
        first-break (if first-lone? -1 (grapheme-code first-packed))
        first-incb (if first-lone? 0 (incb-code first-packed))
        first-pictographic? (and (not first-lone?) (bit-test first-packed 9))]
    (loop [cluster-start 0
           index first-end
           previous-break first-break
           previous-lone? first-lone?
           incb-state (initial-incb-state first-incb)
           emoji-state (if first-pictographic? 1 0)
           ri-run (if (= 5 first-break) 1 0)
           result init]
      (if (= index length)
        (let [next-result (rf result text cluster-start length)]
          (if (reduced? next-result) @next-result next-result))
        (let [code-point (scalar-at text index length)
              end (scalar-end text index length)
              current-lone? (lone-surrogate? text index length)
              packed (when-not current-lone? (unicode/grapheme-properties code-point))
              current-break (if current-lone? -1 (grapheme-code packed))
              current-incb (if current-lone? 0 (incb-code packed))
              current-pictographic? (and (not current-lone?) (bit-test packed 9))
              break? (boundary? previous-break current-break
                                previous-lone? current-lone?
                                incb-state current-incb emoji-state
                                current-pictographic? ri-run)
              next-result (if break?
                            (rf result text cluster-start index)
                            result)]
          (if (reduced? next-result)
            @next-result
            (recur (if break? index cluster-start)
                   end
                   current-break
                   current-lone?
                   (next-incb-state incb-state current-incb break?)
                   (next-emoji-state emoji-state current-break
                                     current-pictographic? break?)
                   (if (= 5 current-break)
                     (if break? 1 (inc ri-run))
                     0)
                   next-result)))))))

(defn reduce-clusters
  "Reduce text's grapheme ranges without allocating cluster strings.

  Calls `(rf result text start end)` once per extended grapheme cluster, where
  start and end are UTF-16 indices. Honors a reduced result and returns init
  without calling rf for the empty string."
  [rf init text]
  (fail-unless-string text)
  (when-not (ifn? rf)
    (throw (ex-info "Grapheme reduction requires a reducing function"
                    {:limner/error :limner.unicode.grapheme/invalid-reduce-fn
                     :reducing-function rf})))
  (cond
    (zero? (.length ^String text)) init
    (printable-ascii? text) (reduce-ascii text rf init)
    (simple-unit-run? text) (reduce-ascii text rf init)
    :else (reduce-general text rf init)))

(defn cluster-boundaries
  "Return every extended-grapheme boundary as a vector of UTF-16 indices."
  [text]
  (persistent!
   (reduce-clusters (fn [boundaries _ start end]
                      (let [boundaries (if (zero? start)
                                         (conj! boundaries start)
                                         boundaries)]
                        (conj! boundaries end)))
                    (transient (if (and (string? text) (empty? text)) [0] []))
                    text)))

(defn clusters
  "Return text's Unicode 17 extended grapheme clusters as a vector of strings."
  [text]
  (persistent!
   (reduce-clusters (fn [parts ^String source start end]
                      (conj! parts (subs source start end)))
                    (transient [])
                    text)))
