#!/usr/bin/env bb

(ns state-demo
  "Demonstration of simplified state management with watchers"
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

;; ────────────────────── Watch Keys Demo ──────────────────────

(defn watch-keys-demo []
  (println (core/color :cyan "\n=== Watch Multiple Keys ==="))

  (let [s (state/create-state :initial-value {:name "Alice" :email "alice@example.com" :age 30})
        notifications (atom [])]

    (state/watch-keys s [:name :email] :contact-watcher
      (fn [changed-keys _ _]
        (swap! notifications conj changed-keys)
        (println (str "  Contact info changed: " (pr-str changed-keys)))))

    (println "\nUpdating name...")
    (state/update-state! s assoc :name "Bob")

    (println "\nUpdating age (not watched)...")
    (state/update-state! s assoc :age 31)

    (println "\nUpdating email...")
    (state/update-state! s assoc :email "bob@example.com")

    (println (str "\nTotal notifications: " (count @notifications)))))

;; ────────────────────── Application-Level Undo Demo ──────────────────────

(defn undo-demo []
  (println (core/color :cyan "\n=== Application-Level Undo/Redo ==="))
  (println "Note: Undo/redo removed from state.clj due to race conditions.")
  (println "Here's how to implement it at the application level:\n")

  ;; Simple application-level undo/redo
  (let [history (atom {:past [] :present {:count 0} :future []})

        push-state! (fn [new-state]
                     (swap! history
                            (fn [h]
                              {:past (conj (:past h) (:present h))
                               :present new-state
                               :future []})))

        undo! (fn []
               (when (seq (:past @history))
                 (swap! history
                        (fn [h]
                          {:past (pop (:past h))
                           :present (peek (:past h))
                           :future (conj (:future h) (:present h))}))))

        redo! (fn []
               (when (seq (:future @history))
                 (swap! history
                        (fn [h]
                          {:past (conj (:past h) (:present h))
                           :present (first (:future h))
                           :future (rest (:future h))}))))]

    (println "Initial:" (:present @history))

    ;; Make changes
    (push-state! {:count 1})
    (println "After change 1:" (:present @history))

    (push-state! {:count 2})
    (println "After change 2:" (:present @history))

    (push-state! {:count 3})
    (println "After change 3:" (:present @history))

    ;; Undo
    (println "\nUndoing...")
    (undo!)
    (println "After undo:" (:present @history))
    (undo!)
    (println "After undo:" (:present @history))

    ;; Redo
    (println "\nRedoing...")
    (redo!)
    (println "After redo:" (:present @history))

    (println "\n✓ Simple, thread-safe, no race conditions!")))

;; ────────────────────── Main Entry Point ──────────────────────

(defn -main []
  (println (core/color :bold "\n╔══════════════════════════════════════╗"))
  (println (core/color :bold "║  Limner State Management Demo       ║"))
  (println (core/color :bold "╚══════════════════════════════════════╝"))

  (basic-demo)
  (watchers-demo)
  (reactive-demo)
  (watch-keys-demo)
  (undo-demo)

  (println (core/color :green "\n✓ Demo complete!\n")))

(-main)
