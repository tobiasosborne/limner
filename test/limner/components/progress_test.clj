(ns limner.components.progress-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.components.progress :as progress]
            [clojure.string :as str]))

;; ────────────────────── Spinner Tests ──────────────────────
(deftest test-spinner-creation
  (testing "Create basic spinner"
    (let [spinner (progress/spinner)]
      (is (map? spinner))
      (is (= 0 (:frame spinner)))
      (is (= :dots (:style spinner)))))

  (testing "Create spinner with custom style"
    (let [spinner (progress/spinner :style :line)]
      (is (= :line (:style spinner)))))

  (testing "Create spinner with custom frames"
    (let [frames ["A" "B" "C"]
          spinner (progress/spinner :style :custom :frames frames)]
      (is (= :custom (:style spinner)))
      (is (= frames (:frames spinner))))))

(deftest test-spinner-frames
  (testing "Spinner advances through frames"
    (let [spinner (progress/spinner)
          next-spinner (progress/tick spinner)]
      (is (not= (:frame spinner) (:frame next-spinner)))
      (is (= 1 (:frame next-spinner)))))

  (testing "Spinner wraps around to first frame"
    (let [spinner (progress/spinner :style :dots)
          frames (get progress/spinner-styles :dots)
          ;; Advance to last frame
          last-frame-spinner (assoc spinner :frame (dec (count frames)))
          wrapped (progress/tick last-frame-spinner)]
      (is (= 0 (:frame wrapped)))))

  (testing "Render spinner shows current frame"
    (let [spinner (progress/spinner :style :dots)
          rendered (progress/render spinner)]
      (is (string? rendered))
      (is (pos? (count rendered))))))

;; ────────────────────── Progress Bar Tests ──────────────────────
(deftest test-progress-bar-creation
  (testing "Create determinate progress bar"
    (let [bar (progress/progress-bar :value 50 :width 20)]
      (is (map? bar))
      (is (= 50 (:value bar)))
      (is (= 20 (:width bar)))
      (is (= :determinate (:bar-type bar)))))

  (testing "Create indeterminate progress bar"
    (let [bar (progress/progress-bar :type :indeterminate :width 30)]
      (is (= :indeterminate (:bar-type bar)))
      (is (= 30 (:width bar)))))

  (testing "Progress bar clamps values to 0-100"
    (let [bar-over (progress/progress-bar :value 150)
          bar-under (progress/progress-bar :value -10)]
      (is (= 100 (:value bar-over)))
      (is (= 0 (:value bar-under))))))

(deftest test-progress-bar-rendering
  (testing "Progress bar at 0%"
    (let [bar (progress/progress-bar :value 0 :width 10)
          rendered (progress/render bar)
          visible (limner.core/visible-length rendered)]
      (is (>= visible 10))
      (is (string? rendered))))

  (testing "Progress bar at 50%"
    (let [bar (progress/progress-bar :value 50 :width 20)
          rendered (progress/render bar)]
      (is (string? rendered))
      (is (str/includes? rendered "50%"))))

  (testing "Progress bar at 100%"
    (let [bar (progress/progress-bar :value 100 :width 10)
          rendered (progress/render bar)]
      (is (string? rendered))
      (is (str/includes? rendered "100%"))))

  (testing "Progress bar shows percentage label"
    (let [bar (progress/progress-bar :value 75 :width 20 :show-percentage true)
          rendered (progress/render bar)]
      (is (str/includes? rendered "75%"))))

  (testing "Progress bar hides percentage when requested"
    (let [bar (progress/progress-bar :value 75 :width 20 :show-percentage false)
          rendered (progress/render bar)]
      (is (not (str/includes? rendered "75%"))))))

(deftest test-indeterminate-progress
  (testing "Indeterminate progress bar animates"
    (let [bar (progress/progress-bar :type :indeterminate :width 20)
          next-bar (progress/tick bar)]
      (is (not= (:animation-frame bar) (:animation-frame next-bar)))))

  (testing "Indeterminate progress renders without percentage"
    (let [bar (progress/progress-bar :type :indeterminate :width 20)
          rendered (progress/render bar)]
      (is (string? rendered))
      (is (not (str/includes? rendered "%"))))))

(deftest test-progress-bar-update
  (testing "Update progress value"
    (let [bar (progress/progress-bar :value 25)
          updated (progress/set-value bar 75)]
      (is (= 75 (:value updated)))))

  (testing "Increment progress"
    (let [bar (progress/progress-bar :value 30)
          incremented (progress/increment bar 10)]
      (is (= 40 (:value incremented)))))

  (testing "Increment doesn't exceed 100"
    (let [bar (progress/progress-bar :value 95)
          incremented (progress/increment bar 10)]
      (is (= 100 (:value incremented))))))

;; ────────────────────── Step Indicator Tests ──────────────────────
(deftest test-step-indicator-creation
  (testing "Create step indicator"
    (let [steps (progress/step-indicator :current 1 :total 5)]
      (is (map? steps))
      (is (= 1 (:current steps)))
      (is (= 5 (:total steps)))))

  (testing "Step indicator validates bounds"
    (let [steps (progress/step-indicator :current 6 :total 5)]
      (is (<= (:current steps) (:total steps))))))

(deftest test-step-indicator-rendering
  (testing "Render step indicator as fraction"
    (let [steps (progress/step-indicator :current 2 :total 5)
          rendered (progress/render steps)]
      (is (string? rendered))
      (is (str/includes? rendered "2"))
      (is (str/includes? rendered "5"))))

  (testing "Render step indicator with labels"
    (let [steps (progress/step-indicator :current 1 :total 3
                                         :labels ["Setup" "Process" "Complete"])
          rendered (progress/render steps)]
      (is (str/includes? rendered "Setup"))))

  (testing "Step indicator progress percentage"
    (let [steps (progress/step-indicator :current 3 :total 5)
          pct (progress/step-percentage steps)]
      (is (= 60 pct)))))

(deftest test-step-indicator-navigation
  (testing "Advance to next step"
    (let [steps (progress/step-indicator :current 2 :total 5)
          next-step (progress/next-step steps)]
      (is (= 3 (:current next-step)))))

  (testing "Cannot advance beyond total"
    (let [steps (progress/step-indicator :current 5 :total 5)
          next-step (progress/next-step steps)]
      (is (= 5 (:current next-step)))))

  (testing "Go to previous step"
    (let [steps (progress/step-indicator :current 3 :total 5)
          prev-step (progress/prev-step steps)]
      (is (= 2 (:current prev-step)))))

  (testing "Cannot go before first step"
    (let [steps (progress/step-indicator :current 1 :total 5)
          prev-step (progress/prev-step steps)]
      (is (= 1 (:current prev-step))))))

;; ────────────────────── Pulse Effect Tests ──────────────────────
(deftest test-pulse-creation
  (testing "Create pulse effect"
    (let [pulse (progress/pulse :text "Loading...")]
      (is (map? pulse))
      (is (= "Loading..." (:text pulse)))
      (is (zero? (:frame pulse)))))

  (testing "Create pulse with custom speed"
    (let [pulse (progress/pulse :text "Wait" :speed :fast)]
      (is (= :fast (:speed pulse))))))

(deftest test-pulse-animation
  (testing "Pulse cycles through opacity levels"
    (let [pulse (progress/pulse :text "Loading")
          next-pulse (progress/tick pulse)]
      (is (not= (:frame pulse) (:frame next-pulse)))))

  (testing "Pulse renders with varying intensity"
    (let [pulse (progress/pulse :text "Processing")
          rendered (progress/render pulse)]
      (is (string? rendered))
      (is (str/includes? rendered "Processing")))))

(deftest test-pulse-speed
  (testing "Fast pulse has shorter cycle"
    (let [fast (progress/pulse :speed :fast)
          slow (progress/pulse :speed :slow)
          fast-frames (progress/pulse-frame-count fast)
          slow-frames (progress/pulse-frame-count slow)]
      (is (< fast-frames slow-frames)))))

;; ────────────────────── Animation Timing Tests ──────────────────────
(deftest test-animation-frame-rate
  (testing "Tick increments frame counter"
    (let [spinner (progress/spinner)
          ticked (progress/tick spinner)]
      (is (> (:frame ticked) (:frame spinner)))))

  (testing "Multiple ticks advance correctly"
    (let [bar (progress/progress-bar :type :indeterminate)
          ticked-once (progress/tick bar)
          ticked-twice (progress/tick ticked-once)]
      (is (not= (:animation-frame bar) (:animation-frame ticked-twice))))))

;; ────────────────────── Helper Function Tests ──────────────────────
(deftest test-helper-functions
  (testing "is-complete? for progress bar"
    (let [incomplete (progress/progress-bar :value 50)
          complete (progress/progress-bar :value 100)]
      (is (false? (progress/complete? incomplete)))
      (is (true? (progress/complete? complete)))))

  (testing "is-complete? for step indicator"
    (let [incomplete (progress/step-indicator :current 2 :total 5)
          complete (progress/step-indicator :current 5 :total 5)]
      (is (false? (progress/complete? incomplete)))
      (is (true? (progress/complete? complete)))))

  (testing "progress-type detection"
    (let [bar (progress/progress-bar)
          spinner (progress/spinner)
          steps (progress/step-indicator :current 1 :total 3)]
      (is (= :progress-bar (progress/component-type bar)))
      (is (= :spinner (progress/component-type spinner)))
      (is (= :step-indicator (progress/component-type steps))))))

;; ────────────────────── Custom Style Tests ──────────────────────
(deftest test-custom-styles
  (testing "Progress bar with custom characters"
    (let [bar (progress/progress-bar :value 50 :width 20
                                     :filled-char "█"
                                     :empty-char "░")
          rendered (progress/render bar)]
      (is (str/includes? rendered "█"))
      (is (str/includes? rendered "░"))))

  (testing "Custom spinner frames"
    (let [custom-frames ["→" "↓" "←" "↑"]
          spinner (progress/spinner :style :custom :frames custom-frames)
          rendered (progress/render spinner)]
      (is (some #(str/includes? rendered %) custom-frames)))))

;; Run all tests
(defn run-tests []
  (clojure.test/run-tests 'limner.components.progress-test))
