(ns limner.streaming-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.streaming :as stream]
            [limner.syntax :as syntax]))

;; ────────────────────── Component Creation ──────────────────────

(deftest test-stream-creation
  (testing "Create basic stream component"
    (let [s (stream/stream :text "Hello World")]
      (is (= "Hello World" (:text s)))
      (is (= 0 (:position s)))
      (is (= :pending (:state s)))
      (is (= 30 (:delay-ms s)))))

  (testing "Create stream with custom options"
    (let [s (stream/stream
             :text "Test"
             :delay-ms 50
             :lang :clojure
             :theme :monokai
             :show-cursor false
             :cursor-char "█"
             :cursor-blink-ms 1000)]
      (is (= 50 (:delay-ms s)))
      (is (= :clojure (:lang s)))
      (is (= :monokai (:theme s)))
      (is (= false (:show-cursor s)))
      (is (= "█" (:cursor-char s)))
      (is (= 1000 (:cursor-blink-ms s))))))

;; ────────────────────── State Management ──────────────────────

(deftest test-state-transitions
  (testing "Start streaming"
    (let [s (stream/stream :text "Hello")
          started (stream/start s)]
      (is (= :streaming (:state started)))))

  (testing "Pause streaming"
    (let [s (-> (stream/stream :text "Hello")
                stream/start
                stream/pause)]
      (is (= :paused (:state s)))))

  (testing "Resume streaming"
    (let [s (-> (stream/stream :text "Hello")
                stream/start
                stream/pause
                stream/resume)]
      (is (= :streaming (:state s)))))

  (testing "Cancel streaming"
    (let [s (-> (stream/stream :text "Hello")
                stream/start
                stream/cancel)]
      (is (= :cancelled (:state s)))))

  (testing "Resume only works from paused state"
    (let [pending (stream/stream :text "Hello")
          resumed (stream/resume pending)]
      (is (= :pending (:state resumed))))))

(deftest test-reset-and-set-text
  (testing "Reset stream"
    (let [s (-> (stream/stream :text "Hello")
                stream/start
                (stream/set-position 3)
                stream/reset-stream)]
      (is (= 0 (:position s)))
      (is (= :pending (:state s)))))

  (testing "Set new text"
    (let [s (-> (stream/stream :text "Hello")
                stream/start
                (stream/set-text "New text"))]
      (is (= "New text" (:text s)))
      (is (= 0 (:position s)))
      (is (= :pending (:state s))))))

;; ────────────────────── Stream Advancement ──────────────────────

(deftest test-advance-respects-delay
  (testing "Stream doesn't advance before delay elapsed"
    (let [s (-> (stream/stream :text "Hello" :delay-ms 10000)
                stream/start
                stream/advance)]
      (is (= 0 (:position s)))))

  (testing "Stream advances after delay (simulated)"
    (let [s (stream/stream :text "Hello" :delay-ms 0)
          started (stream/start s)
          advanced (stream/advance started)]
      (is (= 1 (:position advanced)))))

  (testing "Stream doesn't advance when paused"
    (let [s (-> (stream/stream :text "Hello" :delay-ms 0)
                stream/start
                stream/pause
                stream/advance)]
      (is (= 0 (:position s)))))

  (testing "Stream doesn't advance when cancelled"
    (let [s (-> (stream/stream :text "Hello" :delay-ms 0)
                stream/start
                stream/cancel
                stream/advance)]
      (is (= 0 (:position s))))))

(deftest test-completion
  (testing "Stream marks as completed at end"
    (let [s (-> (stream/stream :text "Hi" :delay-ms 0)
                stream/start
                stream/advance
                stream/advance
                stream/advance)]
      (is (= :completed (:state s)))
      (is (stream/completed? s))))

  (testing "Stream to completion helper"
    (let [s (-> (stream/stream :text "Hello World")
                stream/stream-to-completion)]
      (is (= 11 (:position s)))
      (is (= :completed (:state s)))
      (is (stream/completed? s)))))

;; ────────────────────── Rendering ──────────────────────

(deftest test-render-basic
  (testing "Render at start"
    (let [s (stream/stream :text "Hello World")
          output (stream/render s)]
      (is (= "" output))))

  (testing "Render partial text"
    (let [s (-> (stream/stream :text "Hello World")
                (stream/set-position 5))
          output (stream/render s)]
      ;; Strip ANSI codes for comparison
      (is (= "Hello" (syntax/strip-colors output)))))

  (testing "Render complete text"
    (let [s (-> (stream/stream :text "Hello")
                stream/stream-to-completion)
          output (stream/render s)]
      ;; Should contain text (may have cursor)
      (is (clojure.string/includes? (syntax/strip-colors output) "Hello")))))

(deftest test-render-with-syntax
  (testing "Render with syntax highlighting"
    (let [code "(defn hello [] \"world\")"
          s (-> (stream/stream :text code :lang :clojure)
                stream/stream-to-completion)
          output (stream/render s)]
      ;; Output should contain ANSI codes
      (is (clojure.string/includes? output "\u001B["))
      ;; And the original text
      (is (clojure.string/includes? (syntax/strip-colors output) "defn")))))

(deftest test-render-cursor
  (testing "Cursor appears when completed"
    (let [s (-> (stream/stream :text "Hi" :show-cursor true)
                stream/stream-to-completion
                ;; Make cursor visible
                (assoc :cursor-visible true))
          output (stream/render s)]
      ;; Check cursor character is present
      (is (clojure.string/includes? output "▋"))))

  (testing "No cursor when show-cursor is false"
    (let [s (-> (stream/stream :text "Hi" :show-cursor false)
                stream/stream-to-completion)
          output (stream/render s)]
      (is (not (clojure.string/includes? output "▋"))))))

;; ────────────────────── Query Functions ──────────────────────

(deftest test-query-functions
  (testing "State query functions"
    (let [pending (stream/stream :text "Hello")
          streaming (stream/start pending)
          paused (stream/pause streaming)
          cancelled (stream/cancel streaming)
          completed (stream/stream-to-completion streaming)]

      (is (stream/streaming? streaming))
      (is (not (stream/streaming? pending)))

      (is (stream/paused? paused))
      (is (not (stream/paused? streaming)))

      (is (stream/cancelled? cancelled))
      (is (not (stream/cancelled? streaming)))

      (is (stream/completed? completed))
      (is (not (stream/completed? streaming)))))

  (testing "Progress calculation"
    (let [s (stream/stream :text "Hello")]
      (is (= 0 (stream/progress s)))

      (let [half (stream/set-position s 2)]
        (is (= 40 (stream/progress half))))

      (let [done (stream/stream-to-completion s)]
        (is (= 100 (stream/progress done))))))

  (testing "Remaining characters"
    (let [s (stream/stream :text "Hello World")]
      (is (= 11 (stream/remaining-chars s)))

      (let [partial (stream/set-position s 5)]
        (is (= 6 (stream/remaining-chars partial))))

      (let [done (stream/stream-to-completion s)]
        (is (= 0 (stream/remaining-chars done)))))))

;; ────────────────────── Set Position ──────────────────────

(deftest test-set-position
  (testing "Set position within bounds"
    (let [s (-> (stream/stream :text "Hello")
                (stream/set-position 3))]
      (is (= 3 (:position s)))))

  (testing "Position clamped to text length"
    (let [s (-> (stream/stream :text "Hello")
                (stream/set-position 100))]
      (is (= 5 (:position s)))))

  (testing "Position clamped to zero"
    (let [s (-> (stream/stream :text "Hello")
                (stream/set-position -5))]
      (is (= 0 (:position s))))))

;; ────────────────────── Cursor Blink ──────────────────────

(deftest test-cursor-blink
  (testing "Cursor toggles visibility"
    (let [s (stream/stream :text "Hello" :cursor-blink-ms 100)]
      ;; Initial state
      (is (true? (:cursor-visible s)))

      ;; Tick cursor should update visibility based on time
      (let [ticked (stream/tick-cursor s)]
        ;; cursor-visible should be a boolean
        (is (boolean? (:cursor-visible ticked)))))))

;; ────────────────────── Integration Tests ──────────────────────

(deftest test-full-streaming-lifecycle
  (testing "Complete streaming lifecycle"
    (let [text "Hello"
          s (stream/stream :text text :delay-ms 0 :show-cursor false)]

      ;; Start pending
      (is (= :pending (:state s)))
      (is (= 0 (:position s)))

      ;; Start streaming
      (let [started (stream/start s)]
        (is (= :streaming (:state started)))

        ;; Advance through text
        (let [tick1 (stream/tick started)
              tick2 (stream/tick tick1)
              tick3 (stream/tick tick2)
              tick4 (stream/tick tick3)
              tick5 (stream/tick tick4)
              final (stream/tick tick5)]

          ;; Should be completed
          (is (stream/completed? final))
          (is (= text (syntax/strip-colors (stream/render final)))))))))

(deftest test-pause-resume-lifecycle
  (testing "Pause and resume streaming"
    (let [s (-> (stream/stream :text "Hello" :delay-ms 0)
                stream/start
                stream/advance)]

      ;; Advanced to position 1
      (is (= 1 (:position s)))

      ;; Pause
      (let [paused (stream/pause s)
            advanced-while-paused (stream/advance paused)]

        ;; Should not advance while paused
        (is (= 1 (:position advanced-while-paused)))

        ;; Resume and advance
        (let [resumed (stream/resume paused)
              advanced (stream/advance resumed)]
          (is (= :streaming (:state resumed)))
          (is (= 2 (:position advanced))))))))
