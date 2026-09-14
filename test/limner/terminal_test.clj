(ns limner.terminal-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [limner.terminal :as term]))

;; ──────────────────────── Environment Stubbing ──────────────────────
;; Detection reads TERM, COLORTERM, LANG and LC_ALL through term/get-env.
;; Stub it so results never depend on the environment the tests run in.

(defn- with-env*
  "Call f with term/get-env returning values from env-map only"
  [env-map f]
  (with-redefs [term/get-env (fn [var-name] (get env-map var-name))]
    (f)))

(defmacro ^:private with-env [env-map & body]
  `(with-env* ~env-map (fn [] ~@body)))

(def ^:private neutral-env
  {"TERM" "xterm" "LANG" "en_US.UTF-8"})

(def ^:private env-matrix
  [{}
   {"TERM" "xterm-256color" "LANG" "en_US.UTF-8"}
   {"TERM" "xterm" "COLORTERM" "truecolor"}
   {"TERM" "xterm" "COLORTERM" "24bit"}
   {"TERM" "screen" "COLORTERM" ""}
   {"TERM" "dumb"}
   {"TERM" "dumb" "COLORTERM" "truecolor" "LC_ALL" "C"}
   {"TERM" "vt100" "LANG" "C"}])

(use-fixtures :each (fn [f] (with-env* neutral-env f)))

;; ──────────────────────── Detection Tests ──────────────────────

(deftest test-detect-capabilities
  (testing "Detect capabilities returns a map"
    (doseq [env env-matrix]
      (with-env env
        (let [caps (term/detect-capabilities)]
          (is (map? caps) (pr-str env))
          (doseq [k [:term :ansi-colors :256-colors :truecolor :unicode
                     :box-drawing :mouse :windows]]
            (is (contains? caps k) (pr-str [env k]))))))))

(deftest test-capability-values
  (testing "All capability values are correct type"
    (doseq [env env-matrix]
      (with-env env
        (let [caps (term/detect-capabilities)]
          (is (string? (:term caps)) (pr-str env))
          (doseq [k [:ansi-colors :256-colors :truecolor :unicode
                     :box-drawing :mouse :windows]]
            (is (boolean? (get caps k)) (pr-str [env k])))
          (is (string? (:locale caps)) (pr-str env)))))))

(deftest test-color-detection
  (testing "COLORTERM enables colors unless TERM is dumb"
    (with-env {"TERM" "xterm" "COLORTERM" "truecolor"}
      (is (true? (term/supports-ansi-colors?)))
      (is (true? (term/supports-256-colors?)))
      (is (true? (term/supports-truecolor?)))
      (is (= :truecolor (term/select-color-mode))))
    (with-env {"TERM" "xterm" "COLORTERM" "24bit"}
      (is (true? (term/supports-ansi-colors?)))
      (is (true? (term/supports-truecolor?))))
    (with-env {"TERM" "dumb" "COLORTERM" "truecolor"}
      (is (false? (term/supports-ansi-colors?)))))
  (testing "TERM alone"
    (with-env {"TERM" "xterm-256color"}
      (is (true? (term/supports-ansi-colors?)))
      (is (true? (term/supports-256-colors?)))
      (is (false? (term/supports-truecolor?)))
      (is (= :256-colors (term/select-color-mode))))
    (with-env {"TERM" "dumb"}
      (is (false? (term/supports-ansi-colors?)))
      (is (= :none (term/select-color-mode))))
    (with-env {}
      (is (false? (term/supports-ansi-colors?)))
      (is (= "unknown" (term/detect-term-type)))
      (is (false? (term/supports-mouse?)))))
  (testing "Locale"
    (with-env {"LANG" "en_US.UTF-8"}
      (is (true? (term/supports-unicode?))))
    (with-env {"LANG" "en_US.UTF-8" "LC_ALL" "C"}
      (is (false? (term/supports-unicode?))))
    (with-env {}
      (is (= "C" (term/detect-locale)))
      (is (= :ascii (term/select-border-style))))))

;; ──────────────────────── Simulation Tests ──────────────────────

(deftest test-simulate-dumb-terminal
  (testing "Dumb terminal has no features"
    (let [caps (term/simulate-dumb-terminal)]
      (is (= "dumb" (:term caps)))
      (is (false? (:ansi-colors caps)))
      (is (false? (:256-colors caps)))
      (is (false? (:truecolor caps)))
      (is (false? (:unicode caps)))
      (is (false? (:box-drawing caps)))
      (is (false? (:mouse caps))))))

(deftest test-simulate-modern-terminal
  (testing "Modern terminal has all features"
    (let [caps (term/simulate-modern-terminal)]
      (is (true? (:ansi-colors caps)))
      (is (true? (:256-colors caps)))
      (is (true? (:truecolor caps)))
      (is (true? (:unicode caps)))
      (is (true? (:box-drawing caps)))
      (is (true? (:mouse caps))))))

(deftest test-with-simulated-capabilities
  (testing "Can override capabilities with simulation"
    (term/with-simulated-capabilities (term/simulate-dumb-terminal)
      (is (false? (term/supports-feature? :unicode)))
      (is (false? (term/supports-feature? :ansi-colors)))
      (is (= :ascii (term/select-border-style)))
      (is (= :none (term/select-color-mode))))

    (term/with-simulated-capabilities (term/simulate-modern-terminal)
      (is (true? (term/supports-feature? :unicode)))
      (is (true? (term/supports-feature? :ansi-colors)))
      (is (= :single (term/select-border-style)))
      (is (= :truecolor (term/select-color-mode))))))

;; ──────────────────────── Feature Detection Tests ──────────────────────

(deftest test-supports-feature
  (testing "Supports feature returns boolean"
    (doseq [env env-matrix
            feature [:unicode :ansi-colors :256-colors :truecolor
                     :box-drawing :mouse]]
      (with-env env
        (is (boolean? (term/supports-feature? feature))
            (pr-str [env feature]))))))

(deftest test-select-border-style
  (testing "Select border style based on capabilities"
    (term/with-simulated-capabilities {:box-drawing true}
      (is (= :single (term/select-border-style))))

    (term/with-simulated-capabilities {:box-drawing false}
      (is (= :ascii (term/select-border-style))))))

(deftest test-select-color-mode
  (testing "Select color mode based on capabilities"
    (term/with-simulated-capabilities (term/simulate-dumb-terminal)
      (is (= :none (term/select-color-mode))))

    (term/with-simulated-capabilities {:ansi-colors true :256-colors false :truecolor false}
      (is (= :ansi (term/select-color-mode))))

    (term/with-simulated-capabilities {:ansi-colors true :256-colors true :truecolor false}
      (is (= :256-colors (term/select-color-mode))))

    (term/with-simulated-capabilities {:ansi-colors true :256-colors true :truecolor true}
      (is (= :truecolor (term/select-color-mode))))))

;; ──────────────────────── Fallback Tests ──────────────────────

(deftest test-with-fallback
  (testing "With fallback returns correct value based on support"
    (term/with-simulated-capabilities {:unicode true}
      (is (= "┌" (term/with-fallback :unicode "┌" "+"))))

    (term/with-simulated-capabilities {:unicode false}
      (is (= "+" (term/with-fallback :unicode "┌" "+"))))))

(deftest test-maybe-colorize
  (testing "Maybe colorize returns map with color info"
    (term/with-simulated-capabilities {:ansi-colors true}
      (let [result (term/maybe-colorize :red "Error")]
        (is (= :red (:color result)))
        (is (= "Error" (:text result)))))

    (term/with-simulated-capabilities {:ansi-colors false}
      (let [result (term/maybe-colorize :red "Error")]
        (is (nil? (:color result)))
        (is (= "Error" (:text result)))))))

;; ──────────────────────── Capability Report Tests ──────────────────────

(deftest test-capability-report
  (testing "Capability report returns string"
    (let [report (term/capability-report)]
      (is (string? report))
      (is (clojure.string/includes? report "Terminal Capabilities"))
      (is (clojure.string/includes? report "Color Support"))
      (is (clojure.string/includes? report "Other Features")))))

(deftest test-capability-report-format
  (testing "Capability report has expected sections"
    (term/with-simulated-capabilities (term/simulate-modern-terminal)
      (let [report (term/capability-report)]
        (is (clojure.string/includes? report "xterm-256color"))
        (is (clojure.string/includes? report "✓ Yes"))
        (is (clojure.string/includes? report "truecolor"))
        (is (clojure.string/includes? report "single"))))))

;; ──────────────────────── Integration Tests ──────────────────────

(deftest test-degradation-chain
  (testing "Graceful degradation from best to worst"
    ;; Best: modern terminal
    (term/with-simulated-capabilities (term/simulate-modern-terminal)
      (is (= :truecolor (term/select-color-mode)))
      (is (= :single (term/select-border-style))))

    ;; Good: 256 colors, no truecolor
    (term/with-simulated-capabilities {:ansi-colors true :256-colors true :truecolor false :unicode true}
      (is (= :256-colors (term/select-color-mode)))
      (is (= :single (term/select-border-style))))

    ;; OK: ANSI colors only
    (term/with-simulated-capabilities {:ansi-colors true :256-colors false :truecolor false :unicode true}
      (is (= :ansi (term/select-color-mode)))
      (is (= :single (term/select-border-style))))

    ;; Minimal: no colors, ASCII only
    (term/with-simulated-capabilities (term/simulate-dumb-terminal)
      (is (= :none (term/select-color-mode)))
      (is (= :ascii (term/select-border-style))))))

(deftest test-mixed-capabilities
  (testing "Terminals with mixed capabilities"
    ;; Colors but no Unicode
    (term/with-simulated-capabilities {:ansi-colors true :unicode false :box-drawing false}
      (is (true? (term/supports-feature? :ansi-colors)))
      (is (false? (term/supports-feature? :unicode)))
      (is (= :ascii (term/select-border-style))))

    ;; Unicode but no colors
    (term/with-simulated-capabilities {:ansi-colors false :256-colors false :truecolor false :unicode true :box-drawing true}
      (is (false? (term/supports-feature? :ansi-colors)))
      (is (true? (term/supports-feature? :unicode)))
      (is (= :single (term/select-border-style)))
      (is (= :none (term/select-color-mode))))))
