#!/usr/bin/env bb

(ns events-interactive
  "Interactive demonstration of the events system - captures real keyboard input"
  (:require [limner.core :as core]
            [limner.events :as events]
            [clojure.string :as str]
            [babashka.process :as process])
  (:import [java.io BufferedReader InputStreamReader]))

;; ────────────────────── Terminal Raw Mode ──────────────────────

(defn enable-raw-mode!
  "Enable raw mode for character-by-character input"
  []
  (let [pb (process/process ["stty" "-ignbrk" "-brkint" "-parmrk" "-istrip"
                              "-inlcr" "-igncr" "-icrnl" "-ixon" "-opost"
                              "-echo" "-echonl" "-icanon" "-isig" "-iexten"
                              "-parenb" "cs8" "min" "1" "time" "0"]
                             {:inherit true})]
    @pb))

(defn disable-raw-mode!
  "Restore normal terminal mode"
  []
  (let [pb (process/process ["stty" "sane"] {:inherit true})]
    @pb))

(defn read-input
  "Read input from stdin with timeout. Returns string or nil."
  [timeout-ms]
  (let [reader (BufferedReader. (InputStreamReader. System/in))
        start (System/currentTimeMillis)]
    (loop [result ""]
      (if (.ready reader)
        (let [ch (.read reader)]
          (if (= -1 ch)
            (if (empty? result) nil result)
            (recur (str result (char ch)))))
        (if (and (empty? result)
                 (< (- (System/currentTimeMillis) start) timeout-ms))
          (do
            (Thread/sleep 1)
            (recur result))
          (if (empty? result) nil result))))))

;; ────────────────────── UI Helpers ──────────────────────

(defn clear-screen []
  (print "\u001b[2J\u001b[H")
  (flush))

(defn move-to [row col]
  (print (str "\u001b[" row ";" col "H"))
  (flush))

(defn draw-box [x y width height title]
  (move-to y x)
  (print (str "┌" (str/join (repeat (- width 2) "─")) "┐"))
  (when title
    (move-to y (+ x 2))
    (print (core/color :bold title)))
  (doseq [i (range 1 (dec height))]
    (move-to (+ y i) x)
    (print "│")
    (move-to (+ y i) (+ x width -1))
    (print "│"))
  (move-to (+ y height -1) x)
  (print (str "└" (str/join (repeat (- width 2) "─")) "┘"))
  (flush))

(defn draw-text [x y text]
  (move-to y x)
  (print text)
  (flush))

;; ────────────────────── Application State ──────────────────────

(defn initial-state []
  {:focus (events/focus-state :components [:input1 :input2 :log])
   :components {:input1 {:value "" :label "Text Input 1"}
                :input2 {:value "" :label "Text Input 2"}
                :log {:lines []}}
   :last-event nil
   :running true})

;; ────────────────────── Event Handlers ──────────────────────

(defn handle-input [event component-state]
  "Handle keyboard input for text input components"
  (cond
    ;; Regular character - add to value
    (:char event)
    (update component-state :value str (:char event))

    ;; Backspace - remove last character
    (= :backspace (:key event))
    (update component-state :value #(if (empty? %) % (subs % 0 (dec (count %)))))

    ;; Enter - clear input
    (= :enter (:key event))
    (assoc component-state :value "")

    :else
    component-state))

(defn handle-log [event component-state]
  "Handle events for the log component"
  component-state)

;; ────────────────────── Rendering ──────────────────────

(defn render-ui [state]
  "Render the complete UI"
  (clear-screen)

  ;; Title
  (move-to 1 2)
  (print (core/color :cyan "╔═══════════════════════════════════════════════════════════════════════╗"))
  (move-to 2 2)
  (print (core/color :cyan "║  "))
  (print (core/color :bold "LIMNER INTERACTIVE EVENT DEMO"))
  (print (core/color :cyan "  - Press Ctrl+Q to quit                    ║"))
  (move-to 3 2)
  (print (core/color :cyan "╚═══════════════════════════════════════════════════════════════════════╝"))

  ;; Input boxes
  (let [input1 (get-in state [:components :input1])
        input2 (get-in state [:components :input2])
        focused (:focused (:focus state))]

    ;; Input 1
    (draw-box 3 5 40 3 (str (:label input1) (when (= focused :input1) " [FOCUSED]")))
    (draw-text 4 6 (str (:value input1) (when (= focused :input1) "█")))

    ;; Input 2
    (draw-box 45 5 40 3 (str (:label input2) (when (= focused :input2) " [FOCUSED]")))
    (draw-text 46 6 (str (:value input2) (when (= focused :input2) "█")))

    ;; Event log
    (draw-box 3 9 82 12 (str "Event Log" (when (= focused :log) " [FOCUSED]")))
    (let [log-lines (get-in state [:components :log :lines])
          visible-lines (take-last 9 log-lines)]
      (doseq [[i line] (map-indexed vector visible-lines)]
        (draw-text 4 (+ 10 i) (subs (str line (str/join (repeat 80 " "))) 0 80)))))

  ;; Last event info
  (when-let [event (:last-event state)]
    (move-to 22 3)
    (print (core/color :bold "Last Event: "))
    (print (str "Type=" (:type event) " Key=" (:key event)
                " Char=" (when (:char event) (str "'" (:char event) "'"))
                " Modifiers=" (:modifiers event))))

  ;; Instructions
  (move-to 24 3)
  (print "Instructions:")
  (move-to 25 3)
  (print (str "  • " (core/color :yellow "Tab/Shift+Tab") " - Switch focus between inputs"))
  (move-to 26 3)
  (print (str "  • " (core/color :yellow "Type") " - Add text to focused input"))
  (move-to 27 3)
  (print (str "  • " (core/color :yellow "Backspace") " - Delete last character"))
  (move-to 28 3)
  (print (str "  • " (core/color :yellow "Enter") " - Clear focused input"))
  (move-to 29 3)
  (print (str "  • " (core/color :yellow "Ctrl+Q") " - Quit"))

  (flush))

;; ────────────────────── Main Loop ──────────────────────

(defn log-event [state event]
  "Add an event to the log"
  (let [log-msg (str (core/color :cyan (format "[%tT]" (java.util.Date.)))
                     " " (core/color :yellow (str (:type event)))
                     " key=" (:key event)
                     (when (:char event) (str " char='" (:char event) "'"))
                     (when (seq (:modifiers event))
                       (str " mods=" (str/join "," (map name (:modifiers event))))))]
    (update-in state [:components :log :lines] conj log-msg)))

(defn process-input [state input]
  "Process raw input and update state"
  (when input
    (let [event (events/parse-key input)]
      (when event
        (-> state
            (assoc :last-event event)
            (log-event event)
            ;; Check for quit
            (#(if (events/key-matches? event [:ctrl :q])
                (assoc % :running false)
                %))
            ;; Process event through event system
            (#(events/process-event event %)))))))

(defn main-loop []
  "Main interactive loop"
  (let [state (atom (initial-state))
        handlers {:input1 handle-input
                  :input2 handle-input
                  :log handle-log}]

    ;; Set up handlers in state
    (swap! state assoc :handlers handlers)

    ;; Initial render
    (render-ui @state)

    ;; Main loop
    (while (:running @state)
      (when-let [input (read-input 50)]
        (when-let [new-state (process-input @state input)]
          (reset! state new-state)
          (render-ui @state)))
      (Thread/sleep 10))

    ;; Cleanup
    (clear-screen)
    (move-to 1 1)
    (println (core/color :green "Goodbye! Event system demonstration complete."))
    (println)))

;; ────────────────────── Entry Point ──────────────────────

(defn -main []
  (try
    ;; Enable raw mode
    (enable-raw-mode!)
    (core/hide-cursor)

    ;; Run the demo
    (main-loop)

    (finally
      ;; Always restore terminal
      (core/show-cursor)
      (disable-raw-mode!))))

(-main)
