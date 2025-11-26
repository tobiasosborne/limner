(ns limner.events-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.events :as events]))

;; ────────────────────── Key Parsing Tests ──────────────────────

(deftest test-parse-regular-chars
  (testing "Regular character parsing"
    (let [result (events/parse-key "a")]
      (is (= :key (:type result)))
      (is (= :a (:key result)))
      (is (= \a (:char result)))
      (is (= #{} (:modifiers result))))

    (let [result (events/parse-key "5")]
      (is (= :key (:type result)))
      (is (= :5 (:key result)))
      (is (= \5 (:char result))))))

(deftest test-parse-uppercase
  (testing "Uppercase characters include shift modifier"
    (let [result (events/parse-key "A")]
      (is (= :key (:type result)))
      (is (= :shift-a (:key result)))
      (is (= \A (:char result)))
      (is (= #{:shift} (:modifiers result))))))

(deftest test-parse-control-chars
  (testing "Control character parsing"
    (let [result (events/parse-key "\u0003")] ; Ctrl+C
      (is (= :key (:type result)))
      (is (= :ctrl-c (:key result)))
      (is (= #{:ctrl} (:modifiers result))))

    (let [result (events/parse-key "\u0001")] ; Ctrl+A
      (is (= :key (:type result)))
      (is (= :ctrl-a (:key result))))

    (let [result (events/parse-key "\u007f")] ; Backspace
      (is (= :key (:type result)))
      (is (= :backspace (:key result))))))

(deftest test-parse-escape-sequences
  (testing "Arrow keys"
    (is (= :up (:key (events/parse-key "\u001b[A"))))
    (is (= :down (:key (events/parse-key "\u001b[B"))))
    (is (= :right (:key (events/parse-key "\u001b[C"))))
    (is (= :left (:key (events/parse-key "\u001b[D")))))

  (testing "Home/End"
    (is (= :home (:key (events/parse-key "\u001b[H"))))
    (is (= :end (:key (events/parse-key "\u001b[F")))))

  (testing "Function keys"
    (is (= :f1 (:key (events/parse-key "\u001bOP"))))
    (is (= :f5 (:key (events/parse-key "\u001b[15~"))))
    (is (= :f12 (:key (events/parse-key "\u001b[24~")))))

  (testing "Page Up/Down"
    (is (= :page-up (:key (events/parse-key "\u001b[5~"))))
    (is (= :page-down (:key (events/parse-key "\u001b[6~"))))))

(deftest test-parse-modified-keys
  (testing "Shift + arrows"
    (is (= :shift-up (:key (events/parse-key "\u001b[1;2A"))))
    (is (= :shift-down (:key (events/parse-key "\u001b[1;2B")))))

  (testing "Ctrl + arrows"
    (is (= :ctrl-up (:key (events/parse-key "\u001b[1;5A"))))
    (is (= :ctrl-right (:key (events/parse-key "\u001b[1;5C")))))

  (testing "Alt + arrows"
    (is (= :alt-left (:key (events/parse-key "\u001b[1;3D"))))
    (is (= :alt-down (:key (events/parse-key "\u001b[1;3B"))))))

(deftest test-parse-alt-combinations
  (testing "Alt + character"
    (let [result (events/parse-key "\u001bx")] ; Alt+X
      (is (= :key (:type result)))
      (is (= :alt-x (:key result)))
      (is (= \x (:char result)))
      (is (= #{:alt} (:modifiers result))))

    (let [result (events/parse-key "\u001bq")] ; Alt+Q
      (is (= :key (:type result)))
      (is (= :alt-q (:key result))))))

(deftest test-parse-special-keys
  (testing "Enter and Tab"
    (is (= :enter (:key (events/parse-key "\u000a"))))
    (is (= :enter (:key (events/parse-key "\u000d"))))
    (is (= :tab (:key (events/parse-key "\u0009"))))))

(deftest test-parse-mouse-events-sgr
  (testing "SGR mouse format - left button press"
    (let [result (events/parse-key "\u001b[<0;10;5M")]
      (is (= :mouse (:type result)))
      (is (= :left (:button result)))
      (is (= :press (:action result)))
      (is (= 10 (:x result)))
      (is (= 5 (:y result)))))

  (testing "SGR mouse format - left button release"
    (let [result (events/parse-key "\u001b[<0;10;5m")]
      (is (= :mouse (:type result)))
      (is (= :left (:button result)))
      (is (= :release (:action result)))))

  (testing "SGR mouse format - right button"
    (let [result (events/parse-key "\u001b[<2;20;10M")]
      (is (= :mouse (:type result)))
      (is (= :right (:button result)))))

  (testing "SGR mouse format - scroll"
    (let [result (events/parse-key "\u001b[<64;15;8M")]
      (is (= :mouse (:type result)))
      (is (= :scroll-up (:button result))))

    (let [result (events/parse-key "\u001b[<65;15;8M")]
      (is (= :mouse (:type result)))
      (is (= :scroll-down (:button result))))))

;; ────────────────────── Key Combo Tests ──────────────────────

(deftest test-key-combo-creation
  (testing "Create key combo from event"
    (let [event {:key :ctrl-x :modifiers #{:ctrl}}
          combo (events/key-combo event)]
      (is (= [:ctrl :x] combo))))

  (testing "Create key combo from args"
    (is (= [:ctrl :x] (events/key-combo :ctrl :x)))
    (is (= [:ctrl :shift :x] (events/key-combo :ctrl :shift :x)))
    (is (= [:alt :q] (events/key-combo :alt :q)))))

(deftest test-key-matches
  (testing "Key event matches combo"
    (let [event {:key :ctrl-c :modifiers #{:ctrl}}]
      (is (events/key-matches? event [:ctrl :c]))
      (is (not (events/key-matches? event [:ctrl :x]))))

    (let [event {:key :shift-a :modifiers #{:shift}}]
      (is (events/key-matches? event [:shift :a])))

    (let [event {:key :alt-q :modifiers #{:alt}}]
      (is (events/key-matches? event [:alt :q])))))

;; ────────────────────── Keybinding Registry Tests ──────────────────────

(deftest test-keybinding-registry
  (testing "Create and use keybindings"
    (let [called (atom nil)
          registry (events/keybindings
                    {[:ctrl :c] (fn [event state]
                                  (reset! called [:ctrl-c event state])
                                  :new-state)})
          event {:key :ctrl-c :modifiers #{:ctrl} :type :key}]

      ;; Dispatch matching key
      (is (= :new-state (events/dispatch-key registry event :old-state)))
      (is (= [:ctrl-c event :old-state] @called))

      ;; Non-matching key returns nil
      (let [other-event {:key :ctrl-x :modifiers #{:ctrl} :type :key}]
        (is (nil? (events/dispatch-key registry other-event :state)))))))

(deftest test-bind-unbind-key
  (testing "Add and remove keybindings"
    (let [registry (events/keybindings {})
          handler (fn [e s] :handled)]

      ;; Bind a key
      (events/bind-key! registry [:ctrl :x] handler)
      (let [event {:key :ctrl-x :modifiers #{:ctrl} :type :key}]
        (is (= :handled (events/dispatch-key registry event :state))))

      ;; Unbind the key
      (events/unbind-key! registry [:ctrl :x])
      (let [event {:key :ctrl-x :modifiers #{:ctrl} :type :key}]
        (is (nil? (events/dispatch-key registry event :state)))))))

(deftest test-complex-keybindings
  (testing "Complex key combinations"
    (let [results (atom [])
          registry (events/keybindings
                    {[:ctrl :shift :x] (fn [e s] (swap! results conj :ctrl-shift-x) s)
                     [:alt :enter] (fn [e s] (swap! results conj :alt-enter) s)
                     [:ctrl :alt :delete] (fn [e s] (swap! results conj :ctrl-alt-delete) s)})]

      ;; Note: These would be the parsed events from the complex combos
      ;; In practice, terminal may not send these exact sequences
      (events/dispatch-key registry
                          {:key :x :modifiers #{:ctrl :shift} :type :key}
                          :state)
      ;; For testing purposes, we'll just verify the registry structure
      (is (contains? @(:bindings registry) [:ctrl :shift :x]))
      (is (contains? @(:bindings registry) [:alt :enter]))
      (is (contains? @(:bindings registry) [:ctrl :alt :delete])))))

;; ────────────────────── Focus Management Tests ──────────────────────

(deftest test-focus-state-creation
  (testing "Create focus state"
    (let [state (events/focus-state :components [:input1 :input2 :button1])]
      (is (= [:input1 :input2 :button1] (:components state)))
      (is (= :input1 (:focused state)))
      (is (= true (:wrap state))))

    (let [state (events/focus-state :components [:a :b] :focused :b :wrap false)]
      (is (= :b (:focused state)))
      (is (= false (:wrap state))))))

(deftest test-focus-navigation
  (testing "Focus next cycles through components"
    (let [state (events/focus-state :components [:a :b :c])]
      (is (= :a (:focused state)))

      (let [state2 (events/focus-next state)]
        (is (= :b (:focused state2))))

      (let [state3 (-> state events/focus-next events/focus-next)]
        (is (= :c (:focused state3))))

      ;; Wrap around to beginning
      (let [state4 (-> state events/focus-next events/focus-next events/focus-next)]
        (is (= :a (:focused state4))))))

  (testing "Focus next with wrap disabled"
    (let [state (events/focus-state :components [:a :b :c] :wrap false)
          final-state (-> state events/focus-next events/focus-next events/focus-next)]
      ;; Should stay at :c
      (is (= :c (:focused final-state))))))

(deftest test-focus-previous
  (testing "Focus previous cycles backward"
    (let [state (events/focus-state :components [:a :b :c] :focused :c)]
      (is (= :c (:focused state)))

      (let [state2 (events/focus-prev state)]
        (is (= :b (:focused state2))))

      ;; Wrap around to end
      (let [state3 (-> (events/focus-state :components [:a :b :c])
                       events/focus-prev)]
        (is (= :c (:focused state3))))))

  (testing "Focus previous with wrap disabled"
    (let [state (events/focus-state :components [:a :b :c] :wrap false)
          final-state (events/focus-prev state)]
      ;; Should stay at :a
      (is (= :a (:focused final-state))))))

(deftest test-focus-component
  (testing "Set focus to specific component"
    (let [state (events/focus-state :components [:a :b :c])]
      (is (= :c (:focused (events/focus-component state :c))))
      (is (= :b (:focused (events/focus-component state :b))))

      ;; Non-existent component doesn't change focus
      (is (= :a (:focused (events/focus-component state :nonexistent)))))))

(deftest test-focused-check
  (testing "Check if component is focused"
    (let [state (events/focus-state :components [:a :b :c] :focused :b)]
      (is (not (events/focused? state :a)))
      (is (events/focused? state :b))
      (is (not (events/focused? state :c))))))

(deftest test-add-remove-components
  (testing "Add component to focus order"
    (let [state (events/focus-state :components [:a :b])
          state2 (events/add-component state :c)]
      (is (= [:a :b :c] (:components state2)))
      (is (= :a (:focused state2))))

    (let [state (events/focus-state :components [:a :b])
          state2 (events/add-component state :c :focus true)]
      (is (= [:a :b :c] (:components state2)))
      (is (= :c (:focused state2))))

    (let [state (events/focus-state :components [:a :c])
          state2 (events/add-component state :b :index 1)]
      (is (= [:a :b :c] (:components state2)))))

  (testing "Remove component from focus order"
    (let [state (events/focus-state :components [:a :b :c])
          state2 (events/remove-component state :b)]
      (is (= [:a :c] (:components state2)))
      (is (= :a (:focused state2))))

    ;; Remove focused component - focus moves to next
    (let [state (events/focus-state :components [:a :b :c] :focused :b)
          state2 (events/remove-component state :b)]
      (is (= [:a :c] (:components state2)))
      (is (= :a (:focused state2))))))

;; ────────────────────── Event Routing Tests ──────────────────────

(deftest test-route-event
  (testing "Route event to focused component"
    (let [focus-state (events/focus-state :components [:input1 :input2])
          component-states {:input1 {:value "hello"}
                           :input2 {:value "world"}}
          handlers {:input1 (fn [event state]
                             (assoc state :value (str (:value state) "!")))
                   :input2 (fn [event state]
                             (assoc state :value (str (:value state) "?")))}
          event {:type :key :key :x}]

      ;; Event goes to :input1 (focused)
      (let [result (events/route-event event focus-state component-states handlers)]
        (is (= "hello!" (get-in result [:input1 :value])))
        (is (= "world" (get-in result [:input2 :value]))))

      ;; Change focus to :input2
      (let [focus-state2 (events/focus-next focus-state)
            result (events/route-event event focus-state2 component-states handlers)]
        (is (= "hello" (get-in result [:input1 :value])))
        (is (= "world?" (get-in result [:input2 :value])))))))

(deftest test-route-mouse-event
  (testing "Route mouse click to component at coordinates"
    (let [layout {:panel1 {:x 0 :y 0 :width 20 :height 10}
                  :panel2 {:x 20 :y 0 :width 20 :height 10}}
          component-states {:panel1 {:clicked false}
                           :panel2 {:clicked false}}
          handlers {:panel1 (fn [event state]
                             (assoc state :clicked true :event event))
                   :panel2 (fn [event state]
                             (assoc state :clicked true :event event))}]

      ;; Click at (5, 5) - should hit panel1
      (let [event {:type :mouse :button :left :action :press :x 5 :y 5}
            [target-id result] (events/route-mouse-event event layout handlers component-states)]
        (is (= :panel1 target-id))
        (is (get-in result [:panel1 :clicked]))
        (is (not (get-in result [:panel2 :clicked])))
        ;; Check that coordinates were translated to local
        (is (= 5 (get-in result [:panel1 :event :x])))
        (is (= 5 (get-in result [:panel1 :event :y]))))

      ;; Click at (25, 5) - should hit panel2
      (let [event {:type :mouse :button :left :action :press :x 25 :y 5}
            [target-id result] (events/route-mouse-event event layout handlers component-states)]
        (is (= :panel2 target-id))
        (is (get-in result [:panel2 :clicked]))
        ;; Coordinates translated to panel2's local space
        (is (= 5 (get-in result [:panel2 :event :x])))
        (is (= 5 (get-in result [:panel2 :event :y]))))

      ;; Click outside all components
      (let [event {:type :mouse :button :left :action :press :x 50 :y 5}
            result (events/route-mouse-event event layout handlers component-states)]
        (is (nil? result))))))

;; ────────────────────── Tab Navigation Tests ──────────────────────

(deftest test-tab-navigation
  (testing "Tab key moves focus forward"
    (let [focus-state (events/focus-state :components [:a :b :c])
          tab-event {:type :key :key :tab :modifiers #{}}]

      (let [new-focus (events/handle-tab-navigation tab-event focus-state)]
        (is (= :b (:focused new-focus)))

        (let [new-focus2 (events/handle-tab-navigation tab-event new-focus)]
          (is (= :c (:focused new-focus2)))

          ;; Wrap around
          (let [new-focus3 (events/handle-tab-navigation tab-event new-focus2)]
            (is (= :a (:focused new-focus3))))))))

  (testing "Shift+Tab moves focus backward"
    (let [focus-state (events/focus-state :components [:a :b :c])
          shift-tab-event {:type :key :key :shift-tab :modifiers #{:shift}}]

      (let [new-focus (events/handle-tab-navigation shift-tab-event focus-state)]
        (is (= :c (:focused new-focus)))

        (let [new-focus2 (events/handle-tab-navigation shift-tab-event new-focus)]
          (is (= :b (:focused new-focus2)))))))

  (testing "Non-tab keys return nil"
    (let [focus-state (events/focus-state :components [:a :b :c])
          event {:type :key :key :x :modifiers #{}}]
      (is (nil? (events/handle-tab-navigation event focus-state))))))

;; ────────────────────── Integration Tests ──────────────────────

(deftest test-process-event-integration
  (testing "Complete event processing flow"
    (let [state {:focus (events/focus-state :components [:input :button])
                 :components {:input {:value ""}
                             :button {:pressed false}}
                 :handlers {:input (fn [event comp-state]
                                    (if (= :a (:key event))
                                      (update comp-state :value str "a")
                                      comp-state))
                           :button (fn [event comp-state]
                                    (assoc comp-state :pressed true))}
                 :keybindings (events/keybindings
                               {[:ctrl :q] (fn [event state]
                                            (assoc state :quit true))})}]

      ;; Type 'a' - goes to focused input
      (let [event {:type :key :key :a :char \a :modifiers #{}}
            new-state (events/process-event event state)]
        (is (= "a" (get-in new-state [:components :input :value]))))

      ;; Press Tab - focus moves to button
      (let [tab-event {:type :key :key :tab :modifiers #{}}
            new-state (events/process-event tab-event state)]
        (is (= :button (get-in new-state [:focus :focused]))))

      ;; Press Ctrl+Q - global keybinding
      (let [quit-event {:type :key :key :ctrl-q :modifiers #{:ctrl}}
            new-state (events/process-event quit-event state)]
        (is (= true (:quit new-state)))))))

(deftest test-process-event-with-mouse
  (testing "Process mouse events with layout"
    (let [state {:focus (events/focus-state :components [:panel1 :panel2])
                 :components {:panel1 {:clicked false}
                             :panel2 {:clicked false}}
                 :handlers {:panel1 (fn [event comp-state]
                                     (assoc comp-state :clicked true))
                           :panel2 (fn [event comp-state]
                                     (assoc comp-state :clicked true))}
                 :layout {:panel1 {:x 0 :y 0 :width 10 :height 5}
                         :panel2 {:x 10 :y 0 :width 10 :height 5}}}
          click-event {:type :mouse :button :left :action :press :x 5 :y 2}]

      ;; Click on panel1
      (let [new-state (events/process-event click-event state)]
        (is (get-in new-state [:components :panel1 :clicked]))
        ;; Focus should move to clicked component
        (is (= :panel1 (get-in new-state [:focus :focused])))))))
