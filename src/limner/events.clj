(ns limner.events
  "Event system - keyboard and mouse input handling"
  (:require [clojure.string :as str]))

;; ────────────────────── Key Event Parsing ──────────────────────

(def ^:private escape-sequences
  "Map of ANSI escape sequences to key names"
  {"\u001b[A" :up
   "\u001b[B" :down
   "\u001b[C" :right
   "\u001b[D" :left
   "\u001b[H" :home
   "\u001b[F" :end
   "\u001b[2~" :insert
   "\u001b[3~" :delete
   "\u001b[5~" :page-up
   "\u001b[6~" :page-down
   "\u001b[1;2A" :shift-up
   "\u001b[1;2B" :shift-down
   "\u001b[1;2C" :shift-right
   "\u001b[1;2D" :shift-left
   "\u001b[1;5A" :ctrl-up
   "\u001b[1;5B" :ctrl-down
   "\u001b[1;5C" :ctrl-right
   "\u001b[1;5D" :ctrl-left
   "\u001b[1;3A" :alt-up
   "\u001b[1;3B" :alt-down
   "\u001b[1;3C" :alt-right
   "\u001b[1;3D" :alt-left
   "\u001bOP" :f1
   "\u001bOQ" :f2
   "\u001bOR" :f3
   "\u001bOS" :f4
   "\u001b[15~" :f5
   "\u001b[17~" :f6
   "\u001b[18~" :f7
   "\u001b[19~" :f8
   "\u001b[20~" :f9
   "\u001b[21~" :f10
   "\u001b[23~" :f11
   "\u001b[24~" :f12})

(def ^:private ctrl-chars
  "Map of control character codes to key names"
  {\u0001 :ctrl-a
   \u0002 :ctrl-b
   \u0003 :ctrl-c
   \u0004 :ctrl-d
   \u0005 :ctrl-e
   \u0006 :ctrl-f
   \u0007 :ctrl-g
   \u0008 :ctrl-h
   \u0009 :tab
   \u000a :enter
   \u000b :ctrl-k
   \u000c :ctrl-l
   \u000d :enter
   \u000e :ctrl-n
   \u000f :ctrl-o
   \u0010 :ctrl-p
   \u0011 :ctrl-q
   \u0012 :ctrl-r
   \u0013 :ctrl-s
   \u0014 :ctrl-t
   \u0015 :ctrl-u
   \u0016 :ctrl-v
   \u0017 :ctrl-w
   \u0018 :ctrl-x
   \u0019 :ctrl-y
   \u001a :ctrl-z
   \u007f :backspace})

(defn- parse-mouse-event
  "Parse mouse event from CSI sequence. Format: CSI M Cb Cx Cy or CSI < Cb ; Cx ; Cy M"
  [input]
  (cond
    ;; SGR mouse format: ESC [ < button ; x ; y M/m
    (str/starts-with? input "\u001b[<")
    (when-let [[_ button x y release] (re-find #"\u001b\[<(\d+);(\d+);(\d+)([Mm])" input)]
      (let [btn (Integer/parseInt button)
            button-type (cond
                          (= btn 0) :left
                          (= btn 1) :middle
                          (= btn 2) :right
                          (= btn 64) :scroll-up
                          (= btn 65) :scroll-down
                          :else :unknown)]
        {:type :mouse
         :button button-type
         :action (if (= release "m") :release :press)
         :x (Integer/parseInt x)
         :y (Integer/parseInt y)
         :raw input}))

    ;; Normal mouse format: ESC [ M Cb Cx Cy
    (str/starts-with? input "\u001b[M")
    (when (>= (count input) 6)
      (let [cb (int (nth input 3))
            cx (- (int (nth input 4)) 32)
            cy (- (int (nth input 5)) 32)
            button (case (bit-and cb 3)
                     0 :left
                     1 :middle
                     2 :right
                     :unknown)]
        {:type :mouse
         :button button
         :action :press
         :x cx
         :y cy
         :raw input}))

    :else nil))

(defn parse-key
  "Parse raw input string into a key event map.
   Returns {:type :key, :key <key>, :char <char>, :modifiers #{...}, :raw <input>}
   or {:type :mouse, ...} for mouse events, or nil if unparseable."
  [input]
  (when (seq input)
    (cond
      ;; Empty or nil
      (empty? input)
      nil

      ;; Mouse events
      (or (str/starts-with? input "\u001b[<")
          (str/starts-with? input "\u001b[M"))
      (parse-mouse-event input)

      ;; Escape sequences (function keys, arrows, etc.)
      (escape-sequences input)
      {:type :key
       :key (escape-sequences input)
       :char nil
       :modifiers #{}
       :raw input}

      ;; Control characters
      (and (= 1 (count input))
           (ctrl-chars (first input)))
      (let [key (ctrl-chars (first input))]
        {:type :key
         :key key
         :char nil
         :modifiers (if (str/starts-with? (name key) "ctrl-") #{:ctrl} #{})
         :raw input})

      ;; Alt + character (ESC prefix)
      (and (str/starts-with? input "\u001b")
           (> (count input) 1)
           (not (str/starts-with? input "\u001b[")))
      (let [char (subs input 1 2)]
        {:type :key
         :key (keyword (str "alt-" char))
         :char (first char)
         :modifiers #{:alt}
         :raw input})

      ;; Regular character
      (= 1 (count input))
      (let [c (first input)]
        {:type :key
         :key (if (Character/isUpperCase c)
                (keyword (str "shift-" (str/lower-case c)))
                (keyword (str c)))
         :char c
         :modifiers (if (Character/isUpperCase c) #{:shift} #{})
         :raw input})

      ;; Unknown/unparseable
      :else
      {:type :unknown
       :raw input})))

(defn key-combo
  "Create a key combo representation from key event or key spec.
   Examples:
     (key-combo {:key :ctrl-c :modifiers #{:ctrl}}) => [:ctrl :c]
     (key-combo :ctrl :shift :x) => [:ctrl :shift :x]"
  [& args]
  (if (and (= 1 (count args)) (map? (first args)))
    ;; Parse from key event map
    (let [{:keys [key modifiers]} (first args)]
      (vec (concat (sort modifiers)
                   [(if (keyword? key)
                      (keyword (str/replace (name key) #"^(ctrl|alt|shift)-" ""))
                      key)])))
    ;; Create from args
    (vec args)))

(defn key-matches?
  "Check if a key event matches a key combo specification.
   combo can be a vector like [:ctrl :x] or a key event map."
  [event combo]
  (let [event-combo (key-combo event)
        target-combo (if (vector? combo) combo (key-combo combo))]
    (= event-combo target-combo)))

;; ────────────────────── Keybinding Registry ──────────────────────

(defn keybindings
  "Create a new keybinding registry.
   Bindings is a map of key-combo -> handler-fn.
   Handler fn receives the event and returns updated state or nil."
  [bindings]
  {:bindings (atom bindings)})

(defn bind-key!
  "Add a keybinding to the registry.
   combo can be [:ctrl :c] or similar.
   handler is (fn [event state] ...) -> new-state"
  [registry combo handler]
  (swap! (:bindings registry) assoc combo handler)
  registry)

(defn unbind-key!
  "Remove a keybinding from the registry."
  [registry combo]
  (swap! (:bindings registry) dissoc combo)
  registry)

(defn dispatch-key
  "Dispatch a key event to the appropriate handler in the registry.
   Returns the result of the handler or nil if no binding matched."
  [registry event state]
  (let [bindings @(:bindings registry)
        combo (key-combo event)]
    (when-let [handler (get bindings combo)]
      (handler event state))))

;; ────────────────────── Focus Management ──────────────────────

(defn focus-state
  "Create a focus management state.
   Options:
   - :components - vector of component IDs in tab order
   - :focused - currently focused component ID (default: first component)
   - :wrap - whether to wrap around at boundaries (default: true)"
  [& {:keys [components focused wrap]
      :or {components []
           wrap true}}]
  {:components (vec components)
   :focused (or focused (first components))
   :wrap wrap})

(defn focus-next
  "Move focus to the next component in tab order."
  [{:keys [components focused wrap] :as state}]
  (if (empty? components)
    state
    (let [current-idx (or (.indexOf components focused) -1)
          next-idx (inc current-idx)]
      (cond
        (>= next-idx (count components))
        (if wrap
          (assoc state :focused (first components))
          state)

        :else
        (assoc state :focused (nth components next-idx))))))

(defn focus-prev
  "Move focus to the previous component in tab order."
  [{:keys [components focused wrap] :as state}]
  (if (empty? components)
    state
    (let [current-idx (or (.indexOf components focused) (count components))
          prev-idx (dec current-idx)]
      (cond
        (< prev-idx 0)
        (if wrap
          (assoc state :focused (last components))
          state)

        :else
        (assoc state :focused (nth components prev-idx))))))

(defn focus-component
  "Set focus to a specific component by ID."
  [state component-id]
  (if (some #{component-id} (:components state))
    (assoc state :focused component-id)
    state))

(defn focused?
  "Check if a component is currently focused."
  [state component-id]
  (= (:focused state) component-id))

(defn add-component
  "Add a component to the focus order.
   Options:
   - :index - insert at specific index (default: append)
   - :focus - whether to focus the new component (default: false)"
  [state component-id & {:keys [index focus] :or {focus false}}]
  (let [new-components (if index
                        (let [[before after] (split-at index (:components state))]
                          (vec (concat before [component-id] after)))
                        (conj (:components state) component-id))
        new-state (assoc state :components new-components)]
    (if focus
      (assoc new-state :focused component-id)
      new-state)))

(defn remove-component
  "Remove a component from the focus order.
   If the removed component was focused, focus moves to the next component."
  [state component-id]
  (let [was-focused? (= (:focused state) component-id)
        new-components (vec (remove #{component-id} (:components state)))
        new-state (assoc state :components new-components)]
    (if (and was-focused? (seq new-components))
      (assoc new-state :focused (first new-components))
      new-state)))

;; ────────────────────── Event Routing ──────────────────────

(defn route-event
  "Route an event to the appropriate component based on focus state.
   handlers is a map of component-id -> handler-fn.
   Handler fn receives (event component-state) and returns new component-state.
   Returns updated component states map or nil if no handler."
  [event focus-state component-states handlers]
  (let [focused-id (:focused focus-state)]
    (when-let [handler (get handlers focused-id)]
      (if-let [component-state (get component-states focused-id)]
        (let [new-component-state (handler event component-state)]
          (assoc component-states focused-id new-component-state))
        component-states))))

(defn route-mouse-event
  "Route a mouse event to the component at the given coordinates.
   layout-map is a map of component-id -> {:x :y :width :height} bounds.
   Returns [component-id event] or nil if no component at coordinates."
  [event layout-map handlers component-states]
  (when (= :mouse (:type event))
    (let [{:keys [x y]} event
          ;; Find component whose bounds contain the click
          target-id (first
                     (for [[id bounds] layout-map
                           :when (and (>= x (:x bounds))
                                      (< x (+ (:x bounds) (:width bounds)))
                                      (>= y (:y bounds))
                                      (< y (+ (:y bounds) (:height bounds))))]
                       id))]
      (when-let [handler (get handlers target-id)]
        (if-let [component-state (get component-states target-id)]
          (let [;; Translate to component-local coordinates
                local-event (assoc event
                                   :x (- x (get-in layout-map [target-id :x] 0))
                                   :y (- y (get-in layout-map [target-id :y] 0)))
                new-component-state (handler local-event component-state)]
            [target-id (assoc component-states target-id new-component-state)])
          [target-id component-states])))))

;; ────────────────────── Event Loop Helpers ──────────────────────

(defn handle-tab-navigation
  "Built-in handler for tab/shift-tab navigation between components.
   Returns updated focus-state or nil if event wasn't tab/shift-tab."
  [event focus-state]
  (cond
    (key-matches? event [:tab])
    (focus-next focus-state)

    (key-matches? event [:shift :tab])
    (focus-prev focus-state)

    :else
    nil))

(defn process-event
  "High-level event processor that handles both keybindings and routing.
   state map should contain:
   - :focus - focus state from focus-state
   - :components - map of component-id -> component-state
   - :handlers - map of component-id -> handler-fn
   - :keybindings - optional keybinding registry
   - :layout - optional layout map for mouse events

   Returns updated state map."
  [event state]
  (let [{:keys [focus components handlers keybindings layout]} state]
    ;; 1. Check for tab navigation first
    (if-let [new-focus (handle-tab-navigation event focus)]
      (assoc state :focus new-focus)

      ;; 2. Try global keybindings
      (if (and keybindings (= :key (:type event)))
        (if-let [result (dispatch-key keybindings event state)]
          result
          ;; 3. Route to focused component
          (if-let [new-components (route-event event focus components handlers)]
            (assoc state :components new-components)
            state))

        ;; 4. Handle mouse events
        (if (and (= :mouse (:type event)) layout)
          (if-let [[target-id new-components] (route-mouse-event event layout handlers components)]
            (-> state
                (assoc :components new-components)
                (assoc-in [:focus :focused] target-id))
            state)

          ;; 5. Route keyboard event to focused component
          (if-let [new-components (route-event event focus components handlers)]
            (assoc state :components new-components)
            state))))))
