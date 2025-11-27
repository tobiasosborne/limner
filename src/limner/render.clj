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

;; ────────────────────── Terminal Operations ──────────────────────

(defn get-terminal-size
  "Get current terminal dimensions"
  []
  (try
    (let [cols (str/trim (:out (clojure.java.shell/sh "tput" "cols")))
          lines (str/trim (:out (clojure.java.shell/sh "tput" "lines")))]
      {:width (Integer/parseInt cols)
       :height (Integer/parseInt lines)})
    (catch Exception _
      {:width 80 :height 24})))

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

(defn render-state
  "Create initial render state with double buffers"
  [width height]
  {:front-buffer (create-buffer width height)
   :back-buffer (create-buffer width height)
   :width width
   :height height
   :frame-count 0
   :last-render-time (System/nanoTime)
   :target-fps 60})

(defn swap-buffers
  "Swap front and back buffers"
  [state]
  (assoc state
    :front-buffer (:back-buffer state)
    :back-buffer (:front-buffer state)))

(defn update-back-buffer
  "Update the back buffer with new content"
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
   Returns updated state"
  [state content-lines]
  (if (should-render? state)
    (let [updated-state (update-back-buffer state content-lines)
          regions-count (apply-diff (:front-buffer updated-state)
                                   (:back-buffer updated-state))
          swapped-state (swap-buffers updated-state)]
      (-> swapped-state
          (update-render-time)
          (update :frame-count inc)))
    state))

(defn force-render
  "Force a full screen render, ignoring frame rate limits
   Useful for initial render or after terminal resize"
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

(defn create-render-loop
  "Create a render loop that renders content from a state atom

   Options:
   - :fps - target frames per second (default 60)
   - :render-fn - function that takes app-state and returns vector of lines
   - :on-frame - optional callback called each frame with render-state

   Returns a map with control functions:
   - :stop! - function to stop the render loop
   - :force-render! - function to force immediate render
   - :get-stats - function to get render statistics"
  [app-state-atom & {:keys [fps render-fn on-frame]
                     :or {fps 60}}]
  (let [term-size (get-terminal-size)
        render-state (atom (render-state (:width term-size) (:height term-size)))
        running (atom true)

        render-thread
        (Thread.
          (fn []
            (try
              (setup-terminal)

              ;; Initial render
              (let [content (render-fn @app-state-atom)]
                (swap! render-state force-render content))

              ;; Main loop
              (while @running
                (let [content (render-fn @app-state-atom)]
                  (swap! render-state render-frame content)

                  (when on-frame
                    (on-frame @render-state)))

                (Thread/sleep 1))

              (finally
                (restore-terminal)))))]

    (.start render-thread)

    {:stop! (fn []
              (reset! running false)
              (.join render-thread 1000))

     :force-render! (fn []
                      (let [content (render-fn @app-state-atom)]
                        (swap! render-state force-render content)))

     :get-stats (fn []
                  {:frame-count (:frame-count @render-state)
                   :current-fps (get-fps @render-state)
                   :target-fps (:target-fps @render-state)
                   :width (:width @render-state)
                   :height (:height @render-state)})}))

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
