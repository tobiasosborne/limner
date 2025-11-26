#!/usr/bin/env bb

(ns events-demo
  "Demonstration of the events system - keyboard/mouse input handling"
  (:require [limner.core :as core]
            [limner.borders :as borders]
            [limner.events :as events]))

;; ────────────────────── Demo 1: Key Parsing ──────────────────────

(defn demo-key-parsing
  "Demonstrate key event parsing"
  []
  (println (core/color :bold "=== Demo 1: Key Event Parsing ===") "\n")

  ;; Regular characters
  (println "Regular character 'a':")
  (let [event (events/parse-key "a")]
    (println "  Event:" event)
    (println "  Type:" (:type event) "Key:" (:key event) "Char:" (:char event))
    (println))

  ;; Uppercase (with shift)
  (println "Uppercase 'A' (shift modifier):")
  (let [event (events/parse-key "A")]
    (println "  Event:" event)
    (println "  Modifiers:" (:modifiers event))
    (println))

  ;; Control character
  (println "Ctrl+C:")
  (let [event (events/parse-key "\u0003")]
    (println "  Event:" event)
    (println "  Key:" (:key event) "Modifiers:" (:modifiers event))
    (println))

  ;; Arrow keys
  (println "Arrow keys:")
  (doseq [[name seq] [["Up" "\u001b[A"]
                      ["Down" "\u001b[B"]
                      ["Right" "\u001b[C"]
                      ["Left" "\u001b[D"]]]
    (let [event (events/parse-key seq)]
      (println (str "  " name ": ") (:key event))))
  (println)

  ;; Modified arrow keys
  (println "Ctrl+Right:")
  (let [event (events/parse-key "\u001b[1;5C")]
    (println "  Event:" event)
    (println))

  ;; Alt combinations
  (println "Alt+X:")
  (let [event (events/parse-key "\u001bx")]
    (println "  Event:" event)
    (println "  Key:" (:key event) "Modifiers:" (:modifiers event))
    (println))

  ;; Function keys
  (println "Function keys:")
  (doseq [[name seq] [["F1" "\u001bOP"]
                      ["F5" "\u001b[15~"]
                      ["F12" "\u001b[24~"]]]
    (let [event (events/parse-key seq)]
      (println (str "  " name ": ") (:key event))))
  (println)

  ;; Mouse events
  (println "Mouse events (SGR format):")
  (let [click (events/parse-key "\u001b[<0;10;5M")]
    (println "  Left click at (10, 5):" click))
  (let [release (events/parse-key "\u001b[<0;10;5m")]
    (println "  Button release:" (:action release)))
  (let [scroll (events/parse-key "\u001b[<64;15;8M")]
    (println "  Scroll up:" (:button scroll)))
  (println))

;; ────────────────────── Demo 2: Key Combos ──────────────────────

(defn demo-key-combos
  "Demonstrate key combo creation and matching"
  []
  (println (core/color :bold "=== Demo 2: Key Combos & Matching ===") "\n")

  ;; Create combos from events
  (let [ctrl-c-event {:key :ctrl-c :modifiers #{:ctrl}}
        combo (events/key-combo ctrl-c-event)]
    (println "Ctrl+C event -> combo:" combo)
    (println "  Matches [:ctrl :c]?" (events/key-matches? ctrl-c-event [:ctrl :c]))
    (println "  Matches [:ctrl :x]?" (events/key-matches? ctrl-c-event [:ctrl :x]))
    (println))

  ;; Create combos directly
  (println "Creating combos directly:")
  (println "  (key-combo :ctrl :x) =>" (events/key-combo :ctrl :x))
  (println "  (key-combo :ctrl :shift :x) =>" (events/key-combo :ctrl :shift :x))
  (println "  (key-combo :alt :enter) =>" (events/key-combo :alt :enter))
  (println))

;; ────────────────────── Demo 3: Keybinding Registry ──────────────────────

(defn demo-keybindings
  "Demonstrate keybinding registry"
  []
  (println (core/color :bold "=== Demo 3: Keybinding Registry ===") "\n")

  (let [state (atom {:text "" :count 0})
        registry (events/keybindings
                  {[:ctrl :c] (fn [event s]
                               (println "  -> Ctrl+C pressed! Incrementing counter")
                               (update s :count inc))
                   [:ctrl :x] (fn [event s]
                               (println "  -> Ctrl+X pressed! Clearing text")
                               (assoc s :text ""))
                   [:alt :a] (fn [event s]
                              (println "  -> Alt+A pressed! Adding 'A' to text")
                              (update s :text str "A"))})]

    (println "Initial state:" @state)
    (println)

    ;; Simulate some key presses
    (println "Simulating key presses:")
    (let [ctrl-c-event {:key :ctrl-c :modifiers #{:ctrl} :type :key}
          result (events/dispatch-key registry ctrl-c-event @state)]
      (reset! state result))

    (let [alt-a-event {:key :alt-a :modifiers #{:alt} :type :key}
          result (events/dispatch-key registry alt-a-event @state)]
      (reset! state result))

    (let [alt-a-event {:key :alt-a :modifiers #{:alt} :type :key}
          result (events/dispatch-key registry alt-a-event @state)]
      (reset! state result))

    (let [ctrl-c-event {:key :ctrl-c :modifiers #{:ctrl} :type :key}
          result (events/dispatch-key registry ctrl-c-event @state)]
      (reset! state result))

    (println)
    (println "Final state:" @state)
    (println)

    ;; Bind a new key dynamically
    (println "Dynamically binding [:ctrl :z]:")
    (events/bind-key! registry [:ctrl :z]
                     (fn [event s]
                       (println "  -> Ctrl+Z pressed! Resetting everything")
                       {:text "" :count 0}))

    (let [ctrl-z-event {:key :ctrl-z :modifiers #{:ctrl} :type :key}
          result (events/dispatch-key registry ctrl-z-event @state)]
      (reset! state result))

    (println "State after Ctrl+Z:" @state)
    (println)))

;; ────────────────────── Demo 4: Focus Management ──────────────────────

(defn demo-focus-management
  "Demonstrate focus management and tab order"
  []
  (println (core/color :bold "=== Demo 4: Focus Management ===") "\n")

  (let [focus (events/focus-state :components [:input1 :input2 :button :checkbox])]
    (println "Initial focus state:")
    (println "  Components:" (:components focus))
    (println "  Focused:" (:focused focus))
    (println)

    ;; Tab through components
    (println "Pressing Tab to cycle through components:")
    (loop [state focus
           n 0]
      (when (< n 5)
        (println (str "  Step " n ": focused = " (:focused state)))
        (recur (events/focus-next state) (inc n))))
    (println)

    ;; Shift+Tab backward
    (println "Pressing Shift+Tab to go backward:")
    (loop [state focus
           n 0]
      (when (< n 3)
        (println (str "  Step " n ": focused = " (:focused state)))
        (recur (events/focus-prev state) (inc n))))
    (println)

    ;; Focus specific component
    (println "Focusing :button directly:")
    (let [state (events/focus-component focus :button)]
      (println "  Focused:" (:focused state))
      (println "  Is :button focused?" (events/focused? state :button))
      (println "  Is :input1 focused?" (events/focused? state :input1)))
    (println)

    ;; Add/remove components
    (println "Adding :submit-button to focus order:")
    (let [state (events/add-component focus :submit-button)]
      (println "  Components:" (:components state)))

    (println "Adding :cancel-button with focus:")
    (let [state (events/add-component focus :cancel-button :focus true)]
      (println "  Components:" (:components state))
      (println "  Focused:" (:focused state)))

    (println)
    (println "Removing :input2:")
    (let [state (events/remove-component focus :input2)]
      (println "  Components:" (:components state)))
    (println)))

;; ────────────────────── Demo 5: Event Routing ──────────────────────

(defn demo-event-routing
  "Demonstrate event routing to focused components"
  []
  (println (core/color :bold "=== Demo 5: Event Routing ===") "\n")

  (let [focus-state (events/focus-state :components [:input1 :input2])
        component-states {:input1 {:value "Hello"}
                         :input2 {:value "World"}}
        handlers {:input1 (fn [event state]
                           (println (str "  -> Input1 received: " (:key event)))
                           (update state :value str "!"))
                 :input2 (fn [event state]
                           (println (str "  -> Input2 received: " (:key event)))
                           (update state :value str "?"))}
        event {:type :key :key :x :char \x :modifiers #{}}]

    (println "Initial component states:" component-states)
    (println "Focused component:" (:focused focus-state))
    (println)

    ;; Route event to focused component (input1)
    (println "Pressing 'x' (focused on :input1):")
    (let [new-states (events/route-event event focus-state component-states handlers)]
      (println "  New states:" new-states))
    (println)

    ;; Change focus and route again
    (println "Changing focus to :input2 and pressing 'x':")
    (let [focus-state2 (events/focus-next focus-state)
          new-states (events/route-event event focus-state2 component-states handlers)]
      (println "  Focused:" (:focused focus-state2))
      (println "  New states:" new-states))
    (println)))

;; ────────────────────── Demo 6: Mouse Routing ──────────────────────

(defn demo-mouse-routing
  "Demonstrate mouse event routing to components"
  []
  (println (core/color :bold "=== Demo 6: Mouse Event Routing ===") "\n")

  (let [layout {:panel1 {:x 0 :y 0 :width 20 :height 10}
                :panel2 {:x 20 :y 0 :width 20 :height 10}
                :panel3 {:x 0 :y 10 :width 40 :height 10}}
        component-states {:panel1 {:clicks 0}
                         :panel2 {:clicks 0}
                         :panel3 {:clicks 0}}
        handlers {:panel1 (fn [event state]
                           (println (str "  -> Panel1 clicked at local (" (:x event) ", " (:y event) ")"))
                           (update state :clicks inc))
                 :panel2 (fn [event state]
                           (println (str "  -> Panel2 clicked at local (" (:x event) ", " (:y event) ")"))
                           (update state :clicks inc))
                 :panel3 (fn [event state]
                           (println (str "  -> Panel3 clicked at local (" (:x event) ", " (:y event) ")"))
                           (update state :clicks inc))}]

    (println "Layout:")
    (println "  Panel1: x=0,  y=0,  width=20, height=10")
    (println "  Panel2: x=20, y=0,  width=20, height=10")
    (println "  Panel3: x=0,  y=10, width=40, height=10")
    (println)

    ;; Click on panel1
    (println "Clicking at global (5, 5) - should hit Panel1:")
    (let [event {:type :mouse :button :left :action :press :x 5 :y 5}
          [target-id new-states] (events/route-mouse-event event layout handlers component-states)]
      (println "  Target:" target-id)
      (println "  States:" new-states))
    (println)

    ;; Click on panel2
    (println "Clicking at global (25, 5) - should hit Panel2:")
    (let [event {:type :mouse :button :left :action :press :x 25 :y 5}
          [target-id new-states] (events/route-mouse-event event layout handlers component-states)]
      (println "  Target:" target-id)
      (println "  States:" new-states))
    (println)

    ;; Click on panel3
    (println "Clicking at global (15, 12) - should hit Panel3:")
    (let [event {:type :mouse :button :left :action :press :x 15 :y 12}
          [target-id new-states] (events/route-mouse-event event layout handlers component-states)]
      (println "  Target:" target-id)
      (println "  States:" new-states))
    (println)

    ;; Click outside
    (println "Clicking at global (50, 5) - outside all panels:")
    (let [event {:type :mouse :button :left :action :press :x 50 :y 5}
          result (events/route-mouse-event event layout handlers component-states)]
      (println "  Result:" result))
    (println)))

;; ────────────────────── Demo 7: Integration ──────────────────────

(defn demo-integration
  "Demonstrate complete event processing flow"
  []
  (println (core/color :bold "=== Demo 7: Complete Event Processing ===") "\n")

  (let [state (atom {:focus (events/focus-state :components [:input :button])
                     :components {:input {:value ""}
                                 :button {:pressed false}}
                     :handlers {:input (fn [event comp-state]
                                        (println (str "    Input handling: " (:char event)))
                                        (if (:char event)
                                          (update comp-state :value str (:char event))
                                          comp-state))
                               :button (fn [event comp-state]
                                        (println "    Button handling: pressed!")
                                        (assoc comp-state :pressed true))}
                     :keybindings (events/keybindings
                                   {[:ctrl :q] (fn [event state]
                                                (println "    Global: Ctrl+Q - Quit!")
                                                (assoc state :quit true))
                                    [:ctrl :r] (fn [event state]
                                                (println "    Global: Ctrl+R - Reset!")
                                                (assoc-in state [:components :input :value] ""))})})]

    (println "Initial state:")
    (println "  Focus:" (:focused (:focus @state)))
    (println "  Input value:" (get-in @state [:components :input :value]))
    (println "  Button pressed:" (get-in @state [:components :button :pressed]))
    (println)

    ;; Type some characters
    (println "Typing 'H', 'i' (input is focused):")
    (doseq [c [\H \i]]
      (let [event {:type :key :key (keyword (str c)) :char c :modifiers #{}}]
        (swap! state #(events/process-event event %))))
    (println "  Input value:" (get-in @state [:components :input :value]))
    (println)

    ;; Press Tab to change focus
    (println "Pressing Tab (focus moves to button):")
    (let [event {:type :key :key :tab :modifiers #{}}]
      (swap! state #(events/process-event event %)))
    (println "  Focus:" (:focused (:focus @state)))
    (println)

    ;; Press Enter on button
    (println "Pressing Enter (button receives event):")
    (let [event {:type :key :key :enter :modifiers #{}}]
      (swap! state #(events/process-event event %)))
    (println "  Button pressed:" (get-in @state [:components :button :pressed]))
    (println)

    ;; Press global keybinding
    (println "Pressing Ctrl+R (global reset):")
    (let [event {:type :key :key :ctrl-r :modifiers #{:ctrl}}]
      (swap! state #(events/process-event event %)))
    (println "  Input value:" (get-in @state [:components :input :value]))
    (println)

    ;; Press Ctrl+Q
    (println "Pressing Ctrl+Q (global quit):")
    (let [event {:type :key :key :ctrl-q :modifiers #{:ctrl}}]
      (swap! state #(events/process-event event %)))
    (println "  Quit flag:" (:quit @state))
    (println)))

;; ────────────────────── Main ──────────────────────

(defn -main []
  (println)
  (println (core/color :cyan "╔═══════════════════════════════════════════════════════╗"))
  (println (core/color :cyan "║        LIMNER EVENT SYSTEM DEMONSTRATION             ║"))
  (println (core/color :cyan "╚═══════════════════════════════════════════════════════╝"))
  (println)

  (demo-key-parsing)
  (println "─────────────────────────────────────────────────────────\n")

  (demo-key-combos)
  (println "─────────────────────────────────────────────────────────\n")

  (demo-keybindings)
  (println "─────────────────────────────────────────────────────────\n")

  (demo-focus-management)
  (println "─────────────────────────────────────────────────────────\n")

  (demo-event-routing)
  (println "─────────────────────────────────────────────────────────\n")

  (demo-mouse-routing)
  (println "─────────────────────────────────────────────────────────\n")

  (demo-integration)

  (println)
  (println (core/color :green "✓ All demos completed!"))
  (println)
  (println "This demonstrates the complete event system including:")
  (println "  • Key parsing (regular, control, arrows, function keys, mouse)")
  (println "  • Key combos and matching")
  (println "  • Keybinding registry (bind/unbind/dispatch)")
  (println "  • Focus management (next/prev/specific, add/remove components)")
  (println "  • Event routing (keyboard to focused, mouse to coordinates)")
  (println "  • Tab navigation (Tab/Shift+Tab)")
  (println "  • Complete integration (global bindings + component handlers)")
  (println))

(-main)
