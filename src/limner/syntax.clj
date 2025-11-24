(ns limner.syntax
  "Language-aware syntax highlighting with pluggable rules and themes"
  (:require [clojure.string :as str]
            [limner.core :as core]))

;; ────────────────────── Themes ──────────────────────
(def themes
  "Color schemes for syntax highlighting"
  {:default {:keyword :cyan
             :string :green
             :comment :bright-green
             :number :yellow
             :function :blue
             :operator :cyan
             :decorator :yellow
             :special :bright-green}

   :monokai {:keyword :cyan
             :string :yellow
             :comment :bright-green
             :number :bright-green
             :function :green
             :operator :red
             :decorator :yellow
             :special :cyan}

   :solarized {:keyword :blue
               :string :cyan
               :comment :bright-green
               :number :green
               :function :blue
               :operator :cyan
               :decorator :yellow
               :special :yellow}})

(defn get-theme
  "Get theme by name, defaults to :default"
  [theme-name]
  (get themes theme-name (:default themes)))

(defn available-themes
  "List all available theme names"
  []
  (keys themes))

;; ────────────────────── Language Rules ──────────────────────
(def clojure-keywords
  #{"def" "defn" "defn-" "defmacro" "defmethod" "defmulti" "defonce" "defprotocol"
    "defrecord" "defstruct" "deftype" "let" "letfn" "if" "when" "when-not" "when-let"
    "when-first" "if-not" "if-let" "cond" "condp" "case" "do" "loop" "recur" "fn"
    "require" "use" "import" "ns" "in-ns" "refer" "quote" "var" "try" "catch" "finally"
    "throw" "assert" "and" "or" "not" "binding" "with-open" "with-redefs" "doseq" "dotimes"
    "for" "while" "lazy-seq" "delay" "future" "promise" "atom" "ref" "agent" "swap!"
    "reset!" "deref" "set!" "alter" "commute" "send" "send-off"})

(def python-keywords
  #{"def" "class" "if" "elif" "else" "for" "while" "break" "continue" "return" "yield"
    "import" "from" "as" "with" "try" "except" "finally" "raise" "assert" "pass" "lambda"
    "and" "or" "not" "in" "is" "None" "True" "False" "async" "await" "del" "global"
    "nonlocal" "exec" "print"})

(def javascript-keywords
  #{"const" "let" "var" "function" "return" "if" "else" "for" "while" "do" "switch"
    "case" "default" "break" "continue" "try" "catch" "finally" "throw" "new" "this"
    "class" "extends" "static" "async" "await" "import" "export" "from" "as" "typeof"
    "instanceof" "delete" "void" "yield" "true" "false" "null" "undefined"})

(def language-rules
  {:clojure {:keywords clojure-keywords
             :comment-char ";"
             :string-delimiters ["\""]
             :number-pattern #"\b\d+\.?\d*\b"
             :keyword-pattern #":\S+"
             :symbol-pattern #"[a-zA-Z\-\+\*\/\?<>=!][a-zA-Z0-9\-\+\*\/\?<>=!]*"}

   :python {:keywords python-keywords
            :comment-char "#"
            :string-delimiters ["\"" "'" "\"\"\"" "'''"]
            :number-pattern #"\b\d+\.?\d*\b"
            :decorator-pattern #"@[\w\.]+"
            :fstring-pattern #"f['\"].*?['\"]"}

   :javascript {:keywords javascript-keywords
                :comment-char "//"
                :multiline-comment ["/*" "*/"]
                :string-delimiters ["\"" "'" "`"]
                :template-literal "`"
                :number-pattern #"\b\d+\.?\d*\b"
                :arrow-function #"=>"}})

;; ────────────────────── Tokenization ──────────────────────
(defn- match-string
  "Find string match starting at position"
  [code pos delim]
  (let [start pos
        code-from-pos (subs code pos)]
    (when (str/starts-with? code-from-pos delim)
      (let [delim-len (count delim)
            search-from (+ pos delim-len)
            rest-code (subs code search-from)]
        (loop [i 0
               escaped false]
          (cond
            (>= i (count rest-code))
            {:type :string
             :value (subs code pos)
             :end (count code)}

            escaped
            (recur (inc i) false)

            (= (nth rest-code i) \\)
            (recur (inc i) true)

            (str/starts-with? (subs rest-code i) delim)
            {:type :string
             :value (subs code pos (+ search-from i (count delim)))
             :end (+ search-from i (count delim))}

            :else
            (recur (inc i) false)))))))

(defn- match-comment
  "Find comment match starting at position"
  [code pos comment-char]
  (when (str/starts-with? (subs code pos) comment-char)
    (let [newline-pos (str/index-of code "\n" pos)
          end-pos (if newline-pos newline-pos (count code))]
      {:type :comment
       :value (subs code pos end-pos)
       :end end-pos})))

(defn- match-multiline-comment
  "Find multiline comment (/* */) starting at position"
  [code pos start-delim end-delim]
  (when (str/starts-with? (subs code pos) start-delim)
    (let [search-from (+ pos (count start-delim))
          end-pos (str/index-of code end-delim search-from)]
      (if end-pos
        {:type :comment
         :value (subs code pos (+ end-pos (count end-delim)))
         :end (+ end-pos (count end-delim))}
        {:type :comment
         :value (subs code pos)
         :end (count code)}))))

(defn- match-keyword
  "Find keyword match (Clojure :keyword style)"
  [code pos pattern]
  (when-let [match (re-find pattern (subs code pos))]
    (when (= pos (str/index-of code match pos))
      {:type :keyword
       :value match
       :end (+ pos (count match))})))

(defn- match-decorator
  "Find Python decorator (@decorator)"
  [code pos pattern]
  (when-let [match (re-find pattern (subs code pos))]
    (when (= pos (str/index-of code match pos))
      {:type :decorator
       :value match
       :end (+ pos (count match))})))

(defn- match-number
  "Find number literal"
  [code pos pattern]
  (when-let [match (re-find pattern (subs code pos))]
    (when (= pos (str/index-of code match pos))
      {:type :number
       :value match
       :end (+ pos (count match))})))

(defn- match-word-keyword
  "Find language keyword (def, if, etc.)"
  [code pos keywords]
  (let [word-match (re-find #"[a-zA-Z\-\+\*\/\?<>=!][a-zA-Z0-9\-\+\*\/\?<>=!]*" (subs code pos))]
    (when (and word-match
               (= pos (str/index-of code word-match pos))
               (contains? keywords word-match))
      {:type :keyword
       :value word-match
       :end (+ pos (count word-match))})))

(defn tokenize
  "Tokenize code into a sequence of typed tokens"
  [code lang]
  (let [rules (get language-rules lang {})
        keywords (:keywords rules)
        comment-char (:comment-char rules)
        string-delims (:string-delimiters rules [])
        multiline-comment (:multiline-comment rules)
        keyword-pattern (:keyword-pattern rules)
        number-pattern (:number-pattern rules)
        decorator-pattern (:decorator-pattern rules)]

    (loop [pos 0
           tokens []]
      (if (>= pos (count code))
        tokens
        (let [char (nth code pos)
              ;; Try to match various token types
              string-match (some #(match-string code pos %) string-delims)
              comment-match (when comment-char (match-comment code pos comment-char))
              multiline-comment-match (when multiline-comment
                                       (match-multiline-comment code pos
                                                                (first multiline-comment)
                                                                (second multiline-comment)))
              keyword-match (when keyword-pattern (match-keyword code pos keyword-pattern))
              decorator-match (when decorator-pattern (match-decorator code pos decorator-pattern))
              number-match (when number-pattern (match-number code pos number-pattern))
              word-keyword-match (when keywords (match-word-keyword code pos keywords))

              ;; Priority: comments, strings, decorators, keywords, numbers, then default
              token (or multiline-comment-match
                       comment-match
                       string-match
                       decorator-match
                       keyword-match
                       word-keyword-match
                       number-match
                       {:type :default
                        :value (str char)
                        :end (inc pos)})]

          (recur (:end token)
                 (conj tokens token)))))))

;; ────────────────────── Highlighting ──────────────────────
(defn- apply-token-color
  "Apply color to token based on theme"
  [token theme]
  (let [color (get theme (:type token) nil)
        value (:value token)]
    (if color
      (core/color color value)
      value)))

(defn highlight
  "Highlight code with syntax coloring
   Options:
   - :theme - theme map or keyword (default :default)"
  [code lang & {:keys [theme]
                :or {theme :default}}]
  (if (empty? code)
    ""
    (let [theme-map (if (keyword? theme)
                     (get-theme theme)
                     theme)
          tokens (tokenize code lang)
          highlighted-tokens (map #(apply-token-color % theme-map) tokens)]
      (apply str highlighted-tokens))))

;; ────────────────────── Language Detection ──────────────────────
(def extension-map
  {"clj" :clojure
   "cljs" :clojure
   "cljc" :clojure
   "py" :python
   "js" :javascript
   "jsx" :javascript
   "ts" :javascript
   "tsx" :javascript})

(defn detect-language
  "Detect language from filename"
  [filename]
  (when filename
    (let [ext (last (str/split filename #"\."))]
      (get extension-map ext :unknown))))

(defn detect-language-from-content
  "Detect language from code content (simple heuristic)"
  [code]
  (cond
    (re-find #"^\(def[n]?\s" code) :clojure
    (re-find #"^def\s+\w+\(" code) :python
    (re-find #"^const\s+\w+\s*=" code) :javascript
    (re-find #"=>" code) :javascript
    (str/includes? code "(ns ") :clojure
    (str/includes? code "import ") :python
    :else :unknown))

;; ────────────────────── Utility Functions ──────────────────────
(defn highlight-with-line-numbers
  "Highlight code and add line numbers"
  [code lang & {:keys [theme start-line]
                :or {theme :default
                     start-line 1}}]
  (let [lines (str/split-lines code)
        max-line-num (+ start-line (count lines))
        num-width (count (str max-line-num))
        format-str (str "%" num-width "d │ %s")]
    (->> lines
         (map-indexed (fn [idx line]
                       (let [line-num (+ start-line idx)
                             highlighted (highlight line lang :theme theme)]
                         (format format-str line-num highlighted))))
         (str/join "\n"))))

(defn strip-colors
  "Remove ANSI color codes from string"
  [s]
  (str/replace s #"\u001B\[[0-9;]*m" ""))
