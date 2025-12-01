#!/usr/bin/env bb

(require '[limner.core :as core])

(println "\n╔════════════════════════════════════════════════════════╗")
(println "║     Limner Color System - Complete Demonstration      ║")
(println "╚════════════════════════════════════════════════════════╝\n")

;; ──────────────────────── Basic 16-Color Palette ──────────────────────
(println (core/color :bold "1. Basic 16-Color Palette\n"))

(println "Standard Colors:")
(doseq [color [:black :red :green :yellow :blue :magenta :cyan :white]]
  (println (str "  " (core/color color (format "%-15s" (str color)))
                "  Sample text")))

(println "\nBright Colors:")
(doseq [color [:bright-black :bright-red :bright-green :bright-yellow
               :bright-blue :bright-magenta :bright-cyan :bright-white]]
  (println (str "  " (core/color color (format "%-15s" (str color)))
                "  Sample text")))

(println "\nBackground Colors:")
(println (str "  " (core/color :bg-red "  Red BG  ") "  "
              (core/color :bg-green "  Green BG  ") "  "
              (core/color :bg-blue "  Blue BG  ")))

(println (str "  " (core/color :bg-bright-yellow "  Bright Yellow BG  ") "  "
              (core/color :bg-bright-magenta "  Bright Magenta BG  ")))

;; ──────────────────────── 256-Color Palette ──────────────────────
(println (str "\n" (core/color :bold "2. 256-Color Palette Examples\n")))

(println "System colors (0-15):")
(print "  ")
(doseq [i (range 16)]
  (print (core/color (core/color-256 i) "██")))
(println)

(println "\n216-Color RGB Cube (16-231) - Red gradient:")
(print "  ")
(doseq [r (range 6)]
  (print (core/color (core/color-256 (+ 16 (* r 36))) "██")))
(println)

(println "\nGrayscale Ramp (232-255):")
(print "  ")
(doseq [i (range 232 256)]
  (print (core/color (core/color-256 i) "█")))
(println)

;; ──────────────────────── RGB/Truecolor ──────────────────────
(println (str "\n" (core/color :bold "3. RGB/Truecolor (24-bit)\n")))

(println "Custom RGB colors:")
(println (str "  " (core/color (core/rgb 255 100 0) "█ Orange (255, 100, 0)")))
(println (str "  " (core/color (core/rgb 147 112 219) "█ Medium Purple (147, 112, 219)")))
(println (str "  " (core/color (core/rgb 50 205 50) "█ Lime Green (50, 205, 50)")))
(println (str "  " (core/color (core/rgb 255 20 147) "█ Deep Pink (255, 20, 147)")))
(println (str "  " (core/color (core/rgb 0 191 255) "█ Deep Sky Blue (0, 191, 255)")))

(println "\nRGB Gradient (Red to Blue):")
(print "  ")
(doseq [i (range 0 256 8)]
  (print (core/color (core/rgb (- 255 i) 0 i) "█")))
(println)

(println "\nRGB Background Colors:")
(println (str "  " (core/color (core/bg-rgb 139 0 0) "  Dark Red Background  ")))
(println (str "  " (core/color (core/bg-rgb 0 100 0) "  Dark Green Background  ")))
(println (str "  " (core/color (core/bg-rgb 25 25 112) "  Midnight Blue Background  ")))

;; ──────────────────────── Style Combinations ──────────────────────
(println (str "\n" (core/color :bold "4. Style Combinations\n")))

(println (str "  " (core/color :bold "Bold text")))
(println (str "  " (core/color :dim "Dimmed text")))
(println (str "  " (core/color :italic "Italic text")))
(println (str "  " (core/color :underline "Underlined text")))
(println (str "  " (core/color :bold (core/color :red "Bold Red"))))
(println (str "  " (core/color :underline (core/color :green "Underlined Green"))))

;; ──────────────────────── Semantic Colors ──────────────────────
(println (str "\n" (core/color :bold "5. Semantic Color Presets\n")))

(println (str "  " (core/color (core/colors :error) "✗ Error message")))
(println (str "  " (core/color (core/colors :success) "✓ Success message")))
(println (str "  " (core/color (core/colors :warning) "⚠ Warning message")))
(println (str "  " (core/color (core/colors :info) "ℹ Info message")))
(println (str "  " (core/color (core/colors :muted) "  Muted text")))

;; ──────────────────────── Practical Examples ──────────────────────
(println (str "\n" (core/color :bold "6. Practical UI Examples\n")))

;; Status indicators
(println "Status Indicators:")
(println (str "  Server: " (core/color :green "● ONLINE")))
(println (str "  Database: " (core/color :yellow "● DEGRADED")))
(println (str "  API: " (core/color :red "● OFFLINE")))

;; Log levels
(println "\nLog Messages:")
(println (str "  " (core/color :bright-black "[DEBUG]") " Application started"))
(println (str "  " (core/color :cyan "[INFO]") " Processing request"))
(println (str "  " (core/color :yellow "[WARN]") " Slow query detected"))
(println (str "  " (core/color :red "[ERROR]") " Connection failed"))
(println (str "  " (core/color :bg-red " [CRITICAL] ") " System failure"))

;; Progress/Completion
(println "\nProgress Bar:")
(let [complete (apply str (repeat 30 "█"))
      incomplete (apply str (repeat 10 "░"))]
  (println (str "  [" (core/color :green complete)
                (core/color :bright-black incomplete) "] 75%")))

;; Syntax-like highlighting
(println "\nCode Syntax-like coloring:")
(println (str "  "
              (core/color :magenta "def") " "
              (core/color :blue "greet") "("
              (core/color :cyan "name") "):\n    "
              (core/color :yellow "\"Hello, \"") " + "
              (core/color :cyan "name")))

;; ──────────────────────── Color Availability ──────────────────────
(println (str "\n" (core/color :bold "7. Available Colors\n")))
(println (str "  Total basic colors defined: " (count (core/available-colors))))
(println (str "  Plus 256-color palette (0-255)"))
(println (str "  Plus 16.7 million RGB colors (0-255, 0-255, 0-255)"))

(println "\n" (core/color :green "✓ Color system demonstration complete!") "\n")
