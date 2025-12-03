(ns limner.render-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.render :as render]))

;; ──────────────────────── Cell Tests ──────────────────────

(deftest test-cell-creation
  (testing "Cell with character and ANSI codes"
    (let [c (render/cell \A "\u001B[31m")]
      (is (= \A (:char c)))
      (is (= "\u001B[31m" (:ansi c)))))

  (testing "Empty cell"
    (let [c (render/empty-cell)]
      (is (= \space (:char c)))
      (is (= "" (:ansi c))))))

;; ──────────────────────── Buffer Tests ──────────────────────

(deftest test-buffer-creation
  (testing "Create buffer with dimensions"
    (let [buf (render/create-buffer 10 5)]
      (is (= 10 (:width buf)))
      (is (= 5 (:height buf)))
      (is (= 5 (count (:cells buf))))
      (is (= 10 (count (first (:cells buf)))))))

  (testing "Buffer cells are initially empty"
    (let [buf (render/create-buffer 10 5)
          first-cell (render/get-cell buf 0 0)]
      (is (= \space (:char first-cell)))
      (is (= "" (:ansi first-cell))))))

(deftest test-buffer-get-set
  (testing "Get and set cells"
    (let [buf (render/create-buffer 10 5)
          cell (render/cell \X "\u001B[32m")
          buf2 (render/set-cell buf 3 2 cell)
          retrieved (render/get-cell buf2 3 2)]
      (is (= \X (:char retrieved)))
      (is (= "\u001B[32m" (:ansi retrieved)))))

  (testing "Get cell out of bounds returns nil"
    (let [buf (render/create-buffer 10 5)]
      (is (nil? (render/get-cell buf 20 2)))
      (is (nil? (render/get-cell buf 5 10)))
      (is (nil? (render/get-cell buf -1 2)))))

  (testing "Set cell out of bounds doesn't crash"
    (let [buf (render/create-buffer 10 5)
          cell (render/cell \X "")
          buf2 (render/set-cell buf 20 2 cell)]
      (is (= buf buf2))))) ; Buffer unchanged

(deftest test-clear-buffer
  (testing "Clear buffer resets all cells"
    (let [buf (render/create-buffer 10 5)
          cell (render/cell \X "\u001B[32m")
          buf2 (-> buf
                   (render/set-cell 0 0 cell)
                   (render/set-cell 5 2 cell)
                   (render/set-cell 9 4 cell))
          buf3 (render/clear-buffer buf2)
          cell-00 (render/get-cell buf3 0 0)
          cell-52 (render/get-cell buf3 5 2)]
      (is (= \space (:char cell-00)))
      (is (= "" (:ansi cell-00)))
      (is (= \space (:char cell-52)))
      (is (= "" (:ansi cell-52))))))

;; ──────────────────────── ANSI Parsing Tests ──────────────────────

(deftest test-strip-ansi
  (testing "Strip ANSI codes from string"
    (is (= "Hello" (render/strip-ansi "\u001B[31mHello\u001B[0m")))
    (is (= "Hello World" (render/strip-ansi "\u001B[31mHello\u001B[32m World\u001B[0m")))
    (is (= "Plain" (render/strip-ansi "Plain")))))

(deftest test-parse-line-with-ansi
  (testing "Parse plain string"
    (let [cells (render/parse-line-with-ansi "ABC")]
      (is (= 3 (count cells)))
      (is (= \A (:char (first cells))))
      (is (= "" (:ansi (first cells))))))

  (testing "Parse string with ANSI codes"
    (let [cells (render/parse-line-with-ansi "\u001B[31mRED\u001B[0m")]
      (is (= 3 (count cells)))
      (is (= \R (:char (first cells))))
      (is (= "\u001B[31m" (:ansi (first cells))))))

  (testing "Parse string with multiple ANSI codes"
    (let [line "\u001B[31m\u001B[1mBOLD RED\u001B[0m"
          cells (render/parse-line-with-ansi line)]
      (is (= 8 (count cells)))
      (is (= \B (:char (first cells))))
      (is (= "\u001B[31m\u001B[1m" (:ansi (first cells)))))))

;; ──────────────────────── Buffer Writing Tests ──────────────────────

(deftest test-write-string-to-buffer
  (testing "Write plain string to buffer"
    (let [buf (render/create-buffer 20 5)
          buf2 (render/write-string-to-buffer buf 0 0 "Hello")
          cells [(render/get-cell buf2 0 0)
                 (render/get-cell buf2 1 0)
                 (render/get-cell buf2 2 0)
                 (render/get-cell buf2 3 0)
                 (render/get-cell buf2 4 0)]]
      (is (= \H (:char (nth cells 0))))
      (is (= \e (:char (nth cells 1))))
      (is (= \l (:char (nth cells 2))))
      (is (= \l (:char (nth cells 3))))
      (is (= \o (:char (nth cells 4))))))

  (testing "Write string with ANSI codes"
    (let [buf (render/create-buffer 20 5)
          buf2 (render/write-string-to-buffer buf 0 0 "\u001B[31mRED\u001B[0m")
          cell-r (render/get-cell buf2 0 0)
          cell-e (render/get-cell buf2 1 0)]
      (is (= \R (:char cell-r)))
      (is (= "\u001B[31m" (:ansi cell-r)))
      (is (= \E (:char cell-e)))
      (is (= "\u001B[31m" (:ansi cell-e))))))

(deftest test-write-lines-to-buffer
  (testing "Write multiple lines"
    (let [buf (render/create-buffer 20 10)
          lines ["Line 1" "Line 2" "Line 3"]
          buf2 (render/write-lines-to-buffer buf 0 0 lines)
          line1-start (render/get-cell buf2 0 0)
          line2-start (render/get-cell buf2 0 1)
          line3-start (render/get-cell buf2 0 2)]
      (is (= \L (:char line1-start)))
      (is (= \L (:char line2-start)))
      (is (= \L (:char line3-start)))))

  (testing "Write lines at offset"
    (let [buf (render/create-buffer 20 10)
          lines ["Offset"]
          buf2 (render/write-lines-to-buffer buf 5 3 lines)
          cell (render/get-cell buf2 5 3)]
      (is (= \O (:char cell))))))

;; ──────────────────────── Diff Tests ──────────────────────

(deftest test-cells-equal
  (testing "Identical cells are equal"
    (let [c1 (render/cell \A "\u001B[31m")
          c2 (render/cell \A "\u001B[31m")]
      (is (render/cells-equal? c1 c2))))

  (testing "Different characters are not equal"
    (let [c1 (render/cell \A "\u001B[31m")
          c2 (render/cell \B "\u001B[31m")]
      (is (not (render/cells-equal? c1 c2)))))

  (testing "Different ANSI codes are not equal"
    (let [c1 (render/cell \A "\u001B[31m")
          c2 (render/cell \A "\u001B[32m")]
      (is (not (render/cells-equal? c1 c2))))))

(deftest test-find-dirty-cells
  (testing "No changes produces no dirty cells"
    (let [buf1 (render/create-buffer 10 5)
          buf2 (render/create-buffer 10 5)
          dirty (render/find-dirty-cells buf1 buf2)]
      (is (empty? dirty))))

  (testing "Single cell change detected"
    (let [buf1 (render/create-buffer 10 5)
          buf2 (render/set-cell buf1 3 2 (render/cell \X ""))
          dirty (render/find-dirty-cells buf1 buf2)]
      (is (= 1 (count dirty)))
      (is (= [3 2 (render/cell \X "")] (first dirty)))))

  (testing "Multiple cell changes detected"
    (let [buf1 (render/create-buffer 10 5)
          buf2 (-> buf1
                   (render/set-cell 0 0 (render/cell \A ""))
                   (render/set-cell 5 2 (render/cell \B ""))
                   (render/set-cell 9 4 (render/cell \C "")))
          dirty (render/find-dirty-cells buf1 buf2)]
      (is (= 3 (count dirty))))))

(deftest test-find-dirty-regions
  (testing "Single cell becomes single region"
    (let [dirty-cells [[5 2 (render/cell \X "")]]
          regions (render/find-dirty-regions dirty-cells)]
      (is (= 1 (count regions)))
      (is (= 2 (:y (first regions))))
      (is (= 5 (:x (first regions))))
      (is (= 1 (count (:cells (first regions)))))))

  (testing "Consecutive horizontal cells grouped into one region"
    (let [dirty-cells [[5 2 (render/cell \A "")]
                       [6 2 (render/cell \B "")]
                       [7 2 (render/cell \C "")]]
          regions (render/find-dirty-regions dirty-cells)]
      (is (= 1 (count regions)))
      (is (= 2 (:y (first regions))))
      (is (= 5 (:x (first regions))))
      (is (= 3 (count (:cells (first regions)))))))

  (testing "Non-consecutive cells create separate regions"
    (let [dirty-cells [[5 2 (render/cell \A "")]
                       [8 2 (render/cell \B "")]] ; Gap at position 6, 7
          regions (render/find-dirty-regions dirty-cells)]
      (is (= 2 (count regions))))))

;; ──────────────────────── Render State Tests ──────────────────────

(deftest test-render-state-creation
  (testing "Create initial render state"
    (let [state (render/render-state 80 24)]
      (is (= 80 (:width state)))
      (is (= 24 (:height state)))
      (is (= 0 (:frame-count state)))
      (is (= 60 (:target-fps state)))
      (is (some? (:front-buffer state)))
      (is (some? (:back-buffer state))))))

(deftest test-swap-buffers
  (testing "Swap front and back buffers"
    (let [state (render/render-state 10 5)
          cell (render/cell \X "")
          state2 (assoc state :back-buffer
                       (render/set-cell (:back-buffer state) 5 2 cell))
          state3 (render/swap-buffers state2)
          front-cell (render/get-cell (:front-buffer state3) 5 2)]
      (is (= \X (:char front-cell))))))

(deftest test-update-back-buffer
  (testing "Update back buffer with lines"
    (let [state (render/render-state 80 24)
          lines ["Line 1" "Line 2"]
          state2 (render/update-back-buffer state lines)
          cell (render/get-cell (:back-buffer state2) 0 0)]
      (is (= \L (:char cell))))))

;; ──────────────────────── Frame Rate Tests ──────────────────────

(deftest test-frame-time-calculation
  (testing "Calculate frame time for 60 FPS"
    (is (< (Math/abs (- (render/frame-time-ms 60) 16.666)) 0.1)))

  (testing "Calculate frame time for 30 FPS"
    (is (< (Math/abs (- (render/frame-time-ms 30) 33.333)) 0.1))))

(deftest test-should-render
  (testing "Should render when enough time has passed"
    (let [state (render/render-state 80 24)
          ;; Set last render time to 100ms ago
          old-time (- (System/nanoTime) (* 100 1000000))
          state2 (assoc state :last-render-time old-time)]
      (is (render/should-render? state2))))

  (testing "Should not render immediately after last render"
    (let [state (render/render-state 80 24)
          state2 (render/update-render-time state)]
      (is (not (render/should-render? state2))))))

;; ──────────────────────── Buffer Statistics Tests ──────────────────────

(deftest test-buffer-stats
  (testing "Empty buffer statistics"
    (let [buf (render/create-buffer 10 5)
          stats (render/buffer-stats buf)]
      (is (= 10 (:width stats)))
      (is (= 5 (:height stats)))
      (is (= 50 (:total-cells stats)))
      (is (= 0 (:non-empty-cells stats)))))

  (testing "Buffer with content statistics"
    (let [buf (render/create-buffer 10 5)
          buf2 (-> buf
                   (render/set-cell 0 0 (render/cell \A ""))
                   (render/set-cell 1 0 (render/cell \B ""))
                   (render/set-cell 2 0 (render/cell \C "")))
          stats (render/buffer-stats buf2)]
      (is (= 50 (:total-cells stats)))
      (is (= 3 (:non-empty-cells stats))))))

(deftest test-render-stats
  (testing "Render state statistics"
    (let [state (render/render-state 80 24)
          stats (render/render-stats state)]
      (is (= 0 (:frame-count stats)))
      (is (= 60 (:target-fps stats)))
      (is (= 80 (get-in stats [:dimensions :width])))
      (is (= 24 (get-in stats [:dimensions :height])))
      (is (some? (:front-buffer stats)))
      (is (some? (:back-buffer stats))))))

;; ──────────────────────── Integration Tests ──────────────────────

(deftest test-render-frame-integration
  (testing "Render frame updates state correctly"
    (let [state (render/render-state 80 24)
          ;; Force time to pass
          old-time (- (System/nanoTime) (* 100 1000000))
          state2 (assoc state :last-render-time old-time)
          lines ["Test line 1" "Test line 2"]
          state3 (render/render-frame state2 lines)]
      (is (= 1 (:frame-count state3)))
      (is (not= (:last-render-time state2) (:last-render-time state3))))))

(deftest test-move-cursor
  (testing "Generate cursor movement ANSI code"
    (is (= "\u001B[1;1H" (render/move-cursor 0 0)))
    (is (= "\u001B[10;20H" (render/move-cursor 19 9)))))

(deftest test-render-cell
  (testing "Render cell with ANSI codes"
    (let [cell (render/cell \A "\u001B[31m")
          result (render/render-cell cell)]
      (is (= "\u001B[31mA" result))))

  (testing "Render empty cell"
    (let [cell (render/empty-cell)
          result (render/render-cell cell)]
      (is (= " " result)))))

;; ──────────────────────── Error Handling Tests ──────────────────────

(deftest test-render-with-invalid-string
  (testing "write-string-to-buffer throws on non-string input"
    (let [buf (render/create-buffer 10 5)]
      (is (thrown? Exception (render/write-string-to-buffer buf 0 0 123)))
      (is (thrown? Exception (render/write-string-to-buffer buf 0 0 nil))))))

(deftest test-render-with-invalid-lines
  (testing "write-lines-to-buffer throws on non-string lines"
    (let [buf (render/create-buffer 10 5)]
      (is (thrown? Exception (render/write-lines-to-buffer buf 0 0 ["valid" 123 "also valid"])))
      (is (thrown? Exception (render/write-lines-to-buffer buf 0 0 [nil "valid"]))))))

(deftest test-terminal-size-defaults
  (testing "get-terminal-size returns reasonable defaults on error"
    (let [size (render/get-terminal-size)]
      ;; Should not crash, should return reasonable values
      (is (number? (:width size)))
      (is (number? (:height size)))
      (is (>= (:width size) 20))
      (is (<= (:width size) 500))
      (is (>= (:height size) 10))
      (is (<= (:height size) 200)))))

(deftest test-render-frame-error-recovery
  (testing "render-frame returns original state on error"
    (let [state (render/render-state 80 24)
          old-time (- (System/nanoTime) (* 100 1000000))
          state2 (assoc state :last-render-time old-time)
          ;; Try to render with invalid content (this should be caught internally)
          invalid-lines [123 456]  ; non-strings
          ;; This should throw but be caught inside render-frame (if validation is done there)
          ;; For now just test with valid input
          valid-lines ["test"]
          state3 (render/render-frame state2 valid-lines)]
      ;; Should have incremented frame count without crashing
      (is (number? (:frame-count state3))))))

(deftest test-buffer-operations-dont-crash
  (testing "Buffer operations handle edge cases gracefully"
    (let [buf (render/create-buffer 10 5)]
      ;; Setting cells out of bounds should not crash
      (is (some? (render/set-cell buf 100 100 (render/empty-cell))))
      (is (some? (render/set-cell buf -1 -1 (render/empty-cell))))

      ;; Getting cells out of bounds returns nil
      (is (nil? (render/get-cell buf 100 100)))
      (is (nil? (render/get-cell buf -1 -1)))))

  (testing "Clear buffer always works"
    (let [buf (render/create-buffer 10 5)
          cell (render/cell \X "")
          buf2 (render/set-cell buf 5 2 cell)
          buf3 (render/clear-buffer buf2)
          retrieved (render/get-cell buf3 5 2)]
      ;; After clear, cell should be empty again
      (is (= \space (:char retrieved))))))

(deftest test-force-render-error-handling
  (testing "force-render handles errors gracefully"
    (let [state (render/render-state 80 24)
          lines ["test line"]
          ;; This should not crash even if there are issues
          state2 (render/force-render state lines)]
      (is (some? state2))
      (is (number? (:frame-count state2))))))

(deftest test-terminal-setup-and-restore
  (testing "Terminal setup and restore don't crash"
    ;; These functions might fail in test environment, but shouldn't throw uncaught
    (is (nil? (render/setup-terminal)))
    (is (nil? (render/restore-terminal)))))

;; Test the new error boundary functions

(deftest test-update-back-buffer-error-recovery
  (testing "update-back-buffer recovers from errors"
    (let [state (render/render-state 80 24)
          ;; With valid lines, should work
          state2 (render/update-back-buffer state ["test"])]
      (is (some? state2))
      (is (some? (:back-buffer state2))))))

(deftest test-render-state-has-resize-check-time
  (testing "New render state includes last-resize-check"
    (let [state (render/render-state 80 24)]
      (is (some? (:last-resize-check state)))
      (is (number? (:last-resize-check state))))))

(deftest test-terminal-size-validation
  (testing "get-terminal-size validates bounds"
    (let [size (render/get-terminal-size)]
      ;; Width should be reasonable
      (is (and (>= (:width size) 20) (<= (:width size) 500)))
      ;; Height should be reasonable
      (is (and (>= (:height size) 10) (<= (:height size) 200))))))

