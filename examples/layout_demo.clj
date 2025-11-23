#!/usr/bin/env bb
;; Layout Engine Demo
;; Demonstrates vertical stacking, horizontal splits, and grid layouts

(ns layout-demo
  (:require [limner.layout :as layout]
            [limner.borders :as borders]
            [limner.core :as core]
            [clojure.string :as str]))

(defn render-box
  "Render a box with its content and border"
  [box]
  (let [{:keys [x y width height content]} box
        content-str (or (:text content) (str content))
        lines (str/split-lines content-str)
        bordered (borders/draw-box lines :border-style :single)
        ;; Clip to fit in box
        clipped (take height bordered)]
    {:x x :y y :lines clipped}))

(defn render-to-screen
  "Render all boxes to a 2D character array"
  [boxes screen-width screen-height]
  (let [screen (vec (repeat screen-height (vec (repeat screen-width \ ))))]
    (reduce
     (fn [scr rendered-box]
       (let [{:keys [x y lines]} rendered-box]
         (reduce
          (fn [s [line-idx line]]
            (let [row (+ y line-idx)]
              (if (< row screen-height)
                (reduce
                 (fn [s2 [col-idx ch]]
                   (let [col (+ x col-idx)]
                     (if (< col screen-width)
                       (assoc-in s2 [row col] ch)
                       s2)))
                 s
                 (map-indexed vector line))
                s)))
          scr
          (map-indexed vector lines))))
     screen
     (map render-box boxes))))

(defn print-screen
  "Print the 2D character array to stdout"
  [screen]
  (doseq [row screen]
    (println (str/join row))))

;; ──────────────── Demo 1: Vertical Stack ────────────────
(defn demo-vertical-stack []
  (println (core/color :cyan "\n═══ Demo 1: Vertical Stack Layout ═══\n"))

  (let [components [{:constraint (layout/fixed 5)
                     :content {:text "Header\n(fixed 5 lines)"}}
                    {:constraint (layout/flex 1)
                     :content {:text "Main Content\n(flex 1)\nExpands to\nfill space"}}
                    {:constraint (layout/fixed 3)
                     :content {:text "Footer\n(fixed 3)"}}]
        stack-layout (layout/stack components :spacing 1)
        boxes (layout/layout-stack stack-layout 40 20)
        screen (render-to-screen boxes 40 20)]

    (print-screen screen)))

;; ──────────────── Demo 2: Horizontal Split ────────────────
(defn demo-horizontal-split []
  (println (core/color :cyan "\n═══ Demo 2: Horizontal Split Layout ═══\n"))

  (let [components [{:constraint (layout/fixed 15)
                     :content {:text "Sidebar\n(fixed 15\ncolumns)"}}
                    {:constraint (layout/flex 1)
                     :content {:text "Main Panel\n(flex 1)\nTakes remaining\nspace"}}]
        hsplit-layout (layout/hsplit components :spacing 1)
        boxes (layout/layout-hsplit hsplit-layout 50 12)
        screen (render-to-screen boxes 50 12)]

    (print-screen screen)))

;; ──────────────── Demo 3: Grid Layout ────────────────
(defn demo-grid []
  (println (core/color :cyan "\n═══ Demo 3: Grid Layout (2x2) ═══\n"))

  (let [components [{:content {:text "Cell A"}}
                    {:content {:text "Cell B"}}
                    {:content {:text "Cell C"}}
                    {:content {:text "Cell D"}}]
        grid-layout (layout/grid components :columns 2 :spacing 1)
        boxes (layout/layout-grid grid-layout 40 16)
        screen (render-to-screen boxes 40 16)]

    (print-screen screen)))

;; ──────────────── Demo 4: Nested Layouts ────────────────
(defn demo-nested []
  (println (core/color :cyan "\n═══ Demo 4: Nested Layouts (Stack + HSplit) ═══\n"))
  (println "Demonstrates a typical app layout:")
  (println "  Header (fixed)")
  (println "  Body: Sidebar (fixed) | Main Content (flex)")
  (println "  Footer (fixed)\n")

  ;; For nested layouts, we'd need to manually compose them
  ;; This shows the concept with comments
  (let [header-component {:constraint (layout/fixed 4)
                          :content {:text "App Header\n(fixed)"}}
        footer-component {:constraint (layout/fixed 3)
                          :content {:text "App Footer\n(fixed)"}}

        ;; Body would be a horizontal split (sidebar + main)
        sidebar-text "Sidebar\n(15 cols)"
        main-text "Main Content\n(fills space)"

        ;; For demo, we'll show them stacked
        components [{:constraint (layout/fixed 4)
                     :content {:text "═ Header ═\n(fixed 4)"}}
                    {:constraint (layout/fixed 8)
                     :content {:text (str "Sidebar    │ Main\n"
                                         "(fixed)    │ (flex)\n"
                                         "           │\n"
                                         "Body Area (flex 1)")}}
                    {:constraint (layout/fixed 3)
                     :content {:text "═ Footer ═\n(fixed 3)"}}]
        stack-layout (layout/stack components :spacing 0)
        boxes (layout/layout-stack stack-layout 50 18)
        screen (render-to-screen boxes 50 18)]

    (print-screen screen)))

;; ──────────────── Demo 5: Constraint Types ────────────────
(defn demo-constraints []
  (println (core/color :cyan "\n═══ Demo 5: Constraint Types ═══\n"))

  (let [components [{:constraint (layout/fixed 4)
                     :content {:text "Fixed\n(4 lines)"}}
                    {:constraint (layout/percent 25)
                     :content {:text "Percent\n(25%)"}}
                    {:constraint (layout/flex 1)
                     :content {:text "Flex 1"}}
                    {:constraint (layout/flex 2)
                     :content {:text "Flex 2\n(2x flex 1)"}}]
        stack-layout (layout/stack components :spacing 1)
        boxes (layout/layout-stack stack-layout 35 24)
        screen (render-to-screen boxes 35 24)]

    (print-screen screen)))

;; ──────────────── Main ────────────────
(defn -main []
  (demo-vertical-stack)
  (demo-horizontal-split)
  (demo-grid)
  (demo-nested)
  (demo-constraints)

  (println (core/color :bright-green "\n✓ Layout engine demo complete!\n"))
  (println "The layout engine supports:")
  (println "  • Fixed sizes (in characters/lines)")
  (println "  • Percentage-based sizing")
  (println "  • Flexible sizing (flex)")
  (println "  • Auto-sizing to content")
  (println "  • Vertical stacking")
  (println "  • Horizontal splits")
  (println "  • Grid layouts")
  (println "  • Spacing and padding"))

(when (or (System/getProperty "babashka.version")
          (= *file* (System/getProperty "babashka.file")))
  (-main))
