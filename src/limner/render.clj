(ns limner.render
  "Efficient render loop with diff-based updates and double buffering"
  (:require [clojure.string :as str]
            [limner.core :as core]))

;; ────────────────────── Screen Buffer ──────────────────────

(defn cell
  "Create a cell with character and ANSI codes"
  [char ansi-codes]
  {:char char :ansi ansi-codes})

(defn empty-cell
  "Create an empty cell (space with no formatting)"
  []
  (cell \space ""))

(defn create-buffer
  "Create an empty screen buffer of given dimensions"
  [width height]
  {:width width
   :height height
   :cells (vec (repeat height (vec (repeat width (empty-cell)))))})

(defn get-cell
  "Get cell at position (x, y) in buffer"
  [buffer x y]
  (when (and (>= y 0) (< y (:height buffer))
             (>= x 0) (< x (:width buffer)))
    (get-in buffer [:cells y x])))

(defn set-cell
  "Set cell at position (x, y) in buffer"
  [buffer x y cell]
  (if (and (>= y 0) (< y (:height buffer))
           (>= x 0) (< x (:width buffer)))
    (assoc-in buffer [:cells y x] cell)
    buffer))

(defn clear-buffer
  "Clear buffer by filling with empty cells"
  [buffer]
  (assoc buffer :cells
    (vec (repeat (:height buffer)
                 (vec (repeat (:width buffer) (empty-cell)))))))

;; ────────────────────── ANSI Code Extraction ──────────────────────

(def ansi-pattern
  "Regex pattern to match ANSI escape sequences"
  #"\u001B\[[0-9;]*m")

(defn extract-ansi-state
  "Extract accumulated ANSI codes from a string up to position"
  [s]
  (let [matches (re-seq ansi-pattern s)]
    (if (seq matches)
      (str/join matches)
      "")))

(defn strip-ansi
  "Remove ANSI codes from string"
  [s]
  (str/replace s ansi-pattern ""))

(defn parse-line-with-ansi
  "Parse a line with ANSI codes into a sequence of cells
   Returns vector of {:char c :ansi \"codes\"}"
  [line]
  (let [chars (vec line)
        result (atom [])
        current-ansi (atom "")
        i (atom 0)]

    (while (< @i (count chars))
      (let [remaining (subs line @i)
            ansi-match (re-find ansi-pattern remaining)]

        (if (and ansi-match (= @i (.indexOf line ansi-match @i)))
          ;; Found ANSI code at current position
          (do
            (swap! current-ansi str ansi-match)
            (swap! i + (count ansi-match)))

          ;; Regular character
          (let [ch (nth chars @i)]
            (swap! result conj (cell ch @current-ansi))
            (swap! i inc)))))

    @result))

;; ────────────────────── Buffer Writing ──────────────────────

(defn write-string-to-buffer
  "Write a string (with ANSI codes) to buffer at position (x, y)"
  [buffer x y s]
  (when-not (string? s)
    (throw (ex-info (str "Expected string but got " (type s) ": " (pr-str s))
                    {:type (type s) :value s})))
  (let [cells (parse-line-with-ansi s)]
    (reduce
      (fn [buf [idx cell]]
        (set-cell buf (+ x idx) y cell))
      buffer
      (map-indexed vector cells))))

(defn write-lines-to-buffer
  "Write multiple lines to buffer starting at (x, y)
   lines: must be a collection of strings"
  [buffer x y lines]
  (reduce
    (fn [buf [line-idx line]]
      (when-not (string? line)
        (throw (ex-info (str "Line " line-idx " is not a string: " (type line) " = " (pr-str line))
                        {:line-idx line-idx :type (type line) :value line})))
      (write-string-to-buffer buf x (+ y line-idx) line))
    buffer
    (map-indexed vector lines)))

;; ────────────────────── Diff & Render ──────────────────────

(defn cells-equal?
  "Check if two cells are equal"
  [cell1 cell2]
  (and (= (:char cell1) (:char cell2))
       (= (:ansi cell1) (:ansi cell2))))

(defn find-dirty-cells
  "Find all cells that differ between old and new buffers
   Returns sequence of [x y cell]"
  [old-buffer new-buffer]
  (for [y (range (:height new-buffer))
        x (range (:width new-buffer))
        :let [old-cell (get-cell old-buffer x y)
              new-cell (get-cell new-buffer x y)]
        :when (not (cells-equal? old-cell new-cell))]
    [x y new-cell]))

(defn find-dirty-regions
  "Group dirty cells into contiguous horizontal regions
   Returns sequence of {:y row :x start-col :cells [cell...]}
   This is more efficient than updating individual cells"
  [dirty-cells]
  (let [by-row (group-by second dirty-cells)]
    (for [[y row-cells] (sort-by key by-row)
          :let [sorted-cells (sort-by first row-cells)]
          region (partition-by
                   (fn [[x _ _]]
                     ;; Group consecutive x positions together
                     (- x (.indexOf (map first sorted-cells) x)))
                   sorted-cells)]
      (let [cells (vec region)
            start-x (first (first cells))]
        {:y y
         :x start-x
         :cells (mapv #(nth % 2) cells)}))))

(defn move-cursor
  "Generate ANSI code to move cursor to position (x, y)
   Terminal coordinates are 1-based"
  [x y]
  (str "\u001B[" (inc y) ";" (inc x) "H"))

(defn render-cell
  "Render a single cell to a string with ANSI codes"
  [cell]
  (str (:ansi cell) (:char cell)))

(defn render-region
  "Render a dirty region to the terminal"
  [{:keys [x y cells]}]
  (str (move-cursor x y)
       (str/join (map render-cell cells))
       "\u001B[0m")) ; Reset ANSI codes after region

(defn render-dirty-regions
  "Render all dirty regions to terminal without flushing"
  [regions]
  (doseq [region regions]
    (print (render-region region))))

(defn apply-diff
  "Find differences between buffers and render only changed regions"
  [old-buffer new-buffer]
  (let [dirty-cells (find-dirty-cells old-buffer new-buffer)
        regions (find-dirty-regions dirty-cells)]
    (when (seq regions)
      (render-dirty-regions regions)
      (flush))
    (count regions)))

;; ────────────────────── Error Handling & Validation ──────────────────────

(defn- validate-render-output
  "Validate that render function output is a collection of strings"
  [output context]
  (cond
    (nil? output)
    (do
      (binding [*out* *err*]
        (println (str "Warning: " context " returned nil, using empty output")))
      [])

    (not (sequential? output))
    (do
      (binding [*out* *err*]
        (println (str "Warning: " context " returned non-sequential output: " (type output))))
      [(str output)])

    :else
    (try
      (mapv (fn [line]
              (if (string? line)
                line
                (do
                  (binding [*out* *err*]
                    (println (str "Warning: Non-string line in render output: " (type line))))
                  (str line))))
            output)
      (catch Exception e
        (binding [*out* *err*]
          (println (str "Error validating render output: " (.getMessage e))))
        ["[Render validation error]"]))))

(defn- safe-render-fn
  "Safely call render function with error boundary"
  [render-fn state context]
  (try
    (let [output (render-fn state)]
      (validate-render-output output context))
    (catch Exception e
      (binding [*out* *err*]
        (println (str "Error in " context ": " (.getMessage e))))
      [(str "╔════════════════════════════════════════╗")
       (str "║  RENDER ERROR                          ║")
       (str "║                                        ║")
       (str "║  " (subs (str (.getMessage e) "                                  ") 0 36) "  ║")
       (str "║                                        ║")
       (str "║  Check logs for details                ║")
       (str "╚════════════════════════════════════════╝")])))

(defn- safe-buffer-operation
  "Safely perform buffer operation with error recovery"
  [operation buffer fallback-fn context]
  (try
    (operation buffer)
    (catch Exception e
      (binding [*out* *err*]
        (println (str "Error in buffer operation (" context "): " (.getMessage e))))
      (fallback-fn buffer e))))

;; ────────────────────── Terminal Operations ──────────────────────

(defn get-terminal-size
  "Get current terminal dimensions with error handling and validation"
  []
  (try
    (let [result (clojure.java.shell/sh "tput" "cols")
          cols (str/trim (:out result))
          result2 (clojure.java.shell/sh "tput" "lines")
          lines (str/trim (:out result2))]
      (when (or (seq (:err result)) (seq (:err result2)))
        (binding [*out* *err*]
          (println "Warning: tput command had errors, using defaults")))
      (let [raw-width (try (Integer/parseInt cols) (catch Exception _ 80))
            raw-height (try (Integer/parseInt lines) (catch Exception _ 24))
            ;; Validate and clamp to reasonable bounds
            width (cond
                    (< raw-width 20) (do (binding [*out* *err*]
                                           (println (str "Warning: Terminal width " raw-width " too small, using 80")))
                                         80)
                    (> raw-width 500) (do (binding [*out* *err*]
                                            (println (str "Warning: Terminal width " raw-width " too large, using 500")))
                                          500)
                    :else raw-width)
            height (cond
                     (< raw-height 10) (do (binding [*out* *err*]
                                             (println (str "Warning: Terminal height " raw-height " too small, using 24")))
                                           24)
                     (> raw-height 200) (do (binding [*out* *err*]
                                              (println (str "Warning: Terminal height " raw-height " too large, using 200")))
                                            200)
                     :else raw-height)]
        {:width width :height height}))
    (catch Exception e
      (binding [*out* *err*]
        (println (str "Error getting terminal size: " (.getMessage e) ", using defaults")))
      {:width 80 :height 24})))

(defn clear-screen
  "Clear entire screen and move cursor to home"
  []
  (print "\u001B[2J\u001B[H")
  (flush))

(defn setup-terminal
  "Setup terminal for rendering (hide cursor, clear screen)"
  []
  (try
    (core/hide-cursor)
    (clear-screen)
    (catch Exception e
      (binding [*out* *err*]
        (println (str "Error setting up terminal: " (.getMessage e)))))))

(defn restore-terminal
  "Restore terminal to normal state"
  []
  (try
    (core/show-cursor)
    (print "\u001B[0m") ; Reset colors
    (flush)
    (catch Exception e
      (binding [*out* *err*]
        (println (str "Error restoring terminal: " (.getMessage e)))))))

;; ────────────────────── Render State ──────────────────────

(defn render-state
  "Create initial render state with double buffers"
  [width height]
  {:front-buffer (create-buffer width height)
   :back-buffer (create-buffer width height)
   :width width
   :height height
   :frame-count 0
   :last-render-time (System/nanoTime)
   :last-resize-check (System/currentTimeMillis)
   :target-fps 60})

(defn swap-buffers
  "Swap front and back buffers"
  [state]
  (assoc state
    :front-buffer (:back-buffer state)
    :back-buffer (:front-buffer state)))

(defn update-back-buffer
  "Update the back buffer with new content, with error recovery"
  [state content-lines]
  (try
    (let [clean-buffer (clear-buffer (:back-buffer state))
          updated-buffer (write-lines-to-buffer clean-buffer 0 0 content-lines)]
      (assoc state :back-buffer updated-buffer))
    (catch Exception e
      (binding [*out* *err*]
        (println (str "Error updating back buffer: " (.getMessage e))))
      ;; Return state with cleared buffer on error
      (assoc state :back-buffer (clear-buffer (:back-buffer state))))))

;; ────────────────────── Frame Rate Control ──────────────────────

(defn frame-time-ms
  "Calculate target frame time in milliseconds for given FPS"
  [fps]
  (/ 1000.0 fps))

(defn should-render?
  "Check if enough time has passed to render next frame"
  [state]
  (let [now (System/nanoTime)
        elapsed-ms (/ (- now (:last-render-time state)) 1000000.0)
        target-ms (frame-time-ms (:target-fps state))]
    (>= elapsed-ms target-ms)))

(defn update-render-time
  "Update last render time to now"
  [state]
  (assoc state :last-render-time (System/nanoTime)))

(defn- should-check-resize?
  "Check if enough time has passed to check for terminal resize
   Checks every 500ms to avoid excessive polling"
  [state]
  (let [now (System/currentTimeMillis)
        elapsed (- now (:last-resize-check state 0))]
    (>= elapsed 500)))

(defn- resize-buffers
  "Resize buffers to new dimensions, preserving what we can
   Returns updated state with resized buffers"
  [state new-width new-height]
  (try
    (let [old-width (:width state)
          old-height (:height state)]
      (if (and (= new-width old-width) (= new-height old-height))
        ;; No change needed
        state
        ;; Resize needed
        (do
          (binding [*out* *err*]
            (println (str "Terminal resized: " old-width "x" old-height
                         " → " new-width "x" new-height)))
          (assoc state
            :width new-width
            :height new-height
            :front-buffer (create-buffer new-width new-height)
            :back-buffer (create-buffer new-width new-height)
            :last-resize-check (System/currentTimeMillis)))))
    (catch Exception e
      (binding [*out* *err*]
        (println (str "Error resizing buffers: " (.getMessage e))))
      ;; Return original state on error
      (assoc state :last-resize-check (System/currentTimeMillis)))))

(defn- check-and-handle-resize
  "Check for terminal resize and handle it if detected
   Returns [state resized?] where resized? indicates if resize occurred"
  [state]
  (if (should-check-resize? state)
    (try
      (let [current-size (get-terminal-size)
            new-width (:width current-size)
            new-height (:height current-size)]
        (if (or (not= new-width (:width state))
                (not= new-height (:height state)))
          ;; Resize detected
          [(resize-buffers state new-width new-height) true]
          ;; No resize, just update check time
          [(assoc state :last-resize-check (System/currentTimeMillis)) false]))
      (catch Exception e
        (binding [*out* *err*]
          (println (str "Error checking terminal size: " (.getMessage e))))
        ;; Return original state on error
        [(assoc state :last-resize-check (System/currentTimeMillis)) false]))
    ;; Not time to check yet
    [state false]))

(defn get-fps
  "Calculate actual FPS based on last render time"
  [state]
  (let [now (System/nanoTime)
        elapsed-ms (/ (- now (:last-render-time state)) 1000000.0)]
    (if (pos? elapsed-ms)
      (int (/ 1000.0 elapsed-ms))
      0)))

;; ────────────────────── High-Level Render Functions ──────────────────────

(defn render-frame
  "Render a single frame with diff-based updates
   content-lines: vector of strings to render
   Returns updated state

   Error handling: Catches all exceptions and returns original state on error"
  [state content-lines]
  (if (should-render? state)
    (try
      (let [updated-state (update-back-buffer state content-lines)
            regions-count (apply-diff (:front-buffer updated-state)
                                     (:back-buffer updated-state))
            swapped-state (swap-buffers updated-state)]
        (-> swapped-state
            (update-render-time)
            (update :frame-count inc)))
      (catch Exception e
        (binding [*out* *err*]
          (println (str "Error rendering frame: " (.getMessage e))))
        state))
    state))

(defn force-render
  "Force a full screen render, ignoring frame rate limits
   Useful for initial render or after terminal resize

   Error handling: Catches exceptions and attempts partial render"
  [state content-lines]
  (try
    (clear-screen)
    (let [clean-front (assoc state :front-buffer
                            (clear-buffer (:front-buffer state)))
          updated-state (update-back-buffer clean-front content-lines)
          _ (apply-diff (:front-buffer updated-state)
                       (:back-buffer updated-state))
          swapped-state (swap-buffers updated-state)]
      (-> swapped-state
          (update-render-time)
          (update :frame-count inc)))
    (catch Exception e
      (binding [*out* *err*]
        (println (str "Error in force-render: " (.getMessage e))))
      ;; Attempt to at least clear screen on error
      (try
        (clear-screen)
        (catch Exception _
          (binding [*out* *err*]
            (println "Error: Could not clear screen"))))
      state)))

;; ────────────────────── Render Loop ──────────────────────

(defn- calculate-sleep-time
  "Calculate how long to sleep to maintain target FPS"
  [target-fps last-frame-time]
  (let [target-frame-time (/ 1000.0 target-fps)
        elapsed (- (System/currentTimeMillis) last-frame-time)
        sleep-time (- target-frame-time elapsed)]
    (max 1 (long sleep-time))))

(defn create-render-loop
  "Create a render loop that renders content from a state atom

   Options:
   - :fps - target frames per second (default 60)
   - :render-fn - function that takes app-state and returns vector of lines
   - :on-frame - optional callback called each frame with render-state
   - :on-error - optional error handler (fn [exception] ...)

   Returns a map with control functions:
   - :stop! - function to stop the render loop (blocks until stopped)
   - :force-render! - function to force immediate render
   - :get-stats - function to get render statistics
   - :running? - function to check if loop is still running

   Thread Safety:
   - Uses future for managed concurrency
   - Proper shutdown coordination via promise
   - Error handling with optional callback
   - Graceful cleanup on shutdown"
  [app-state-atom & {:keys [fps render-fn on-frame on-error]
                     :or {fps 60}}]
  {:pre [(fn? render-fn)
         (or (nil? on-frame) (fn? on-frame))
         (or (nil? on-error) (fn? on-error))]}

  (let [term-size (get-terminal-size)
        render-state (atom (render-state (:width term-size) (:height term-size)))
        running (atom true)
        shutdown-promise (promise)
        error-atom (atom nil)

        ;; Use future instead of raw Thread for managed concurrency
        render-future
        (future
          (try
            (setup-terminal)

            ;; Initial render with error boundary
            (let [content (safe-render-fn render-fn @app-state-atom "render-fn (initial)")]
              (swap! render-state force-render content))

            ;; Main loop
            (loop [last-frame-time (System/currentTimeMillis)]
              (when @running
                (try
                  ;; Check for terminal resize
                  (let [[new-state resized?] (check-and-handle-resize @render-state)]
                    (reset! render-state new-state)

                    ;; If resized, force full re-render
                    (when resized?
                      (let [content (safe-render-fn render-fn @app-state-atom "render-fn (after resize)")]
                        (swap! render-state force-render content))))

                  ;; Render frame with error boundary
                  (let [content (safe-render-fn render-fn @app-state-atom "render-fn")]
                    (swap! render-state render-frame content)

                    ;; Optional frame callback
                    (when on-frame
                      (try
                        (on-frame @render-state)
                        (catch Exception callback-error
                          ;; Don't let frame callback crash the loop
                          (binding [*out* *err*]
                            (println "Error in on-frame callback:" callback-error))))))

                  ;; Handle render loop errors
                  (catch Exception e
                    (reset! error-atom e)
                    (binding [*out* *err*]
                      (println "Fatal error in render loop:" (.getMessage e)))
                    (when on-error
                      (try
                        (on-error e)
                        (catch Exception callback-error
                          ;; Don't let error handler crash the loop
                          (binding [*out* *err*]
                            (println "Error in on-error callback:" callback-error)))))))

                ;; Sleep to maintain target FPS
                (let [sleep-time (calculate-sleep-time fps last-frame-time)]
                  (Thread/sleep sleep-time))

                ;; Continue loop
                (recur (System/currentTimeMillis))))

            ;; Cleanup
            (finally
              (restore-terminal)
              (deliver shutdown-promise true))))]

    ;; Return control map
    {:stop! (fn []
              (reset! running false)
              ;; Wait for clean shutdown with timeout
              (let [result (deref shutdown-promise 2000 :timeout)]
                (when (= result :timeout)
                  (binding [*out* *err*]
                    (println "Warning: Render loop did not stop within 2 seconds"))
                  ;; Force cancellation as last resort
                  (future-cancel render-future))
                ;; Re-throw any errors that occurred
                (when-let [error @error-atom]
                  (throw error))))

     :force-render! (fn []
                      (let [content (safe-render-fn render-fn @app-state-atom "render-fn (force)")]
                        (swap! render-state force-render content)))

     :get-stats (fn []
                  {:frame-count (:frame-count @render-state)
                   :current-fps (get-fps @render-state)
                   :target-fps fps
                   :width (:width @render-state)
                   :height (:height @render-state)
                   :running @running
                   :error @error-atom})

     :running? (fn []
                 @running)

     :future render-future}))

;; ────────────────────── Simple Render Function ──────────────────────

(defn render-once
  "Render content once without a loop (useful for testing)
   content-lines: vector of strings"
  [content-lines]
  (let [term-size (get-terminal-size)
        state (render-state (:width term-size) (:height term-size))]
    (setup-terminal)
    (force-render state content-lines)
    (restore-terminal)))

;; ────────────────────── Statistics & Debugging ──────────────────────

(defn buffer-stats
  "Get statistics about a buffer"
  [buffer]
  {:width (:width buffer)
   :height (:height buffer)
   :total-cells (* (:width buffer) (:height buffer))
   :non-empty-cells (count (filter #(not= (:char %) \space)
                                  (apply concat (:cells buffer))))})

(defn render-stats
  "Get detailed render state statistics"
  [state]
  {:frame-count (:frame-count state)
   :current-fps (get-fps state)
   :target-fps (:target-fps state)
   :dimensions {:width (:width state) :height (:height state)}
   :front-buffer (buffer-stats (:front-buffer state))
   :back-buffer (buffer-stats (:back-buffer state))})
