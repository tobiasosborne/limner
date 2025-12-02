#!/usr/bin/env bb

(ns terminal-demo
  "Demonstration of terminal capability detection and graceful degradation"
  (:require [limner.terminal :as term]
            [limner.core :as core]
            [limner.borders :as borders]))

;; ────────────────────── Current Terminal Info ──────────────────────

(defn show-current-terminal []
  (println (core/color :bold "\n╔══════════════════════════════════════╗"))
  (println (core/color :bold "║  Your Terminal Capabilities         ║"))
  (println (core/color :bold "╚══════════════════════════════════════╝\n"))

  (println (term/capability-report))
  (println))

;; ────────────────────── Graceful Degradation Demo ──────────────────────

(defn show-border-degradation []
  (println (core/color :cyan "\n=== Border Style Degradation ===\n"))

  (println "With Unicode support:")
  (term/with-simulated-capabilities {:box-drawing true}
    (let [style (term/select-border-style)
          box (borders/draw-box ["Unicode borders look great!"]
                               :border-style style)]
      (doseq [line box]
        (println "  " line))))

  (println "\nWithout Unicode support (ASCII fallback):")
  (term/with-simulated-capabilities {:box-drawing false}
    (let [style (term/select-border-style)
          box (borders/draw-box ["ASCII borders work everywhere"]
                               :border-style style)]
      (doseq [line box]
        (println "  " line)))))

;; ────────────────────── Color Mode Demo ──────────────────────

(defn show-color-degradation []
  (println (core/color :cyan "\n=== Color Mode Selection ===\n"))

  (println "Modern terminal (truecolor):")
  (term/with-simulated-capabilities (term/simulate-modern-terminal)
    (println "  Color mode:" (term/select-color-mode))
    (println "  Example:" (core/color (core/rgb 255 100 50) "Custom RGB color")))

  (println "\n256-color terminal:")
  (term/with-simulated-capabilities {:ansi-colors true :256-colors true :truecolor false}
    (println "  Color mode:" (term/select-color-mode))
    (println "  Example:" (core/color (core/color-256 196) "256-color palette")))

  (println "\nBasic ANSI terminal:")
  (term/with-simulated-capabilities {:ansi-colors true :256-colors false :truecolor false}
    (println "  Color mode:" (term/select-color-mode))
    (println "  Example:" (core/color :red "Basic ANSI red")))

  (println "\nDumb terminal (no colors):")
  (term/with-simulated-capabilities (term/simulate-dumb-terminal)
    (println "  Color mode:" (term/select-color-mode))
    (println "  Example: Plain text (no color)")))

;; ────────────────────── Feature Detection Demo ──────────────────────

(defn show-feature-detection []
  (println (core/color :cyan "\n=== Feature Detection in Code ===\n"))

  (println "Using with-fallback for safe defaults:")
  (println "  Modern:" (term/with-fallback :unicode "✓" "[OK]"))
  (println "  Fallback:" (term/with-simulated-capabilities {:unicode false}
                           (term/with-fallback :unicode "✓" "[OK]")))

  (println "\nDynamic border selection:")
  (term/with-simulated-capabilities {:box-drawing true}
    (println "  Unicode terminal → border style:"
            (term/select-border-style)))

  (term/with-simulated-capabilities {:box-drawing false}
    (println "  ASCII terminal → border style:"
            (term/select-border-style))))

;; ────────────────────── Practical Example ──────────────────────

(defn render-status-message [status message]
  "Render a status message with appropriate fallbacks"
  (let [;; Select icon based on Unicode support
        icon (case status
               :success (term/with-fallback :unicode "✓" "[OK]")
               :error   (term/with-fallback :unicode "✗" "[ERROR]")
               :warning (term/with-fallback :unicode "⚠" "[WARN]")
               :info    (term/with-fallback :unicode "ℹ" "[INFO]"))

        ;; Select color based on terminal support
        color-fn (if (term/supports-feature? :ansi-colors)
                  (fn [c text] (core/color c text))
                  (fn [_ text] text))

        colored-icon (case status
                       :success (color-fn :green icon)
                       :error   (color-fn :red icon)
                       :warning (color-fn :yellow icon)
                       :info    (color-fn :cyan icon))]
    (str colored-icon " " message)))

(defn show-practical-example []
  (println (core/color :cyan "\n=== Practical Example: Status Messages ===\n"))

  (println "With full terminal support:")
  (term/with-simulated-capabilities (term/simulate-modern-terminal)
    (println "  " (render-status-message :success "Operation completed"))
    (println "  " (render-status-message :error "Connection failed"))
    (println "  " (render-status-message :warning "Deprecated feature"))
    (println "  " (render-status-message :info "Processing...")))

  (println "\nWith dumb terminal (no Unicode, no colors):")
  (term/with-simulated-capabilities (term/simulate-dumb-terminal)
    (println "  " (render-status-message :success "Operation completed"))
    (println "  " (render-status-message :error "Connection failed"))
    (println "  " (render-status-message :warning "Deprecated feature"))
    (println "  " (render-status-message :info "Processing..."))))

;; ────────────────────── Terminal Comparison ──────────────────────

(defn compare-terminals []
  (println (core/color :cyan "\n=== Terminal Type Comparison ===\n"))

  (let [terminals [["Modern (iTerm2, Windows Terminal)" (term/simulate-modern-terminal)]
                   ["Dumb (minimal support)" (term/simulate-dumb-terminal)]
                   ["Your actual terminal" (term/detect-capabilities)]]]

    (doseq [[name caps] terminals]
      (println (core/color :bold (str name ":")))
      (term/with-simulated-capabilities caps
        (println "  Border style:" (term/select-border-style))
        (println "  Color mode:  " (term/select-color-mode))
        (println "  Unicode:     " (if (term/supports-feature? :unicode) "✓" "✗"))
        (println "  Mouse:       " (if (term/supports-feature? :mouse) "✓" "✗")))
      (println))))

;; ────────────────────── Main Entry Point ──────────────────────

(defn -main []
  (println (core/color :bold "\n╔══════════════════════════════════════╗"))
  (println (core/color :bold "║  Terminal Capability Detection      ║"))
  (println (core/color :bold "╚══════════════════════════════════════╝"))

  (show-current-terminal)
  (show-border-degradation)
  (show-color-degradation)
  (show-feature-detection)
  (show-practical-example)
  (compare-terminals)

  (println (core/color :green "\n✓ Demo complete!\n")))

(-main)
