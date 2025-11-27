(ns limner.state
  "Application state management with reactive updates and undo/redo"
  (:require [clojure.string :as str]
            [clojure.edn :as edn]))

;; ────────────────────── State Creation ──────────────────────

(defn create-state
  "Create a new application state atom with metadata

   Options:
   - :initial-value - initial state value (default {})
   - :history-limit - maximum undo history size (default 50)
   - :enable-history - enable undo/redo (default true)
   - :watchers - map of watcher-id to watcher-fn

   Returns an atom with metadata for history tracking"
  [& {:keys [initial-value history-limit enable-history watchers]
      :or {initial-value {}
           history-limit 50
           enable-history true
           watchers {}}}]
  (let [state-atom (atom initial-value
                        :meta {:history (if enable-history [initial-value] [])
                               :history-position (if enable-history 0 -1)
                               :history-limit history-limit
                               :enable-history enable-history
                               :watchers (atom watchers)
                               :paused-watchers (atom #{})
                               :recording-history true})]

    ;; Add internal watcher for history recording if enabled
    (when enable-history
      (add-watch state-atom ::history-recorder
        (fn [_ _ old-state new-state]
          (when (and (get (meta state-atom) :enable-history)
                    (get (meta state-atom) :recording-history)
                    (not= old-state new-state))
            (let [history-limit (get (meta state-atom) :history-limit)
                  current-pos (get (meta state-atom) :history-position)
                  current-history (get (meta state-atom) :history)
                  trimmed-history (vec (take (inc current-pos) current-history))
                  new-history (conj trimmed-history new-state)
                  final-history (if (> (count new-history) history-limit)
                                 (vec (take-last history-limit new-history))
                                 new-history)
                  new-position (dec (count final-history))]
              (alter-meta! state-atom assoc
                           :history final-history
                           :history-position new-position))))))

    ;; Add user watchers if provided
    (doseq [[watcher-id watcher-fn] watchers]
      (add-watch state-atom watcher-id watcher-fn))

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

(defn set-state!
  "Set entire state to new value (records in history)"
  [state-atom new-value]
  (reset! state-atom new-value))

(defn update-state!
  "Update state with function (records in history)
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

;; ────────────────────── History Management ──────────────────────

(defn- get-meta-atom
  "Get metadata from state atom"
  [state-atom key]
  (get (meta state-atom) key))

(defn- update-meta-atom!
  "Update metadata on state atom"
  [state-atom key f & args]
  (alter-meta! state-atom update key #(apply f % args)))


(defn can-undo?
  "Check if undo is available"
  [state-atom]
  (and (get-meta-atom state-atom :enable-history)
       (> (get-meta-atom state-atom :history-position) 0)))  ; Can go back if pos > 0

(defn can-redo?
  "Check if redo is available"
  [state-atom]
  (and (get-meta-atom state-atom :enable-history)
       (< (get-meta-atom state-atom :history-position)
          (dec (count (get-meta-atom state-atom :history))))))  ; Can go forward if not at end

(defn undo!
  "Undo last state change
   Returns true if undo was performed, false otherwise

   With new history model:
   history = [state0, state1, state2, ...]
   position points to current state
   To undo: move position back and restore history[new_position]"
  [state-atom]
  (if (can-undo? state-atom)
    (let [new-position (dec (get-meta-atom state-atom :history-position))
          history (get-meta-atom state-atom :history)
          previous-state (get history new-position)]

      ;; Move position back
      (alter-meta! state-atom assoc :history-position new-position)

      ;; Temporarily disable history recording during undo
      (alter-meta! state-atom assoc :recording-history false)
      (reset! state-atom previous-state)
      (alter-meta! state-atom assoc :recording-history true)

      true)
    false))

(defn redo!
  "Redo last undone state change
   Returns true if redo was performed, false otherwise

   With new history model:
   To redo: move position forward and restore history[new_position]"
  [state-atom]
  (if (can-redo? state-atom)
    (let [new-position (inc (get-meta-atom state-atom :history-position))
          history (get-meta-atom state-atom :history)
          next-state (get history new-position)]

      ;; Move position forward
      (alter-meta! state-atom assoc :history-position new-position)

      ;; Temporarily disable history recording during redo
      (alter-meta! state-atom assoc :recording-history false)
      (reset! state-atom next-state)
      (alter-meta! state-atom assoc :recording-history true)

      true)
    false))

(defn clear-history!
  "Clear undo/redo history, keeping only current state"
  [state-atom]
  (let [current-state @state-atom]
    (alter-meta! state-atom assoc
                 :history [current-state]
                 :history-position 0)))

(defn history-size
  "Get current history size"
  [state-atom]
  (count (get-meta-atom state-atom :history)))

(defn history-position
  "Get current position in history"
  [state-atom]
  (get-meta-atom state-atom :history-position))

;; ────────────────────── Watchers ──────────────────────

(defn add-watcher!
  "Add a state watcher function
   watcher-fn receives: (fn [key ref old-state new-state] ...)

   Returns the state-atom for chaining"
  [state-atom watcher-id watcher-fn]
  (add-watch state-atom watcher-id watcher-fn)
  (swap! (get-meta-atom state-atom :watchers) assoc watcher-id watcher-fn)
  state-atom)

(defn remove-watcher!
  "Remove a state watcher by id

   Returns the state-atom for chaining"
  [state-atom watcher-id]
  (remove-watch state-atom watcher-id)
  (swap! (get-meta-atom state-atom :watchers) dissoc watcher-id)
  state-atom)

(defn pause-watcher!
  "Temporarily pause a watcher (it won't receive updates)"
  [state-atom watcher-id]
  (remove-watch state-atom watcher-id)
  (swap! (get-meta-atom state-atom :paused-watchers) conj watcher-id)
  state-atom)

(defn resume-watcher!
  "Resume a paused watcher"
  [state-atom watcher-id]
  (let [watchers @(get-meta-atom state-atom :watchers)
        watcher-fn (get watchers watcher-id)]
    (when watcher-fn
      (add-watch state-atom watcher-id watcher-fn)
      (swap! (get-meta-atom state-atom :paused-watchers) disj watcher-id)))
  state-atom)

(defn pause-all-watchers!
  "Pause all watchers"
  [state-atom]
  (let [watchers @(get-meta-atom state-atom :watchers)]
    (doseq [[watcher-id _] watchers]
      (pause-watcher! state-atom watcher-id)))
  state-atom)

(defn resume-all-watchers!
  "Resume all paused watchers"
  [state-atom]
  (let [paused @(get-meta-atom state-atom :paused-watchers)]
    (doseq [watcher-id paused]
      (resume-watcher! state-atom watcher-id)))
  state-atom)

(defn list-watchers
  "List all registered watchers"
  [state-atom]
  (keys @(get-meta-atom state-atom :watchers)))

(defn list-paused-watchers
  "List all paused watchers"
  [state-atom]
  @(get-meta-atom state-atom :paused-watchers))

;; ────────────────────── Reactive Updates ──────────────────────

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

;; ────────────────────── Serialization ──────────────────────

(defn serialize
  "Serialize state to EDN string
   Options:
   - :pretty - pretty-print output (default false)
   - :include-history - include undo/redo history (default false)"
  [state-atom & {:keys [pretty include-history]
                 :or {pretty false include-history false}}]
  (let [state-value @state-atom
        data (if include-history
              {:state state-value
               :history (get-meta-atom state-atom :history)
               :history-position (get-meta-atom state-atom :history-position)}
              state-value)
        edn-str (pr-str data)]
    (if pretty
      (with-out-str
        (clojure.pprint/pprint data))
      edn-str)))

(defn deserialize
  "Deserialize state from EDN string
   Returns a new state atom"
  [edn-string & opts]
  (let [data (edn/read-string edn-string)
        has-history? (and (map? data) (contains? data :state))]
    (if has-history?
      ;; Restore with history
      (let [state-atom (apply create-state
                            :initial-value (:state data)
                            opts)]
        (alter-meta! state-atom assoc
                     :history (:history data)
                     :history-position (:history-position data))
        state-atom)
      ;; Restore without history
      (apply create-state :initial-value data opts))))

(defn save-to-file
  "Save state to file
   Options: same as serialize"
  [state-atom filename & opts]
  (spit filename (apply serialize state-atom opts)))

(defn load-from-file
  "Load state from file
   Returns a new state atom"
  [filename & opts]
  (apply deserialize (slurp filename) opts))

;; ────────────────────── Transactional Updates ──────────────────────

(defn without-watchers
  "Execute updates without triggering watchers

   Usage:
   (without-watchers state
     (assoc-in-state! state [:temp :data] value))"
  [state-atom f]
  (pause-all-watchers! state-atom)
  (try
    (f)
    (finally
      (resume-all-watchers! state-atom))))

;; ────────────────────── State Statistics ──────────────────────

(defn state-info
  "Get information about state atom"
  [state-atom]
  {:value @state-atom
   :history-enabled (get-meta-atom state-atom :enable-history)
   :history-size (history-size state-atom)
   :history-position (history-position state-atom)
   :history-limit (get-meta-atom state-atom :history-limit)
   :can-undo (can-undo? state-atom)
   :can-redo (can-redo? state-atom)
   :watchers (list-watchers state-atom)
   :paused-watchers (list-paused-watchers state-atom)})

;; ────────────────────── Integration with Render Loop ──────────────────────

(defn create-reactive-state
  "Create a state atom that triggers render on changes

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

;; ────────────────────── Helper Macros ──────────────────────

(defmacro with-state-transaction
  "Execute multiple state updates with history recorded as single entry"
  [state-atom & body]
  `(let [old-state# @~state-atom]
     (alter-meta! ~state-atom assoc :recording-history false)
     (try
       ~@body
       (finally
         ;; Manually record the combined change as one history entry
         (let [new-state# @~state-atom]
           (when (not= old-state# new-state#)
             (let [history-limit# (get (meta ~state-atom) :history-limit)
                   current-pos# (get (meta ~state-atom) :history-position)
                   current-history# (get (meta ~state-atom) :history)
                   ;; Trim future history and add new state
                   trimmed-history# (vec (take (inc current-pos#) current-history#))
                   new-history# (conj trimmed-history# new-state#)
                   final-history# (if (> (count new-history#) history-limit#)
                                   (vec (take-last history-limit# new-history#))
                                   new-history#)
                   new-position# (dec (count final-history#))]
               (alter-meta! ~state-atom assoc
                           :history final-history#
                           :history-position new-position#))))
         (alter-meta! ~state-atom assoc :recording-history true)))))

(defmacro when-state-changed
  "Execute body only if state actually changed"
  [state-atom update-expr & body]
  `(let [old-val# @~state-atom
         new-val# ~update-expr]
     (when (not= old-val# new-val#)
       ~@body
       new-val#)))
