(ns limner.unicode.width.string
  "One-pass Unicode 17 width for complete plain strings.

  The general scanner mirrors ADR 0002's ordered UAX #29 boundary state while
  accumulating ADR 0001 cluster state from the same packed property lookup.
  It never materialises cluster strings. Whole printable-ASCII, Latin-1, and
  BMP CJK/Hangul runs use proven shortcuts; the public entry point and option
  validation remain in `limner.unicode.width`."
  (:require [limner.unicode :as unicode]))

(def ^:private category-shift 13)
(def ^:private scalar-width-shift 17)
(def ^:private ambiguous-bit 19)

(defn- packed-code-point [code-point]
  (let [packed (unicode/packed-properties code-point)]
    (when (= 8 (bit-and 15 (bit-shift-right packed category-shift)))
      (throw (ex-info "Width is undefined for a UTF-16 surrogate code point"
                      {:code-point code-point
                       :action "Pass a Unicode scalar value, not a UTF-16 code unit"})))
    packed))

(defn- packed-width [packed ambiguous]
  (if (and (= 2 ambiguous) (bit-test packed ambiguous-bit))
    2
    (bit-and 3 (bit-shift-right packed scalar-width-shift))))

(defn- emoji-base? [code-point]
  (let [packed (unicode/packed-properties code-point)]
    (or (bit-test packed 9) (bit-test packed 10))))

(defn- eaw-width [code-point ambiguous]
  (case (bit-and 7 (unicode/packed-properties code-point))
    (2 5) 2, 1 ambiguous, 1))

(defn- printable-ascii? [^String text]
  (every? #(<= 0x20 (int %) 0x7E) text))

(defn- latin-1? [^String text]
  (every? #(<= (int %) 0xFF) text))

(defn- simple-wide? [unit]
  (let [unit (int unit)]
    (or (<= 0x3400 unit 0x4DBF)
        (<= 0x4E00 unit 0x9FFF)
        (<= 0xAC00 unit 0xD7A3))))

(defn- simple-wide-run? [^String text]
  (and (seq text) (every? simple-wide? text)))

(defn- boundary?
  [previous current incb-state current-incb emoji-state pictographic? ri-run]
  (cond
    (and (= 1 previous) (= 2 current)) false
    (<= 1 previous 3) true
    (<= 1 current 3) true
    (and (= 8 previous) (#{8 9 12 13} current)) false
    (and (#{12 9} previous) (#{9 10} current)) false
    (and (#{13 10} previous) (= 10 current)) false
    (#{4 11} current) false
    (= 7 current) false
    (= 6 previous) false
    (and (= 2 current-incb) (= 2 incb-state)) false
    (and pictographic? (= 2 emoji-state)) false
    (and (= 5 current) (odd? ri-run)) false
    :else true))

(defn- next-incb [state incb boundary?]
  (if boundary?
    (if (= 2 incb) 1 0)
    (case incb 2 1, 3 (if (pos? state) state 0), 1 (if (pos? state) 2 0), 0)))

(defn- next-emoji [state gcb pictographic? boundary?]
  (if boundary?
    (if pictographic? 1 0)
    (cond pictographic? 1
          (= 4 gcb) (if (= 1 state) 1 0)
          (= 11 gcb) (if (= 1 state) 2 0)
          :else 0)))

(defn- completed-width
  [base first-packed text-base emoji-base point-count ri-count
   first-pictographic? modifier? zwj? pictographic-count keycap-state ambiguous]
  (let [base (or base
                 (let [category (bit-and 15 (bit-shift-right first-packed category-shift))]
                   (if (and (#{1 2} category) (not (bit-test first-packed 12))) 1 0)))]
    (cond
      (and text-base (emoji-base? text-base)) (eaw-width text-base ambiguous)
      (and emoji-base (emoji-base? emoji-base)) 2
      (and (= 2 point-count) (= 2 ri-count)) 2
      (and first-pictographic? modifier?) 2
      (and zwj? (<= 2 pictographic-count)) 2
      (and (= 3 point-count) (= 3 keycap-state)) 2
      :else base)))

(defn- general-width [^String text ambiguous]
  (let [length (.length text), first-point (.codePointAt text 0)
        first-packed (packed-code-point first-point)
        first-gcb (bit-and 15 (bit-shift-right first-packed 3))
        first-incb (bit-and 3 (bit-shift-right first-packed 7))
        first-pictographic? (bit-test first-packed 9)
        first-width (packed-width first-packed ambiguous)]
    (loop [index (Character/charCount first-point), previous-point first-point
           previous-gcb first-gcb, incb-state (if (= 2 first-incb) 1 0)
           emoji-state (if first-pictographic? 1 0), ri-run (if (= 5 first-gcb) 1 0)
           total 0, base (when (pos? first-width) first-width), first-packed first-packed
           text-base nil, emoji-base nil, point-count 1, ri-count (if (= 5 first-gcb) 1 0)
           first-pictographic? first-pictographic?, modifier? (bit-test first-packed 11)
           zwj? (= first-point 0x200D), pictographic-count (if first-pictographic? 1 0)
           keycap-state (if (or (<= 0x30 first-point 0x39) (#{0x23 0x2A} first-point)) 1 0)]
      (if (= index length)
        (+ total (completed-width base first-packed text-base emoji-base point-count ri-count
                                  first-pictographic? modifier? zwj? pictographic-count
                                  keycap-state ambiguous))
        (let [point (.codePointAt text index), packed (packed-code-point point)
              end (+ index (Character/charCount point))
              gcb (bit-and 15 (bit-shift-right packed 3))
              incb (bit-and 3 (bit-shift-right packed 7))
              pictographic? (bit-test packed 9)
              break? (boundary? previous-gcb gcb incb-state incb emoji-state pictographic? ri-run)
              width (packed-width packed ambiguous)
              total (if break?
                      (+ total (completed-width base first-packed text-base emoji-base
                                                point-count ri-count first-pictographic?
                                                modifier? zwj? pictographic-count
                                                keycap-state ambiguous)) total)]
          (recur
           end point gcb (next-incb incb-state incb break?)
           (next-emoji emoji-state gcb pictographic? break?)
           (if (= 5 gcb) (if break? 1 (inc ri-run)) 0) total
           (if break? (when (pos? width) width) (or base (when (pos? width) width)))
           (if break? packed first-packed)
           (if break? nil (or text-base (when (= point 0xFE0E) previous-point)))
           (if break? nil (or emoji-base (when (= point 0xFE0F) previous-point)))
           (if break? 1 (inc point-count))
           (if break? (if (= 5 gcb) 1 0) (if (= 5 gcb) (inc ri-count) ri-count))
           (if break? pictographic? first-pictographic?)
           (if break? (bit-test packed 11) (or modifier? (bit-test packed 11)))
           (if break? (= point 0x200D) (or zwj? (= point 0x200D)))
           (if break? (if pictographic? 1 0)
               (if pictographic? (inc pictographic-count) pictographic-count))
           (if break?
             (if (or (<= 0x30 point 0x39) (#{0x23 0x2A} point)) 1 0)
             (case keycap-state 1 (if (= point 0xFE0F) 2 -1)
               2 (if (= point 0x20E3) 3 -1), -1))))))))

(defn width [^String text ambiguous]
  (let [length (.length text)]
    (cond
      (zero? length) 0
      (printable-ascii? text) length
      (simple-wide-run? text) (* 2 length)
      (latin-1? text)
      (reduce (fn [result unit]
                (+ result (packed-width (packed-code-point (int unit)) ambiguous)))
              0 text)
      :else (general-width text ambiguous))))
