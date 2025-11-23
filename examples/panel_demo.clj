#!/usr/bin/env bb
;; Panel Component Demo
;; Demonstrates all panel features: titles, scrolling, collapsing, nesting

(ns panel-demo
  (:require [limner.components.panel :as panel]
            [limner.core :as core]
            [clojure.string :as str]))

(defn print-panel [p & [description]]
  (when description
    (println (core/color :cyan (str "\n" description))))
  (println (panel/render-to-string p))
  (println))

;; ──────────────── Demo 1: Basic Panels ────────────────
(defn demo-basic-panels []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 1: Basic Panels"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [simple (panel/panel :content "Simple panel\nwith content")]
    (print-panel simple "Simple panel (no title):"))

  (let [titled (panel/panel
                :title "My Panel"
                :content "Panel with a title")]
    (print-panel titled "Panel with title:"))

  (let [centered (panel/panel
                  :title "Centered Title"
                  :title-pos :center
                  :content "Title is centered")]
    (print-panel centered "Panel with centered title:"))

  (let [double-border (panel/panel
                       :title "Double Border"
                       :border-style :double
                       :content "Using double-line borders")]
    (print-panel double-border "Panel with double border:")))

;; ──────────────── Demo 2: Scrollable Panels ────────────────
(defn demo-scrollable []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 2: Scrollable Panels"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [long-content (str/join "\n" (map #(str "Line " %) (range 1 21)))
        p (panel/panel
           :title "Scrollable Content"
           :content long-content
           :height 8
           :scrollable true)]

    (print-panel p "Scrollable panel (showing lines 1-8):")

    (let [scrolled (panel/scroll-down p 5)]
      (print-panel scrolled "After scrolling down 5 lines (showing 6-13):"))

    (let [scrolled-more (panel/scroll-down p 12)]
      (print-panel scrolled-more "Scrolled to bottom (showing 13-20):"))))

;; ──────────────── Demo 3: Collapsible Panels ────────────────
(defn demo-collapsible []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 3: Collapsible Panels"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [p (panel/panel
           :title "Collapsible Panel"
           :content "This content can be\nhidden when collapsed"
           :collapsible true)]

    (print-panel p "Expanded panel (notice [-] indicator):")

    (let [collapsed (panel/toggle-collapse p)]
      (print-panel collapsed "Collapsed panel (notice [+] indicator):")

      (let [expanded (panel/toggle-collapse collapsed)]
        (print-panel expanded "Toggled back to expanded:")))))

;; ──────────────── Demo 4: Nested Panels ────────────────
(defn demo-nested []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 4: Nested Panels"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [inner (panel/panel
               :title "Inner Panel"
               :border-style :rounded
               :content "I'm inside another panel!")
        outer (panel/panel
               :title "Outer Container"
               :border-style :double)
        nested (panel/nest-panels outer inner)]

    (print-panel nested "Nested panel:"))

  (let [child1 (panel/panel
                :title "Child 1"
                :border-style :single
                :content "First child panel")
        child2 (panel/panel
                :title "Child 2"
                :border-style :single
                :content "Second child panel")
        child3 (panel/panel
                :title "Child 3"
                :border-style :single
                :content "Third child panel")
        parent (panel/panel
                :title "Parent with Multiple Children"
                :border-style :thick)
        nested (panel/nest-panels parent [child1 child2 child3] :spacing 1)]

    (print-panel nested "Multiple nested panels:")))

;; ──────────────── Demo 5: Panel with Padding ────────────────
(defn demo-padding []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 5: Panels with Padding"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [no-padding (panel/panel
                    :title "No Padding"
                    :content "Content touches\nthe borders")
        with-padding (panel/panel
                      :title "With Padding (2)"
                      :content "Content has\nspace around it"
                      :padding 2)]

    (print-panel no-padding "Panel without padding:")
    (print-panel with-padding "Panel with padding:")))

;; ──────────────── Demo 6: Panel State Management ────────────────
(defn demo-state-management []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 6: Panel State Management"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [p (panel/panel
           :title "Dynamic Panel"
           :content "Original content"
           :scrollable true
           :height 5)]

    (println (core/color :cyan "\nOriginal panel:"))
    (println (panel/render-to-string p))

    ;; Update content
    (let [updated (panel/set-content p "Updated content\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6")]
      (println (core/color :cyan "\nAfter updating content:"))
      (println (panel/render-to-string updated))

      ;; Scroll
      (let [scrolled (panel/scroll-down updated 2)]
        (println (core/color :cyan "\nAfter scrolling down 2 lines:"))
        (println (panel/render-to-string scrolled))))))

;; ──────────────── Demo 7: Complex Example ────────────────
(defn demo-complex []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 7: Complex Dashboard Example"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [;; Create status panel
        status (panel/panel
                :title "System Status"
                :border-style :rounded
                :content "CPU: 45%\nMemory: 62%\nDisk: 78%\nNetwork: OK")

        ;; Create log panel with scrolling
        logs (panel/panel
              :title "Recent Logs"
              :border-style :single
              :content (str/join "\n" ["[INFO] Server started"
                                       "[INFO] Connection established"
                                       "[WARN] High memory usage"
                                       "[INFO] Request processed"
                                       "[ERROR] Connection timeout"
                                       "[INFO] Retry successful"
                                       "[INFO] Cache cleared"
                                       "[INFO] Task completed"])
              :scrollable true
              :height 5
              :scroll-offset 0)

        ;; Create collapsible help panel
        help (panel/panel
              :title "Quick Help"
              :border-style :dots
              :content "Arrow keys: Scroll\nSpace: Toggle collapse\nQ: Quit"
              :collapsible true
              :collapsed false)

        ;; Nest everything in dashboard
        dashboard (panel/panel
                   :title "Application Dashboard"
                   :border-style :double)
        with-panels (panel/nest-panels dashboard [status logs help] :spacing 1)]

    (print-panel with-panels "Complete dashboard with nested panels:")))

;; ──────────────── Demo 8: Helper Functions ────────────────
(defn demo-helpers []
  (println (core/color :bright-green "\n═══════════════════════════════════════"))
  (println (core/color :bright-green "  Demo 8: Helper Functions"))
  (println (core/color :bright-green "═══════════════════════════════════════"))

  (let [long-content (str/join "\n" (map #(str "Line " %) (range 1 21)))
        p (panel/panel
           :title "Test Panel"
           :content long-content
           :height 5
           :scrollable true
           :collapsible true
           :scroll-offset 5)]

    (println (core/color :yellow "\nPanel properties:"))
    (println "  panel? ->" (panel/panel? p))
    (println "  scrollable? ->" (panel/scrollable? p))
    (println "  collapsible? ->" (panel/collapsible? p))
    (println "  collapsed? ->" (panel/collapsed? p))
    (println "  can-scroll-up? ->" (panel/can-scroll-up? p))
    (println "  can-scroll-down? ->" (panel/can-scroll-down? p))

    (println (core/color :yellow "\nAfter toggling collapse:"))
    (let [toggled (panel/toggle-collapse p)]
      (println "  collapsed? ->" (panel/collapsed? toggled)))))

;; ──────────────── Main ────────────────
(defn -main []
  (println (core/color :bright-green "\n"))
  (println (core/color :bright-green "╔═══════════════════════════════════════════════════╗"))
  (println (core/color :bright-green "║                                                   ║"))
  (println (core/color :bright-green "║        POORICH PANEL COMPONENT SHOWCASE           ║"))
  (println (core/color :bright-green "║                                                   ║"))
  (println (core/color :bright-green "╚═══════════════════════════════════════════════════╝"))

  (demo-basic-panels)
  (demo-scrollable)
  (demo-collapsible)
  (demo-nested)
  (demo-padding)
  (demo-state-management)
  (demo-complex)
  (demo-helpers)

  (println (core/color :bright-green "\n✓ Panel component demo complete!\n"))
  (println "Features demonstrated:")
  (println "  • Basic panels with titles")
  (println "  • Title positioning (left/center/right)")
  (println "  • Different border styles")
  (println "  • Scrollable content with indicators")
  (println "  • Collapsible/expandable panels")
  (println "  • Nested panels (single and multiple)")
  (println "  • Padding control")
  (println "  • Dynamic content updates")
  (println "  • State management (scroll, collapse)")
  (println "  • Helper functions for panel queries")
  (println))

(when (or (System/getProperty "babashka.version")
          (= *file* (System/getProperty "babashka.file")))
  (-main))
