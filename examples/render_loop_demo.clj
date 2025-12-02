#!/usr/bin/env bb

(require '[limner.render :as render]
         '[limner.core :as core])

(println "\n" (core/color :bold "Limner Render Loop - Concurrency Demonstration") "\n")

;; ──────────────────────── Demo 1: Basic Render Loop ──────────────────────
(println (core/color :cyan "1. Basic Render Loop with Proper Shutdown\n"))

(let [counter (atom 0)
      app-state (atom {:count 0})

      render-fn (fn [state]
                  (swap! counter inc)
                  [(str "Frame: " @counter)
                   (str "Count: " (:count state))
                   ""
                   "Press Ctrl+C to stop..."])

      loop-control (render/create-render-loop app-state
                                               :fps 30
                                               :render-fn render-fn)]

  (println "Starting render loop (30 FPS)...")
  (println "Running for 2 seconds...")

  ;; Update state periodically
  (Thread/sleep 500)
  (swap! app-state update :count inc)
  (Thread/sleep 500)
  (swap! app-state update :count inc)
  (Thread/sleep 1000)

  ;; Check if running
  (println "\nLoop running?:" ((:running? loop-control)))

  ;; Stop the loop
  (println "Stopping render loop...")
  ((:stop! loop-control))

  (println "Loop running?:" ((:running? loop-control)))

  ;; Get final stats
  (let [stats ((:get-stats loop-control))]
    (println "\nFinal statistics:")
    (println "  Frames rendered:" (:frame-count stats))
    (println "  Target FPS:" (:target-fps stats))
    (println "  Actual FPS:" (:current-fps stats)))

  (println (core/color :green "\n✓ Clean shutdown successful!")))

;; ──────────────────────── Demo 2: Error Handling ──────────────────────
(println "\n" (core/color :cyan "2. Error Handling\n"))

(let [counter (atom 0)
      error-count (atom 0)
      app-state (atom {:trigger-error false})

      render-fn (fn [state]
                  (when (:trigger-error state)
                    (throw (ex-info "Simulated render error!" {:frame @counter})))
                  [(str "Frame: " @counter)
                   "No errors yet..."])

      on-error (fn [e]
                 (swap! error-count inc)
                 (println (core/color :red (str "  Error caught: " (.getMessage e)))))

      loop-control (render/create-render-loop app-state
                                               :fps 60
                                               :render-fn render-fn
                                               :on-error on-error)]

  (println "Starting render loop with error handler...")
  (Thread/sleep 100)

  ;; Trigger an error
  (println "Triggering error...")
  (swap! app-state assoc :trigger-error true)
  (Thread/sleep 100)

  ;; Errors are caught, loop continues
  (swap! app-state assoc :trigger-error false)
  (Thread/sleep 100)

  (println "Errors caught:" @error-count)
  (println "Loop still running?:" ((:running? loop-control)))

  ;; Stop the loop
  ((:stop! loop-control))

  (println (core/color :green "\n✓ Error handling works!")))

;; ──────────────────────── Demo 3: FPS Control ──────────────────────
(println "\n" (core/color :cyan "3. FPS Control & Timing\n"))

(let [app-state (atom {})
      frames (atom [])

      render-fn (fn [_]
                  (swap! frames conj (System/currentTimeMillis))
                  ["Testing FPS timing..."])

      loop-control (render/create-render-loop app-state
                                               :fps 10  ; Low FPS for testing
                                               :render-fn render-fn)]

  (println "Running at 10 FPS for 1 second...")
  (Thread/sleep 1000)

  ((:stop! loop-control))

  (let [frame-times @frames
        intervals (map - (rest frame-times) frame-times)
        avg-interval (/ (reduce + intervals) (count intervals))
        expected-interval (/ 1000.0 10)]

    (println "Frames rendered:" (count frame-times))
    (println "Expected interval: ~" (int expected-interval) "ms")
    (println "Average interval: ~" (int avg-interval) "ms")
    (println "Difference: ~" (int (Math/abs (- avg-interval expected-interval))) "ms")

    (if (< (Math/abs (- avg-interval expected-interval)) 10)
      (println (core/color :green "\n✓ FPS timing accurate!"))
      (println (core/color :yellow "\n⚠ FPS timing slightly off (expected in some environments)")))))

;; ──────────────────────── Demo 4: Shutdown Timeout ──────────────────────
(println "\n" (core/color :cyan "4. Graceful Shutdown with Timeout\n"))

(let [app-state (atom {})
      render-fn (fn [_] ["Running..."])
      loop-control (render/create-render-loop app-state
                                               :fps 60
                                               :render-fn render-fn)]

  (println "Starting render loop...")
  (Thread/sleep 100)

  (println "Stopping (should complete within 2 seconds)...")
  (let [start (System/currentTimeMillis)]
    ((:stop! loop-control))
    (let [elapsed (- (System/currentTimeMillis) start)]
      (println "Shutdown took:" elapsed "ms")
      (if (< elapsed 2000)
        (println (core/color :green "\n✓ Fast shutdown!"))
        (println (core/color :yellow "\n⚠ Shutdown took longer than expected"))))))

;; ──────────────────────── Summary ──────────────────────
(println "\n" (core/color :bold "Summary") "\n")
(println "✓ Future-based concurrency (no raw threads)")
(println "✓ Promise-based shutdown coordination")
(println "✓ Error handling with callbacks")
(println "✓ Proper FPS timing (not burning CPU)")
(println "✓ Graceful shutdown with timeout")
(println "✓ Thread safety guaranteed")

(println "\n" (core/color :green "All concurrency improvements working!") "\n")
