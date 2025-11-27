#!/usr/bin/env bb

(ns render-demo
  "Demonstration of the render loop with diff-based updates"
  (:require [limner.render :as render]
            [limner.core :as core]
            [limner.components.panel :as panel]
            [limner.components.progress :as progress]
            [limner.components.statusbar :as statusbar]
            [clojure.string :as str]))

;; ────────────────────── Application State ──────────────────────

(defn initial-state []
  {:counter 0
   :fps 0
   :frame-count 0
   :spinner (progress/spinner :dots)
   :progress-bar (progress/progress-bar :value 0)
   :running true})

;; ────────────────────── UI Rendering ──────────────────────

(defn render-app [state]
  "Render the application to a vector of lines"
  (let [;; Title panel
        title-panel (panel/render
                     (panel/panel
                      :title "Limner Render Loop Demo"
                      :border-style :double
                      :content (str "Frame: " (:frame-count state)
                                   "\nFPS: " (:fps state)
                                   "\nCounter: " (:counter state))
                      :padding 1))

        ;; Spinner panel
        spinner-panel (panel/render
                       (panel/panel
                        :title "Animated Spinner"
                        :content (str "  " (progress/render (:spinner state)))
                        :padding 1))

        ;; Progress bar panel
        progress-value (mod (:counter state) 101)
        progress-panel (panel/render
                        (panel/panel
                         :title (str "Progress Bar (" progress-value "%)")
                         :content (progress/render
                                   (assoc (:progress-bar state) :value progress-value))
                         :padding 1))

        ;; Info panel
        info-panel (panel/render
                    (panel/panel
                     :title "Info"
                     :border-style :rounded
                     :content (str/join "\n"
                                       [(core/color :cyan "This demo showcases:")
                                        ""
                                        (core/color :yellow "• Diff-based rendering")
                                        "  Only changed cells are updated"
                                        ""
                                        (core/color :yellow "• Double buffering")
                                        "  Prevents screen tearing"
                                        ""
                                        (core/color :yellow "• Frame rate control")
                                        "  Smooth 60 FPS updates"
                                        ""
                                        (core/color :yellow "• Dirty region tracking")
                                        "  Efficient screen updates"
                                        ""
                                        (core/color :green "Press Ctrl+C to exit")])
                     :padding 1))

        ;; Status bar
        status-lines (statusbar/render
                      (statusbar/statusbar
                       :left (str (core/color :cyan "Limner") " v1.0")
                       :center (core/color :yellow "Render Loop Active")
                       :right (str "Frame " (:frame-count state))))

        ;; Combine all panels with spacing
        all-lines (concat
                   title-panel
                   [""]
                   spinner-panel
                   [""]
                   progress-panel
                   [""]
                   info-panel
                   ["" ""]
                   [status-lines])]  ; Wrap status-lines in vector since it's a string

    (vec all-lines)))

;; ────────────────────── State Updates ──────────────────────

(defn update-state [state]
  "Update application state for next frame"
  (-> state
      (update :counter inc)
      (update :spinner progress/tick)
      (update :frame-count inc)))

;; ────────────────────── Main Demo ──────────────────────

(defn -main []
  (println (core/color :cyan "Starting Limner Render Loop Demo..."))
  (println "Press Ctrl+C to exit")
  (Thread/sleep 1000)

  (let [app-state (atom (initial-state))

        ;; Create render loop
        render-loop (render/create-render-loop
                     app-state
                     :fps 60
                     :render-fn render-app
                     :on-frame (fn [render-state]
                                 ;; Update FPS in app state
                                 (swap! app-state assoc
                                       :fps (render/get-fps render-state)
                                       :frame-count (:frame-count render-state))))

        ;; Update thread - updates state independently of rendering
        update-thread (Thread.
                       (fn []
                         (while (:running @app-state)
                           (swap! app-state update-state)
                           (Thread/sleep 50))))]

    ;; Start update thread
    (.start update-thread)

    ;; Wait for interrupt
    (try
      (while (:running @app-state)
        (Thread/sleep 100))
      (catch InterruptedException _
        (println "\n" (core/color :yellow "Interrupt received, stopping..."))))

    ;; Cleanup
    (swap! app-state assoc :running false)
    (.join update-thread 1000)
    ((:stop! render-loop))

    ;; Show final statistics
    (let [stats ((:get-stats render-loop))]
      (println)
      (println (core/color :green "Demo completed!"))
      (println (str "Total frames rendered: " (:frame-count stats)))
      (println (str "Final FPS: " (:current-fps stats)))
      (println (str "Screen dimensions: " (:width stats) "x" (:height stats))))))

;; ────────────────────── Entry Point ──────────────────────

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
