(ns limner.terminal
  "Terminal capability detection for graceful degradation

   This module detects terminal capabilities and provides helpers for
   graceful degradation when features are not supported."
  (:require [clojure.string :as str]))

;; ────────────────────── Environment Detection ──────────────────────

(defn get-env
  "Get environment variable value, returns nil if not set"
  [var-name]
  (System/getenv var-name))

(defn detect-term-type
  "Detect terminal type from TERM environment variable"
  []
  (or (get-env "TERM") "unknown"))

(defn detect-color-term
  "Detect color support from COLORTERM environment variable"
  []
  (get-env "COLORTERM"))

(defn detect-locale
  "Detect locale from LANG or LC_ALL environment variables"
  []
  (or (get-env "LC_ALL")
      (get-env "LANG")
      "C"))

;; ────────────────────── Capability Detection ──────────────────────

(defn supports-ansi-colors?
  "Check if terminal supports ANSI colors

   Checks:
   - TERM variable contains 'color', 'xterm', 'screen', or 'tmux'
   - COLORTERM is set
   - Terminal is not 'dumb'"
  []
  (let [term (str/lower-case (detect-term-type))
        colorterm (detect-color-term)]
    (and (not= term "dumb")
         (or colorterm
             (str/includes? term "color")
             (str/includes? term "xterm")
             (str/includes? term "screen")
             (str/includes? term "tmux")
             (str/includes? term "rxvt")
             (str/includes? term "linux")))))

(defn supports-256-colors?
  "Check if terminal supports 256-color palette

   Checks:
   - TERM contains '256color'
   - COLORTERM is set to 'truecolor' or '24bit'"
  []
  (let [term (str/lower-case (detect-term-type))
        colorterm (str/lower-case (or (detect-color-term) ""))]
    (or (str/includes? term "256color")
        (str/includes? colorterm "truecolor")
        (str/includes? colorterm "24bit"))))

(defn supports-truecolor?
  "Check if terminal supports true color (24-bit RGB)

   Checks:
   - COLORTERM is 'truecolor' or '24bit'
   - Some modern terminals (iTerm2, Konsole, etc.)"
  []
  (let [term (str/lower-case (detect-term-type))
        colorterm (str/lower-case (or (detect-color-term) ""))]
    (or (= colorterm "truecolor")
        (= colorterm "24bit")
        (str/includes? term "iterm")
        (str/includes? term "konsole")
        (str/includes? term "gnome-terminal")
        (str/includes? term "vte"))))

(defn supports-unicode?
  "Check if terminal supports Unicode

   Checks:
   - LANG or LC_ALL contains 'UTF-8' or 'utf8'"
  []
  (let [locale (str/upper-case (detect-locale))]
    (or (str/includes? locale "UTF-8")
        (str/includes? locale "UTF8"))))

(defn supports-box-drawing?
  "Check if terminal supports Unicode box drawing characters

   Same as Unicode support check, but semantically clearer"
  []
  (supports-unicode?))

(defn detect-windows?
  "Detect if running on Windows

   Checks os.name system property"
  []
  (let [os-name (str/lower-case (System/getProperty "os.name"))]
    (str/includes? os-name "windows")))

(defn supports-mouse?
  "Check if terminal likely supports mouse tracking

   Most modern terminals support it, but 'dumb' and very old terminals don't"
  []
  (let [term (str/lower-case (detect-term-type))]
    (not (or (= term "dumb")
             (= term "unknown")
             (str/starts-with? term "vt")))))

;; ────────────────────── Capability Map ──────────────────────

(defn detect-capabilities
  "Detect all terminal capabilities and return a map

   Returns a map with keys:
   - :term - terminal type string
   - :ansi-colors - boolean, supports ANSI colors
   - :256-colors - boolean, supports 256-color palette
   - :truecolor - boolean, supports 24-bit RGB
   - :unicode - boolean, supports Unicode
   - :box-drawing - boolean, supports Unicode box drawing
   - :mouse - boolean, supports mouse tracking
   - :windows - boolean, running on Windows
   - :locale - locale string
   - :colorterm - COLORTERM value"
  []
  {:term          (detect-term-type)
   :ansi-colors   (supports-ansi-colors?)
   :256-colors    (supports-256-colors?)
   :truecolor     (supports-truecolor?)
   :unicode       (supports-unicode?)
   :box-drawing   (supports-box-drawing?)
   :mouse         (supports-mouse?)
   :windows       (detect-windows?)
   :locale        (detect-locale)
   :colorterm     (detect-color-term)})

;; ────────────────────── Graceful Degradation Helpers ──────────────────────

(def ^:dynamic *capabilities*
  "Dynamic var for current terminal capabilities
   Can be overridden for testing or forced behavior"
  nil)

(defn get-capabilities
  "Get current terminal capabilities

   Returns cached *capabilities* if bound, otherwise detects fresh"
  []
  (or *capabilities* (detect-capabilities)))

(defn supports-feature?
  "Check if current terminal supports a feature

   Valid features: :ansi-colors :256-colors :truecolor :unicode
                   :box-drawing :mouse"
  [feature]
  (get (get-capabilities) feature false))

(defn with-fallback
  "Use value if feature is supported, otherwise use fallback

   Usage:
   (with-fallback :unicode \"┌\" \"+\")  ; Returns \"+\" if no Unicode"
  [feature value fallback]
  (if (supports-feature? feature)
    value
    fallback))

(defn select-border-style
  "Select appropriate border style based on terminal capabilities

   Returns :single if Unicode supported, :ascii otherwise"
  []
  (if (supports-feature? :box-drawing)
    :single
    :ascii))

(defn select-color-mode
  "Select appropriate color mode based on terminal capabilities

   Returns :truecolor, :256-colors, :ansi, or :none"
  []
  (cond
    (supports-feature? :truecolor)   :truecolor
    (supports-feature? :256-colors)  :256-colors
    (supports-feature? :ansi-colors) :ansi
    :else                            :none))

(defn maybe-colorize
  "Apply color if supported, otherwise return text unchanged

   Usage:
   (maybe-colorize :red \"Error\")  ; Returns colored or plain based on support"
  [color text]
  (if (supports-feature? :ansi-colors)
    ;; We'll apply color - actual coloring done by caller
    {:color color :text text}
    {:color nil :text text}))

;; ────────────────────── Capability Report ──────────────────────

(defn capability-report
  "Generate a human-readable capability report"
  []
  (let [caps (get-capabilities)]
    (str "Terminal Capabilities:\n"
         "  Type: " (:term caps) "\n"
         "  Locale: " (:locale caps) "\n"
         "  COLORTERM: " (or (:colorterm caps) "(not set)") "\n"
         "  Windows: " (if (:windows caps) "Yes" "No") "\n"
         "\n"
         "Color Support:\n"
         "  ANSI (16 colors): " (if (:ansi-colors caps) "✓ Yes" "✗ No") "\n"
         "  256 colors: " (if (:256-colors caps) "✓ Yes" "✗ No") "\n"
         "  True color (RGB): " (if (:truecolor caps) "✓ Yes" "✗ No") "\n"
         "  Recommended: " (name (select-color-mode)) "\n"
         "\n"
         "Other Features:\n"
         "  Unicode: " (if (:unicode caps) "✓ Yes" "✗ No") "\n"
         "  Box drawing: " (if (:box-drawing caps) "✓ Yes" "✗ No") "\n"
         "  Mouse support: " (if (:mouse caps) "✓ Yes (likely)" "✗ No") "\n"
         "  Border style: " (name (select-border-style)))))

;; ────────────────────── Testing Helpers ──────────────────────

(defmacro with-simulated-capabilities
  "Execute body with simulated terminal capabilities (for testing)

   Usage:
   (with-simulated-capabilities {:unicode false :ansi-colors true}
     (select-border-style))  ; => :ascii"
  [caps & body]
  `(binding [*capabilities* (merge (detect-capabilities) ~caps)]
     ~@body))

(defn simulate-dumb-terminal
  "Return capability map for a dumb terminal (minimal capabilities)"
  []
  {:term          "dumb"
   :ansi-colors   false
   :256-colors    false
   :truecolor     false
   :unicode       false
   :box-drawing   false
   :mouse         false
   :windows       false
   :locale        "C"
   :colorterm     nil})

(defn simulate-modern-terminal
  "Return capability map for a modern terminal (all capabilities)"
  []
  {:term          "xterm-256color"
   :ansi-colors   true
   :256-colors    true
   :truecolor     true
   :unicode       true
   :box-drawing   true
   :mouse         true
   :windows       false
   :locale        "en_US.UTF-8"
   :colorterm     "truecolor"})
