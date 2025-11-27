(ns limner.state-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.state :as state]
            [clojure.string :as str]
            [clojure.java.io :as io]))

;; ──────────────────────── State Creation Tests ──────────────────────

(deftest test-create-state
  (testing "Create state with default values"
    (let [s (state/create-state)]
      (is (= {} @s))
      (is (= 1 (state/history-size s)))))

  (testing "Create state with initial value"
    (let [s (state/create-state :initial-value {:count 0 :name "test"})]
      (is (= {:count 0 :name "test"} @s))))

  (testing "Create state with history disabled"
    (let [s (state/create-state :enable-history false)]
      (state/update-state! s assoc :count 1)
      (is (= 0 (state/history-size s))))))

;; ──────────────────────── State Access Tests ──────────────────────

(deftest test-state-access
  (testing "Get state value"
    (let [s (state/create-state :initial-value {:a 1 :b 2})]
      (is (= {:a 1 :b 2} (state/get-state s)))))

  (testing "Get value at path"
    (let [s (state/create-state :initial-value {:user {:name "Alice" :age 30}})]
      (is (= "Alice" (state/get-in-state s [:user :name])))
      (is (= 30 (state/get-in-state s [:user :age])))))

  (testing "Get non-existent path returns nil"
    (let [s (state/create-state :initial-value {})]
      (is (nil? (state/get-in-state s [:foo :bar]))))))

;; ──────────────────────── State Update Tests ──────────────────────

(deftest test-state-updates
  (testing "Set entire state"
    (let [s (state/create-state :initial-value {:a 1})]
      (state/set-state! s {:b 2})
      (is (= {:b 2} @s))))

  (testing "Update state with function"
    (let [s (state/create-state :initial-value {:count 0})]
      (state/update-state! s assoc :count 5)
      (is (= {:count 5} @s))))

  (testing "Update value at path"
    (let [s (state/create-state :initial-value {:user {:name "Alice"}})]
      (state/update-in-state! s [:user :name] str/upper-case)
      (is (= "ALICE" (state/get-in-state s [:user :name])))))

  (testing "Assoc value at path"
    (let [s (state/create-state :initial-value {})]
      (state/assoc-in-state! s [:user :name] "Bob")
      (is (= "Bob" (state/get-in-state s [:user :name])))))

  (testing "Dissoc value at path"
    (let [s (state/create-state :initial-value {:user {:name "Alice" :age 30}})]
      (state/dissoc-in-state! s [:user :age])
      (is (= {:user {:name "Alice"}} @s)))))

;; ──────────────────────── History Tests ──────────────────────

(deftest test-undo-redo
  (testing "Can undo after changes"
    (let [s (state/create-state :initial-value {:count 0})]
      (state/update-state! s assoc :count 1)
      (state/update-state! s assoc :count 2)
      (is (state/can-undo? s))
      (state/undo! s)
      (is (= {:count 1} @s))
      (state/undo! s)
      (is (= {:count 0} @s))))

  (testing "Can redo after undo"
    (let [s (state/create-state :initial-value {:count 0})]
      (state/update-state! s assoc :count 1)
      (state/update-state! s assoc :count 2)
      (state/undo! s)
      (is (state/can-redo? s))
      (state/redo! s)
      (is (= {:count 2} @s))))

  (testing "Cannot undo when no history"
    (let [s (state/create-state :initial-value {:count 0})]
      (is (not (state/can-undo? s)))
      (is (false? (state/undo! s)))))

  (testing "Cannot redo when at end of history"
    (let [s (state/create-state :initial-value {:count 0})]
      (state/update-state! s assoc :count 1)
      (is (not (state/can-redo? s)))
      (is (false? (state/redo! s)))))

  (testing "New change clears redo history"
    (let [s (state/create-state :initial-value {:count 0})]
      (state/update-state! s assoc :count 1)
      (state/update-state! s assoc :count 2)
      (state/undo! s)
      (is (state/can-redo? s))
      (state/update-state! s assoc :count 3)
      (is (not (state/can-redo? s)))))

  (testing "History respects size limit"
    (let [s (state/create-state :initial-value {:count 0} :history-limit 3)]
      (dotimes [i 10]
        (state/update-state! s assoc :count (inc i)))
      (is (<= (state/history-size s) 3)))))

(deftest test-clear-history
  (testing "Clear history removes all undo/redo"
    (let [s (state/create-state :initial-value {:count 0})]
      (state/update-state! s assoc :count 1)
      (state/update-state! s assoc :count 2)
      (is (pos? (state/history-size s)))
      (state/clear-history! s)
      ;; After clear, history has only current state
      (is (= 1 (state/history-size s)))
      (is (not (state/can-undo? s))))))

;; ──────────────────────── Watcher Tests ──────────────────────

(deftest test-watchers
  (testing "Watcher fires on state change"
    (let [s (state/create-state :initial-value {:count 0})
          calls (atom [])
          watcher (fn [_ _ old new]
                   (swap! calls conj {:old old :new new}))]
      (state/add-watcher! s :test-watcher watcher)
      (state/update-state! s assoc :count 1)
      (is (= 1 (count @calls)))
      (is (= {:count 0} (:old (first @calls))))
      (is (= {:count 1} (:new (first @calls))))))

  (testing "Watcher does not fire when paused"
    (let [s (state/create-state :initial-value {:count 0})
          calls (atom 0)
          watcher (fn [_ _ _ _] (swap! calls inc))]
      (state/add-watcher! s :test-watcher watcher)
      (state/pause-watcher! s :test-watcher)
      (state/update-state! s assoc :count 1)
      (is (zero? @calls))))

  (testing "Watcher fires again after resume"
    (let [s (state/create-state :initial-value {:count 0})
          calls (atom 0)
          watcher (fn [_ _ _ _] (swap! calls inc))]
      (state/add-watcher! s :test-watcher watcher)
      (state/pause-watcher! s :test-watcher)
      (state/update-state! s assoc :count 1)
      (state/resume-watcher! s :test-watcher)
      (state/update-state! s assoc :count 2)
      (is (= 1 @calls))))

  (testing "Remove watcher stops notifications"
    (let [s (state/create-state :initial-value {:count 0})
          calls (atom 0)
          watcher (fn [_ _ _ _] (swap! calls inc))]
      (state/add-watcher! s :test-watcher watcher)
      (state/update-state! s assoc :count 1)
      (state/remove-watcher! s :test-watcher)
      (state/update-state! s assoc :count 2)
      (is (= 1 @calls)))))

(deftest test-pause-resume-all-watchers
  (testing "Pause all watchers"
    (let [s (state/create-state :initial-value {:count 0})
          calls (atom 0)
          watcher (fn [_ _ _ _] (swap! calls inc))]
      (state/add-watcher! s :watcher1 watcher)
      (state/add-watcher! s :watcher2 watcher)
      (state/pause-all-watchers! s)
      (state/update-state! s assoc :count 1)
      (is (zero? @calls))))

  (testing "Resume all watchers"
    (let [s (state/create-state :initial-value {:count 0})
          calls (atom 0)
          watcher (fn [_ _ _ _] (swap! calls inc))]
      (state/add-watcher! s :watcher1 watcher)
      (state/add-watcher! s :watcher2 watcher)
      (state/pause-all-watchers! s)
      (state/update-state! s assoc :count 1)
      (state/resume-all-watchers! s)
      (state/update-state! s assoc :count 2)
      (is (= 2 @calls)))))

;; ──────────────────────── Reactive Watcher Tests ──────────────────────

(deftest test-watch-path
  (testing "Watch specific path"
    (let [s (state/create-state :initial-value {:user {:name "Alice" :age 30}})
          changes (atom [])]
      (state/watch-path s [:user :name] :name-watcher
        (fn [old new]
          (swap! changes conj {:old old :new new})))

      ;; Change name - should fire
      (state/assoc-in-state! s [:user :name] "Bob")
      (is (= 1 (count @changes)))
      (is (= "Alice" (:old (first @changes))))
      (is (= "Bob" (:new (first @changes))))

      ;; Change age - should not fire
      (state/assoc-in-state! s [:user :age] 31)
      (is (= 1 (count @changes))))))

(deftest test-watch-keys
  (testing "Watch multiple keys"
    (let [s (state/create-state :initial-value {:name "Alice" :age 30 :city "NYC"})
          changes (atom [])]
      (state/watch-keys s [:name :age] :user-watcher
        (fn [changed-keys _ _]
          (swap! changes conj changed-keys)))

      ;; Change name - should fire
      (state/update-state! s assoc :name "Bob")
      (is (= 1 (count @changes)))
      (is (= [:name] (first @changes)))

      ;; Change city - should not fire
      (state/update-state! s assoc :city "LA")
      (is (= 1 (count @changes))))))

(deftest test-watch-predicate
  (testing "Watch with predicate"
    (let [s (state/create-state :initial-value {:count 0})
          triggered (atom false)]
      (state/watch-predicate s :threshold-watcher
        (fn [state] (> (:count state) 10))
        (fn [_ _] (reset! triggered true)))

      (state/update-state! s assoc :count 5)
      (is (false? @triggered))

      (state/update-state! s assoc :count 11)
      (is (true? @triggered)))))

;; ──────────────────────── Serialization Tests ──────────────────────

(deftest test-serialization
  (testing "Serialize and deserialize state"
    (let [s (state/create-state :initial-value {:count 42 :name "Test"})
          serialized (state/serialize s)
          deserialized (state/deserialize serialized)]
      (is (= @s @deserialized))))

  (testing "Serialize with history"
    (let [s (state/create-state :initial-value {:count 0})]
      (state/update-state! s assoc :count 1)
      (state/update-state! s assoc :count 2)

      (let [serialized (state/serialize s :include-history true)
            deserialized (state/deserialize serialized)]
        (is (= @s @deserialized))
        (is (= (state/history-size s) (state/history-size deserialized)))
        (is (state/can-undo? deserialized))))))

(deftest test-file-serialization
  (testing "Save and load from file"
    (let [temp-file (str "/tmp/limner-state-test-" (System/currentTimeMillis) ".edn")
          s (state/create-state :initial-value {:test "data" :count 123})]

      (try
        ;; Save to file
        (state/save-to-file s temp-file)
        (is (.exists (io/file temp-file)))

        ;; Load from file
        (let [loaded (state/load-from-file temp-file)]
          (is (= @s @loaded)))

        (finally
          ;; Clean up
          (io/delete-file temp-file true))))))

;; ──────────────────────── State Info Tests ──────────────────────

(deftest test-state-info
  (testing "Get state information"
    (let [s (state/create-state :initial-value {:count 0})]
      (state/update-state! s assoc :count 1)
      (state/add-watcher! s :test (fn [_ _ _ _] nil))

      (let [info (state/state-info s)]
        (is (= {:count 1} (:value info)))
        (is (:history-enabled info))
        (is (pos? (:history-size info)))
        (is (state/can-undo? s) (:can-undo info))
        (is (contains? (set (:watchers info)) :test))))))

;; ──────────────────────── Reactive State Tests ──────────────────────

(deftest test-reactive-state
  (testing "Create reactive state with on-change"
    (let [changes (atom [])
          s (state/create-reactive-state
             :initial-value {:count 0}
             :on-change (fn [old new]
                         (swap! changes conj {:old old :new new})))]

      (state/update-state! s assoc :count 1)
      (is (= 1 (count @changes)))
      (is (= {:count 0} (:old (first @changes))))
      (is (= {:count 1} (:new (first @changes)))))))

;; ──────────────────────── List Watchers Tests ──────────────────────

(deftest test-list-watchers
  (testing "List all watchers"
    (let [s (state/create-state)]
      (state/add-watcher! s :watcher1 (fn [_ _ _ _] nil))
      (state/add-watcher! s :watcher2 (fn [_ _ _ _] nil))

      (let [watchers (state/list-watchers s)]
        (is (= 2 (count watchers)))
        (is (contains? (set watchers) :watcher1))
        (is (contains? (set watchers) :watcher2)))))

  (testing "List paused watchers"
    (let [s (state/create-state)]
      (state/add-watcher! s :watcher1 (fn [_ _ _ _] nil))
      (state/add-watcher! s :watcher2 (fn [_ _ _ _] nil))
      (state/pause-watcher! s :watcher1)

      (let [paused (state/list-paused-watchers s)]
        (is (= 1 (count paused)))
        (is (contains? paused :watcher1))))))

;; ──────────────────────── Integration Tests ──────────────────────

(deftest test-complex-workflow
  (testing "Complex state management workflow"
    (let [s (state/create-state :initial-value {:users []})
          log (atom [])]

      ;; Add watcher to log changes
      (state/add-watcher! s :logger
        (fn [_ _ old new]
          (swap! log conj {:old old :new new})))

      ;; Add users
      (state/update-state! s update :users conj {:id 1 :name "Alice"})
      (state/update-state! s update :users conj {:id 2 :name "Bob"})

      ;; Check log
      (is (= 2 (count @log)))

      ;; Undo
      (state/undo! s)
      (is (= 1 (count (:users @s))))

      ;; Redo
      (state/redo! s)
      (is (= 2 (count (:users @s))))

      ;; Serialize and restore
      (let [serialized (state/serialize s)
            restored (state/deserialize serialized)]
        (is (= @s @restored))))))

(deftest test-without-watchers
  (testing "Update without triggering watchers"
    (let [s (state/create-state :initial-value {:count 0})
          calls (atom 0)]
      (state/add-watcher! s :counter (fn [_ _ _ _] (swap! calls inc)))

      ;; Normal update - fires watcher
      (state/update-state! s assoc :count 1)
      (is (= 1 @calls))

      ;; Update without watchers
      (state/without-watchers s
        #(state/update-state! s assoc :count 2))
      (is (= 1 @calls)) ; Still 1, watcher didn't fire

      ;; Normal update again - fires watcher
      (state/update-state! s assoc :count 3)
      (is (= 2 @calls)))))
