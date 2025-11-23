(ns limner.components.markdown
  "Markdown renderer - extensible markdown to ANSI-styled text

  Architecture:
  1. Parser: Markdown string -> AST (Abstract Syntax Tree)
  2. Renderer: AST -> ANSI-styled strings
  3. Registry: Pluggable renderers for each node type

  Design for extensibility:
  - Each markdown element is a node with :type and :content
  - Renderers are functions registered by node type
  - Easy to add new markdown features or override defaults
  - Clean integration point for future syntax highlighting"
  (:require [clojure.string :as str]
            [limner.core :as core]))

;; ═══════════════════════════════════════════════════════════════════════
;; AST NODE TYPES
;; ═══════════════════════════════════════════════════════════════════════
;; Nodes are maps with :type and type-specific keys
;; This makes the system easily extensible

(defn text-node
  "Plain text node"
  [content]
  {:type :text
   :content content})

(defn bold-node
  "Bold text node"
  [content]
  {:type :bold
   :content content})

(defn italic-node
  "Italic text node"
  [content]
  {:type :italic
   :content content})

(defn code-node
  "Inline code node"
  [content]
  {:type :code
   :content content})

(defn link-node
  "Link node with text and URL"
  [text url]
  {:type :link
   :text text
   :url url})

(defn header-node
  "Header node with level (1-6) and inline content"
  [level content]
  {:type :header
   :level level
   :content content})

(defn paragraph-node
  "Paragraph node with inline content"
  [content]
  {:type :paragraph
   :content content})

(defn code-block-node
  "Code block node with optional language"
  [content lang]
  {:type :code-block
   :content content
   :lang lang})

(defn list-node
  "List node (ordered or unordered) with items"
  [ordered? items]
  {:type :list
   :ordered? ordered?
   :items items})

(defn list-item-node
  "List item with inline content and optional nested items"
  [content nested-items]
  {:type :list-item
   :content content
   :nested-items nested-items})

(defn blockquote-node
  "Blockquote node with block content"
  [content]
  {:type :blockquote
   :content content})

(defn hr-node
  "Horizontal rule node"
  []
  {:type :hr})

;; ═══════════════════════════════════════════════════════════════════════
;; INLINE PARSING
;; ═══════════════════════════════════════════════════════════════════════

(defn parse-inline-code
  "Parse inline code: `code`"
  [text]
  (let [pattern #"`([^`]+)`"]
    (if-let [match (re-find pattern text)]
      (let [[full code] match
            before (subs text 0 (.indexOf text full))
            after (subs text (+ (.indexOf text full) (count full)))]
        (concat
         (when-not (str/blank? before) [(text-node before)])
         [(code-node code)]
         (parse-inline-code after)))
      [(text-node text)])))

(defn parse-bold
  "Parse bold text: **bold** or __bold__"
  [nodes]
  (mapcat
   (fn [node]
     (if (not= :text (:type node))
       [node]
       (let [text (:content node)
             pattern #"\*\*([^\*]+)\*\*|__([^_]+)__"]
         (if-let [match (re-find pattern text)]
           (let [[full group1 group2] match
                 bold-content (or group1 group2)
                 before (subs text 0 (.indexOf text full))
                 after (subs text (+ (.indexOf text full) (count full)))]
             (concat
              (when-not (str/blank? before) [(text-node before)])
              [(bold-node bold-content)]
              (parse-bold [(text-node after)])))
           [node]))))
   nodes))

(defn parse-italic
  "Parse italic text: *italic* or _italic_"
  [nodes]
  (mapcat
   (fn [node]
     (if (not= :text (:type node))
       [node]
       (let [text (:content node)
             pattern #"(?<!\*)\*([^\*]+)\*(?!\*)|(?<!_)_([^_]+)_(?!_)"]
         (if-let [match (re-find pattern text)]
           (let [[full group1 group2] match
                 italic-content (or group1 group2)
                 before (subs text 0 (.indexOf text full))
                 after (subs text (+ (.indexOf text full) (count full)))]
             (concat
              (when-not (str/blank? before) [(text-node before)])
              [(italic-node italic-content)]
              (parse-italic [(text-node after)])))
           [node]))))
   nodes))

(defn parse-links
  "Parse links: [text](url)"
  [nodes]
  (mapcat
   (fn [node]
     (if (not= :text (:type node))
       [node]
       (let [text (:content node)
             pattern #"\[([^\]]+)\]\(([^\)]+)\)"]
         (if-let [match (re-find pattern text)]
           (let [[full link-text url] match
                 before (subs text 0 (.indexOf text full))
                 after (subs text (+ (.indexOf text full) (count full)))]
             (concat
              (when-not (str/blank? before) [(text-node before)])
              [(link-node link-text url)]
              (parse-links [(text-node after)])))
           [node]))))
   nodes))

(defn parse-inline
  "Parse inline markdown (bold, italic, code, links)
   Returns vector of inline nodes"
  [text]
  (if (str/blank? text)
    []
    (-> text
        parse-inline-code
        parse-bold
        parse-italic
        parse-links
        vec)))

;; ═══════════════════════════════════════════════════════════════════════
;; BLOCK PARSING
;; ═══════════════════════════════════════════════════════════════════════

;; Forward declarations for mutual recursion
(declare parse-blocks)

(defn parse-header
  "Parse header line: # Header or ## Header etc."
  [line]
  (when-let [match (re-matches #"^(#{1,6})\s+(.+)$" line)]
    (let [[_ hashes text] match
          level (count hashes)]
      (header-node level (parse-inline text)))))

(defn parse-code-block
  "Parse fenced code block starting at current line index
   Returns [node lines-consumed] or nil"
  [lines idx]
  (let [line (get lines idx)]
    (when-let [match (re-matches #"^```(\w*)$" line)]
      (let [lang (second match)
            content-lines (take-while
                           #(not (str/starts-with? % "```"))
                           (drop (inc idx) lines))
            consumed (+ 2 (count content-lines))] ; opening + content + closing
        [(code-block-node (str/join "\n" content-lines)
                         (when-not (str/blank? lang) lang))
         consumed]))))

(defn parse-blockquote
  "Parse blockquote lines starting at current index
   Returns [node lines-consumed] or nil"
  [lines idx]
  (let [line (get lines idx)]
    (when (str/starts-with? line ">")
      (let [quote-lines (take-while
                         #(or (str/starts-with? % ">")
                              (str/blank? %))
                         (drop idx lines))
            content-text (str/join "\n"
                                  (map #(str/replace % #"^>\s?" "") quote-lines))
            ;; Recursively parse the blockquote content
            nested-blocks (parse-blocks content-text)]
        [(blockquote-node nested-blocks)
         (count quote-lines)]))))

(defn parse-unordered-list
  "Parse unordered list starting at current index
   Returns [node lines-consumed] or nil"
  [lines idx]
  (let [line (get lines idx)]
    (when-let [match (re-matches #"^[\*\-\+]\s+(.+)$" line)]
      (let [list-lines (take-while
                        #(or (re-matches #"^[\*\-\+]\s+.+$" %)
                             (str/blank? %))
                        (drop idx lines))
            items (keep (fn [l]
                         (when-let [m (re-matches #"^[\*\-\+]\s+(.+)$" l)]
                           (list-item-node (parse-inline (second m)) nil)))
                       list-lines)]
        [(list-node false items)
         (count list-lines)]))))

(defn parse-ordered-list
  "Parse ordered list starting at current index
   Returns [node lines-consumed] or nil"
  [lines idx]
  (let [line (get lines idx)]
    (when-let [match (re-matches #"^\d+\.\s+(.+)$" line)]
      (let [list-lines (take-while
                        #(or (re-matches #"^\d+\.\s+.+$" %)
                             (str/blank? %))
                        (drop idx lines))
            items (keep (fn [l]
                         (when-let [m (re-matches #"^\d+\.\s+(.+)$" l)]
                           (list-item-node (parse-inline (second m)) nil)))
                       list-lines)]
        [(list-node true items)
         (count list-lines)]))))

(defn parse-hr
  "Parse horizontal rule: --- or *** or ___"
  [line]
  (when (re-matches #"^(\*\*\*|---|___)$" line)
    (hr-node)))

(defn parse-paragraph
  "Parse paragraph (accumulate non-blank lines until blank or special)"
  [lines idx]
  (let [para-lines (take-while
                    (fn [l]
                      (and (not (str/blank? l))
                           (not (parse-header l))
                           (not (str/starts-with? l "```"))
                           (not (str/starts-with? l ">"))
                           (not (re-matches #"^[\*\-\+]\s+.+" l))
                           (not (re-matches #"^\d+\.\s+.+" l))
                           (not (parse-hr l))))
                    (drop idx lines))
        text (str/join " " para-lines)]
    (when-not (str/blank? text)
      [(paragraph-node (parse-inline text))
       (count para-lines)])))

(defn parse-blocks
  "Parse markdown text into block-level AST
   Returns vector of block nodes"
  [text]
  (let [lines (str/split-lines text)]
    (loop [idx 0
           result []]
      (if (>= idx (count lines))
        result
        (let [line (get lines idx)]
          (cond
            ;; Skip blank lines
            (str/blank? line)
            (recur (inc idx) result)

            ;; Try parsing different block types
            :else
            (if-let [node (parse-header line)]
              (recur (inc idx) (conj result node))

              (if-let [[node consumed] (parse-code-block lines idx)]
                (recur (+ idx consumed) (conj result node))

                (if-let [[node consumed] (parse-blockquote lines idx)]
                  (recur (+ idx consumed) (conj result node))

                  (if-let [[node consumed] (parse-unordered-list lines idx)]
                    (recur (+ idx consumed) (conj result node))

                    (if-let [[node consumed] (parse-ordered-list lines idx)]
                      (recur (+ idx consumed) (conj result node))

                      (if-let [node (parse-hr line)]
                        (recur (inc idx) (conj result node))

                        ;; Default to paragraph
                        (if-let [[node consumed] (parse-paragraph lines idx)]
                          (recur (+ idx consumed) (conj result node))
                          (recur (inc idx) result))))))))))))))

;; ═══════════════════════════════════════════════════════════════════════
;; RENDERER REGISTRY
;; ═══════════════════════════════════════════════════════════════════════
;; Pluggable renderer system - easily extensible and overrideable

(def ^:dynamic *renderer-registry*
  "Registry of rendering functions by node type.
   Each renderer takes (node options) and returns a string or vector of strings.
   Override with (binding [*renderer-registry* {...}] ...)"
  (atom {}))

(defn register-renderer!
  "Register a renderer function for a node type"
  [node-type renderer-fn]
  (swap! *renderer-registry* assoc node-type renderer-fn))

(defn get-renderer
  "Get renderer for node type, or default text renderer"
  [node-type]
  (get @*renderer-registry* node-type
       (fn [node _] (str (:content node)))))

;; ═══════════════════════════════════════════════════════════════════════
;; DEFAULT RENDERERS
;; ═══════════════════════════════════════════════════════════════════════

;; Forward declaration for mutual recursion
(declare render-node)

(defn render-text
  "Render plain text node"
  [node _options]
  (:content node))

(defn render-bold
  "Render bold text"
  [node _options]
  (core/color :bold (:content node)))

(defn render-italic
  "Render italic text (using dim color as approximation)"
  [node _options]
  ;; Note: True italic isn't widely supported in terminals
  ;; Using a style approximation
  (str "\u001B[3m" (:content node) "\u001B[23m"))

(defn render-code
  "Render inline code"
  [node _options]
  (core/color :cyan (str "`" (:content node) "`")))

(defn render-link
  "Render link"
  [node _options]
  (str (core/color :blue (:text node))
       (core/color :cyan (str " (" (:url node) ")"))))

(defn render-inline-nodes
  "Render a sequence of inline nodes"
  [nodes options]
  (str/join (map #(render-node % options) nodes)))

(defn render-header
  "Render header with level-based styling"
  [node options]
  (let [level (:level node)
        content (render-inline-nodes (:content node) options)
        styled (case level
                 1 (core/color :bright-green (str "# " content))
                 2 (core/color :green (str "## " content))
                 3 (core/color :cyan (str "### " content))
                 4 (core/color :blue (str "#### " content))
                 5 (core/color :yellow (str "##### " content))
                 6 (str "###### " content)
                 content)]
    (str styled "\n")))

(defn render-paragraph
  "Render paragraph"
  [node options]
  (let [content (render-inline-nodes (:content node) options)]
    (str content "\n")))

(defn render-code-block
  "Render code block
   Integration point for future syntax highlighting"
  [node options]
  (let [content (:content node)
        lang (:lang node)
        ;; Future: If syntax-highlighter is provided in options, use it
        ;; (if-let [highlighter (:syntax-highlighter options)]
        ;;   (highlighter content lang)
        ;;   content)
        lines (str/split-lines content)
        formatted (map #(str "  " %) lines)]
    (str (when lang (core/color :cyan (str "```" lang "\n")))
         (str/join "\n" formatted)
         "\n"
         (when lang "```\n"))))

(defn render-list-item
  "Render list item"
  [node options depth marker]
  (let [indent (apply str (repeat (* depth 2) " "))
        content (render-inline-nodes (:content node) options)]
    (str indent marker " " content)))

(defn render-list
  "Render ordered or unordered list"
  [node options]
  (let [ordered? (:ordered? node)
        items (:items node)
        rendered (map-indexed
                  (fn [idx item]
                    (let [marker (if ordered? (str (inc idx) ".") "•")]
                      (render-list-item item options 0 marker)))
                  items)]
    (str (str/join "\n" rendered) "\n")))

(defn render-blockquote
  "Render blockquote with > prefix"
  [node options]
  (let [content (:content node)
        rendered (map #(render-node % options) content)
        lines (mapcat str/split-lines rendered)
        quoted (map #(str (core/color :cyan "> ") %) lines)]
    (str (str/join "\n" quoted) "\n")))

(defn render-hr
  "Render horizontal rule"
  [node _options]
  (str (core/color :cyan "─────────────────────────────────────────") "\n"))

;; Register default renderers
(defn init-default-renderers!
  "Initialize default renderers in registry"
  []
  (register-renderer! :text render-text)
  (register-renderer! :bold render-bold)
  (register-renderer! :italic render-italic)
  (register-renderer! :code render-code)
  (register-renderer! :link render-link)
  (register-renderer! :header render-header)
  (register-renderer! :paragraph render-paragraph)
  (register-renderer! :code-block render-code-block)
  (register-renderer! :list render-list)
  (register-renderer! :list-item render-list-item)
  (register-renderer! :blockquote render-blockquote)
  (register-renderer! :hr render-hr))

;; Initialize on load
(init-default-renderers!)

;; ═══════════════════════════════════════════════════════════════════════
;; MAIN RENDERING
;; ═══════════════════════════════════════════════════════════════════════

(defn render-node
  "Render a single node using registered renderer
   Options can include:
   - :syntax-highlighter - function for code block highlighting (future)
   - Custom options passed to renderers"
  [node options]
  (let [renderer (get-renderer (:type node))]
    (renderer node options)))

(defn render-ast
  "Render AST to ANSI-styled text
   Returns string with newlines"
  [ast options]
  (str/join (map #(render-node % options) ast)))

;; ═══════════════════════════════════════════════════════════════════════
;; PUBLIC API
;; ═══════════════════════════════════════════════════════════════════════

(defn parse
  "Parse markdown text into AST
   Returns vector of block nodes"
  [text]
  (parse-blocks text))

(defn render
  "Render markdown text to ANSI-styled output

   Options:
   - :syntax-highlighter - function (code lang) -> styled-code (future integration)
   - Custom renderer options

   Example:
     (render \"# Hello\\nThis is **bold**\")

   Example with custom renderer:
     (binding [*renderer-registry* (atom (assoc @*renderer-registry* :bold my-bold-fn))]
       (render \"**custom bold**\"))"
  ([text]
   (render text {}))
  ([text options]
   (let [ast (parse text)]
     (render-ast ast options))))

(defn render-to-lines
  "Render markdown to vector of lines (without trailing newlines)"
  ([text]
   (render-to-lines text {}))
  ([text options]
   (let [rendered (render text options)]
     (vec (str/split-lines rendered)))))

;; ═══════════════════════════════════════════════════════════════════════
;; EXTENSIBILITY HELPERS
;; ═══════════════════════════════════════════════════════════════════════

(defn with-custom-renderers
  "Execute function with custom renderers temporarily

   Example:
     (with-custom-renderers {:bold my-bold-renderer}
       (render \"**custom bold**))"
  [renderer-map f]
  (let [old-registry @*renderer-registry*]
    (try
      (swap! *renderer-registry* merge renderer-map)
      (f)
      (finally
        (reset! *renderer-registry* old-registry)))))

(defmacro with-renderers
  "Convenience macro for with-custom-renderers"
  [renderer-map & body]
  `(with-custom-renderers ~renderer-map
     (fn [] ~@body)))
