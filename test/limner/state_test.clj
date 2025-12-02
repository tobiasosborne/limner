(ns limner.state-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.state :as state]
            [clojure.string :as str]))

;; ──────────────────────── State Creation Tests ──────────────────────

(deftest test-create-state
  (testing "Create state with default values"
    (let [s (state/create-state)]
      (is (= {} @s))))

  (testing "Create state with initial value"
    (let [s (state/create-state :initial-value {:count 0 :name "test"})]
      (is (= {:count 0 :name "test"} @s)))))

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

  (testing "Remove watcher stops notifications"
    (let [s (state/create-state :initial-value {:count 0})
          calls (atom 0)
          watcher (fn [_ _ _ _] (swap! calls inc))]
      (state/add-watcher! s :test-watcher watcher)
      (state/update-state! s assoc :count 1)
      (state/remove-watcher! s :test-watcher)
      (state/update-state! s assoc :count 2)
      (is (= 1 @calls)))))

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
        (is (contains? (set watchers) :watcher2))))))

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

      ;; Verify final state
      (is (= 2 (count (:users @s)))))))

(deftest test-bind-to-render
  (testing "Bind state to render control"
    (let [s (state/create-state :initial-value {:count 0})
          render-calls (atom 0)
          render-control {:force-render! (fn [] (swap! render-calls inc))}]

      (state/bind-to-render s render-control)

      ;; Update state - should trigger render
      (state/update-state! s assoc :count 1)
      (is (= 1 @render-calls))

      ;; Update again
      (state/update-state! s assoc :count 2)
      (is (= 2 @render-calls)))))
