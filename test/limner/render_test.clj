(ns limner.render-test
  "Golden, direct-byte and property tests for the ADR 0005 render buffer.

  Golden tests feed the emitted byte stream through a test-only virtual
  terminal (`limner.vt`) and compare the resulting `{:cluster :style}` grid,
  so they do not depend on escape-sequence spelling."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [limner.render :as render]
            [limner.text :as text]
            [limner.unicode.grapheme :as grapheme]
            [limner.vt :as vt]))

;; ──────────────────────── REVIEW probes ──────────────────────

(deftest probe-6-emoji-is-one-grapheme-cell
  (testing "REVIEW probe 6: one emoji is one cluster, not two surrogate halves"
    (let [units (text/clusters "😀")]
      (is (= 1 (count units)))
      (is (= "😀" (:cluster (first units))))
      (is (= 2 (:width (first units))))))
  (testing "a wide grapheme occupies a lead cell and a continuation cell"
    (let [buf (render/write-string-to-buffer (render/create-buffer 4 1) 0 0 "😀")
          cells (:cells buf)]
      (is (= "😀" (:cluster (render/get-cell buf 0 0))))
      (is (render/wide? (render/get-cell buf 0 0)))
      (is (render/continuation? (render/get-cell buf 1 0)))
      (is (= 4 (count (first cells)))))))

(deftest probe-6c-style-is-a-value
  (testing "REVIEW probe 6c: reset does not accumulate in a cell style"
    (let [units (text/clusters (str "\u001B[31mA" "\u001B[0mB"))]
      (is (= [{:fg :red} {}] (mapv :style units)))
      (is (not= (:style (first units)) (:style (second units))))))
  (testing "visually identical cells compare equal"
    (let [a (render/cell "x" {:fg :red})
          b (render/cell "x" {:fg :red})
          c (render/cell "x" {:fg :red :bold true})]
      (is (= a b))
      (is (not= a c)))))

;; ──────────────────────── Cell and buffer basics ──────────────────────

(deftest test-cell-and-empty-cell
  (let [c (render/cell "A" {:fg :red :bold true})]
    (is (= "A" (:cluster c)))
    (is (= {:fg :red :bold true} (:style c)))
    (is (= 1 (:width c))))
  (let [e (render/empty-cell)]
    (is (= " " (:cluster e)))
    (is (= {} (:style e)))
    (is (= 1 (:width e)))))

(deftest test-buffer-creation-and-bounds
  (let [buf (render/create-buffer 4 2)]
    (is (= 4 (:width buf)))
    (is (= 2 (:height buf)))
    (is (= 2 (count (:cells buf))))
    (is (= 4 (count (first (:cells buf)))))
    (is (= (render/empty-cell) (render/get-cell buf 3 1)))
    (is (nil? (render/get-cell buf 4 1)))
    (is (nil? (render/get-cell buf -1 0)))
    (is (= buf (render/set-cell buf 99 99 (render/cell "x"))))))

(deftest test-write-string-plain-and-styled
  (testing "plain text occupies one column per grapheme"
    (let [buf (render/write-string-to-buffer (render/create-buffer 5 1) 0 0 "hi")]
      (is (= "h" (:cluster (render/get-cell buf 0 0))))
      (is (= "i" (:cluster (render/get-cell buf 1 0))))
      (is (= " " (:cluster (render/get-cell buf 2 0))))))
  (testing "ANSI styles become style values"
    (let [buf (render/write-string-to-buffer
               (render/create-buffer 5 1) 0 0 "\u001B[31mRED\u001B[0m")
          red (render/get-cell buf 0 0)]
      (is (= "R" (:cluster red)))
      (is (= {:fg :red} (:style red)))
      (is (= {:fg :red} (:style (render/get-cell buf 1 0)))))))

(deftest test-write-lines
  (let [buf (render/write-lines-to-buffer (render/create-buffer 4 3) 0 0 ["a" "b" "c"])]
    (is (= "a" (:cluster (render/get-cell buf 0 0))))
    (is (= "b" (:cluster (render/get-cell buf 0 1))))
    (is (= "c" (:cluster (render/get-cell buf 0 2))))))

;; ──────────────────────── Edge rules ──────────────────────

(deftest test-write-clears-half-a-wide-cell
  (testing "a write starting in the middle of a wide cell replaces it with spaces"
    (let [buf (-> (render/create-buffer 4 1)
                  (render/write-string-to-buffer 0 0 "😀")
                  (render/write-string-to-buffer 1 0 "X"))]
      (is (= " " (:cluster (render/get-cell buf 0 0))))
      (is (= "X" (:cluster (render/get-cell buf 1 0))))
      (is (= " " (:cluster (render/get-cell buf 2 0))))))
  (testing "a wide cluster over an existing pair clears both old cells"
    (let [buf (-> (render/create-buffer 5 1)
                  (render/write-string-to-buffer 0 0 "ab")
                  (render/write-string-to-buffer 0 0 "中"))]
      (is (= "中" (:cluster (render/get-cell buf 0 0))))
      (is (render/continuation? (render/get-cell buf 1 0))))
    (let [buf (-> (render/create-buffer 5 1)
                  (render/write-string-to-buffer 0 0 "😀")
                  (render/write-string-to-buffer 0 0 "x"))]
      (is (= "x" (:cluster (render/get-cell buf 0 0))))
      (is (= " " (:cluster (render/get-cell buf 1 0)))))))

(deftest test-wide-cluster-at-right-edge-does-not-split
  (let [buf (render/write-string-to-buffer (render/create-buffer 3 1) 2 0 "😀")]
    (is (= " " (:cluster (render/get-cell buf 2 0))))
    (is (not (render/wide? (render/get-cell buf 2 0))))))

(deftest test-zero-width-attaches-to-preceding-cluster
  ;; "e" + COMBINING ACUTE ACCENT is one cluster.
  (let [buf (render/write-string-to-buffer (render/create-buffer 4 1) 0 0 "ae\u0301b")]
    (is (= "a" (:cluster (render/get-cell buf 0 0))))
    (is (= "e\u0301" (:cluster (render/get-cell buf 1 0))))
    (is (= "b" (:cluster (render/get-cell buf 2 0))))
    (is (= " " (:cluster (render/get-cell buf 3 0))))))

;; ──────────────────────── Dirty cells and regions ──────────────────────

(deftest test-find-dirty-cells-is-value-based
  (let [old (render/create-buffer 3 1)
        new (render/write-string-to-buffer old 1 0 "x")]
    (is (= [[1 0 (render/get-cell new 1 0)]] (vec (render/find-dirty-cells old new)))))
  (testing "changing only an escape spelling does not dirty a cell"
    (let [old (render/write-string-to-buffer (render/create-buffer 3 1) 0 0 "\u001B[31mA")
          new (render/write-string-to-buffer old 0 0 "\u001B[31mA")]
      (is (empty? (render/find-dirty-cells old new))))))

(deftest test-find-dirty-regions
  (let [dirty [[5 2 (render/cell "A")]
               [6 2 (render/cell "B")]
               [7 2 (render/cell "C")]
               [8 2 (render/cell "D")]]
        regions (render/find-dirty-regions dirty)]
    (is (= 1 (count regions)))
    (is (= {:y 2 :x 5} (select-keys (first regions) [:y :x])))
    (is (= ["A" "B" "C" "D"] (mapv :cluster (:cells (first regions))))))
  (let [regions (render/find-dirty-regions [[5 2 (render/cell "A")]
                                            [8 2 (render/cell "B")]])]
    (is (= 2 (count regions))))
  (testing "a large contiguous row is one region without a quadratic scan"
    (let [dirty (mapv (fn [x] [x 0 (render/cell "x")]) (range 5000))]
      (is (= 1 (count (render/find-dirty-regions dirty)))))))

(deftest test-buffer-stats
  (let [buf (render/write-string-to-buffer (render/create-buffer 4 1) 0 0 "ab")
        stats (render/buffer-stats buf)]
    (is (= 4 (:width stats)))
    (is (= 1 (:height stats)))
    (is (= 4 (:total-cells stats)))
    (is (= 2 (:non-empty-cells stats)))))

;; ──────────────────────── Golden tests through the virtual terminal ──────────────────────

(defn- golden [width height lines]
  (let [state (render/render-state width height :clock (constantly 0))
        output (with-out-str (render/force-render state lines))]
    (vt/feed (vt/make-vt width height) output)))

(deftest test-golden-plain-text
  (let [vt (golden 6 2 ["hi" "world"])]
    (is (= [["h" "i" " " " " " " " "]
            ["w" "o" "r" "l" "d" " "]]
           (vt/cluster-grid vt)))
    (is (= {} (:style (vt/cell-at vt 0 0))))))

(deftest test-golden-emoji-and-cjk
  (let [vt (golden 6 1 ["a😀中"])]
    (is (= [["a" "😀" "" "中" "" " "]] (vt/cluster-grid vt)))))

(deftest test-golden-styles
  (let [vt (golden 6 1 ["\u001B[31mR\u001B[0mB"])]
    (is (= [["R" "B" " " " " " " " "]] (vt/cluster-grid vt)))
    (is (= {:fg :red} (:style (vt/cell-at vt 0 0))))
    (is (= {} (:style (vt/cell-at vt 1 0))))))

;; ──────────────────────── Direct byte tests ──────────────────────

(deftest test-apply-diff-exact-bytes
  (testing "a plain region emits cursor plus graphemes, no reset"
    (let [old (render/create-buffer 2 1)
          new (render/write-string-to-buffer old 0 0 "AB")
          output (with-out-str (render/apply-diff old new))]
      (is (= "\u001B[1;1HAB" output))))
  (testing "a styled region emits minimal SGR and closes it"
    (let [old (render/create-buffer 3 1)
          new (render/write-string-to-buffer old 0 0 "\u001B[31mRED")
          output (with-out-str (render/apply-diff old new))]
      (is (= "\u001B[1;1H\u001B[31mRED\u001B[0m" output))))
  (testing "a wide region emits the lead grapheme once"
    (let [old (render/create-buffer 3 1)
          new (render/write-string-to-buffer old 0 0 "😀")
          output (with-out-str (render/apply-diff old new))]
      (is (= "\u001B[1;1H😀" output)))))

(deftest test-terminal-controls
  (is (= "\u001B[2J\u001B[H" (with-out-str (render/clear-screen))))
  (is (= "\u001B[?25l\u001B[2J\u001B[H" (with-out-str (render/setup-terminal))))
  (is (= "\u001B[?25h\u001B[0m" (with-out-str (render/restore-terminal)))))

;; ──────────────────────── Render state and timing ──────────────────────

(deftest test-render-state-and-clock
  (let [state (render/render-state 80 24 :clock (constantly 42))]
    (is (= 80 (:width state)))
    (is (= 24 (:height state)))
    (is (= 0 (:frame-count state)))
    (is (= 60 (:target-fps state)))
    (is (= 42 (:last-render-time state)))))

(deftest test-should-render-uses-injected-clock
  (let [now (atom 0)
        state (render/render-state 80 24 :clock #(deref now))]
    (reset! now 16000000)
    (is (false? (render/should-render? state)))
    (reset! now 16666667)
    (is (true? (render/should-render? state)))
    (is (= 16666667 (:last-render-time (render/update-render-time state))))))

;; ──────────────────────── Error handling ──────────────────────

(deftest test-invalid-input-throws
  (is (= :limner.render/invalid-string
         (try (render/write-string-to-buffer (render/create-buffer 3 1) 0 0 42)
              (catch clojure.lang.ExceptionInfo e (:limner/error (ex-data e))))))
  (is (= :limner.render/invalid-lines
         (try (render/write-lines-to-buffer (render/create-buffer 3 1) 0 0 ["ok" 7])
              (catch clojure.lang.ExceptionInfo e (:limner/error (ex-data e)))))))

;; ──────────────────────── Property tests ──────────────────────

(def ^:private cluster-gen
  (gen/elements ["a" "b" "hi" " " "😀" "中" "é" "✓" "⚡" "x"]))

(def ^:private style-gen
  (gen/elements [{} {:fg :red} {:bold true} {:bg :blue :fg :white} {:underline :single}]))

(def ^:private line-gen
  (gen/fmap (fn [pairs]
              (text/to-ansi (mapv (fn [[cluster style]]
                                    {:text cluster :style style})
                                  pairs)))
            (gen/vector (gen/tuple cluster-gen style-gen) 0 5)))

(def ^:private frames-gen
  (gen/vector (gen/vector line-gen 0 4) 1 6))

(defn- diff-frame!
  "Apply one frame to a render state and return [state emitted-bytes]."
  [state lines]
  (let [updated (render/update-back-buffer state lines)
        output (with-out-str
                 (render/apply-diff (:front-buffer updated) (:back-buffer updated)))]
    [(render/swap-buffers updated) output]))

(defn- valid-row?
  "True when every wide lead has its continuation and vice versa."
  [row]
  (every?
   (fn [index]
     (let [c (nth row index)]
       (cond
         (render/wide? c) (and (< (inc index) (count row))
                               (render/continuation? (nth row (inc index))))
         (render/continuation? c) (and (pos? index)
                                       (render/wide? (nth row (dec index))))
         :else true)))
   (range (count row))))

(defn- no-lone-surrogate? [s]
  (loop [i 0]
    (if (>= i (.length ^String s))
      true
      (let [ch (.charAt ^String s i)]
        (cond
          (Character/isHighSurrogate ch)
          (if (and (< (inc i) (.length ^String s))
                   (Character/isLowSurrogate (.charAt ^String s (inc i))))
            (recur (+ i 2))
            false)
          (Character/isLowSurrogate ch) false
          :else (recur (inc i)))))))

(deftest diff-render-matches-full-redraw
  (let [width 12 height 4
        result
        (tc/quick-check
         100
         (prop/for-all [frames frames-gen]
           (let [[_final diff-vt]
                 (reduce (fn [[state target-vt] lines]
                           (let [[state output] (diff-frame! state lines)]
                             [state (vt/feed target-vt output)]))
                         [(render/render-state width height) (vt/make-vt width height)]
                         frames)
                 full-output (with-out-str
                               (render/force-render (render/render-state width height)
                                                    (last frames)))
                 full-vt (vt/feed (vt/make-vt width height) full-output)]
             (= (vt/screen diff-vt) (vt/screen full-vt))))
         :seed 4711)]
    (is (:pass? result) (pr-str (dissoc result :result-data)))))

(deftest buffer-never-holds-half-a-wide-cluster
  (let [result
        (tc/quick-check
         200
         (prop/for-all [frames frames-gen]
           (let [state (reduce (fn [state lines]
                                 (render/update-back-buffer state lines))
                               (render/render-state 12 4)
                               frames)]
             (every? (fn [row]
                       (and (valid-row? row)
                            (every? (fn [c] (no-lone-surrogate? (:cluster c))) row)))
                     (:cells (:back-buffer state)))))
         :seed 90210)]
    (is (:pass? result) (pr-str (dissoc result :result-data)))))
