#!/usr/bin/env bb
;; Border Styles Demo
;; Demonstrates all border styles, shadows, nesting, and custom styles

(ns borders-demo
  (:require [limner.borders :as borders]
            [limner.core :as core]
            [clojure.string :as str]))

(defn print-box [box & [title]]
  (when title
    (println (core/color :cyan (str "\n" title))))
  (doseq [line box]
    (println line)))

;; ──────────────── Demo 1: Predefined Styles ────────────────
(defn demo-predefined-styles []
  (let [header (borders/draw-titled-box "Demo 1: Predefined Border Styles"
                                        []
                                        :border-style :double)]
    (doseq [line header]
      (println (core/color :bright-green line))))

  (let [content ["Hello, World!" "This is a demo" "of border styles"]]

    (print-box (borders/draw-box content :border-style :single)
               "Single border:")

    (print-box (borders/draw-box content :border-style :double)
               "Double border:")

    (print-box (borders/draw-box content :border-style :rounded)
               "Rounded border:")

    (print-box (borders/draw-box content :border-style :thick)
               "Thick border:")

    (print-box (borders/draw-box content :border-style :ascii)
               "ASCII border:")

    (print-box (borders/draw-box content :border-style :dots)
               "Dots border:")

    (print-box (borders/draw-box content :border-style :stars)
               "Stars border:")))

;; ──────────────── Demo 2: Titled Boxes ────────────────
(defn demo-titled-boxes []
  (println (core/color :bright-green "\n╔══════════════════════════════════════════╗"))
  (println (core/color :bright-green "║  Demo 2: Titled Boxes                   ║"))
  (println (core/color :bright-green "╚══════════════════════════════════════════╝"))

  (let [content ["Titles can be positioned" "left, center, or right"]]

    (print-box (borders/draw-titled-box "Left Title" content
                                        :border-style :double
                                        :title-pos :left)
               "Title position: left")

    (print-box (borders/draw-titled-box "Center Title" content
                                        :border-style :double
                                        :title-pos :center)
               "Title position: center")

    (print-box (borders/draw-titled-box "Right Title" content
                                        :border-style :double
                                        :title-pos :right)
               "Title position: right")))

;; ──────────────── Demo 3: Custom Styles ────────────────
(defn demo-custom-styles []
  (println (core/color :bright-green "\n╔══════════════════════════════════════════╗"))
  (println (core/color :bright-green "║  Demo 3: Custom Border Styles           ║"))
  (println (core/color :bright-green "╚══════════════════════════════════════════╝"))

  (let [content ["Custom borders" "can be defined" "as vectors or maps"]]

    ;; Custom as vector
    (let [custom-vec ["<" ">" "{" "}" "=" "|"]]
      (print-box (borders/draw-box content :border-style custom-vec)
                 "Custom style (vector): [\"<\" \">\" \"{\" \"}\" \"=\" \"|\"]"))

    ;; Custom as map
    (let [custom-map {:top-left "╒"
                      :top-right "╕"
                      :bottom-left "╘"
                      :bottom-right "╛"
                      :horizontal "═"
                      :vertical "│"}]
      (print-box (borders/draw-box content :border-style custom-map)
                 "Custom style (map): mixed single/double"))

    ;; Emoji style
    (let [emoji-style ["🌟" "🌟" "🌟" "🌟" "⭐" "⭐"]]
      (print-box (borders/draw-box ["Emoji borders!"] :border-style emoji-style)
                 "Custom style: emojis"))))

;; ──────────────── Demo 4: Shadow Effects ────────────────
(defn demo-shadows []
  (println (core/color :bright-green "\n╔══════════════════════════════════════════╗"))
  (println (core/color :bright-green "║  Demo 4: Shadow Effects                  ║"))
  (println (core/color :bright-green "╚══════════════════════════════════════════╝"))

  (let [content ["Boxes can have" "drop shadows"]]

    (let [box (borders/draw-box content :border-style :double)]
      (print-box (borders/add-shadow box)
                 "Light shadow (░):"))

    (let [box (borders/draw-box content :border-style :rounded)]
      (print-box (borders/add-heavy-shadow box)
                 "Heavy shadow (▓):"))

    ;; Colored shadow
    (let [box (borders/draw-box content :border-style :single)]
      (print-box (borders/add-shadow box :shadow-char "█" :shadow-color :blue)
                 "Colored shadow:"))))

;; ──────────────── Demo 5: Nested Boxes ────────────────
(defn demo-nested []
  (println (core/color :bright-green "\n╔══════════════════════════════════════════╗"))
  (println (core/color :bright-green "║  Demo 5: Nested Boxes                   ║"))
  (println (core/color :bright-green "╚══════════════════════════════════════════╝"))

  ;; Simple nested box
  (let [inner (borders/draw-box ["Inner box" "content here"] :border-style :single)
        nested (borders/nest-box inner 2)
        outer (borders/draw-titled-box "Outer Container" nested :border-style :double)]
    (print-box outer "Nested box with padding:"))

  ;; Side by side boxes
  (let [left-box (borders/draw-box ["Left" "Box" "Here"] :border-style :rounded)
        right-box (borders/draw-box ["Right" "Box" "Here"] :border-style :rounded)
        combined (borders/side-by-side left-box right-box 3)
        outer (borders/draw-titled-box "Two Boxes" combined :border-style :thick)]
    (print-box outer "\nSide-by-side boxes:")))

;; ──────────────── Demo 6: Colored Borders ────────────────
(defn demo-colored []
  (println (core/color :bright-green "\n╔══════════════════════════════════════════╗"))
  (println (core/color :bright-green "║  Demo 6: Colored Borders                 ║"))
  (println (core/color :bright-green "╚══════════════════════════════════════════╝"))

  (let [content ["Borders can be" "colored with ANSI"]]

    (print-box (borders/colorize-border
                (borders/draw-box content :border-style :double)
                :cyan)
               "Cyan borders:")

    (print-box (borders/colorize-border
                (borders/draw-box content :border-style :thick)
                :yellow)
               "Yellow borders:")

    (print-box (borders/colorize-border
                (borders/draw-box content :border-style :rounded)
                :green)
               "Green borders:")))

;; ──────────────── Demo 7: Complex Example ────────────────
(defn demo-complex []
  (println (core/color :bright-green "\n╔══════════════════════════════════════════╗"))
  (println (core/color :bright-green "║  Demo 7: Complex Example                 ║"))
  (println (core/color :bright-green "╚══════════════════════════════════════════╝"))

  ;; Create a complex nested structure with colors and shadows
  (let [;; Two inner boxes side by side
        inner-left (borders/draw-box ["Status:" "✓ Ready"] :border-style :rounded)
        inner-right (borders/draw-box ["Count:" "42 items"] :border-style :rounded)
        side-by-side (borders/side-by-side inner-left inner-right 2)

        ;; Wrap in outer box with title
        outer (borders/draw-titled-box "Dashboard" side-by-side
                                       :border-style :double
                                       :title-pos :center)

        ;; Add shadow
        with-shadow (borders/add-shadow outer)

        ;; Colorize
        colored (borders/colorize-border with-shadow :cyan)]

    (print-box colored "Combined: nested boxes + shadow + color:")))

;; ──────────────── Demo 8: Width Handling ────────────────
(defn demo-width-handling []
  (println (core/color :bright-green "\n╔══════════════════════════════════════════╗"))
  (println (core/color :bright-green "║  Demo 8: Width Handling                  ║"))
  (println (core/color :bright-green "╚══════════════════════════════════════════╝"))

  ;; Box adapts to longest content
  (let [content ["short" "medium length" "very very long content line here"]]
    (print-box (borders/draw-box content :border-style :single)
               "Box adapts to longest line:"))

  ;; Title wider than content
  (let [content ["Small"]]
    (print-box (borders/draw-titled-box "Very Long Title That Extends Beyond Content"
                                        content
                                        :border-style :double)
               "\nTitle wider than content:")))

;; ──────────────── Main ────────────────
(defn -main []
  (println (core/color :bright-green "\n"))
  (println (core/color :bright-green "╔═══════════════════════════════════════════════════╗"))
  (println (core/color :bright-green "║                                                   ║"))
  (println (core/color :bright-green "║        LIMNER BORDER STYLES SHOWCASE              ║"))
  (println (core/color :bright-green "║                                                   ║"))
  (println (core/color :bright-green "╚═══════════════════════════════════════════════════╝"))

  (demo-predefined-styles)
  (demo-titled-boxes)
  (demo-custom-styles)
  (demo-shadows)
  (demo-nested)
  (demo-colored)
  (demo-complex)
  (demo-width-handling)

  (println (core/color :bright-green "\n✓ Border demo complete!\n"))
  (println "Features demonstrated:")
  (println "  • 7 predefined border styles")
  (println "  • Custom border styles (vector or map)")
  (println "  • Titled boxes (left/center/right)")
  (println "  • Shadow effects (light and heavy)")
  (println "  • Nested boxes with padding")
  (println "  • Side-by-side box arrangement")
  (println "  • Colored borders")
  (println "  • Automatic width calculation")
  (println))

(when (or (System/getProperty "babashka.version")
          (= *file* (System/getProperty "babashka.file")))
  (-main))
