#!/usr/bin/env bb

(ns streaming-demo
  "Demonstration of streaming text functionality with syntax highlighting"
  (:require [limner.streaming :as stream]
            [limner.core :as core]
            [limner.components.progress :as progress]
            [clojure.string :as str]))

;; ────────────────────── Demo Helpers ──────────────────────

(defn clear-screen []
  (print "\u001B[2J\u001B[H")
  (flush))

(defn move-cursor [row col]
  (print (str "\u001B[" row ";" col "H"))
  (flush))

(defn print-at [row col text]
  (move-cursor row col)
  (print text)
  (flush))

(defn wait [ms]
  (Thread/sleep ms))

;; ────────────────────── Demo 1: Basic Streaming ──────────────────────

(defn demo-basic-streaming []
  (clear-screen)
  (println (core/color :cyan "╔═══════════════════════════════════════╗"))
  (println (core/color :cyan "║  Demo 1: Basic Text Streaming        ║"))
  (println (core/color :cyan "╚═══════════════════════════════════════╝\n"))

  (let [text "Hello! This is a streaming text demonstration. Watch as each character appears one by one..."
        s (-> (stream/stream :text text :delay-ms 30)
              stream/start)]

    (println "Starting stream...\n")

    ;; Stream the text
    (loop [current s]
      (let [output (stream/render current)]
        (print-at 7 1 (str output "   "))
        (wait 20)

        (if-not (stream/completed? current)
          (recur (stream/tick current))
          (do
            (wait 2000)
            (println "\n\nStream completed!")))))))

;; ────────────────────── Demo 2: Code Streaming with Syntax ──────────────────────

(defn demo-code-streaming []
  (clear-screen)
  (println (core/color :cyan "╔═══════════════════════════════════════╗"))
  (println (core/color :cyan "║  Demo 2: Code Streaming               ║"))
  (println (core/color :cyan "╚═══════════════════════════════════════╝\n"))

  (let [code "(defn factorial [n]
  (if (<= n 1)
    1
    (* n (factorial (dec n)))))"
        s (-> (stream/stream
               :text code
               :delay-ms 25
               :lang :clojure
               :theme :default)
              stream/start)]

    (println "Streaming Clojure code with syntax highlighting...\n")

    ;; Stream the code
    (loop [current s]
      (let [output (stream/render current)]
        (print-at 7 1 output)
        (wait 20)

        (if-not (stream/completed? current)
          (recur (stream/tick current))
          (do
            (wait 3000)
            (println "\n\n\nCode streaming completed!")))))))

;; ────────────────────── Demo 3: Pause and Resume ──────────────────────

(defn demo-pause-resume []
  (clear-screen)
  (println (core/color :cyan "╔═══════════════════════════════════════╗"))
  (println (core/color :cyan "║  Demo 3: Pause and Resume             ║"))
  (println (core/color :cyan "╚═══════════════════════════════════════╝\n"))

  (let [text "This text will pause halfway through... and then resume streaming to completion."
        s (-> (stream/stream :text text :delay-ms 30)
              stream/start)]

    (println "Starting stream (will pause in middle)...\n")

    ;; Stream first half
    (loop [current s
           ticks 0]
      (let [output (stream/render current)]
        (print-at 7 1 (str output "   "))
        (wait 20)

        (cond
          ;; Pause at 40% progress
          (and (= ticks 30) (not (stream/paused? current)))
          (do
            (println "\n\n" (core/color :yellow "[PAUSED - waiting 2 seconds...]"))
            (wait 2000)
            (println (core/color :green "[RESUMING...]"))
            (recur (stream/resume (stream/pause current)) (inc ticks)))

          (stream/completed? current)
          (do
            (wait 1000)
            (println "\n\n\nStream completed!"))

          :else
          (recur (stream/tick current) (inc ticks)))))))

;; ────────────────────── Demo 4: Progress Indicator ──────────────────────

(defn demo-with-progress []
  (clear-screen)
  (println (core/color :cyan "╔═══════════════════════════════════════╗"))
  (println (core/color :cyan "║  Demo 4: Streaming with Progress     ║"))
  (println (core/color :cyan "╚═══════════════════════════════════════╝\n"))

  (let [text "Streaming text with a visual progress indicator showing completion percentage."
        s (-> (stream/stream :text text :delay-ms 40)
              stream/start)]

    (println "Watch the progress bar fill as text streams...\n")

    ;; Stream with progress bar
    (loop [current s]
      (let [output (stream/render current)
            prog (stream/progress current)
            bar (progress/progress-bar :value prog :width 40)]

        (print-at 7 1 (str output "   "))
        (print-at 9 1 "")
        (print (progress/render bar))
        (print (str "  " prog "% complete   "))
        (flush)
        (wait 30)

        (if-not (stream/completed? current)
          (recur (stream/tick current))
          (do
            (wait 1500)
            (println "\n\n\nStreaming complete!")))))))

;; ────────────────────── Demo 5: Multiple Languages ──────────────────────

(defn demo-multi-language []
  (clear-screen)
  (println (core/color :cyan "╔═══════════════════════════════════════╗"))
  (println (core/color :cyan "║  Demo 5: Multiple Languages           ║"))
  (println (core/color :cyan "╚═══════════════════════════════════════╝\n"))

  (let [examples [["Clojure" :clojure "(defn hello [] \"World\")"]
                  ["Python" :python "def hello():\n    return \"World\""]
                  ["JavaScript" :javascript "const hello = () => \"World\";"]]

        stream-example (fn [[lang-name lang code]]
                        (println (str "\n" (core/color :bright-green (str "→ " lang-name ":"))))
                        (wait 500)
                        (let [s (-> (stream/stream
                                     :text code
                                     :delay-ms 20
                                     :lang lang
                                     :theme :default)
                                    stream/start)]
                          (loop [current s]
                            (let [output (stream/render current)]
                              (print-at 12 1 output)
                              (wait 15)

                              (if-not (stream/completed? current)
                                (recur (stream/tick current))
                                (wait 1500))))))]

    (println "Streaming code in different languages with syntax highlighting:\n")
    (doseq [example examples]
      (stream-example example))

    (println "\n\n\nAll languages streamed!")))

;; ────────────────────── Main Menu ──────────────────────

(defn print-menu []
  (clear-screen)
  (println (core/color :cyan "╔═══════════════════════════════════════╗"))
  (println (core/color :cyan "║     Streaming Text Demo Suite         ║"))
  (println (core/color :cyan "╚═══════════════════════════════════════╝\n"))
  (println "Select a demo to run:\n")
  (println "  1. Basic text streaming")
  (println "  2. Code streaming with syntax highlighting")
  (println "  3. Pause and resume")
  (println "  4. Streaming with progress indicator")
  (println "  5. Multiple languages")
  (println "  6. Run all demos")
  (println "  q. Quit\n")
  (print "Enter choice: ")
  (flush))

(defn run-demo [choice]
  (case choice
    "1" (demo-basic-streaming)
    "2" (demo-code-streaming)
    "3" (demo-pause-resume)
    "4" (demo-with-progress)
    "5" (demo-multi-language)
    "6" (do
          (demo-basic-streaming)
          (wait 1000)
          (demo-code-streaming)
          (wait 1000)
          (demo-pause-resume)
          (wait 1000)
          (demo-with-progress)
          (wait 1000)
          (demo-multi-language))
    (println "Invalid choice!"))
  (wait 1000))

(defn -main [& args]
  (core/hide-cursor)
  (try
    (loop []
      (print-menu)
      (let [choice (str/trim (read-line))]
        (when-not (= choice "q")
          (run-demo choice)
          (recur))))
    (finally
      (core/show-cursor)
      (clear-screen)
      (println "Thanks for trying the streaming demo!"))))

;; Run if executed directly
(when (= *file* (System/getProperty "babashka.file"))
  (-main))
