(ns limner.state
  "Simple application state management with reactive updates

   This is a simplified state management system that wraps Clojure atoms
   with convenient helpers for common patterns in TUI applications.

   Previous versions included undo/redo functionality which has been removed
   due to race conditions and over-engineering. If you need undo/redo, consider
   implementing it at the application level or using a dedicated library.")

;; ────────────────────── State Creation ──────────────────────

(defn create-state
  "Create a new application state atom

   Options:
   - :initial-value - initial state value (default {})
   - :watchers - map of watcher-id to watcher-fn

   Returns a plain Clojure atom with optional watchers attached"
  [& {:keys [initial-value watchers]
      :or {initial-value {}
           watchers {}}}]
  (let [state-atom (atom initial-value
                        :meta {:watchers (atom {})})]
    ;; Add user watchers if provided
    (doseq [[watcher-id watcher-fn] watchers]
      (add-watch state-atom watcher-id watcher-fn)
      (swap! (-> state-atom meta :watchers) assoc watcher-id watcher-fn))
    state-atom))

;; ────────────────────── State Access ──────────────────────

(defn get-state
  "Get current state value"
  [state-atom]
  @state-atom)

(defn get-in-state
  "Get value at path in state"
  [state-atom path]
  (get-in @state-atom path))

;; ────────────────────── State Updates ──────────────────────

(defn set-state!
  "Set entire state to new value"
  [state-atom new-value]
  (reset! state-atom new-value))

(defn update-state!
  "Update state with function
   Usage: (update-state! state assoc :key value)"
  [state-atom f & args]
  (apply swap! state-atom f args))

(defn update-in-state!
  "Update value at path in state
   Usage: (update-in-state! state [:user :name] str/upper-case)"
  [state-atom path f & args]
  (apply swap! state-atom update-in path f args))

(defn assoc-in-state!
  "Associate value at path in state"
  [state-atom path value]
  (swap! state-atom assoc-in path value))

(defn dissoc-in-state!
  "Dissociate value at path in state"
  [state-atom path]
  (swap! state-atom
         (fn [state]
           (if (= 1 (count path))
             (dissoc state (first path))
             (update-in state (butlast path) dissoc (last path))))))

;; ────────────────────── Watchers ──────────────────────

(defn add-watcher!
  "Add a state watcher function
   watcher-fn receives: (fn [key ref old-state new-state] ...)

   Returns the state-atom for chaining"
  [state-atom watcher-id watcher-fn]
  (add-watch state-atom watcher-id watcher-fn)
  (when-let [watchers-atom (-> state-atom meta :watchers)]
    (swap! watchers-atom assoc watcher-id watcher-fn))
  state-atom)

(defn remove-watcher!
  "Remove a state watcher by id

   Returns the state-atom for chaining"
  [state-atom watcher-id]
  (remove-watch state-atom watcher-id)
  (when-let [watchers-atom (-> state-atom meta :watchers)]
    (swap! watchers-atom dissoc watcher-id))
  state-atom)

(defn list-watchers
  "List all registered watchers (returns watcher keys)"
  [state-atom]
  (if-let [watchers-atom (-> state-atom meta :watchers)]
    (keys @watchers-atom)
    []))

;; ────────────────────── Reactive Watchers ──────────────────────

(defn watch-path
  "Create a watcher that only fires when a specific path changes

   Usage:
   (watch-path state [:user :name] :name-watcher
     (fn [old-val new-val]
       (println \"Name changed from\" old-val \"to\" new-val)))"
  [state-atom path watcher-id callback]
  (add-watcher! state-atom watcher-id
    (fn [_ _ old-state new-state]
      (let [old-val (get-in old-state path)
            new-val (get-in new-state path)]
        (when (not= old-val new-val)
          (callback old-val new-val)))))
  state-atom)

(defn watch-keys
  "Create a watcher that fires when any of the specified keys change

   Usage:
   (watch-keys state [:name :email] :user-watcher
     (fn [changed-keys old-state new-state]
       (println \"Changed keys:\" changed-keys)))"
  [state-atom keys watcher-id callback]
  (add-watcher! state-atom watcher-id
    (fn [_ _ old-state new-state]
      (let [changed-keys (filter #(not= (get old-state %)
                                       (get new-state %))
                                keys)]
        (when (seq changed-keys)
          (callback changed-keys old-state new-state)))))
  state-atom)

(defn watch-predicate
  "Create a watcher that fires when a predicate returns true

   Usage:
   (watch-predicate state :count-watcher
     (fn [state] (> (:count state) 10))
     (fn [old-state new-state]
       (println \"Count exceeded 10!\")))"
  [state-atom watcher-id predicate callback]
  (add-watcher! state-atom watcher-id
    (fn [_ _ old-state new-state]
      (when (and (not (predicate old-state))
                (predicate new-state))
        (callback old-state new-state))))
  state-atom)

;; ────────────────────── Integration with Render Loop ──────────────────────

(defn create-reactive-state
  "Create a state atom that triggers a callback on changes

   Usage:
   (create-reactive-state
     :initial-value {:count 0}
     :on-change (fn [old new] (render-app new)))

   The :on-change callback is triggered for every state change"
  [& {:keys [on-change] :as opts}]
  (let [state-atom (apply create-state (apply concat (dissoc opts :on-change)))]
    (when on-change
      (add-watcher! state-atom ::reactive-render
        (fn [_ _ old-state new-state]
          (when (not= old-state new-state)
            (on-change old-state new-state)))))
    state-atom))

(defn bind-to-render
  "Bind an existing state atom to a render function

   Usage:
   (bind-to-render state render-loop-control)

   Where render-loop-control has a :force-render! function"
  [state-atom render-control]
  (add-watcher! state-atom ::render-binding
    (fn [_ _ old-state new-state]
      (when (not= old-state new-state)
        ((:force-render! render-control)))))
  state-atom)
