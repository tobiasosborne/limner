# Limner Tutorial

## Introduction

Welcome to Limner! This tutorial will guide you through building terminal user interfaces in Clojure, from simple static displays to fully interactive applications. We'll build progressively more complex examples, teaching core concepts along the way.

By the end of this tutorial, you'll understand:
- How to render beautiful borders and panels
- How to compose layouts with flexible constraints
- How to add colors and styling
- How to handle keyboard and mouse events
- How to build reactive applications with state management
- How to create smooth animations and progress indicators

### Prerequisites

- Basic Clojure knowledge (functions, data structures, namespaces)
- Babashka or Clojure CLI installed
- A terminal emulator (any modern terminal will work)

### Installation

Add to your `deps.edn`:

```clojure
{:deps {io.github.yourusername/limner {:git/tag "v1.0.0" :git/sha "..."}}}
```

For Babashka scripts:

```clojure
#!/usr/bin/env bb
```

Then require namespaces as needed.

---

## Chapter 1: Hello, Terminal!

### Lesson 1.1: Your First Box

Let's start with the simplest possible program—drawing a box around text.

```clojure
#!/usr/bin/env bb
(require '[limner.borders :as borders])

(def message ["Hello, Terminal!"])
(def box (borders/draw-box message :border-style :rounded))

(doseq [line box]
  (println line))
```

**Output:**
```
╭──────────────────╮
│ Hello, Terminal! │
╰──────────────────╯
```

**What's happening:**
1. `borders/draw-box` takes a vector of strings and wraps them in a border
2. `:border-style :rounded` selects rounded corners (╭╮╰╯)
3. The function returns a vector of strings—the rendered box
4. We print each line with `doseq`

**Key concept:** Limner rendering functions are **pure**—they take data and return strings. No side effects, no state changes.

### Lesson 1.2: Multiple Lines

Boxes naturally handle multiple lines:

```clojure
(def poem ["Roses are red,"
           "Violets are blue,"
           "Limner draws borders,"
           "And layouts them too!"])

(def box (borders/draw-box poem :border-style :double))

(doseq [line box]
  (println line))
```

**Output:**
```
╔════════════════════╗
║ Roses are red,     ║
║ Violets are blue,  ║
║ Limner draws boxes,║
║ And layouts them!  ║
╚════════════════════╝
```

The box automatically sizes itself to the longest line.

### Lesson 1.3: Border Styles

Limner provides 7 predefined border styles:

```clojure
(def styles [:single :double :rounded :thick :ascii :dots :stars])

(doseq [style styles]
  (println (str "\n" (name style) " style:"))
  (doseq [line (borders/draw-box ["Example"] :border-style style)]
    (println line)))
```

**Try this:** Run the code and see all the border styles. Which one do you prefer?

### Lesson 1.4: Titled Boxes

Add titles to boxes for better organization:

```clojure
(def box (borders/draw-titled-box
           "Important Message"
           ["This box has a title!"]
           :border-style :double
           :title-pos :center))

(doseq [line box]
  (println line))
```

**Output:**
```
╔══ Important Message ══╗
║ This box has a title! ║
╚═══════════════════════╝
```

Title positions: `:left`, `:center`, `:right`

---

## Chapter 2: Adding Color

### Lesson 2.1: Basic Colors

Limner supports 37 basic colors plus 256-color palette plus RGB/truecolor.

```clojure
(require '[limner.core :as core]
         '[limner.borders :as borders])

(def box (borders/draw-box ["Success!"] :border-style :rounded))
(def colored (borders/colorize-border box :green))

(doseq [line colored]
  (println line))
```

The border is now green!

**Available basic colors:**
- Standard: `:black`, `:red`, `:green`, `:yellow`, `:blue`, `:magenta`, `:cyan`, `:white`
- Bright: `:bright-red`, `:bright-green`, `:bright-blue`, etc.
- Backgrounds: `:bg-red`, `:bg-green`, `:bg-blue`, etc.

### Lesson 2.2: Coloring Text

Use `core/color` to colorize any string:

```clojure
(println (core/color :red "Error: Something went wrong!"))
(println (core/color :green "✓ Success"))
(println (core/color :yellow "⚠ Warning"))
```

### Lesson 2.3: Semantic Colors

Limner provides semantic color presets:

```clojure
(require '[limner.core :refer [colors]])

(println (core/color (colors :error) "✗ Failed"))
(println (core/color (colors :success) "✓ Passed"))
(println (core/color (colors :warning) "⚠ Caution"))
(println (core/color (colors :info) "ℹ Info"))
```

### Lesson 2.4: Advanced Colors

#### 256-Color Palette

```clojure
(require '[limner.core :refer [color-256]])

;; Color #196 is a bright red
(println (core/color (color-256 196) "Bright red text"))

;; Color #27 is a nice blue
(println (core/color (color-256 27) "Nice blue"))
```

#### RGB/Truecolor

```clojure
(require '[limner.core :refer [rgb]])

;; Custom orange
(println (core/color (rgb 255 128 0) "Custom orange"))

;; Pastel pink
(println (core/color (rgb 255 182 193) "Pastel pink"))
```

### Lesson 2.5: Combining Styles

Stack multiple styles:

```clojure
(println (core/color :bold (core/color :red "Bold Red")))
(println (core/color :underline (core/color :cyan "Underlined Cyan")))
(println (core/color :italic (core/color :yellow "Italic Yellow")))
```

---

## Chapter 3: Layout Composition

### Lesson 3.1: The Box Model

Every layout element is a **box** with:
- Position (x, y)
- Dimensions (width, height)
- Margin and padding
- Z-index for layering

```clojure
(require '[limner.layout :as layout])

(def my-box (layout/box
              :x 10
              :y 5
              :width 40
              :height 10
              :margin 2
              :padding 1))
```

### Lesson 3.2: Constraints

Instead of fixed sizes, use **constraints** that adapt to available space:

#### Fixed Size

```clojure
(def header (layout/box :height (layout/fixed 5)))
```

Exactly 5 lines tall.

#### Percentage

```clojure
(def sidebar (layout/box :width (layout/percent 25)))
```

25% of parent width.

#### Flex (Proportional)

```clojure
(def content (layout/box :height (layout/flex 1)))
```

Takes remaining space. Multiple flex items share proportionally:

```clojure
;; Box A gets 1/3, Box B gets 2/3 of remaining space
(def box-a (layout/box :height (layout/flex 1)))
(def box-b (layout/box :height (layout/flex 2)))
```

#### Auto (Content-sized)

```clojure
(def auto-box (layout/box :height (layout/auto)))
```

Sized to fit content.

### Lesson 3.3: Vertical Stacking

Stack boxes vertically:

```clojure
(def my-layout
  (layout/stack
    [(layout/box :height (layout/fixed 3))    ; Header
     (layout/box :height (layout/flex 1))     ; Content
     (layout/box :height (layout/fixed 2))])) ; Footer
```

Given terminal height 24:
- Header: 3 lines
- Content: 19 lines (24 - 3 - 2)
- Footer: 2 lines

### Lesson 3.4: Horizontal Splitting

Split space horizontally:

```clojure
(def my-layout
  (layout/hsplit
    [(layout/box :width (layout/percent 30))  ; Sidebar
     (layout/box :width (layout/flex 1))]     ; Main content
    :spacing 1))  ; 1 character gap between
```

### Lesson 3.5: Grid Layout

Create grids with rows and columns:

```clojure
(def grid-layout
  (layout/grid
    3  ; 3 columns
    [(layout/box)  ; Cell 1
     (layout/box)  ; Cell 2
     (layout/box)  ; Cell 3
     (layout/box)  ; Cell 4
     (layout/box)  ; Cell 5
     (layout/box)] ; Cell 6
    :column-spacing 2
    :row-spacing 1))
```

Creates a 3×2 grid (6 cells arranged in 3 columns).

### Lesson 3.6: Practical Example - Dashboard Layout

```clojure
(def dashboard
  (layout/stack
    [;; Header
     (layout/box :height (layout/fixed 3))

     ;; Main area: sidebar + content
     (layout/hsplit
       [(layout/box :width (layout/fixed 20))   ; Sidebar
        (layout/stack
          [(layout/box :height (layout/flex 2))  ; Main content
           (layout/box :height (layout/flex 1))] ; Bottom panel
          :spacing 1)]
       :spacing 1)

     ;; Footer
     (layout/box :height (layout/fixed 2))]
    :spacing 0))
```

This creates a classic dashboard layout:
```
┌─────────────────────────────┐ Header (3 lines)
├──────┬──────────────────────┤
│      │                      │
│ Side │    Main Content      │ Main area
│ bar  │                      │ (remaining space)
│      ├──────────────────────┤
│      │   Bottom Panel       │
├──────┴──────────────────────┤
│          Footer             │ Footer (2 lines)
└─────────────────────────────┘
```

---

## Chapter 4: Interactive Applications

### Lesson 4.1: Event Handling Basics

Limner provides keyboard and mouse event handling:

```clojure
(require '[limner.events :as events])

;; Parse keyboard input
(def event (events/parse-key-event "A"))
;; => {:type :char, :key \A}

(def event (events/parse-key-event "\u001b[A"))
;; => {:type :arrow, :key :up}
```

### Lesson 4.2: Key Event Types

Common event types:

```clojure
;; Character keys
{:type :char, :key \a}

;; Arrow keys
{:type :arrow, :key :up}
{:type :arrow, :key :down}
{:type :arrow, :key :left}
{:type :arrow, :key :right}

;; Special keys
{:type :ctrl, :key :ctrl-c}
{:type :enter}
{:type :escape}
{:type :tab}
{:type :backspace}

;; Function keys
{:type :function, :key :f1}
```

### Lesson 4.3: Event Handlers

Register event handlers that transform state:

```clojure
(def handlers
  {:arrow (fn [state event]
            (case (:key event)
              :up   (update state :y dec)
              :down (update state :y inc)
              :left (update state :x dec)
              :right (update state :x inc)
              state))

   :char (fn [state event]
           (if (= (:key event) \q)
             (assoc state :running false)
             state))})
```

### Lesson 4.4: Mouse Events

Handle mouse input:

```clojure
;; Parse mouse events
(def event (events/parse-mouse-event "\u001b[<0;10;5M"))
;; => {:type :mouse, :action :press, :button :left, :x 10, :y 5}

;; Mouse event types:
;; :press - button pressed
;; :release - button released
;; :drag - mouse moved while button held
;; :scroll-up - scroll up
;; :scroll-down - scroll down
```

### Lesson 4.5: Dispatching Events

```clojure
(def state (atom {:x 0, :y 0, :running true}))

(def event (events/parse-key-event "\u001b[A"))  ; Up arrow
(def new-state (events/dispatch-event @state event handlers))

(reset! state new-state)
```

---

## Chapter 5: State Management

### Lesson 5.1: Reactive State

Limner provides reactive state management that automatically triggers re-renders:

```clojure
(require '[limner.state :as state])

;; Create state with change callback
(def app-state
  (state/create-reactive-state
    :on-change (fn [old new]
                 (println "State changed from" old "to" new))))

;; Initialize
(state/set-state! app-state {:count 0})

;; Update
(state/update-state! app-state update :count inc)
;; Prints: State changed from {:count 0} to {:count 1}
```

### Lesson 5.2: Path-Based Operations

Work with nested state:

```clojure
(state/set-state! app-state
  {:user {:name "Alice"
          :score 0}
   :game {:level 1}})

;; Get nested value
(state/get-in-state app-state [:user :name])
;; => "Alice"

;; Update nested value
(state/update-in-state! app-state [:user :score] + 10)

;; Assoc nested value
(state/assoc-in-state! app-state [:game :level] 2)

;; Dissoc nested value
(state/dissoc-in-state! app-state [:user :score])
```

### Lesson 5.3: Watchers

Add watchers for specific state changes:

```clojure
;; Watch a specific path
(state/watch-path app-state [:user :score]
  :score-watcher
  (fn [old-score new-score]
    (when (> new-score 100)
      (println "High score!"))))

;; Watch multiple keys
(state/watch-keys app-state [:count :total]
  :sum-watcher
  (fn [old-state new-state]
    (println "Count or total changed")))

;; Watch with predicate
(state/watch-predicate app-state
  :error-watcher
  (fn [state] (contains? state :error))
  (fn [old new]
    (println "Error occurred:" (:error new))))
```

### Lesson 5.4: Render Integration

Bind state to render loop:

```clojure
(require '[limner.render :as render])

(def app-state (state/create-reactive-state))
(state/set-state! app-state {:count 0})

(def render-control
  (render/create-render-loop
    app-state
    :fps 60
    :render-fn (fn [state]
                 (str "Count: " (:count state)))))

;; Bind them together - state changes trigger re-renders
(state/bind-to-render app-state render-control)
```

---

## Chapter 6: Render Loop

### Lesson 6.1: Creating a Render Loop

The render loop efficiently updates the terminal:

```clojure
(require '[limner.render :as render]
         '[limner.state :as state])

(def app-state (atom {:frame 0}))

(def render-control
  (render/create-render-loop
    app-state
    :fps 30  ; 30 frames per second
    :render-fn (fn [state]
                 (str "Frame: " (:frame state)))))

;; Update state - triggers re-render
(swap! app-state update :frame inc)

;; Stop when done
((:stop! render-control))
```

### Lesson 6.2: Render Function

Your render function receives current state and returns what to display:

```clojure
(defn render-fn [state]
  (let [box (borders/draw-box
              [(str "Score: " (:score state))
               (str "Lives: " (:lives state))]
              :border-style :rounded)]
    (str/join "\n" box)))
```

**Key principle:** The render function is **pure**. Same state = same output.

### Lesson 6.3: Double Buffering

Limner uses double buffering to prevent flicker:

```clojure
(def width 80)
(def height 24)

;; Create buffers
(def front-buffer (render/create-buffer width height))
(def back-buffer (render/create-buffer width height))

;; Write to back buffer
(def new-back (render/write-string-to-buffer
                back-buffer
                10 5
                "Hello!"))

;; Compute diff and swap
(def diff (render/diff-buffers front-buffer new-back))
```

You don't usually work with buffers directly—the render loop handles this.

### Lesson 6.4: FPS Control

Control frame rate:

```clojure
(def render-control
  (render/create-render-loop
    app-state
    :fps 60))  ; Smooth 60 FPS for animations

(def render-control
  (render/create-render-loop
    app-state
    :fps 10))  ; Lower FPS for status displays
```

Higher FPS = smoother animations but more CPU usage.

### Lesson 6.5: Render Statistics

Get rendering statistics:

```clojure
(def stats ((:get-stats render-control)))
;; => {:frames 1234
;;     :total-time 30000
;;     :avg-fps 41.13
;;     :running true}
```

### Lesson 6.6: Error Handling

Handle render errors gracefully:

```clojure
(def render-control
  (render/create-render-loop
    app-state
    :fps 60
    :render-fn render-fn
    :on-error (fn [error]
                (println "Render error:" (.getMessage error))
                ;; Log, notify, or recover
                )))
```

---

## Chapter 7: Complete Application

Let's build a complete interactive counter application:

```clojure
#!/usr/bin/env bb
(require '[limner.core :as core]
         '[limner.borders :as borders]
         '[limner.events :as events]
         '[limner.state :as state]
         '[limner.render :as render])

;; ═══════════ State ═══════════
(def app-state (state/create-state :initial-value {:count 0 :running true}))

;; ═══════════ Rendering ═══════════
(defn render-ui [state]
  (let [count-str (str "Count: " (:count state))
        help-str  "Press SPACE to increment, Q to quit"

        box (borders/draw-titled-box
              "Counter App"
              [count-str "" help-str]
              :border-style :double)

        colored (borders/colorize-border box :cyan)]

    (str (core/clear-screen)
         (core/move-cursor 1 1)
         (str/join "\n" colored))))

;; ═══════════ Event Handlers ═══════════
(def handlers
  {:char (fn [state event]
           (case (:key event)
             \space (update state :count inc)
             \q     (assoc state :running false)
             state))

   :ctrl (fn [state event]
           (if (= (:key event) :ctrl-c)
             (assoc state :running false)
             state))})

;; ═══════════ Main Loop ═══════════
(defn -main []
  ;; Enable raw mode
  (core/enable-raw-mode!)
  (core/hide-cursor!)

  ;; Start render loop
  (def render-control
    (render/create-render-loop
      app-state
      :fps 30
      :render-fn render-ui))

  ;; Event loop
  (while (:running @app-state)
    (when-let [input (core/read-char)]
      (let [event (events/parse-key-event input)
            new-state (events/dispatch-event @app-state event handlers)]
        (reset! app-state new-state))))

  ;; Cleanup
  ((:stop! render-control))
  (core/show-cursor!)
  (core/disable-raw-mode!)
  (println "\nGoodbye!"))

(-main)
```

**How it works:**
1. **State**: Holds count and running flag
2. **Rendering**: Draws bordered box with count and instructions
3. **Events**: Space increments, Q quits
4. **Main loop**: Reads input, dispatches events, updates state
5. **Cleanup**: Restores terminal on exit

---

## Chapter 8: Components

### Lesson 8.1: Progress Bars

Show progress:

```clojure
(require '[limner.components.progress :as progress])

;; Determinate (0-100%)
(def bar (progress/progress-bar :value 65 :width 40))
(println (progress/render bar))
;; => [████████████████████░░░░░░░░░░░░░░░░░░░░]  65%

;; Indeterminate (animated)
(def bar (progress/indeterminate-bar :width 40))
(loop [b bar, n 0]
  (when (< n 100)
    (print "\r" (progress/render b))
    (flush)
    (Thread/sleep 50)
    (recur (progress/tick b) (inc n))))
```

### Lesson 8.2: Spinners

Animated loading indicators:

```clojure
(def spinner (progress/spinner :style :dots))
;; Styles: :dots :line :arrow :circle :bounce :pulse :dots-circle :flip

(loop [s spinner, n 0]
  (when (< n 50)
    (print "\r Loading " (progress/render s))
    (flush)
    (Thread/sleep 100)
    (recur (progress/tick s) (inc n))))

(println "\r Done!              ")
```

### Lesson 8.3: Lists

Selectable lists:

```clojure
(require '[limner.components.list :as list])

(def items ["Option 1" "Option 2" "Option 3" "Option 4"])

(def my-list
  (list/list-component
    :items items
    :selected 0
    :multi-select false))

;; Render
(println (list/render-to-string my-list))

;; Navigation
(def moved-down (list/move-selection my-list :down))
(def moved-up (list/move-selection my-list :up))

;; Selection
(def selected (list/toggle-selection my-list))
```

### Lesson 8.4: Input Fields

Text input:

```clojure
(require '[limner.components.input :as input])

;; Single-line input
(def text-field
  (input/input-field
    :value ""
    :placeholder "Enter your name..."
    :max-length 50))

;; Handle character input
(def updated (input/handle-char text-field \A))

;; Handle special keys
(def with-deletion (input/handle-key updated :backspace))
(def with-cursor-move (input/handle-key updated :left))

;; Multi-line input
(def text-area
  (input/text-area
    :value ""
    :width 40
    :height 10))
```

### Lesson 8.5: Markdown Rendering

Render markdown:

```clojure
(require '[limner.components.markdown :as md])

(def markdown-text "
# Welcome to Limner

This is **bold** and this is *italic*.

## Features

- Beautiful borders
- Flexible layouts
- Event handling

```clojure
(println \"Code blocks!\")
```

> Quotes are supported too.
")

(def rendered (md/render markdown-text))
(println rendered)
```

### Lesson 8.6: Status Bar

Application status line:

```clojure
(require '[limner.components.statusbar :as statusbar])

(def status
  (statusbar/status-bar
    :left "File: document.txt"
    :center "Line 42, Col 18"
    :right "UTF-8 | 45%"))

(println (statusbar/render status 80))  ; 80 chars wide
```

---

## Chapter 9: Advanced Features

### Lesson 9.1: Streaming Text

Animate text character-by-character:

```clojure
(require '[limner.streaming :as stream])

(def code "(defn hello [] (println \"Hello!\"))")

(def streamer
  (stream/stream
    :text code
    :lang :clojure  ; Syntax highlighting
    :delay-ms 30
    :show-cursor true))

;; Start streaming
(loop [s (stream/start streamer)]
  (print "\r" (stream/render s))
  (flush)
  (when-not (stream/complete? s)
    (Thread/sleep (:delay-ms s))
    (recur (stream/tick s))))
```

**States**: `:pending`, `:streaming`, `:paused`, `:cancelled`, `:completed`

**Controls**:
- `(stream/start s)` - Begin streaming
- `(stream/pause s)` - Pause
- `(stream/resume s)` - Resume
- `(stream/cancel s)` - Cancel
- `(stream/skip-to-end s)` - Jump to end

### Lesson 9.2: Syntax Highlighting

Highlight code:

```clojure
(require '[limner.syntax :as syntax])

(def code "(defn factorial [n]
  (if (<= n 1)
    1
    (* n (factorial (dec n)))))")

(def highlighted (syntax/highlight code :clojure))
(println highlighted)
```

**Supported languages**:
- `:clojure`, `:python`, `:javascript`, `:java`
- `:ruby`, `:go`, `:rust`, `:bash`

### Lesson 9.3: Terminal Capabilities

Detect what your terminal supports:

```clojure
(require '[limner.terminal :as term])

;; Detect capabilities
(def caps (term/detect-capabilities))
;; => {:ansi-colors true
;;     :256-colors true
;;     :truecolor true
;;     :unicode true
;;     :mouse true
;;     :box-drawing true}

;; Check specific features
(when (term/supports-feature? :unicode)
  (println "✓ Unicode supported"))

;; Graceful degradation
(def border-style (term/select-border-style))
;; => :single (if unicode) or :ascii (if not)

;; Conditional rendering
(def icon (term/with-fallback :unicode "✓" "[OK]"))
```

### Lesson 9.4: Shadow Effects

Add depth to boxes:

```clojure
(def box (borders/draw-box
           ["Shadow box"]
           :border-style :double))

;; Light shadow
(def with-light-shadow (borders/add-shadow box))

;; Heavy shadow
(def with-heavy-shadow (borders/add-heavy-shadow box))

;; Custom shadow
(def with-custom-shadow
  (borders/add-shadow box
    :shadow-char "█"
    :shadow-color :blue))

(doseq [line with-light-shadow]
  (println line))
```

### Lesson 9.5: Nested Compositions

Build complex layouts:

```clojure
;; Create inner boxes
(def left-box
  (borders/draw-box ["Left" "Panel"] :border-style :rounded))

(def right-box
  (borders/draw-box ["Right" "Panel"] :border-style :rounded))

;; Place side by side
(def combined
  (borders/side-by-side left-box right-box 3))

;; Add padding
(def padded
  (borders/nest-box combined 2))

;; Wrap in outer box
(def final-box
  (borders/draw-titled-box
    "Dashboard"
    padded
    :border-style :double))

;; Add shadow and color
(def styled
  (-> final-box
      borders/add-shadow
      (borders/colorize-border :cyan)))

(doseq [line styled]
  (println line))
```

Result:
```
╔══ Dashboard ══╗░
║               ║░
║  ╭─────╮  ╭──║░
║  │Left │  │Ri║░
║  │Panel│  │Pa║░
║  ╰─────╯  ╰──║░
║               ║░
╚═══════════════╝░
 ░░░░░░░░░░░░░░░░
```

---

## Chapter 10: Real-World Example - Task Manager

Let's build a complete task manager application:

```clojure
#!/usr/bin/env bb
(require '[limner.core :as core]
         '[limner.borders :as borders]
         '[limner.events :as events]
         '[limner.state :as state]
         '[limner.components.list :as list])

;; ═══════════ State ═══════════
(def app-state
  (state/create-state
    :initial-value
    {:tasks [{:id 1 :text "Learn Limner" :done false}
             {:id 2 :text "Build a TUI" :done false}
             {:id 3 :text "Ship to production" :done false}]
     :selected 0
     :running true
     :message ""}))

;; ═══════════ Rendering ═══════════
(defn render-task [idx task selected?]
  (let [checkbox (if (:done task) "[✓]" "[ ]")
        text (:text task)
        style (if (:done task) :dim :reset)
        cursor (if selected? "► " "  ")]
    (core/color style (str cursor checkbox " " text))))

(defn render-ui [state]
  (let [tasks (:tasks state)
        selected (:selected state)

        task-lines (map-indexed
                     (fn [idx task]
                       (render-task idx task (= idx selected)))
                     tasks)

        help ["" "Keys: j/k=move, Space=toggle, a=add, d=delete, q=quit"]
        message-line (if (seq (:message state))
                      [(core/color :yellow (:message state))]
                      [])

        all-lines (concat task-lines message-line help)

        box (borders/draw-titled-box
              (str "Task Manager (" (count tasks) " tasks)")
              all-lines
              :border-style :double)

        colored (borders/colorize-border box :cyan)]

    (str (core/clear-screen)
         (core/move-cursor 1 1)
         (str/join "\n" colored))))

;; ═══════════ Actions ═══════════
(defn move-selection [state direction]
  (let [tasks (:tasks state)
        current (:selected state)
        new-pos (case direction
                  :up (max 0 (dec current))
                  :down (min (dec (count tasks)) (inc current))
                  current)]
    (assoc state :selected new-pos :message "")))

(defn toggle-task [state]
  (let [selected (:selected state)]
    (-> state
        (update-in [:tasks selected :done] not)
        (assoc :message "Task toggled"))))

(defn add-task [state]
  (let [new-id (inc (apply max (map :id (:tasks state))))
        new-task {:id new-id :text "New task" :done false}]
    (-> state
        (update :tasks conj new-task)
        (assoc :selected (count (:tasks state)))
        (assoc :message "Task added"))))

(defn delete-task [state]
  (let [selected (:selected state)
        tasks (:tasks state)]
    (if (empty? tasks)
      state
      (-> state
          (assoc :tasks (vec (concat (take selected tasks)
                                     (drop (inc selected) tasks))))
          (update :selected #(min % (dec (count tasks))))
          (assoc :message "Task deleted")))))

;; ═══════════ Event Handlers ═══════════
(def handlers
  {:char (fn [state event]
           (case (:key event)
             \j (move-selection state :down)
             \k (move-selection state :up)
             \space (toggle-task state)
             \a (add-task state)
             \d (delete-task state)
             \q (assoc state :running false)
             state))

   :arrow (fn [state event]
            (case (:key event)
              :up (move-selection state :up)
              :down (move-selection state :down)
              state))

   :ctrl (fn [state event]
           (if (= (:key event) :ctrl-c)
             (assoc state :running false)
             state))})

;; ═══════════ Main ═══════════
(defn -main []
  (core/enable-raw-mode!)
  (core/hide-cursor!)

  (try
    (while (:running @app-state)
      ;; Render
      (print (render-ui @app-state))
      (flush)

      ;; Handle input
      (when-let [input (core/read-char)]
        (let [event (events/parse-key-event input)
              new-state (events/dispatch-event @app-state event handlers)]
          (reset! app-state new-state))))

    (finally
      (core/show-cursor!)
      (core/disable-raw-mode!)
      (println "\nGoodbye!"))))

(-main)
```

**Features:**
- ✓ Task list with checkboxes
- ✓ Keyboard navigation (j/k or arrows)
- ✓ Toggle completion (space)
- ✓ Add/delete tasks (a/d)
- ✓ Status messages
- ✓ Clean exit (q)

---

## Chapter 11: Best Practices

### 11.1: Keep Rendering Pure

**Do:**
```clojure
(defn render-ui [state]
  (str "Count: " (:count state)))
```

**Don't:**
```clojure
(defn render-ui [state]
  (println "Rendering!")  ; Side effect!
  (str "Count: " (:count state)))
```

### 11.2: Use State Management

**Do:**
```clojure
(def app-state (state/create-state :initial-value {:count 0}))
(state/update-state! app-state update :count inc)
```

**Don't:**
```clojure
(def app-state (atom {:count 0}))
(swap! app-state update :count inc)  ; Works but misses reactive features
```

### 11.3: Handle Errors

**Do:**
```clojure
(def render-control
  (render/create-render-loop
    app-state
    :render-fn render-ui
    :on-error (fn [e]
                (log-error e)
                (safe-render-fallback))))
```

**Don't:**
```clojure
(def render-control
  (render/create-render-loop
    app-state
    :render-fn render-ui))  ; Crashes on error
```

### 11.4: Clean Up Resources

**Do:**
```clojure
(try
  (core/enable-raw-mode!)
  (core/hide-cursor!)
  ;; ... app logic ...
  (finally
    ((:stop! render-control))
    (core/show-cursor!)
    (core/disable-raw-mode!)))
```

**Don't:**
```clojure
(core/enable-raw-mode!)
;; ... app logic ...
;; Forgot to restore terminal!
```

### 11.5: Use Terminal Capabilities

**Do:**
```clojure
(require '[limner.terminal :as term])

(def border-style (term/select-border-style))
(def icon (term/with-fallback :unicode "✓" "[OK]"))
```

**Don't:**
```clojure
(def border-style :rounded)  ; Might break on ASCII-only terminals
(def icon "✓")  ; Might not render correctly
```

### 11.6: Optimize Rendering

**Do:**
```clojure
;; Only re-render on state changes
(def render-control
  (render/create-render-loop
    app-state
    :fps 30))  ; Reasonable FPS
```

**Don't:**
```clojure
(while true
  (println (render-ui @app-state))  ; Renders continuously!
  (Thread/sleep 1))
```

---

## Chapter 12: Troubleshooting

### Problem: Terminal Doesn't Reset After Exit

**Solution:** Always cleanup in a `finally` block:

```clojure
(try
  (core/enable-raw-mode!)
  ;; ... app logic ...
  (finally
    (core/disable-raw-mode!)
    (core/show-cursor!)))
```

### Problem: Colors Don't Show

**Check:**
1. Terminal supports colors: `(term/supports-feature? :ansi-colors)`
2. Use capability detection: `(term/select-color-mode)`
3. Environment variable `TERM` is set

### Problem: Unicode Renders Incorrectly

**Check:**
1. Terminal supports Unicode: `(term/supports-feature? :unicode)`
2. Locale is UTF-8: `echo $LANG`
3. Use fallbacks: `(term/with-fallback :unicode "✓" "[OK]")`

### Problem: Mouse Events Don't Work

**Check:**
1. Mouse mode enabled: `(core/enable-mouse-tracking!)`
2. Terminal supports mouse: `(term/supports-feature? :mouse)`
3. Using correct parsing: `(events/parse-mouse-event input)`

### Problem: Application Freezes

**Possible causes:**
1. Blocking operation in render function
2. Deadlock in state updates
3. Event loop not processing

**Debug:**
```clojure
(def render-control
  (render/create-render-loop
    app-state
    :render-fn render-ui
    :on-error (fn [e]
                (println "Error:" e)  ; Debug output
                )))
```

---

## Conclusion

You've learned:
- ✓ How to create beautiful bordered boxes
- ✓ How to use colors and styling
- ✓ How to compose flexible layouts
- ✓ How to handle keyboard and mouse events
- ✓ How to manage reactive state
- ✓ How to build complete interactive applications
- ✓ How to use all major components
- ✓ Best practices for TUI development

### Next Steps

1. **Explore examples:** Run all demos in `examples/` directory
2. **Read the code:** Check out the source in `src/limner/`
3. **Build something:** Create your own TUI application
4. **Contribute:** Submit improvements or new components

### Additional Resources

- [API Reference](api.md) (planned)
- [Architecture Guide](architecture.md) (planned)
- [Code Review](../plans/code_review.md)
- [GitHub Issues](https://github.com/yourusername/limner/issues)

---

**Happy TUI building!**

*May your terminals be colorful and your layouts be flexible.*
