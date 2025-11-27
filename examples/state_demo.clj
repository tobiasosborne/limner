#!/usr/bin/env bb

(ns state-demo
  "Demonstration of state management with undo/redo and watchers"
  (:require [limner.state :as state]
            [limner.core :as core]))

;; ────────────────────── Basic State Usage ──────────────────────

(defn basic-demo []
  (println (core/color :cyan "\n=== Basic State Management ==="))

  ;; Create state with initial value
  (let [s (state/create-state :initial-value {:count 0 :name "Demo"})]
    (println "Initial state:" @s)

    ;; Update state
    (state/update-state! s assoc :count 1)
    (println "After update:" @s)

    ;; Update nested path
    (state/assoc-in-state! s [:user :email] "demo@example.com")
    (println "After nested update:" @s)))

;; ────────────────────── Undo/Redo Demo ──────────────────────

(defn undo-redo-demo []
  (println (core/color :cyan "\n=== Undo/Redo System ==="))

  (let [s (state/create-state :initial-value {:counter 0})]
    (println "Starting value:" (:counter @s))

    ;; Make several changes
    (doseq [i (range 1 6)]
      (state/update-state! s assoc :counter i)
      (println (str "  Step " i ": " (:counter @s))))

    ;; Undo a few times
    (println "\nUndoing...")
    (dotimes [_ 3]
      (state/undo! s)
      (println "  After undo: " (:counter @s)))

    ;; Redo
    (println "\nRedoing...")
    (dotimes [_ 2]
      (state/redo! s)
      (println "  After redo: " (:counter @s)))

    ;; Show history info
    (println "\nHistory info:")
    (println "  Can undo?" (state/can-undo? s))
    (println "  Can redo?" (state/can-redo? s))
    (println "  History size:" (state/history-size s))
    (println "  Position:" (state/history-position s))))

;; ────────────────────── Watchers Demo ──────────────────────

(defn watchers-demo []
  (println (core/color :cyan "\n=== Reactive Watchers ==="))

  (let [s (state/create-state :initial-value {:score 0 :level 1})
        changes (atom [])]

    ;; Add general watcher
    (state/add-watcher! s :logger
      (fn [_ _ old-state new-state]
        (swap! changes conj {:old old-state :new new-state})))

    ;; Add path-specific watcher
    (state/watch-path s [:score] :score-watcher
      (fn [old-score new-score]
        (when (and (< old-score 100) (>= new-score 100))
          (println (core/color :green "  🎉 Score milestone reached: 100!")))))

    ;; Add predicate watcher
    (state/watch-predicate s :level-up
      (fn [state] (>= (:score state) 50))
      (fn [_ _] (println (core/color :yellow "  ⬆️  Ready to level up!"))))

    ;; Make changes
    (println "\nMaking changes...")
    (state/update-state! s assoc :score 25)
    (println "Score: 25")

    (state/update-state! s assoc :score 50)
    (println "Score: 50")

    (state/update-state! s assoc :score 100)
    (println "Score: 100")

    (println (str "\nTotal watcher notifications: " (count @changes)))))

;; ────────────────────── Serialization Demo ──────────────────────

(defn serialization-demo []
  (println (core/color :cyan "\n=== State Serialization ==="))

  (let [s (state/create-state :initial-value {:game "Demo" :highscore 1000})]
    ;; Make some changes
    (state/update-state! s assoc :highscore 1500)
    (state/update-state! s assoc :highscore 2000)

    ;; Serialize
    (let [serialized (state/serialize s :include-history true)]
      (println "Serialized state:")
      (println (subs serialized 0 (min 100 (count serialized))) "...")

      ;; Deserialize
      (let [restored (state/deserialize serialized)]
        (println "\nRestored state:" @restored)
        (println "Can undo in restored?" (state/can-undo? restored))

        ;; Test undo on restored state
        (state/undo! restored)
        (println "After undo:" @restored)))))

;; ────────────────────── Reactive State Demo ──────────────────────

(defn reactive-demo []
  (println (core/color :cyan "\n=== Reactive State (Auto-Render) ==="))

  (let [render-count (atom 0)
        s (state/create-reactive-state
           :initial-value {:text "Hello"}
           :on-change (fn [old-state new-state]
                       (swap! render-count inc)
                       (println (str "  Render #" @render-count
                                   ": " (:text old-state)
                                   " → " (:text new-state)))))]

    (println "\nUpdating state (triggers automatic renders)...")
    (state/update-state! s assoc :text "Hello World")
    (state/update-state! s assoc :text "Hello Limner")
    (state/update-state! s assoc :text "Hello TUI")

    (println (str "\nTotal renders: " @render-count))))

;; ────────────────────── Main Entry Point ──────────────────────

(defn -main []
  (println (core/color :bold "\n╔══════════════════════════════════════╗"))
  (println (core/color :bold "║  Limner State Management Demo       ║"))
  (println (core/color :bold "╚══════════════════════════════════════╝"))

  (basic-demo)
  (undo-redo-demo)
  (watchers-demo)
  (serialization-demo)
  (reactive-demo)

  (println (core/color :green "\n✓ Demo complete!\n")))

(-main)
