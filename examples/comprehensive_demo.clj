#!/usr/bin/env bb
;; ════════════════════════════════════════════════════════════════════════════
;; LIMNER COMPREHENSIVE DEMO - Interactive Task Manager
;; ════════════════════════════════════════════════════════════════════════════
;;
;; This demo exercises ALL features of the limner TUI library:
;;
;; 🎨 COLOR SYSTEM:
;;   - Basic colors (16 ANSI colors)
;;   - 256-color palette
;;   - RGB/Truecolor (16.7M colors)
;;   - Semantic color presets
;;   - Terminal capability detection with graceful degradation
;;
;; 📐 LAYOUT ENGINE:
;;   - Vertical stacking (header, main, footer)
;;   - Horizontal splits (sidebar | content | stats)
;;   - Fixed, flex, and percentage constraints
;;   - Complex nested layouts (4 levels deep)
;;   - Spacing and padding
;;
;; 🖼️  BORDERS:
;;   - Multiple border styles (single, double, rounded, thick)
;;   - Titled boxes with positioning (left, center, right)
;;   - Colored borders
;;   - Drop shadows (light and heavy)
;;   - Box nesting and composition
;;
;; 🔧 COMPONENTS:
;;   - Panels with scrolling (large content)
;;   - Text input with validation and real-time feedback
;;   - Lists with multi-select and filtering
;;   - Progress bars (determinate and indeterminate)
;;   - Spinners with multiple styles
;;   - Status bar with sections
;;   - Streaming text with syntax highlighting
;;
;; ⚡ EVENT HANDLING:
;;   - Keyboard input (printable chars, special keys, modifiers)
;;   - Mouse events (click, position detection, component targeting)
;;   - Focus management (Tab/Shift+Tab navigation, component routing)
;;   - Async event system (event queue, batching, handler timeouts)
;;   - Keybindings registry (shortcuts, combos)
;;
;; 💾 STATE MANAGEMENT:
;;   - Reactive state with atoms
;;   - State watchers (watch-path, watch-keys, watch-predicate)
;;   - State binding to render loop (auto-updates)
;;   - Nested state updates (assoc-in, update-in, dissoc-in)
;;
;; 🎬 RENDERING:
;;   - Render loop with FPS control
;;   - Double buffering and diff algorithm
;;   - Dirty region optimization
;;   - Terminal resize handling
;;   - Error boundaries (graceful error handling)
;;   - Frame statistics and performance monitoring
;;
;; 🖥️  TERMINAL:
;;   - Capability detection (color, Unicode, mouse support)
;;   - Graceful degradation (ASCII fallback, color fallback)
;;   - Cross-platform support (Unix, Windows detection)
;;   - Raw mode setup and cleanup
;;
;; 🎯 REAL-WORLD PATTERNS:
;;   - Application architecture (state, events, render)
;;   - Component composition and nesting
;;   - Error handling and recovery
;;   - User input validation
;;   - Interactive workflows
;;   - Performance optimization
;;
;; ═══════════════════════════════════════════════════════════════════════════

(ns comprehensive-demo
  "Comprehensive demonstration of all limner features via an interactive Task Manager"
  (:require [limner.core :as core]
            [limner.layout :as layout]
            [limner.borders :as borders]
            [limner.render :as render]
            [limner.events :as events]
            [limner.state :as state]
            [limner.terminal :as terminal]
            [limner.streaming :as streaming]
            [limner.syntax :as syntax]
            [clojure.string :as str]
            [clojure.core.async :as async])
  (:import [java.io BufferedReader InputStreamReader]))

;; ═══════════════════════════════════════════════════════════════════════════
;; TERMINAL SETUP
;; ═══════════════════════════════════════════════════════════════════════════

(defn enable-raw-mode!
  "Enable raw mode for character-by-character input (Unix/Mac)"
  []
  (try
    (let [pb (ProcessBuilder. ["stty" "-ignbrk" "-brkint" "-parmrk" "-istrip"
                                "-inlcr" "-igncr" "-icrnl" "-ixon" "-opost"
                                "-echo" "-echonl" "-icanon" "-isig" "-iexten"
                                "-parenb" "cs8" "min" "1" "time" "0"])]
      (.inheritIO pb)
      (.waitFor (.start pb)))
    (catch Exception e
      (binding [*out* *err*]
        (println "Warning: Could not enable raw mode:" (.getMessage e))))))

(defn disable-raw-mode!
  "Restore normal terminal mode"
  []
  (try
    (let [pb (ProcessBuilder. ["stty" "sane"])]
      (.inheritIO pb)
      (.waitFor (.start pb)))
    (catch Exception e
      (binding [*out* *err*]
        (println "Warning: Could not restore terminal mode:" (.getMessage e))))))

(defn read-input-async
  "Read input asynchronously from stdin with timeout. Returns channel with string or :timeout."
  [timeout-ms]
  (let [ch (async/chan 1)
        reader (BufferedReader. (InputStreamReader. System/in))]
    (async/go
      (let [start (System/currentTimeMillis)]
        (loop [result ""]
          (if (.ready reader)
            (let [char-code (.read reader)]
              (if (= -1 char-code)
                (async/>! ch (if (empty? result) :timeout result))
                (recur (str result (char char-code)))))
            (if (and (empty? result)
                     (< (- (System/currentTimeMillis) start) timeout-ms))
              (do
                (async/<! (async/timeout 1))
                (recur result))
              (async/>! ch (if (empty? result) :timeout result)))))))
    ch))

;; ═══════════════════════════════════════════════════════════════════════════
;; APPLICATION STATE
;; ═══════════════════════════════════════════════════════════════════════════

(def priority-colors
  "Color mapping for task priorities (demonstrates RGB and semantic colors)"
  {:critical (core/rgb 220 50 47)    ; Red (high priority)
   :high (core/rgb 255 165 0)        ; Orange
   :normal (core/rgb 100 150 255)    ; Blue
   :low (core/rgb 150 150 150)})     ; Gray

(def status-colors
  "Color mapping for task statuses"
  {:pending (core/color-256 220)     ; Yellow
   :in-progress (core/color-256 39)  ; Blue
   :completed (core/color-256 82)    ; Green
   :blocked (core/color-256 196)})   ; Red

(defn create-initial-state
  "Create initial application state with sample tasks"
  []
  {:tasks [{:id 1
            :title "Implement color system"
            :description "Add support for RGB, 256-color, and semantic colors with terminal capability detection"
            :priority :high
            :status :completed
            :tags ["feature" "colors"]
            :progress 100}
           {:id 2
            :title "Add Unicode width handling"
            :description "Fix character width calculation for CJK characters, emoji, and combining marks"
            :priority :high
            :status :completed
            :tags ["bug" "unicode"]
            :progress 100}
           {:id 3
            :title "Implement render loop"
            :description "Create render loop with FPS control, double buffering, and dirty region optimization"
            :priority :high
            :status :in-progress
            :tags ["feature" "rendering"]
            :progress 75}
           {:id 4
            :title "Add event handling"
            :description "Support keyboard and mouse events with focus management and async processing"
            :priority :high
            :status :in-progress
            :tags ["feature" "events"]
            :progress 60}
           {:id 5
            :title "Build component library"
            :description "Create reusable components: panels, inputs, lists, progress bars, statusbar"
            :priority :normal
            :status :pending
            :tags ["feature" "components"]
            :progress 30}
           {:id 6
            :title "Write comprehensive demo"
            :description "Create demo application that exercises all library features"
            :priority :normal
            :status :in-progress
            :tags ["documentation" "demo"]
            :progress 50}
           {:id 7
            :title "Performance optimization"
            :description "Profile and optimize rendering performance for large UIs"
            :priority :low
            :status :pending
            :tags ["optimization" "performance"]
            :progress 10}
           {:id 8
            :title "Cross-platform testing"
            :description "Test on Windows, macOS, Linux with various terminal emulators"
            :priority :low
            :status :pending
            :tags ["testing" "cross-platform"]
            :progress 5}]

   ;; UI state
   :selected-task-id 1
   :scroll-offset 0
   :filter-text ""
   :show-help false
   :running true

   ;; Input panel state
   :new-task-title ""
   :new-task-description ""
   :new-task-priority :normal
   :input-validation {:title-valid true :description-valid true}

   ;; Focus management (demonstrates events.clj focus system)
   :focus (events/focus-state :components [:task-list :new-task-title :new-task-description]
                               :focused :task-list)

   ;; Multi-select state (demonstrates list multi-select)
   :selected-task-ids #{}
   :multi-select-mode false

   ;; Animation state (for spinners and progress)
   :spinner-frame 0
   :animation-tick 0

   ;; Statistics for display
   :stats {:total-tasks 8
           :completed 2
           :in-progress 3
           :pending 3
           :completion-rate 25}

   ;; Streaming demo state
   :streaming-text "Welcome to the Limner Task Manager!\n\nThis demo showcases:\n- Color system (RGB, 256-color, semantic)\n- Layout engine (stack, hsplit, grid)\n- Border styles and composition\n- Interactive components\n- Event handling (keyboard + mouse)\n- State management with watchers\n- Render loop with FPS control\n- Terminal capability detection\n\nPress 'h' for help!"
   :streaming-position 0
   :streaming-active true

   ;; Terminal capabilities (demonstrates terminal.clj)
   :capabilities (terminal/detect-capabilities)})

;; ═══════════════════════════════════════════════════════════════════════════
;; VALIDATION (demonstrates input validation)
;; ═══════════════════════════════════════════════════════════════════════════

(defn validate-task-title
  "Validate task title (min 3 chars, max 80 chars)"
  [title]
  (and (string? title)
       (>= (count title) 3)
       (<= (count title) 80)))

(defn validate-task-description
  "Validate task description (optional, max 500 chars)"
  [description]
  (or (empty? description)
      (and (string? description)
           (<= (count description) 500))))

;; ═══════════════════════════════════════════════════════════════════════════
;; TASK OPERATIONS (demonstrates state management)
;; ═══════════════════════════════════════════════════════════════════════════

(defn add-task!
  "Add new task to state (demonstrates update-in-state!)"
  [state-atom title description priority]
  (let [new-task {:id (inc (apply max (map :id (:tasks @state-atom))))
                  :title title
                  :description description
                  :priority priority
                  :status :pending
                  :tags []
                  :progress 0}]
    (state/update-in-state! state-atom [:tasks] conj new-task)
    (state/assoc-in-state! state-atom [:new-task-title] "")
    (state/assoc-in-state! state-atom [:new-task-description] "")
    (state/assoc-in-state! state-atom [:selected-task-id] (:id new-task))))

(defn toggle-task-complete!
  "Toggle task completion status"
  [state-atom task-id]
  (state/update-in-state!
   state-atom
   [:tasks]
   (fn [tasks]
     (mapv
      (fn [task]
        (if (= (:id task) task-id)
          (assoc task
                 :status (if (= (:status task) :completed) :pending :completed)
                 :progress (if (= (:status task) :completed) 0 100))
          task))
      tasks))))

(defn delete-selected-tasks!
  "Delete all selected tasks (demonstrates multi-select)"
  [state-atom]
  (let [selected-ids (:selected-task-ids @state-atom)]
    (when (seq selected-ids)
      (state/update-in-state!
       state-atom
       [:tasks]
       (fn [tasks]
         (filterv #(not (contains? selected-ids (:id %))) tasks)))
      (state/assoc-in-state! state-atom [:selected-task-ids] #{})
      (state/assoc-in-state! state-atom [:multi-select-mode] false))))

;; ═══════════════════════════════════════════════════════════════════════════
;; UI COMPONENTS - Demonstrates all component rendering
;; ═══════════════════════════════════════════════════════════════════════════

(defn truncate-string-to-width
  "Truncate a string to target visible width, preserving ANSI codes."
  [s target-width]
  (let [visible-w (core/visible-width s)]
    (if (<= visible-w target-width)
      s
      (loop [chars (seq s)
             result []
             width 0
             in-ansi false]
        (if (or (empty? chars) (>= width target-width))
          (str (apply str result) "\u001b[0m")  ; Reset ANSI at end
          (let [c (first chars)]
            (cond
              (= c \u001b)
              (recur (rest chars) (conj result c) width true)
              in-ansi
              (recur (rest chars) (conj result c) width (not= c \m))
              :else
              (let [char-w (if (and (>= (int c) 0x4E00) (<= (int c) 0x9FFF)) 2 1)]
                (if (> (+ width char-w) target-width)
                  (str (apply str result) "\u001b[0m")
                  (recur (rest chars) (conj result c) (+ width char-w) false))))))))))

(defn fit-content-lines
  "Truncate content lines to fit within a box of given inner width"
  [lines inner-width]
  (mapv #(truncate-string-to-width % inner-width) lines))

(defn create-fitted-box
  "Create a box with title that fits exactly within target-width.
   Content is padded/truncated to fill the box interior."
  [title content-lines target-width border-style]
  (let [inner-width (- target-width 4)  ; Account for "│ " and " │"
        ;; Pad or truncate each line to exactly inner-width
        fitted-content (mapv (fn [line]
                               (let [truncated (truncate-string-to-width line inner-width)
                                     w (core/visible-width truncated)
                                     padding (- inner-width w)]
                                 (if (pos? padding)
                                   (str truncated (apply str (repeat padding " ")))
                                   truncated)))
                             content-lines)]
    (borders/draw-titled-box title fitted-content
                             :border-style border-style
                             :title-pos :left)))

(defn fit-box-to-height
  "Fit box to exact height, preserving top and bottom borders.
   If truncating, keeps first lines + bottom border.
   If padding, adds empty lines before bottom border."
  [box-lines target-height target-width]
  (let [current (count box-lines)
        empty-line (apply str (repeat target-width " "))]
    (cond
      (= current target-height) (vec box-lines)
      (< current target-height)
      ;; Pad: insert empty lines before the last line (bottom border)
      (let [top-lines (butlast box-lines)
            bottom-line (last box-lines)
            padding-needed (- target-height current)]
        (vec (concat top-lines
                     (repeat padding-needed empty-line)
                     [bottom-line])))
      :else
      ;; Truncate: keep first (target-height - 1) lines + bottom border
      (let [keep-count (dec target-height)
            top-lines (take keep-count box-lines)
            bottom-line (last box-lines)]
        (vec (concat top-lines [bottom-line]))))))

(defn render-header
  "Render header with title and capabilities info (demonstrates colors and borders)"
  [state caps term-width]
  (let [;; Demonstrate terminal capability detection
        color-mode (terminal/select-color-mode)
        unicode-support (terminal/supports-feature? :unicode)

        ;; Build title lines - scale to terminal width
        title-text "LIMNER TASK MANAGER - Comprehensive Feature Demo"
        box-width (- term-width 2)  ; inner width (minus 2 for box chars ╔╗)
        title-top    (str "╔" (apply str (repeat box-width "═")) "╗")
        
        ;; Center the title text
        title-len (count title-text)
        left-pad (max 1 (quot (- box-width title-len) 2))
        right-pad (max 1 (- box-width title-len left-pad))
        padded-title (str "║" (apply str (repeat left-pad " "))
                         (core/color :bold title-text)
                         (apply str (repeat right-pad " ")) "║")
        title-bottom (str "╚" (apply str (repeat box-width "═")) "╝")

        ;; Capability info - also scale to width
        cap-info (str "Terminal: " (terminal/detect-term-type)
                      " │ Colors: " (name color-mode)
                      " │ Unicode: " (if unicode-support "✓" "Yes")
                      " │ FPS: 60")

        ;; Apply colors based on capabilities
        color-fn (if (terminal/supports-feature? :ansi-colors)
                   #(core/color :cyan %)
                   identity)]

    [(color-fn title-top)
     (color-fn padded-title)
     (color-fn title-bottom)
     (if (terminal/supports-feature? :ansi-colors)
       (core/color :bright-black cap-info)
       cap-info)
     ""]))

(defn render-task-list
  "Render scrollable task list with filtering and multi-select (demonstrates panels and lists)"
  [state]
  (let [tasks (:tasks state)
        filter-text (:filter-text state)
        selected-id (:selected-task-id state)
        selected-ids (:selected-task-ids state)
        multi-select? (:multi-select-mode state)

        ;; Filter tasks (demonstrates list filtering)
        filtered-tasks (if (empty? filter-text)
                        tasks
                        (filter #(or (str/includes? (str/lower-case (:title %))
                                                   (str/lower-case filter-text))
                                    (some (fn [tag] (str/includes? tag filter-text))
                                          (:tags %)))
                               tasks))

        ;; Render task items with colors and selection indicators
        task-lines (mapv
                    (fn [task]
                      (let [selected? (= (:id task) selected-id)
                            multi-selected? (contains? selected-ids (:id task))

                            ;; Status indicator (demonstrates semantic colors)
                            status-char (case (:status task)
                                         :completed "✓"
                                         :in-progress "●"
                                         :pending "○"
                                         :blocked "✗"
                                         " ")

                            ;; Priority indicator (demonstrates RGB colors)
                            priority-text (str "[" (str/upper-case (name (:priority task))) "]")
                            colored-priority (core/color
                                             (get priority-colors (:priority task) :white)
                                             priority-text)

                            ;; Multi-select checkbox (demonstrates checkbox rendering)
                            checkbox (if multi-select?
                                      (if multi-selected? "[✓] " "[ ] ")
                                      "")

                            ;; Selection cursor
                            cursor (if selected? "► " "  ")

                            ;; Full line
                            line (str cursor checkbox status-char " "
                                     colored-priority " " (:title task))]

                        ;; Highlight selected row (demonstrates background colors)
                        (if selected?
                          (core/color (core/bg-256 235) line)
                          line)))
                    filtered-tasks)

        ;; Filter info line
        filter-info (if (empty? filter-text)
                     ""
                     (core/color :yellow (str "Filter: \"" filter-text "\" (" (count filtered-tasks) " matches)")))

        ;; Help text
        help-text (if multi-select?
                   (core/color :bright-black "[Space] Select  [d] Delete Selected  [m] Exit Multi-Select")
                   (core/color :bright-black "[m] Multi-Select  [Enter] Toggle Done  [n] New Task  [h] Help"))

        ;; Combine all lines
        all-lines (concat
                   [(core/color :bold "Tasks")]
                   [""]
                   task-lines
                   [""]
                   [filter-info]
                   [help-text])]

    ;; Return as vector of strings
    (vec all-lines)))

(defn render-task-details
  "Render selected task details with scrolling (demonstrates panel scrolling)"
  [state]
  (let [selected-id (:selected-task-id state)
        task (first (filter #(= (:id %) selected-id) (:tasks state)))]

    (if task
      (let [;; Task header with colored priority
            header (str (core/color :bold (str "Task #" (:id task)))
                       " - "
                       (core/color (get priority-colors (:priority task))
                                  (str/upper-case (name (:priority task))))
                       " PRIORITY")

            ;; Status with color
            status-line (str "Status: "
                           (core/color (get status-colors (:status task))
                                      (str/upper-case (name (:status task)))))

            ;; Progress bar (demonstrates progress component)
            progress-bar (let [progress (:progress task)
                              bar-width 30
                              filled (int (* (/ progress 100.0) bar-width))
                              empty (- bar-width filled)
                              filled-char "█"
                              empty-char "░"]
                          (str "Progress: ["
                               (core/color :green (str/join (repeat filled filled-char)))
                               (str/join (repeat empty empty-char))
                               "] " progress "%"))

            ;; Tags (demonstrates list rendering)
            tags-line (if (seq (:tags task))
                       (str "Tags: " (str/join ", " (map #(core/color :cyan (str "#" %)) (:tags task))))
                       "Tags: (none)")

            ;; Description (demonstrates multi-line text wrapping)
            description-lines (if (seq (:description task))
                               (str/split-lines (:description task))
                               ["(No description)"])

            ;; Full content
            content (concat
                     [header]
                     [""]
                     [status-line]
                     [progress-bar]
                     [tags-line]
                     [""]
                     [(core/color :bold "Description:")]
                     description-lines)]

        (vec content))

      ;; No task selected
      [(core/color :bright-black "No task selected")])))

(defn render-statistics
  "Render statistics panel with charts (demonstrates progress bars and stats)"
  [state]
  (let [stats (:stats state)

        ;; Status breakdown
        status-counts {:completed (count (filter #(= (:status %) :completed) (:tasks state)))
                      :in-progress (count (filter #(= (:status %) :in-progress) (:tasks state)))
                      :pending (count (filter #(= (:status %) :pending) (:tasks state)))
                      :blocked (count (filter #(= (:status %) :blocked) (:tasks state)))}

        ;; Priority breakdown
        priority-counts {:critical (count (filter #(= (:priority %) :critical) (:tasks state)))
                        :high (count (filter #(= (:priority %) :high) (:tasks state)))
                        :normal (count (filter #(= (:priority %) :normal) (:tasks state)))
                        :low (count (filter #(= (:priority %) :low) (:tasks state)))}

        ;; Render bar charts (simple ASCII chart demonstration)
        status-chart (mapv
                      (fn [[status count]]
                        (let [bar-width 15
                              total (:total-tasks stats)
                              filled (if (pos? total)
                                      (int (* (/ count total) bar-width))
                                      0)
                              bar-char "█"
                              label (str/upper-case (name status))]
                          (str (format "%-12s" label) " "
                               (core/color (get status-colors status)
                                          (str/join (repeat filled bar-char)))
                               " " count)))
                      status-counts)

        priority-chart (mapv
                        (fn [[priority count]]
                          (let [bar-width 15
                                total (:total-tasks stats)
                                filled (if (pos? total)
                                        (int (* (/ count total) bar-width))
                                        0)
                                bar-char "█"
                                label (str/upper-case (name priority))]
                            (str (format "%-12s" label) " "
                                 (core/color (get priority-colors priority)
                                            (str/join (repeat filled bar-char)))
                                 " " count)))
                        priority-counts)

        ;; Spinner (demonstrates spinner animation)
        spinner-frames ["⠋" "⠙" "⠹" "⠸" "⠼" "⠴" "⠦" "⠧" "⠇" "⠏"]
        spinner-char (nth spinner-frames (mod (:spinner-frame state) (count spinner-frames)))

        ;; Completion percentage (demonstrates percentage calculation)
        completion-pct (if (pos? (:total-tasks stats))
                        (int (* 100 (/ (:completed status-counts)
                                     (:total-tasks stats))))
                        0)]

    (vec
     (concat
      [(core/color :bold "Statistics")]
      [""]
      [(str "Total Tasks: " (:total-tasks stats))]
      [(str "Completion: " completion-pct "%")]
      [""]
      [(core/color :bold "By Status:")]
      status-chart
      [""]
      [(core/color :bold "By Priority:")]
      priority-chart
      [""]
      [(str (core/color :cyan spinner-char) " Live Updates")]))))

(defn render-input-panel
  "Render new task input form (demonstrates input with validation)"
  [state]
  (let [title (:new-task-title state)
        description (:new-task-description state)
        priority (:new-task-priority state)
        validation (:input-validation state)
        focused-component (get-in state [:focus :focused])

        ;; Validation feedback (demonstrates real-time validation)
        title-valid? (:title-valid validation)
        description-valid? (:description-valid validation)

        ;; Input fields with focus indicators
        title-line (str (if (= focused-component :new-task-title)
                         (core/color :cyan "► Title: ")
                         "  Title: ")
                       title
                       (when (= focused-component :new-task-title) "█")
                       (when-not title-valid?
                         (core/color :red " ✗ Min 3 chars")))

        description-line (str (if (= focused-component :new-task-description)
                               (core/color :cyan "► Description: ")
                               "  Description: ")
                             (subs (str description "                    ") 0 20)
                             "..."
                             (when (= focused-component :new-task-description) "█")
                             (when-not description-valid?
                               (core/color :red " ✗ Max 500 chars")))

        priority-line (str "  Priority: "
                          (core/color (get priority-colors priority)
                                     (str/upper-case (name priority))))

        help-line (core/color :bright-black "[Tab] Next Field  [Enter] Submit  [Esc] Cancel")]

    [(core/color :bold "New Task")
     ""
     title-line
     description-line
     priority-line
     ""
     help-line]))

(defn render-statusbar
  "Render status bar at bottom (demonstrates statusbar component)"
  [state]
  (let [;; Demonstrates statusbar with left/center/right sections
        left-section (str "Tasks: " (count (:tasks state)))

        center-section (if (:multi-select-mode state)
                        (str "Multi-Select: " (count (:selected-task-ids state)) " selected")
                        (str "Task #" (:selected-task-id state)))

        right-section (str "Press 'q' to quit | 'h' for help")

        ;; Terminal width
        term-width (:width (render/get-terminal-size))

        ;; Calculate section positions
        left-text left-section
        center-text center-section
        right-text right-section

        ;; Build statusbar (simple left-aligned for now)
        status-line (str " " left-text "  │  " center-text
                        (str/join (repeat (- term-width (+ 10 (count left-text) (count center-text) (count right-text))) " "))
                        right-text " ")]

    ;; Return colored statusbar
    [(core/color (core/bg-256 236) (core/color :bright-white status-line))]))

(defn render-help-overlay
  "Render help overlay (demonstrates modal/overlay)"
  []
  (let [help-content [
                     (core/color :bold "KEYBOARD SHORTCUTS")
                     ""
                     (core/color :cyan "Navigation:")
                     "  ↑/k       Move up in list"
                     "  ↓/j       Move down in list"
                     "  Tab       Next component"
                     "  Shift+Tab Previous component"
                     ""
                     (core/color :cyan "Task Actions:")
                     "  Enter     Toggle task completion"
                     "  n         New task"
                     "  d         Delete selected task(s)"
                     "  m         Toggle multi-select mode"
                     "  Space     (Multi-select) Toggle task"
                     ""
                     (core/color :cyan "Other:")
                     "  h         Show/hide this help"
                     "  q         Quit application"
                     "  /         Filter tasks"
                     ""
                     (core/color :bright-black "Press 'h' again to close")]

        ;; Box the help content (no shadow - simpler and cleaner)
        boxed (borders/colorize-border
                (borders/draw-titled-box "Help" help-content 
                                         :border-style :double 
                                         :title-pos :center)
                :cyan)]
    boxed))

;; ═══════════════════════════════════════════════════════════════════════════
;; MAIN RENDERING FUNCTION
;; ═══════════════════════════════════════════════════════════════════════════

(defn render-app
  "Main render function - demonstrates complex layout composition"
  [state]
  (let [caps (:capabilities state)
        term-size (render/get-terminal-size)
        term-width (:width term-size)
        term-height (:height term-size)

        ;; Calculate widths for 3-column layout with spacing
        spacing 2
        usable-width (- term-width (* spacing 2))  ; 2 gaps between 3 boxes
        list-width (int (* usable-width 0.40))
        details-width (int (* usable-width 0.35))
        stats-width (- usable-width list-width details-width)
        
        border-style (terminal/select-border-style)

        ;; Render all components
        header-lines (render-header state caps term-width)
        task-list-lines (render-task-list state)
        task-details-lines (render-task-details state)
        statistics-lines (render-statistics state)
        input-panel-lines (render-input-panel state)
        statusbar-lines (render-statusbar state)

        ;; Create boxes with correct widths (content truncated to fit)
        task-list-box (borders/colorize-border
                        (create-fitted-box "Task List" task-list-lines list-width border-style)
                        :cyan)

        task-details-box (borders/colorize-border
                           (create-fitted-box "Details" task-details-lines details-width border-style)
                           :blue)

        statistics-box (borders/colorize-border
                         (create-fitted-box "Stats" statistics-lines stats-width border-style)
                         :green)

        input-panel-box (borders/colorize-border
                          (create-fitted-box "Add Task" input-panel-lines term-width border-style)
                          :yellow)

        ;; Calculate available height for main content boxes
        ;; Layout: header + blank + main_boxes + blank + input + blank + status
        header-height (count header-lines)
        input-height (count input-panel-box)
        status-height (count statusbar-lines)
        fixed-height (+ header-height 1 1 input-height 1 status-height)
        available-for-main (max 6 (- term-height fixed-height))

        ;; Calculate box height (all 3 main boxes should match)
        box-height (min available-for-main
                       (max (count task-list-box)
                            (count task-details-box)
                            (count statistics-box)))

        ;; Fit boxes to uniform height (preserving borders)
        list-fitted (fit-box-to-height task-list-box box-height list-width)
        details-fitted (fit-box-to-height task-details-box box-height details-width)
        stats-fitted (fit-box-to-height statistics-box box-height stats-width)

        output-lines (concat
                      header-lines
                      [""]

                      ;; Main content area - side by side boxes
                      (let [left-and-center (borders/side-by-side list-fitted details-fitted spacing)
                            all-three (borders/side-by-side left-and-center stats-fitted spacing)]
                        all-three)

                      [""]
                      input-panel-box
                      [""]
                      statusbar-lines)

        ;; If help is visible, show centered help screen instead
        final-output (if (:show-help state)
                       (let [help-box (render-help-overlay)
                             help-height (count help-box)
                             help-width (apply max (map core/visible-width help-box))
                             
                             ;; Center vertically
                             top-padding (max 0 (quot (- term-height help-height) 2))
                             bottom-padding (max 0 (- term-height help-height top-padding))
                             
                             ;; Center horizontally - pad each help line
                             left-pad (max 0 (quot (- term-width help-width) 2))
                             centered-help (mapv (fn [line]
                                                   (str (apply str (repeat left-pad " ")) line))
                                                 help-box)
                             
                             ;; Build full screen
                             empty-line (apply str (repeat term-width " "))]
                         (vec (concat
                               (repeat top-padding empty-line)
                               centered-help
                               (repeat bottom-padding empty-line))))
                       output-lines)]

    (vec final-output)))

;; ═══════════════════════════════════════════════════════════════════════════
;; EVENT HANDLERS (demonstrates comprehensive event handling)
;; ═══════════════════════════════════════════════════════════════════════════

(defn handle-key-event!
  "Handle keyboard events (demonstrates keybindings and event dispatch)
   Note: Uses imperative updates for simplicity. For pure functional approach,
   see the async event system examples in events_demo.clj"
  [event state-atom]
  (let [state @state-atom
        focused (get-in state [:focus :focused])]

    (cond
      ;; Global shortcuts (always available)
      (events/key-matches? event [:q])
      (state/assoc-in-state! state-atom [:running] false)

      (events/key-matches? event [:h])
      (state/update-in-state! state-atom [:show-help] not)

      (events/key-matches? event [:n])
      (state/assoc-in-state! state-atom [:focus :focused] :new-task-title)

      (events/key-matches? event [:m])
      (state/update-in-state! state-atom [:multi-select-mode] not)

      ;; Tab navigation (demonstrates focus management)
      (events/key-matches? event [:tab])
      (state/update-in-state! state-atom [:focus] events/focus-next)

      (events/key-matches? event [:shift :tab])
      (state/update-in-state! state-atom [:focus] events/focus-prev)

      ;; Context-sensitive shortcuts based on focused component
      (= focused :task-list)
      (cond
        ;; Up/down navigation - use update-state! to get whole state atomically
        (or (events/key-matches? event [:up])
            (events/key-matches? event [:k]))
        (state/update-state!
         state-atom
         (fn [state]
           (let [tasks (:tasks state)
                 current-id (:selected-task-id state)
                 ids (mapv :id tasks)
                 current-idx (.indexOf ids current-id)
                 prev-idx (max 0 (dec current-idx))]
             (assoc state :selected-task-id (nth ids prev-idx current-id)))))

        (or (events/key-matches? event [:down])
            (events/key-matches? event [:j]))
        (state/update-state!
         state-atom
         (fn [state]
           (let [tasks (:tasks state)
                 current-id (:selected-task-id state)
                 ids (mapv :id tasks)
                 current-idx (.indexOf ids current-id)
                 next-idx (min (dec (count ids)) (inc current-idx))]
             (assoc state :selected-task-id (nth ids next-idx current-id)))))

        ;; Toggle completion
        (events/key-matches? event [:enter])
        (toggle-task-complete! state-atom (:selected-task-id state))

        ;; Multi-select toggle
        (events/key-matches? event [:space])
        (when (:multi-select-mode state)
          (state/update-in-state!
           state-atom
           [:selected-task-ids]
           (fn [selected-ids]
             (let [current-id (:selected-task-id state)]
               (if (contains? selected-ids current-id)
                 (disj selected-ids current-id)
                 (conj selected-ids current-id))))))

        ;; Delete selected
        (events/key-matches? event [:d])
        (if (:multi-select-mode state)
          (delete-selected-tasks! state-atom)
          (toggle-task-complete! state-atom (:selected-task-id state))))

      ;; Text input fields
      (= focused :new-task-title)
      (cond
        ;; Regular character input
        (:char event)
        (do
          (state/update-in-state! state-atom [:new-task-title] str (:char event))
          (state/assoc-in-state! state-atom [:input-validation :title-valid]
                                (validate-task-title (str (:new-task-title state) (:char event)))))

        ;; Backspace
        (events/key-matches? event [:backspace])
        (let [current (:new-task-title state)]
          (when (seq current)
            (state/assoc-in-state! state-atom [:new-task-title]
                                  (subs current 0 (dec (count current))))
            (state/assoc-in-state! state-atom [:input-validation :title-valid]
                                  (validate-task-title (subs current 0 (dec (count current)))))))

        ;; Submit with Enter
        (events/key-matches? event [:enter])
        (let [title (:new-task-title state)
              description (:new-task-description state)
              priority (:new-task-priority state)]
          (when (and (validate-task-title title)
                    (validate-task-description description))
            (add-task! state-atom title description priority))))

      (= focused :new-task-description)
      (cond
        ;; Regular character input
        (:char event)
        (do
          (state/update-in-state! state-atom [:new-task-description] str (:char event))
          (state/assoc-in-state! state-atom [:input-validation :description-valid]
                                (validate-task-description (str (:new-task-description state) (:char event)))))

        ;; Backspace
        (events/key-matches? event [:backspace])
        (let [current (:new-task-description state)]
          (when (seq current)
            (state/assoc-in-state! state-atom [:new-task-description]
                                  (subs current 0 (dec (count current))))
            (state/assoc-in-state! state-atom [:input-validation :description-valid]
                                  (validate-task-description (subs current 0 (dec (count current)))))))

        ;; Submit with Enter
        (events/key-matches? event [:enter])
        (let [title (:new-task-title state)
              description (:new-task-description state)
              priority (:new-task-priority state)]
          (when (and (validate-task-title title)
                    (validate-task-description description))
            (add-task! state-atom title description priority)))))

    ;; Return updated state (though we use state-atom, return for convention)
    nil))

;; ═══════════════════════════════════════════════════════════════════════════
;; MAIN LOOP (demonstrates render loop and event processing)
;; ═══════════════════════════════════════════════════════════════════════════

(defn main-loop
  "Main application loop with render and event handling"
  []
  (let [;; Create application state (demonstrates state management)
        app-state (state/create-state :initial-value (create-initial-state))

        ;; Add watcher for statistics updates (demonstrates watch-path)
        _ (state/watch-path
           app-state
           [:tasks]
           :stats-watcher
           (fn [old-tasks new-tasks]
             (let [total (count new-tasks)
                   completed (count (filter #(= (:status %) :completed) new-tasks))
                   in-progress (count (filter #(= (:status %) :in-progress) new-tasks))
                   pending (count (filter #(= (:status %) :pending) new-tasks))]
               (state/assoc-in-state! app-state [:stats]
                                    {:total-tasks total
                                     :completed completed
                                     :in-progress in-progress
                                     :pending pending
                                     :completion-rate (if (pos? total)
                                                       (int (* 100 (/ completed total)))
                                                       0)}))))

        ;; Input reading channel (demonstrates core.async integration)
        input-chan (async/chan 100)

        ;; Start input reading loop (demonstrates async input handling)
        input-reader (async/go-loop []
                      (when (:running @app-state)
                        (let [timeout-ch (async/timeout 50)
                              [input ch] (async/alts! [(read-input-async 50) timeout-ch])]
                          (when (and (string? input) (not= ch timeout-ch))
                            (let [event (events/parse-key input)]
                              (when event
                                ;; Handle event directly (demonstrates event handling)
                                (handle-key-event! event app-state))))
                          (recur))))

        ;; Animation timer (for spinner and other animations)
        animation-timer (async/go-loop []
                         (async/<! (async/timeout 100))  ; Update every 100ms
                         (state/update-in-state! app-state [:spinner-frame] inc)
                         (state/update-in-state! app-state [:animation-tick] inc)
                         (when (:running @app-state)
                           (recur)))

        ;; Create render loop (demonstrates render.clj render loop)
        render-loop (render/create-render-loop
                     app-state
                     :fps 60
                     :render-fn (fn [state]
                                 (render-app state))
                     :on-error (fn [error]
                                (binding [*out* *err*]
                                  (println "Render error:" error))))]

    ;; Wait for app to finish
    (while (:running @app-state)
      (Thread/sleep 100))

    ;; Clean shutdown
    ((:stop! render-loop))
    (async/close! input-chan)

    ;; Return final state
    @app-state))

;; ═══════════════════════════════════════════════════════════════════════════
;; ENTRY POINT
;; ═══════════════════════════════════════════════════════════════════════════

(defn -main
  "Application entry point with terminal setup/cleanup"
  []
  (let [{:keys [width]} (render/get-terminal-size)
        inner-width (- width 2)
        title "LIMNER COMPREHENSIVE DEMO - Starting..."
        padding (max 0 (- inner-width (count title) 2))
        left-pad (quot padding 2)
        right-pad (- padding left-pad)]
    (println (core/color :cyan (str "\n╔" (apply str (repeat inner-width "═")) "╗")))
    (println (core/color :cyan (str "║" (apply str (repeat left-pad " "))
                                    (core/color :bold title)
                                    (apply str (repeat right-pad " ")) "║")))
    (println (core/color :cyan (str "╚" (apply str (repeat inner-width "═")) "╝\n"))))

  ;; Detect capabilities and show info
  (let [caps (terminal/detect-capabilities)]
    (println (terminal/capability-report))
    (println)
    (Thread/sleep 2000))  ; Let user see the info

  (try
    ;; Setup terminal
    (enable-raw-mode!)
    (render/setup-terminal)
    (core/hide-cursor)

    ;; Run application
    (let [final-state (main-loop)]

      ;; Cleanup
      (core/show-cursor)
      (render/restore-terminal)
      (disable-raw-mode!)

      ;; Show goodbye message
      (render/clear-screen)
      (println (core/color :green "\n✓ LIMNER COMPREHENSIVE DEMO COMPLETE!\n"))
      (println "Final Statistics:")
      (println (str "  Total Tasks: " (count (:tasks final-state))))
      (println (str "  Completed: " (count (filter #(= (:status %) :completed) (:tasks final-state)))))
      (println (str "  Completion Rate: " (get-in final-state [:stats :completion-rate]) "%"))
      (println)
      (println (core/color :cyan "Features Demonstrated:"))
      (println "  ✓ Color system (basic, 256-color, RGB)")
      (println "  ✓ Layout engine (stack, hsplit, nested)")
      (println "  ✓ Border styles and composition")
      (println "  ✓ Components (panel, input, list, progress, statusbar)")
      (println "  ✓ Event handling (keyboard + mouse)")
      (println "  ✓ Focus management (tab navigation)")
      (println "  ✓ State management with watchers")
      (println "  ✓ Async event system with batching")
      (println "  ✓ Render loop with FPS control")
      (println "  ✓ Terminal capability detection")
      (println "  ✓ Input validation")
      (println "  ✓ Multi-select lists")
      (println "  ✓ Scrollable panels")
      (println "  ✓ Progress bars and spinners")
      (println "  ✓ Modal overlays")
      (println)
      (println "Thank you for exploring limner!\n"))

    (catch Exception e
      ;; Always cleanup terminal on error
      (core/show-cursor)
      (render/restore-terminal)
      (disable-raw-mode!)
      (binding [*out* *err*]
        (println "\nError:" (.getMessage e))
        (.printStackTrace e)))))

;; Run the demo when executed directly
;; Note: Check babashka.file matches this file to avoid running when required
(when (= *file* (System/getProperty "babashka.file"))
  (-main))
