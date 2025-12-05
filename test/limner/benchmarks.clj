(ns limner.benchmarks
  "Comprehensive performance benchmarks for Limner TUI library.

  Benchmarks all critical performance paths:
  - Render loop throughput and latency
  - Layout calculation speed
  - Color application performance
  - Border rendering speed
  - Diff algorithm efficiency
  - Event processing latency
  - Unicode width calculation speed

  Run with: bb benchmark"
  (:require [limner.core :as core]
            [limner.layout :as layout]
            [limner.borders :as borders]
            [limner.render :as render]
            [limner.events :as events]
            [limner.state :as state]))

;; ============================================================================
;; Benchmark Utilities
;; ============================================================================

(defn benchmark
  "Run a benchmark function n times and return timing statistics.

  Returns a map with:
  - :iterations - Number of iterations run
  - :total-ms - Total time in milliseconds
  - :avg-ms - Average time per iteration
  - :ops-per-sec - Operations per second
  - :min-ms - Minimum time
  - :max-ms - Maximum time
  - :median-ms - Median time"
  [name f n]
  (println (format "\n📊 Benchmarking: %s (%d iterations)" name n))
  (System/gc) ; Suggest GC before benchmark
  (Thread/sleep 100) ; Let GC settle

  (let [times (atom [])
        start (System/nanoTime)]

    ;; Run benchmark iterations
    (dotimes [_ n]
      (let [iter-start (System/nanoTime)]
        (f)
        (let [iter-end (System/nanoTime)
              iter-time (/ (- iter-end iter-start) 1000000.0)] ; Convert to ms
          (swap! times conj iter-time))))

    (let [end (System/nanoTime)
          total-ms (/ (- end start) 1000000.0)
          times-sorted (sort @times)
          avg-ms (/ total-ms n)
          ops-per-sec (/ n (/ total-ms 1000.0))
          min-ms (first times-sorted)
          max-ms (last times-sorted)
          median-ms (nth times-sorted (quot (count times-sorted) 2))]

      {:name name
       :iterations n
       :total-ms total-ms
       :avg-ms avg-ms
       :ops-per-sec ops-per-sec
       :min-ms min-ms
       :max-ms max-ms
       :median-ms median-ms})))

(defn format-number
  "Format a number with commas for readability."
  [n]
  (let [s (format "%.2f" (double n))
        [int-part dec-part] (clojure.string/split s #"\.")
        int-reversed (reverse int-part)
        int-partitioned (partition-all 3 int-reversed)
        int-joined (map #(apply str %) int-partitioned)
        int-with-commas (clojure.string/join "," (reverse int-joined))]
    (if dec-part
      (str int-with-commas "." dec-part)
      int-with-commas)))

(defn print-benchmark-result
  "Print a formatted benchmark result."
  [{:keys [name iterations total-ms avg-ms ops-per-sec min-ms max-ms median-ms]}]
  (println (format "  ✓ %s" name))
  (println (format "    Iterations:   %s" (format-number iterations)))
  (println (format "    Total time:   %s ms" (format-number total-ms)))
  (println (format "    Average:      %s ms" (format-number avg-ms)))
  (println (format "    Median:       %s ms" (format-number median-ms)))
  (println (format "    Min/Max:      %s / %s ms" (format-number min-ms) (format-number max-ms)))
  (println (format "    Throughput:   %s ops/sec" (format-number ops-per-sec))))

(defn run-benchmark-suite
  "Run a suite of benchmarks and collect results."
  [benchmarks]
  (println "\n" "=" 70)
  (println "  🚀 Limner Performance Benchmark Suite")
  (println "  " "=" 70)

  (let [results (doall
                  (for [{:keys [name fn iterations]} benchmarks]
                    (let [result (benchmark name fn iterations)]
                      (print-benchmark-result result)
                      result)))]

    (println "\n" "=" 70)
    (println "  📈 Summary")
    (println "  " "=" 70)
    (doseq [result results]
      (println (format "  %-40s %10s ops/sec"
                       (:name result)
                       (format-number (:ops-per-sec result)))))

    results))

;; ============================================================================
;; Color System Benchmarks
;; ============================================================================

(defn benchmark-color-basic
  "Benchmark basic color application."
  []
  (let [text "Hello, World!"
        colors [:red :green :blue :yellow :cyan :magenta :white]]
    (doseq [c colors]
      (core/color c text))))

(defn benchmark-color-256
  "Benchmark 256-color palette."
  []
  (let [text "Test"]
    (doseq [n (range 0 256 16)] ; Sample every 16th color
      (core/color (core/color-256 n) text))))

(defn benchmark-color-rgb
  "Benchmark RGB truecolor."
  []
  (let [text "Test"]
    (doseq [r (range 0 256 64)]
      (doseq [g (range 0 256 64)]
        (core/color (core/rgb r g 128) text)))))

(defn benchmark-color-nested
  "Benchmark nested color application."
  []
  (core/color :bold
    (core/color :underline
      (core/color :red "Nested colors"))))

;; ============================================================================
;; Unicode Width Benchmarks
;; ============================================================================

(defn benchmark-unicode-width-ascii
  "Benchmark Unicode width calculation for ASCII text."
  []
  (let [text "The quick brown fox jumps over the lazy dog"]
    (core/visible-width text)))

(defn benchmark-unicode-width-cjk
  "Benchmark Unicode width calculation for CJK text."
  []
  (let [text "你好世界こんにちは안녕하세요"]
    (core/visible-width text)))

(defn benchmark-unicode-width-emoji
  "Benchmark Unicode width calculation for emoji."
  []
  (let [text "😀😁😂🤣😃😄😅😆😉😊😋😎😍"]
    (core/visible-width text)))

(defn benchmark-unicode-width-mixed
  "Benchmark Unicode width calculation for mixed content."
  []
  (let [text "Hello 世界 😀 Test こんにちは"]
    (core/visible-width text)))

;; ============================================================================
;; Layout Calculation Benchmarks
;; ============================================================================

(defn benchmark-layout-stack-simple
  "Benchmark simple stack layout."
  []
  (let [components [{:constraint (layout/fixed 10) :content "Header"}
                    {:constraint (layout/flex 1) :content "Content"}
                    {:constraint (layout/fixed 5) :content "Footer"}]
        layout-spec (layout/stack components :spacing 0)]
    (layout/layout-stack layout-spec 80 24)))

(defn benchmark-layout-stack-complex
  "Benchmark complex stack layout with many components."
  []
  (let [components (vec (repeatedly 20 #(hash-map :constraint (layout/flex 1) :content "Item")))
        layout-spec (layout/stack components :spacing 0)]
    (layout/layout-stack layout-spec 80 50)))

(defn benchmark-layout-hsplit
  "Benchmark horizontal split layout."
  []
  (let [components [{:constraint (layout/fixed 20) :content "Left"}
                    {:constraint (layout/flex 1) :content "Center"}
                    {:constraint (layout/fixed 15) :content "Right"}]
        layout-spec (layout/hsplit components :spacing 0)]
    (layout/layout-hsplit layout-spec 80 24)))

(defn benchmark-layout-grid
  "Benchmark grid layout."
  []
  (let [components (vec (repeatedly 12 #(hash-map :content "Cell")))
        layout-spec (layout/grid components :columns 3 :spacing 0)]
    (layout/layout-grid layout-spec 80 24)))

(defn benchmark-layout-nested
  "Benchmark deeply nested layout."
  []
  (let [inner-stack-components [{:constraint (layout/flex 1) :content "Top"}
                                {:constraint (layout/flex 1) :content "Bottom"}]
        inner-stack (layout/stack inner-stack-components :spacing 0)
        hsplit-components [{:constraint (layout/flex 1) :content inner-stack}
                           {:constraint (layout/fixed 20) :content "Side"}]
        hsplit-layout (layout/hsplit hsplit-components :spacing 0)
        components [{:constraint (layout/fixed 5) :content "Header"}
                    {:constraint (layout/flex 1) :content hsplit-layout}
                    {:constraint (layout/fixed 3) :content "Footer"}]
        layout-spec (layout/stack components :spacing 0)]
    (layout/layout-stack layout-spec 80 24)))

;; ============================================================================
;; Border Rendering Benchmarks
;; ============================================================================

(defn benchmark-borders-simple
  "Benchmark simple border drawing."
  []
  (borders/draw-box ["Line 1" "Line 2" "Line 3"] :border-style :single))

(defn benchmark-borders-titled
  "Benchmark titled box rendering."
  []
  (borders/draw-titled-box
    "Title"
    ["Content line 1" "Content line 2"]
    :border-style :rounded))

(defn benchmark-borders-shadow
  "Benchmark border with shadow."
  []
  (let [box (borders/draw-box ["Content"] :border-style :double)]
    (borders/add-shadow box)))

(defn benchmark-borders-colorized
  "Benchmark border colorization."
  []
  (let [box (borders/draw-box ["Content"] :border-style :single)]
    (borders/colorize-border box :blue)))

(defn benchmark-borders-nested
  "Benchmark nested boxes."
  []
  (let [inner (borders/draw-box ["Inner content"] :border-style :single)]
    (borders/nest-box inner 2)))

(defn benchmark-borders-side-by-side
  "Benchmark side-by-side boxes."
  []
  (let [box1 (borders/draw-box ["Left"] :border-style :single)
        box2 (borders/draw-box ["Right"] :border-style :single)]
    (borders/side-by-side box1 box2 2)))

;; ============================================================================
;; Render Loop Benchmarks
;; ============================================================================

(defn benchmark-render-diff-no-changes
  "Benchmark diff algorithm with no changes."
  []
  (let [old-buffer (vec (repeat 24 (vec (repeat 80 {:char \space :style nil}))))
        new-buffer old-buffer
        changes (atom 0)]
    ;; Simulate diff by comparing buffers
    (doseq [row (range 24)]
      (doseq [col (range 80)]
        (when (not= (get-in old-buffer [row col])
                    (get-in new-buffer [row col]))
          (swap! changes inc))))
    @changes))

(defn benchmark-render-diff-full-screen
  "Benchmark diff algorithm with full screen change."
  []
  (let [old-buffer (vec (repeat 24 (vec (repeat 80 {:char \space :style nil}))))
        new-buffer (vec (repeat 24 (vec (repeat 80 {:char \# :style nil}))))
        changes (atom 0)]
    (doseq [row (range 24)]
      (doseq [col (range 80)]
        (when (not= (get-in old-buffer [row col])
                    (get-in new-buffer [row col]))
          (swap! changes inc))))
    @changes))

(defn benchmark-render-diff-partial
  "Benchmark diff algorithm with partial screen change (100 cells)."
  []
  (let [old-buffer (vec (repeat 24 (vec (repeat 80 {:char \space :style nil}))))
        new-buffer (reduce
                     (fn [buf idx]
                       (let [row (quot idx 80)
                             col (rem idx 80)]
                         (assoc-in buf [row col] {:char \* :style nil})))
                     old-buffer
                     (range 100))
        changes (atom 0)]
    (doseq [row (range 24)]
      (doseq [col (range 80)]
        (when (not= (get-in old-buffer [row col])
                    (get-in new-buffer [row col]))
          (swap! changes inc))))
    @changes))

(defn benchmark-render-buffer-update
  "Benchmark buffer update operations."
  []
  (let [buffer (vec (repeat 24 (vec (repeat 80 {:char \space :style nil}))))]
    ;; Update 10 random cells
    (reduce
      (fn [buf idx]
        (assoc-in buf [(rem idx 24) (quot idx 3)] {:char \* :style nil}))
      buffer
      (range 10))))

;; ============================================================================
;; Event Processing Benchmarks
;; ============================================================================

(defn benchmark-event-parse-simple-key
  "Benchmark simple key event parsing."
  []
  (events/parse-key "a"))

(defn benchmark-event-parse-special-key
  "Benchmark special key parsing (arrows, function keys)."
  []
  (events/parse-key "\u001B[A")) ; Up arrow

(defn benchmark-event-parse-ctrl-key
  "Benchmark Ctrl+ key parsing."
  []
  (events/parse-key "\u0001")) ; Ctrl+A

(defn benchmark-event-key-matches
  "Benchmark key matching."
  []
  (events/key-matches? {:type :key :key \a} (events/key-combo :a)))

(defn benchmark-event-dispatch
  "Benchmark event dispatch with handlers."
  []
  (let [state {:count 0}
        registry (events/keybindings {})
        key-event {:type :key :key \a}]
    ;; Register a simple handler
    (events/bind-key! registry (events/key-combo :a) (fn [event state] (update state :count inc)))
    ;; Dispatch event
    (events/dispatch-key registry key-event state)))

;; ============================================================================
;; State Management Benchmarks
;; ============================================================================

(defn benchmark-state-get
  "Benchmark state access."
  []
  (let [app-state (atom {:count 0 :data {:nested {:value 42}}})]
    @app-state
    (get-in @app-state [:data :nested :value])))

(defn benchmark-state-update
  "Benchmark state updates."
  []
  (let [app-state (atom {:count 0})]
    (swap! app-state update :count inc)))

(defn benchmark-state-update-in
  "Benchmark nested state updates."
  []
  (let [app-state (atom {:data {:nested {:count 0}}})]
    (swap! app-state assoc-in [:data :nested :count] 1)))

(defn benchmark-state-watchers
  "Benchmark state watchers."
  []
  (let [app-state (atom {:count 0})
        call-count (atom 0)]
    ;; Add watcher
    (add-watch app-state :test-watcher
      (fn [key ref old new] (swap! call-count inc)))
    ;; Trigger watcher
    (swap! app-state assoc :count 1)
    ;; Remove watcher
    (remove-watch app-state :test-watcher)))

;; ============================================================================
;; Benchmark Suite Configuration
;; ============================================================================

(def benchmark-suites
  {:color
   {:name "Color System"
    :benchmarks
    [{:name "Basic colors (16 colors)" :fn benchmark-color-basic :iterations 10000}
     {:name "256-color palette" :fn benchmark-color-256 :iterations 1000}
     {:name "RGB truecolor" :fn benchmark-color-rgb :iterations 500}
     {:name "Nested color application" :fn benchmark-color-nested :iterations 10000}]}

   :unicode
   {:name "Unicode Width Calculation"
    :benchmarks
    [{:name "ASCII text width" :fn benchmark-unicode-width-ascii :iterations 50000}
     {:name "CJK text width" :fn benchmark-unicode-width-cjk :iterations 50000}
     {:name "Emoji width" :fn benchmark-unicode-width-emoji :iterations 50000}
     {:name "Mixed content width" :fn benchmark-unicode-width-mixed :iterations 50000}]}

   :layout
   {:name "Layout Calculation"
    :benchmarks
    [{:name "Simple stack (3 components)" :fn benchmark-layout-stack-simple :iterations 10000}
     {:name "Complex stack (20 components)" :fn benchmark-layout-stack-complex :iterations 5000}
     {:name "Horizontal split (3 sections)" :fn benchmark-layout-hsplit :iterations 10000}
     {:name "Grid layout (12 items, 3 cols)" :fn benchmark-layout-grid :iterations 10000}
     {:name "Nested layout (3 levels deep)" :fn benchmark-layout-nested :iterations 5000}]}

   :borders
   {:name "Border Rendering"
    :benchmarks
    [{:name "Simple box (3 lines)" :fn benchmark-borders-simple :iterations 10000}
     {:name "Titled box" :fn benchmark-borders-titled :iterations 10000}
     {:name "Box with shadow" :fn benchmark-borders-shadow :iterations 10000}
     {:name "Colorized border" :fn benchmark-borders-colorized :iterations 10000}
     {:name "Nested boxes (2 levels)" :fn benchmark-borders-nested :iterations 5000}
     {:name "Side-by-side (2 boxes)" :fn benchmark-borders-side-by-side :iterations 5000}]}

   :render
   {:name "Render & Diff Algorithm"
    :benchmarks
    [{:name "Diff: No changes (80x24)" :fn benchmark-render-diff-no-changes :iterations 10000}
     {:name "Diff: Full screen change (1920 cells)" :fn benchmark-render-diff-full-screen :iterations 5000}
     {:name "Diff: Partial change (100 cells)" :fn benchmark-render-diff-partial :iterations 10000}
     {:name "Buffer update (10 cells)" :fn benchmark-render-buffer-update :iterations 10000}]}

   :events
   {:name "Event Processing"
    :benchmarks
    [{:name "Parse simple key (a-z)" :fn benchmark-event-parse-simple-key :iterations 50000}
     {:name "Parse special key (arrows)" :fn benchmark-event-parse-special-key :iterations 50000}
     {:name "Parse Ctrl+key" :fn benchmark-event-parse-ctrl-key :iterations 50000}
     {:name "Key matching" :fn benchmark-event-key-matches :iterations 50000}
     {:name "Dispatch event with handler" :fn benchmark-event-dispatch :iterations 10000}]}

   :state
   {:name "State Management (Atom Operations)"
    :benchmarks
    [{:name "Atom deref (root + nested)" :fn benchmark-state-get :iterations 50000}
     {:name "Atom swap! (update)" :fn benchmark-state-update :iterations 20000}
     {:name "Atom swap! with assoc-in (3 levels)" :fn benchmark-state-update-in :iterations 20000}
     {:name "Atom watchers (add, trigger, remove)" :fn benchmark-state-watchers :iterations 10000}]}})

;; ============================================================================
;; Main Benchmark Runner
;; ============================================================================

(defn run-all-benchmarks
  "Run all benchmark suites and generate report."
  []
  (println "\n🔧 System Information:")
  (println (format "  Java Version: %s" (System/getProperty "java.version")))
  (println (format "  OS: %s %s"
                   (System/getProperty "os.name")
                   (System/getProperty "os.version")))
  (println (format "  Architecture: %s" (System/getProperty "os.arch")))
  (println (format "  Available Processors: %s" (.availableProcessors (Runtime/getRuntime))))
  (println (format "  Max Memory: %s MB" (/ (.maxMemory (Runtime/getRuntime)) 1024 1024)))

  (doseq [[suite-key {:keys [name benchmarks]}] (sort-by key benchmark-suites)]
    (println "\n" "━" 70)
    (println (format "  📦 %s" name))
    (println "  " "━" 70)
    (run-benchmark-suite benchmarks))

  (println "\n" "=" 70)
  (println "  ✅ All benchmarks completed!")
  (println "  " "=" 70))

(defn run-suite
  "Run a specific benchmark suite by key."
  [suite-key]
  (if-let [{:keys [name benchmarks]} (get benchmark-suites suite-key)]
    (do
      (println "\n" "━" 70)
      (println (format "  📦 %s" name))
      (println "  " "━" 70)
      (run-benchmark-suite benchmarks))
    (do
      (println (format "Unknown benchmark suite: %s" suite-key))
      (println "Available suites:")
      (doseq [[k {:keys [name]}] (sort-by key benchmark-suites)]
        (println (format "  - %s: %s" k name))))))

;; ============================================================================
;; Entry Point
;; ============================================================================

(defn -main
  "Main entry point for benchmark runner."
  [& args]
  (if (or (nil? args) (empty? args) (nil? (first args)))
    (run-all-benchmarks)
    (let [suite-key (keyword (first args))]
      (run-suite suite-key)))
  (System/exit 0))

;; Run benchmarks if executed directly
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
