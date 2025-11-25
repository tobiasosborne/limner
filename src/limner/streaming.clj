(ns limner.streaming
  "Character-by-character text streaming with syntax highlighting and cursor effects"
  (:require [clojure.string :as str]
            [limner.core :as core]
            [limner.syntax :as syntax]))

;; ────────────────────── Streaming Component ──────────────────────

(defn stream
  "Create a streaming text component
   Options:
   - :text - full text to stream
   - :delay-ms - delay between characters in milliseconds (default 30)
   - :lang - language for syntax highlighting (default nil)
   - :theme - theme for syntax highlighting (default :default)
   - :show-cursor - show blinking cursor at end (default true)
   - :cursor-char - character for cursor (default \"▋\")
   - :cursor-blink-ms - cursor blink interval in ms (default 500)"
  [& {:keys [text delay-ms lang theme show-cursor cursor-char cursor-blink-ms]
      :or {text ""
           delay-ms 30
           theme :default
           show-cursor true
           cursor-char "▋"
           cursor-blink-ms 500}}]
  {:type :stream
   :text text
   :position 0
   :delay-ms delay-ms
   :lang lang
   :theme theme
   :show-cursor show-cursor
   :cursor-char cursor-char
   :cursor-blink-ms cursor-blink-ms
   :cursor-visible true
   :cursor-frame 0
   :state :pending  ; :pending, :streaming, :paused, :cancelled, :completed
   :last-update (System/currentTimeMillis)})

;; ────────────────────── State Management ──────────────────────

(defn start
  "Start streaming"
  [stream-component]
  (assoc stream-component
         :state :streaming
         :last-update (System/currentTimeMillis)))

(defn pause
  "Pause streaming"
  [stream-component]
  (assoc stream-component :state :paused))

(defn resume
  "Resume streaming from paused state"
  [stream-component]
  (if (= (:state stream-component) :paused)
    (assoc stream-component
           :state :streaming
           :last-update (System/currentTimeMillis))
    stream-component))

(defn cancel
  "Cancel streaming - stops at current position"
  [stream-component]
  (assoc stream-component :state :cancelled))

(defn reset-stream
  "Reset stream to beginning"
  [stream-component]
  (assoc stream-component
         :position 0
         :state :pending
         :last-update (System/currentTimeMillis)))

(defn set-text
  "Update stream text and reset position"
  [stream-component text]
  (assoc stream-component
         :text text
         :position 0
         :state :pending
         :last-update (System/currentTimeMillis)))

;; ────────────────────── Stream Advancement ──────────────────────

(defn advance
  "Advance stream by one character if enough time has elapsed
   Returns updated stream component"
  [stream-component]
  (let [current-time (System/currentTimeMillis)
        elapsed (- current-time (:last-update stream-component))
        text-length (count (:text stream-component))
        position (:position stream-component)
        state (:state stream-component)]

    (cond
      ;; Not streaming - don't advance
      (not= state :streaming)
      stream-component

      ;; Already at end - mark as completed
      (>= position text-length)
      (assoc stream-component :state :completed)

      ;; Not enough time elapsed
      (< elapsed (:delay-ms stream-component))
      stream-component

      ;; Advance to next character
      :else
      (assoc stream-component
             :position (inc position)
             :last-update current-time))))

(defn tick-cursor
  "Update cursor blink state based on elapsed time"
  [stream-component]
  (let [current-time (System/currentTimeMillis)
        elapsed (- current-time (:last-update stream-component))
        blink-interval (:cursor-blink-ms stream-component)

        ;; Calculate cursor frame (alternates between 0 and 1)
        cursor-frame (:cursor-frame stream-component)
        new-frame (int (mod (/ current-time blink-interval) 2))
        cursor-visible (= new-frame 0)]

    (assoc stream-component
           :cursor-frame new-frame
           :cursor-visible cursor-visible)))

(defn tick
  "Update stream state (advance text and update cursor)"
  [stream-component]
  (-> stream-component
      advance
      tick-cursor))

;; ────────────────────── Rendering ──────────────────────

(defn- get-visible-text
  "Get the currently visible portion of text"
  [stream-component]
  (let [text (:text stream-component)
        position (:position stream-component)]
    (subs text 0 (min position (count text)))))

(defn- apply-syntax-highlighting
  "Apply syntax highlighting to visible text"
  [text lang theme]
  (if (and lang (not= lang :none))
    (syntax/highlight text lang :theme theme)
    text))

(defn- add-cursor
  "Add blinking cursor at end of text if streaming is complete"
  [text stream-component]
  (let [state (:state stream-component)
        show-cursor (:show-cursor stream-component)
        cursor-visible (:cursor-visible stream-component)
        cursor-char (:cursor-char stream-component)]

    (if (and show-cursor
             (= state :completed)
             cursor-visible)
      (str text (core/color :cyan cursor-char))
      text)))

(defn render
  "Render stream to string with optional syntax highlighting"
  [stream-component]
  (let [visible-text (get-visible-text stream-component)
        lang (:lang stream-component)
        theme (:theme stream-component)

        ;; Apply syntax highlighting to visible text
        highlighted (apply-syntax-highlighting visible-text lang theme)

        ;; Add cursor if completed
        with-cursor (add-cursor highlighted stream-component)]

    with-cursor))

;; ────────────────────── Query Functions ──────────────────────

(defn completed?
  "Check if streaming is completed"
  [stream-component]
  (= (:state stream-component) :completed))

(defn streaming?
  "Check if currently streaming"
  [stream-component]
  (= (:state stream-component) :streaming))

(defn paused?
  "Check if streaming is paused"
  [stream-component]
  (= (:state stream-component) :paused))

(defn cancelled?
  "Check if streaming was cancelled"
  [stream-component]
  (= (:state stream-component) :cancelled))

(defn progress
  "Get streaming progress as percentage (0-100)"
  [stream-component]
  (let [text-length (count (:text stream-component))
        position (:position stream-component)]
    (if (zero? text-length)
      100
      (int (* 100 (/ position text-length))))))

(defn remaining-chars
  "Get number of characters remaining to stream"
  [stream-component]
  (let [text-length (count (:text stream-component))
        position (:position stream-component)]
    (max 0 (- text-length position))))

;; ────────────────────── Helper Functions ──────────────────────

(defn stream-to-completion
  "Stream all text immediately (useful for testing)"
  [stream-component]
  (let [text-length (count (:text stream-component))]
    (assoc stream-component
           :position text-length
           :state :completed)))

(defn set-position
  "Set stream position directly (useful for seeking)"
  [stream-component position]
  (let [text-length (count (:text stream-component))
        clamped-pos (max 0 (min position text-length))]
    (assoc stream-component
           :position clamped-pos
           :last-update (System/currentTimeMillis))))
