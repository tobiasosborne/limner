#!/usr/bin/env bb

(ns statusbar-demo
  "Demo of the statusbar component"
  (:require [limner.components.statusbar :as statusbar]
            [limner.core :as core]))

(defn demo-basic []
  (println "\n=== Basic Statusbar ===")
  (let [bar (statusbar/statusbar
             :left "File: example.clj"
             :center "Ready"
             :right "Ctrl+C to quit"
             :width 80)]
    (println (statusbar/render bar))))

(defn demo-git-and-file-info []
  (println "\n=== Git Branch and File Info ===")
  (let [bar (statusbar/statusbar
             :left (str (statusbar/format-git-branch "main")
                       " "
                       (statusbar/format-file-info "src/app.clj" 123 45))
             :center (core/color :green "✓ All tests passing")
             :right (statusbar/format-timestamp)
             :width 80
             :bg-color :bright-blue)]
    (println (statusbar/render bar))))

(defn demo-keybindings []
  (println "\n=== Keybinding Hints ===")
  (let [keybindings (str (statusbar/format-keybinding "Ctrl+S" "Save")
                        " | "
                        (statusbar/format-keybinding "Ctrl+Q" "Quit")
                        " | "
                        (statusbar/format-keybinding "F1" "Help"))
        bar (statusbar/statusbar
             :left "Limner v1.0"
             :right keybindings
             :width 80
             :bg-color :cyan)]
    (println (statusbar/render bar))))

(defn demo-truncation []
  (println "\n=== Truncation (narrow width) ===")
  (let [bar (statusbar/statusbar
             :left "This is a very long status message"
             :center "Center text"
             :right "Right section also long"
             :width 40)]
    (println (statusbar/render bar))))

(defn demo-colors []
  (println "\n=== Different Colors ===")
  (doseq [color [:black :blue :green :yellow :red :magenta :cyan]]
    (let [bar (statusbar/statusbar
               :left (str "Color: " (name color))
               :center "Sample"
               :right "Status"
               :width 60
               :bg-color color)]
      (println (statusbar/render bar)))))

(defn demo-dynamic-update []
  (println "\n=== Dynamic Updates ===")
  (let [initial (statusbar/statusbar
                 :left "Loading..."
                 :width 60
                 :bg-color :yellow)
        _ (do
            (println (statusbar/render initial))
            (Thread/sleep 1000))

        updated (statusbar/update-section initial :left "Processing data...")
        _ (do
            (println (statusbar/render updated))
            (Thread/sleep 1000))

        complete (-> updated
                     (statusbar/update-section :left (core/color :green "✓ Complete!"))
                     (statusbar/update-section :bg-color :green))]
    (println (statusbar/render complete))))

(defn demo-claude-code-style []
  (println "\n=== Claude Code Style ===")
  (let [bar (statusbar/statusbar
             :left (str (core/color :cyan (statusbar/format-git-branch "feature/statusbar"))
                       " "
                       (statusbar/format-file-info "statusbar.clj" 156 28))
             :center (core/color :bright-green "● Running")
             :right (str (statusbar/format-timestamp)
                        " | "
                        (statusbar/format-keybinding "^C" "Cancel"))
             :width 100
             :bg-color :bright-black
             :fg-color :white)]
    (println (statusbar/render bar))))

(defn -main []
  (println "Limner Statusbar Component Demo")
  (println "================================\n")

  (demo-basic)
  (demo-git-and-file-info)
  (demo-keybindings)
  (demo-truncation)
  (demo-colors)
  (demo-dynamic-update)
  (demo-claude-code-style)

  (println "\n=== Demo Complete ==="))

;; Run if executed directly
(when (= *file* (System/getProperty "babashka.file"))
  (-main))
