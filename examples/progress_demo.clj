#!/usr/bin/env bb

(ns progress-demo
  "Demo showcasing progress indicators"
  (:require [limner.components.progress :as progress]
            [limner.components.panel :as panel]
            [limner.core :as core]))

;; ────────────────────── Demo Helpers ──────────────────────
(defn clear-screen []
  (print "\033[2J\033[H")
  (flush))

(defn sleep-ms [ms]
  (Thread/sleep ms))

(defn demo-section [title]
  (println)
  (println (core/color :cyan (str "=== " title " ===")))
  (println))

;; ────────────────────── Spinner Demo ──────────────────────
(defn demo-spinners []
  (demo-section "Spinners")

  (doseq [style [:dots :line :arrow :circle :box]]
    (print (str (name style) ": "))
    (let [spinner (progress/spinner :style style)]
      ;; Animate for 2 seconds
      (dotimes [_ 20]
        (let [s (progress/tick spinner)]
          (print (progress/render s))
          (flush)
          (sleep-ms 100)
          (print "\b")
          (flush)))
      (println "✓")))

  (println)
  (println "Custom spinner:")
  (let [spinner (progress/spinner :style :custom :frames ["🌑" "🌒" "🌓" "🌔" "🌕" "🌖" "🌗" "🌘"])]
    (print "Moon phases: ")
    (dotimes [i 32]
      (let [s (nth (iterate progress/tick spinner) i)]
        (print (progress/render s))
        (flush)
        (sleep-ms 100)
        (print "\b")
        (flush)))
    (println "✓")))

;; ────────────────────── Progress Bar Demo ──────────────────────
(defn demo-progress-bars []
  (demo-section "Progress Bars - Determinate")

  (println "Downloading files...")
  (let [bar (progress/progress-bar :value 0 :width 40)]
    (doseq [v (range 0 101 2)]
      (let [b (progress/set-value bar v)]
        (print "\r" (progress/render b))
        (flush)
        (sleep-ms 50))))
  (println " ✓\n")

  (println "Without percentage:")
  (let [bar (progress/progress-bar :value 0 :width 30 :show-percentage false)]
    (doseq [v (range 0 101 5)]
      (let [b (progress/set-value bar v)]
        (print "\r" (progress/render b))
        (flush)
        (sleep-ms 50))))
  (println " ✓\n")

  (demo-section "Progress Bars - Indeterminate")

  (println "Processing...")
  (let [bar (progress/progress-bar :type :indeterminate :width 40)]
    (dotimes [i 40]
      (let [b (nth (iterate progress/tick bar) i)]
        (print "\r" (progress/render b))
        (flush)
        (sleep-ms 100))))
  (println " ✓"))

;; ────────────────────── Step Indicator Demo ──────────────────────
(defn demo-step-indicators []
  (demo-section "Step Indicators")

  (let [steps (progress/step-indicator :current 1 :total 5
                                       :labels ["Initialize" "Load Data" "Process" "Validate" "Complete"])]
    (println "Installation wizard:")
    (loop [s steps]
      (println (str "\n" (progress/render s)))
      (sleep-ms 1000)
      (when (< (:current s) (:total s))
        (recur (progress/next-step s)))))

  (println "\n\nWith progress bar:")
  (let [steps (progress/step-indicator :current 1 :total 4
                                       :show-progress true)]
    (loop [s steps]
      (println "\n" (progress/render s))
      (sleep-ms 800)
      (when (< (:current s) (:total s))
        (recur (progress/next-step s))))))

;; ────────────────────── Pulse Demo ──────────────────────
(defn demo-pulse []
  (demo-section "Pulse Effect")

  (println "Waiting for response...")
  (let [pulse (progress/pulse :text "● Connecting" :speed :normal)]
    (dotimes [i 24]
      (let [p (nth (iterate progress/tick pulse) i)]
        (print "\r" (progress/render p))
        (flush)
        (sleep-ms 100))))
  (println "\r" (core/color :green "✓ Connected    "))

  (println "\nFast pulse:")
  (let [pulse (progress/pulse :text "⚡ Processing" :speed :fast)]
    (dotimes [i 20]
      (let [p (nth (iterate progress/tick pulse) i)]
        (print "\r" (progress/render p))
        (flush)
        (sleep-ms 80))))
  (println "\r" (core/color :green "✓ Complete     ")))

;; ────────────────────── Combined Demo ──────────────────────
(defn demo-combined []
  (demo-section "Combined Progress Display")

  (println "Building project...\n")
  (let [spinner (progress/spinner :style :dots)
        bar (progress/progress-bar :value 0 :width 30)
        steps (progress/step-indicator :current 1 :total 3
                                       :labels ["Compile" "Test" "Package"])]
    (dotimes [i 60]
      (let [progress-val (* i (/ 100 60))
            current-step (inc (quot i 20))
            s (nth (iterate progress/tick spinner) i)
            b (progress/set-value bar progress-val)
            st (assoc steps :current current-step)]
        (print "\r")
        (print (progress/render s) " ")
        (print (progress/render b) " ")
        (print (progress/render st))
        (flush)
        (sleep-ms 100))))
  (println "\n" (core/color :green "✓ Build complete!")))

;; ────────────────────── Panel Integration Demo ──────────────────────
(defn demo-with-panels []
  (demo-section "Progress in Panels")

  (let [spinner (progress/spinner :style :dots)
        bar (progress/progress-bar :value 65 :width 35)

        spinner-panel (panel/panel
                       :title "Status"
                       :content (progress/render spinner)
                       :border-style :single)

        progress-panel (panel/panel
                        :title "Download Progress"
                        :content (progress/render bar)
                        :border-style :single)]

    (println (panel/render-to-string spinner-panel))
    (println)
    (println (panel/render-to-string progress-panel))))

;; ────────────────────── Main Demo ──────────────────────
(defn -main [& args]
  (clear-screen)
  (println (core/color :bright-green "╔═══════════════════════════════════════════╗"))
  (println (core/color :bright-green "║   Poorich Progress Components Demo       ║"))
  (println (core/color :bright-green "╚═══════════════════════════════════════════╝"))

  (demo-spinners)
  (sleep-ms 500)

  (demo-progress-bars)
  (sleep-ms 500)

  (demo-step-indicators)
  (sleep-ms 500)

  (demo-pulse)
  (sleep-ms 500)

  (demo-combined)
  (sleep-ms 500)

  (demo-with-panels)

  (println)
  (println (core/color :cyan "Demo complete!"))
  (println))

;; Run the demo
(-main)
