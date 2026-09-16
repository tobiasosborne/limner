(ns limner.render
  "Experimental render loop with diff-based updates and double buffering.

  Invalid buffer content throws `ex-info` with `:limner.render/invalid-string`,
  `:limner.render/invalid-lines`, or `:limner.render/invalid-render-output`;
  bad loop options throw `:limner.render/invalid-callback`. The render loop's
  opt-in `:on-error` is the one documented error boundary: it reports the
  error, keeps the last good frame, and never prints (ADR 0004)."
  (:require [clojure.java.shell]
            [clojure.string :as str]
            [limner.ansi :as ansi]
            [limner.core :as core]
            [limner.text :as text]
            [limner.text.style :as style]))

;; ────────────────────── Screen Buffer ──────────────────────

(defn- ensure-cluster [cluster]
  (when-not (string? cluster)
    (throw (ex-info (str "Cell cluster must be a string: " (pr-str cluster))
                    {:limner/error :limner.render/invalid-string
                     :cluster cluster :type (type cluster)}))))

(defn cell
  "Create a display cell from one grapheme cluster and a style map.

  The cell stores the ADR 0003 style as a value; it never accumulates an SGR
  string. `:width` comes from `limner.text` (ADR 0005)."
  ([cluster] (cell cluster {}))
  ([cluster style-map]
   (ensure-cluster cluster)
   {:cluster cluster
    :style (style/normalize-style (or style-map {}))
    :width (text/width cluster)}))

(defn empty-cell
  "Create an empty (space) cell."
  []
  {:cluster " " :style {} :width 1})

(defn continuation-cell
  "Create the second cell of a wide cluster; it renders nothing."
  []
  {:cluster "" :style {} :width 0})

(defn continuation?
  "True when this cell is the continuation half of a wide cluster."
  [c]
  (= "" (:cluster c)))

(defn wide?
  "True when this cell leads a wide (two-column) cluster."
  [c]
  (= 2 (:width c)))

(defn create-buffer
  "Create an empty screen buffer of the given dimensions."
  [width height]
  (when-not (and (integer? width) (<= 0 width) (integer? height) (<= 0 height))
    (throw (ex-info "Buffer dimensions must be non-negative integers"
                    {:limner/error :limner.render/invalid-dimension
                     :width width :height height})))
  {:width width
   :height height
   :cells (vec (repeat height (vec (repeat width (empty-cell)))))})

(defn get-cell
  "Get the cell at (x, y), or nil when out of bounds."
  [buffer x y]
  (when (and (>= y 0) (< y (:height buffer))
             (>= x 0) (< x (:width buffer)))
    (get-in buffer [:cells y x])))

(defn set-cell
  "Set one grid cell at (x, y).

  This is a low-level setter: it does not maintain wide-cluster
  continuations. Use `write-string-to-buffer` for grapheme-safe writes."
  [buffer x y cell]
  (if (and (>= y 0) (< y (:height buffer))
           (>= x 0) (< x (:width buffer)))
    (assoc-in buffer [:cells y x] cell)
    buffer))

(defn clear-buffer
  "Clear buffer by filling with empty cells."
  [buffer]
  (assoc buffer :cells
         (vec (repeat (:height buffer)
                      (vec (repeat (:width buffer) (empty-cell)))))))

;; ────────────────────── ANSI Helpers ──────────────────────

(defn strip-ansi
  "Remove ECMA-48 controls from a string."
  [s]
  (ansi/strip s))

;; ────────────────────── Buffer Writing ──────────────────────

(defn- clear-run
  "Clear the wide pair touching column x in a row, if any."
  [row x]
  (cond
    (or (neg? x) (>= x (count row))) row
    (continuation? (nth row x)) (-> row (assoc x (empty-cell)) (assoc (dec x) (empty-cell)))
    (wide? (nth row x)) (-> row (assoc x (empty-cell)) (assoc (inc x) (empty-cell)))
    :else row))

(defn- put-cell
  "Place `c` at column x, clearing any wide cluster it touches."
  [row x c]
  (let [row (clear-run row x)]
    (if (wide? c)
      (-> (clear-run row (inc x))
          (assoc x c)
          (assoc (inc x) (continuation-cell)))
      (assoc row x c))))

(defn- put-row-cell [buffer x y c]
  (if (and (<= 0 y) (< y (:height buffer)) (<= 0 x) (< x (:width buffer)))
    (assoc-in buffer [:cells y] (put-cell (get-in buffer [:cells y]) x c))
    buffer))

(defn- write-units [buffer x y units]
  (loop [remaining units, buffer buffer, cx x]
    (if-let [{:keys [cluster width style]} (first remaining)]
      (cond
        (zero? width)
        ;; Zero-width content attaches to the preceding cluster, or to the
        ;; following cluster when it leads the line.
        (recur (rest remaining)
               (let [lead (if (and (pos? cx) (continuation? (get-cell buffer (dec cx) y)))
                            (- cx 2) (dec cx))]
                 (if (<= 0 lead)
                   (update-in buffer [:cells y lead :cluster] str cluster)
                   buffer))
               cx)

        (= 2 width)
        (if (< (inc cx) (:width buffer))
          (recur (rest remaining) (put-row-cell buffer cx y (cell cluster style)) (+ cx 2))
          (recur (rest remaining) (put-row-cell buffer cx y (empty-cell)) (inc cx)))

        :else
        (recur (rest remaining) (put-row-cell buffer cx y (cell cluster style)) (inc cx)))
      buffer)))

(defn write-string-to-buffer
  "Write an ANSI string or styled value to the buffer at (x, y).

  Parsing, segmentation and width come from `limner.text/clusters`; a wide
  cluster occupies a lead cell and a continuation cell (ADR 0005)."
  [buffer x y s]
  (when-not (or (string? s) (vector? s))
    (throw (ex-info (str "Expected string or styled text but got " (type s) ": " (pr-str s))
                    {:limner/error :limner.render/invalid-string
                     :type (type s) :value s})))
  (write-units buffer x y (text/clusters s)))

(defn write-lines-to-buffer
  "Write multiple lines to buffer starting at (x, y).
   lines: a collection of strings or styled-text values."
  [buffer x y lines]
  (reduce
   (fn [buf [line-idx line]]
     (when-not (or (string? line) (vector? line))
       (throw (ex-info (str "Line " line-idx " is not a string: " (type line) " = " (pr-str line))
                       {:limner/error :limner.render/invalid-lines
                        :line-idx line-idx :type (type line) :value line})))
     (write-string-to-buffer buf x (+ y line-idx) line))
   buffer
   (map-indexed vector lines)))

;; ────────────────────── Diff & Render ──────────────────────

(defn cells-equal?
  "Check if two cells are equal by value (ADR 0005)."
  [cell1 cell2]
  (= cell1 cell2))

(defn find-dirty-cells
  "Find all cells that differ between old and new buffers.
   Returns a row-major sequence of [x y cell]."
  [old-buffer new-buffer]
  (for [y (range (:height new-buffer))
        x (range (:width new-buffer))
        :let [old-cell (get-cell old-buffer x y)
              new-cell (get-cell new-buffer x y)]
        :when (not (cells-equal? old-cell new-cell))]
    [x y new-cell]))

(defn- ascending-x? [cells]
  (every? (fn [[[x1] [x2]]] (< x1 x2))
          (partition 2 1 (map (fn [cell] [(first cell)]) cells))))

(defn- regions-for-row [y cells]
  (let [ordered (if (ascending-x? cells) cells (sort-by first cells))]
    (loop [remaining ordered, region nil, regions []]
      (if-let [[x _ c] (first remaining)]
        (if (and region (= (inc (:x-end region)) x))
          (recur (rest remaining)
                 (-> region (update :cells conj c) (assoc :x-end x))
                 regions)
          (recur (rest remaining)
                 {:y y :x x :cells [c] :x-end x}
                 (cond-> regions region (conj (dissoc region :x-end)))))
        (cond-> regions region (conj (dissoc region :x-end)))))))

(defn find-dirty-regions
  "Group dirty cells into contiguous horizontal regions.

  One grouping pass plus a per-row scan; there is no per-cell search of the
  row, so the old quadratic `.indexOf` path is gone. Returns a sequence of
  `{:y row :x start-col :cells [cell ...]}`."
  [dirty-cells]
  (->> dirty-cells
       (group-by second)
       (sort-by key)
       (mapcat (fn [[y row-cells]] (regions-for-row y row-cells)))))

(defn move-cursor
  "Generate ANSI code to move cursor to position (x, y)
   Terminal coordinates are 1-based"
  [x y]
  (str "\u001B[" (inc y) ";" (inc x) "H"))

(defn render-cell
  "Render a single cell's cluster and style; a continuation renders nothing."
  [c]
  (if (continuation? c)
    ""
    (text/to-ansi [{:text (:cluster c) :style (:style c)}])))

(defn render-region
  "Render a dirty region with minimal SGR transitions.

  `limner.text/to-ansi` emits only the style changes and returns the SGR state
  to default, so no cell stores an escape string and no region leaks style."
  [{:keys [x y cells]}]
  (let [styled (reduce (fn [spans c]
                         (if (continuation? c)
                           spans
                           (conj spans {:text (:cluster c) :style (:style c)})))
                       [] cells)]
    (str (move-cursor x y)
         (text/to-ansi styled))))

(defn render-dirty-regions
  "Render all dirty regions to terminal without flushing"
  [regions]
  (doseq [region regions]
    (print (render-region region))))

(defn apply-diff
  "Find differences between buffers and render only changed regions.

  Returns the number of regions rendered."
  [old-buffer new-buffer]
  (let [dirty-cells (find-dirty-cells old-buffer new-buffer)
        regions (->> dirty-cells
                     find-dirty-regions
                     (filter (fn [region]
                               (some (complement continuation?) (:cells region)))))]
    (when (seq regions)
      (render-dirty-regions regions)
      (flush))
    (count regions)))

;; ────────────────────── Error Handling & Validation ──────────────────────

(defn- validate-render-output
  "Validate that render function output is a collection of strings.

  Invalid output throws `ex-info` with
  `:limner.render/invalid-render-output`; the render loop's boundary reports
  it and keeps the last good frame (ADR 0004)."
  [output context]
  (cond
    (nil? output)
    (throw (ex-info (str context " returned nil")
                    {:limner/error :limner.render/invalid-render-output
                     :context context :output output}))

    (not (sequential? output))
    (throw (ex-info (str context " returned non-sequential output: " (type output))
                    {:limner/error :limner.render/invalid-render-output
                     :context context :output output :type (type output)}))

    :else
    (mapv (fn [line]
            (if (string? line)
              line
              (throw (ex-info (str "Non-string line in render output: " (type line))
                              {:limner/error :limner.render/invalid-render-output
                               :context context :line line :type (type line)}))))
          output)))

(defn- safe-render-fn
  "Call render function and validate its output.

  This is no longer an error boundary: errors reach the render loop, whose
  opt-in `:on-error` callback reports them and keeps the last good frame.
  Bead limner-2-3 owns the final loop shape."
  [render-fn state context]
  (validate-render-output (render-fn state) context))

;; ────────────────────── Terminal Operations ──────────────────────

(defn- terminal-size-from-system
  "Read terminal dimensions with stty or tput for the production size source."
  []
  (letfn [(try-stty []
            ;; Try stty size which works better with actual terminals
            (try
              (let [result (clojure.java.shell/sh "sh" "-c" "stty size </dev/tty 2>/dev/null")
                    output (str/trim (:out result))]
                (when (seq output)
                  (let [[rows cols] (str/split output #"\s+")]
                    {:width (Integer/parseInt cols)
                     :height (Integer/parseInt rows)})))
              (catch Exception _ nil)))

          (try-tput []
            ;; Fallback to tput
            (try
              (let [cols-result (clojure.java.shell/sh "sh" "-c" "tput cols </dev/tty 2>/dev/null || tput cols")
                    lines-result (clojure.java.shell/sh "sh" "-c" "tput lines </dev/tty 2>/dev/null || tput lines")
                    cols (str/trim (:out cols-result))
                    lines (str/trim (:out lines-result))]
                (when (and (seq cols) (seq lines))
                    {:width (Integer/parseInt cols)
                     :height (Integer/parseInt lines)}))
              (catch Exception _ nil)))]
    (or (try-stty) (try-tput))))

(defn- validate-terminal-size
  "Apply the renderer's current lower fallbacks and upper bounds to a size."
  [{:keys [width height] :as size}]
  (when size
    (let [w (cond (< width 20) 80 (> width 500) 500 :else width)
          h (cond (< height 10) 24 (> height 200) 200 :else height)]
      {:width w :height h})))

(defn get-terminal-size
  "Get validated terminal dimensions as {:width w :height h}.

   With no argument, the production source tries stty and then tput. The
   optional zero-argument size-source is the effects seam for tests; it returns
   a size map or nil. Source errors and nil fall back to 80 by 24."
  ([]
   (get-terminal-size terminal-size-from-system))
  ([size-source]
   (try
     (or (validate-terminal-size (size-source))
         {:width 80 :height 24})
     (catch Exception _
       {:width 80 :height 24}))))

(defn clear-screen
  "Clear entire screen and move cursor to home"
  []
  (print "\u001B[2J\u001B[H")
  (flush))

(defn setup-terminal
  "Setup terminal for rendering (hide cursor, clear screen)"
  []
  (core/hide-cursor)
  (clear-screen))

(defn restore-terminal
  "Restore terminal to normal state"
  []
  (core/show-cursor)
  (print "\u001B[0m") ; Reset colors
  (flush))

;; ────────────────────── Render State ──────────────────────

(defn- system-clock-ns
  "Return monotonic nanoseconds for the production render clock."
  []
  (System/nanoTime))

(defn- current-time-ns
  "Read the render state's injected clock, retaining the production default for
   state maps created before clock injection was available."
  [state]
  ((or (:clock state) system-clock-ns)))

(defn render-state
  "Create initial render state with double buffers.

   The optional :clock is a zero-argument monotonic nanosecond function. It
   defaults to System/nanoTime and is retained in the state so frame and resize
   decisions can use a deterministic clock in tests."
  [width height & {:keys [clock]
                   :or {clock system-clock-ns}}]
  (let [now (clock)]
    {:front-buffer (create-buffer width height)
     :back-buffer (create-buffer width height)
     :width width
     :height height
     :frame-count 0
     :clock clock
     :last-render-time now
     :last-resize-check now
     :target-fps 60}))

(defn swap-buffers
  "Swap front and back buffers"
  [state]
  (assoc state
    :front-buffer (:back-buffer state)
    :back-buffer (:front-buffer state)))

(defn update-back-buffer
  "Update the back buffer with new content.

  Invalid content throws (ADR 0004); the render loop decides how to recover."
  [state content-lines]
  (let [clean-buffer (clear-buffer (:back-buffer state))
        updated-buffer (write-lines-to-buffer clean-buffer 0 0 content-lines)]
    (assoc state :back-buffer updated-buffer)))

;; ────────────────────── Frame Rate Control ──────────────────────

(defn frame-time-ms
  "Calculate target frame time in milliseconds for given FPS"
  [fps]
  (/ 1000.0 fps))

(defn should-render?
  "Check against the render state's clock whether the next frame is due."
  [state]
  (let [now (current-time-ns state)
        elapsed-ms (/ (- now (:last-render-time state)) 1000000.0)
        target-ms (frame-time-ms (:target-fps state))]
    (>= elapsed-ms target-ms)))

(defn update-render-time
  "Update the last render timestamp from the render state's clock."
  [state]
  (assoc state :last-render-time (current-time-ns state)))

(defn- should-check-resize?
  "Check if enough time has passed to check for terminal resize
   Checks every 500ms to avoid excessive polling"
  [state]
  (let [now (current-time-ns state)
        elapsed (- now (:last-resize-check state 0))]
    (>= elapsed (* 500 1000000))))

(defn- resize-buffers
  "Resize buffers to new dimensions, preserving what we can
   Returns updated state with resized buffers"
  [state new-width new-height]
  (let [old-width (:width state)
        old-height (:height state)]
    (if (and (= new-width old-width) (= new-height old-height))
      ;; No change needed
      state
      ;; Resize needed
      (assoc state
             :width new-width
             :height new-height
             :front-buffer (create-buffer new-width new-height)
             :back-buffer (create-buffer new-width new-height)
             :last-resize-check (current-time-ns state)))))

(defn- check-and-handle-resize
  "Check for terminal resize and handle it if detected
   Returns [state resized?] where resized? indicates if resize occurred"
  [state]
  (if (should-check-resize? state)
    (let [current-size (get-terminal-size)
          new-width (:width current-size)
          new-height (:height current-size)]
      (if (or (not= new-width (:width state))
              (not= new-height (:height state)))
        ;; Resize detected
        [(resize-buffers state new-width new-height) true]
        ;; No resize, just update check time
        [(assoc state :last-resize-check (current-time-ns state)) false]))
    ;; Not time to check yet
    [state false]))

(defn get-fps
  "Calculate actual FPS based on last render time"
  [state]
  (let [now (current-time-ns state)
        elapsed-ms (/ (- now (:last-render-time state)) 1000000.0)]
    (if (pos? elapsed-ms)
      (int (/ 1000.0 elapsed-ms))
      0)))

;; ────────────────────── High-Level Render Functions ──────────────────────

(defn render-frame
  "Render a single frame with diff-based updates
   content-lines: vector of strings to render
   Returns updated state

   Invalid content throws (ADR 0004); the render loop reports the error and
   keeps the last good frame."
  [state content-lines]
  (if (should-render? state)
    (let [updated-state (update-back-buffer state content-lines)
          _ (apply-diff (:front-buffer updated-state)
                        (:back-buffer updated-state))
          swapped-state (swap-buffers updated-state)]
      (-> swapped-state
          (update-render-time)
          (update :frame-count inc)))
    state))

(defn force-render
  "Force a full screen render, ignoring frame rate limits
   Useful for initial render or after terminal resize

   Invalid content throws (ADR 0004)."
  [state content-lines]
  (clear-screen)
  (let [clean-front (assoc state :front-buffer
                           (clear-buffer (:front-buffer state)))
        updated-state (update-back-buffer clean-front content-lines)
        _ (apply-diff (:front-buffer updated-state)
                      (:back-buffer updated-state))
        swapped-state (swap-buffers updated-state)]
    (-> swapped-state
        (update-render-time)
        (update :frame-count inc))))

;; ────────────────────── Render Loop ──────────────────────

(defn- calculate-sleep-time
  "Calculate how long to sleep to maintain target FPS"
  [target-fps last-frame-time]
  (let [target-frame-time (/ 1000.0 target-fps)
        elapsed (- (System/currentTimeMillis) last-frame-time)
        sleep-time (- target-frame-time elapsed)]
    (max 1 (long sleep-time))))

(defn- report-render-error!
  "Render-loop error boundary (ADR 0004): record the error, call the opt-in
  :on-error callback, and never print. TODO(limner-2-3): bead 2.3 finalises the
  single allowed library error boundary."
  [error-atom on-error error]
  (reset! error-atom error)
  (when on-error
    (try
      (on-error error)
      (catch Exception callback-error
        ;; TODO(limner-2-3): a broken callback must not kill the loop; record it.
        (reset! error-atom callback-error)))))

(defn create-render-loop
  "Create a render loop that renders content from a state atom

   Options:
   - :fps - target frames per second (default 60)
   - :render-fn - function that takes app-state and returns vector of lines
   - :on-frame - optional callback called each frame with render-state
   - :on-error - opt-in error boundary (fn [exception] ...); the loop keeps its
     last good frame and does not print (ADR 0004)

   Returns a map with control functions:
   - :stop! - function to stop the render loop (blocks until stopped)
   - :force-render! - function to force immediate render
   - :get-stats - function to get render statistics
   - :running? - function to check if loop is still running"
  [app-state-atom & {:keys [fps render-fn on-frame on-error]
                     :or {fps 60}}]
  (when-not (fn? render-fn)
    (throw (ex-info "create-render-loop requires a :render-fn function"
                    {:limner/error :limner.render/invalid-callback
                     :render-fn render-fn})))
  (when-not (or (nil? on-frame) (fn? on-frame))
    (throw (ex-info "create-render-loop :on-frame must be a function"
                    {:limner/error :limner.render/invalid-callback :on-frame on-frame})))
  (when-not (or (nil? on-error) (fn? on-error))
    (throw (ex-info "create-render-loop :on-error must be a function"
                    {:limner/error :limner.render/invalid-callback :on-error on-error})))

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

            ;; Initial render, through the opt-in boundary
            (try
              (let [content (safe-render-fn render-fn @app-state-atom "render-fn (initial)")]
                (swap! render-state force-render content))
              (catch Exception e
                (report-render-error! error-atom on-error e)))

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

                  ;; Render frame, then the optional frame callback
                  (let [content (safe-render-fn render-fn @app-state-atom "render-fn")]
                    (swap! render-state render-frame content)
                    (when on-frame
                      (on-frame @render-state)))

                  ;; The opt-in boundary: report, keep the last good frame
                  (catch Exception e
                    (report-render-error! error-atom on-error e)))

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
                  ;; TODO(limner-2-3): record the stop timeout in loop stats.
                  ;; Force cancellation as last resort
                  (future-cancel render-future))
                ;; Re-throw any errors that occurred
                (when-let [error @error-atom]
                  (throw error))))

     :force-render! (fn []
                      (try
                        (let [content (safe-render-fn render-fn @app-state-atom "render-fn (force)")]
                          (swap! render-state force-render content))
                        (catch Exception e
                          (report-render-error! error-atom on-error e))))

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
  "Get statistics about a buffer.

  A cell is non-empty when its cluster is not a space; continuation cells of a
  wide cluster are not counted."
  [buffer]
  {:width (:width buffer)
   :height (:height buffer)
   :total-cells (* (:width buffer) (:height buffer))
   :non-empty-cells (count (filter #(not= " " (:cluster %))
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
