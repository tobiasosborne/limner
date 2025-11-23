#!/usr/bin/env bb
;; Markdown Component Demo
;; Demonstrates all markdown features and extensibility

(ns markdown-demo
  (:require [limner.components.markdown :as md]
            [limner.core :as core]
            [clojure.string :as str]))

(defn print-section [title]
  (println)
  (println (core/color :bright-green "═══════════════════════════════════════"))
  (println (core/color :bright-green (str "  " title)))
  (println (core/color :bright-green "═══════════════════════════════════════")))

(defn print-markdown [desc md-text]
  (println)
  (println (core/color :cyan (str desc ":")))
  (println (core/color :yellow "Input:"))
  (println md-text)
  (println)
  (println (core/color :yellow "Output:"))
  (println (md/render md-text))
  (println))

;; ──────────────── Demo 1: Headers ────────────────
(defn demo-headers []
  (print-section "Demo 1: Headers (H1-H6)")

  (print-markdown
   "All header levels"
   "# Header 1\n## Header 2\n### Header 3\n#### Header 4\n##### Header 5\n###### Header 6")

  (print-markdown
   "Header with inline formatting"
   "# Header with **bold** and *italic*"))

;; ──────────────── Demo 2: Inline Formatting ────────────────
(defn demo-inline-formatting []
  (print-section "Demo 2: Inline Formatting")

  (print-markdown
   "Bold text"
   "This is **bold with asterisks** and __bold with underscores__.")

  (print-markdown
   "Italic text"
   "This is *italic with asterisks* and _italic with underscores_.")

  (print-markdown
   "Inline code"
   "Use `inline code` for technical terms like `defn` or `let`.")

  (print-markdown
   "Links"
   "Check out [Clojure](https://clojure.org) and [GitHub](https://github.com).")

  (print-markdown
   "Combined formatting"
   "Mix **bold**, *italic*, and `code` together in one paragraph."))

;; ──────────────── Demo 3: Paragraphs ────────────────
(defn demo-paragraphs []
  (print-section "Demo 3: Paragraphs")

  (print-markdown
   "Single paragraph"
   "This is a simple paragraph with some text.")

  (print-markdown
   "Multi-line paragraph"
   "This paragraph spans\nmultiple lines but\nis rendered as one.")

  (print-markdown
   "Multiple paragraphs"
   "First paragraph here.\n\nSecond paragraph here.\n\nThird paragraph here."))

;; ──────────────── Demo 4: Lists ────────────────
(defn demo-lists []
  (print-section "Demo 4: Lists")

  (print-markdown
   "Unordered list with -"
   "- First item\n- Second item\n- Third item")

  (print-markdown
   "Unordered list with *"
   "* Apple\n* Banana\n* Cherry")

  (print-markdown
   "Ordered list"
   "1. First step\n2. Second step\n3. Third step")

  (print-markdown
   "List with inline formatting"
   "- Item with **bold**\n- Item with *italic*\n- Item with `code`"))

;; ──────────────── Demo 5: Code Blocks ────────────────
(defn demo-code-blocks []
  (print-section "Demo 5: Code Blocks")

  (print-markdown
   "Code block without language"
   "```\nfunction hello() {\n  console.log('Hello');\n}\n```")

  (print-markdown
   "Code block with language (Clojure)"
   "```clojure\n(defn greet [name]\n  (println \"Hello,\" name))\n```")

  (print-markdown
   "Code block with language (Python)"
   "```python\ndef greet(name):\n    print(f\"Hello, {name}\")\n```"))

;; ──────────────── Demo 6: Blockquotes ────────────────
(defn demo-blockquotes []
  (print-section "Demo 6: Blockquotes")

  (print-markdown
   "Simple blockquote"
   "> This is a quoted text.")

  (print-markdown
   "Multi-line blockquote"
   "> This is the first line.\n> This is the second line.\n> This is the third line.")

  (print-markdown
   "Blockquote with formatting"
   "> Quote with **bold** and *italic* text."))

;; ──────────────── Demo 7: Horizontal Rules ────────────────
(defn demo-horizontal-rules []
  (print-section "Demo 7: Horizontal Rules")

  (print-markdown
   "HR with three dashes"
   "Above the line\n\n---\n\nBelow the line")

  (print-markdown
   "HR with three asterisks"
   "Above\n\n***\n\nBelow")

  (print-markdown
   "HR with three underscores"
   "Above\n\n___\n\nBelow"))

;; ──────────────── Demo 8: Complex Documents ────────────────
(defn demo-complex-documents []
  (print-section "Demo 8: Complex Documents")

  (print-markdown
   "README-style document"
   "# My Project\n\nA **great** project that does *amazing* things.\n\n## Features\n\n- Easy to use\n- Fast performance\n- Well documented\n\n## Installation\n\n```bash\nnpm install my-project\n```\n\n## Usage\n\nSee the [documentation](https://example.com) for details.\n\n---\n\nMade with ❤️")

  (print-markdown
   "Technical documentation"
   "# API Reference\n\n## Functions\n\n### `parse`\n\nParse markdown text.\n\n```clojure\n(parse \"# Hello\")\n```\n\nReturns an AST.\n\n### `render`\n\nRender markdown to ANSI.\n\n> Note: This is **experimental**."))

;; ──────────────── Demo 9: AST Inspection ────────────────
(defn demo-ast-inspection []
  (print-section "Demo 9: AST Inspection (Extensibility)")

  (let [md-text "# Title\n\nParagraph with **bold**."
        ast (md/parse md-text)]

    (println (core/color :cyan "\nMarkdown text:"))
    (println md-text)

    (println)
    (println (core/color :cyan "Parsed AST:"))
    (doseq [node ast]
      (println (core/color :yellow (str "  Type: " (:type node))))
      (when (:level node)
        (println (str "    Level: " (:level node))))
      (when (:content node)
        (println (str "    Content: " (pr-str (:content node))))))

    (println)
    (println (core/color :cyan "Rendered output:"))
    (println (md/render md-text))))

;; ──────────────── Demo 10: Custom Renderers ────────────────
(defn demo-custom-renderers []
  (print-section "Demo 10: Custom Renderers (Extensibility)")

  (let [md-text "This is **bold** and *italic* text."

        ;; Custom renderers
        custom-bold (fn [node _]
                     (str "【" (:content node) "】"))

        custom-italic (fn [node _]
                       (str "《" (:content node) "》"))]

    (println (core/color :cyan "\nMarkdown text:"))
    (println md-text)

    (println)
    (println (core/color :yellow "Default rendering:"))
    (println (md/render md-text))

    (println (core/color :yellow "Custom rendering (CJK brackets):"))
    (println (md/with-renderers {:bold custom-bold
                                 :italic custom-italic}
               (md/render md-text)))

    ;; Another custom style
    (let [uppercase-bold (fn [node _]
                          (str/upper-case (:content node)))
          reversed-italic (fn [node _]
                           (str/reverse (:content node)))]

      (println (core/color :yellow "Custom rendering (uppercase + reverse):"))
      (println (md/with-renderers {:bold uppercase-bold
                                   :italic reversed-italic}
                 (md/render md-text))))))

;; ──────────────── Demo 11: Future Integration Hook ────────────────
(defn demo-future-integration []
  (print-section "Demo 11: Future Integration (Syntax Highlighting)")

  (let [md-text "```clojure\n(defn hello [name]\n  (str \"Hello, \" name))\n```"

        ;; Placeholder for future syntax highlighter
        mock-highlighter (fn [code lang]
                          (str "[HIGHLIGHTED " (str/upper-case lang) " CODE]\n" code))

        options {:syntax-highlighter mock-highlighter}]

    (println (core/color :cyan "\nCode block:"))
    (println md-text)

    (println)
    (println (core/color :yellow "Standard rendering:"))
    (println (md/render md-text))

    (println (core/color :yellow "With syntax highlighter (future):"))
    (println (core/color :cyan "  Note: This shows the integration point for step 10"))
    (println (core/color :cyan "  Custom syntax highlighter would be passed via options"))
    (println)))

;; ──────────────── Demo 12: Edge Cases ────────────────
(defn demo-edge-cases []
  (print-section "Demo 12: Edge Cases")

  (print-markdown
   "Empty input"
   "")

  (print-markdown
   "Only whitespace"
   "   \n\n   ")

  (print-markdown
   "Incomplete bold"
   "This is **incomplete bold")

  (print-markdown
   "Mixed complete and incomplete"
   "**Complete bold** and **incomplete")

  (print-markdown
   "Special characters"
   "Characters like < > & are preserved"))

;; ──────────────── Demo 13: Performance Example ────────────────
(defn demo-performance []
  (print-section "Demo 13: Performance (Large Document)")

  (let [large-doc (str
                   "# Large Document\n\n"
                   (str/join "\n\n"
                            (for [i (range 20)]
                              (str "## Section " (inc i) "\n\n"
                                   "Paragraph with **bold** and *italic* text. "
                                   "Some `inline code` and a [link](http://example.com).\n\n"
                                   "- List item 1\n"
                                   "- List item 2\n"
                                   "- List item 3"))))]

    (println (core/color :cyan (str "\nLarge document with 20 sections")))
    (println (core/color :cyan (str "Input size: " (count large-doc) " characters")))

    (let [start (System/nanoTime)
          output (md/render large-doc)
          duration (/ (- (System/nanoTime) start) 1000000.0)]

      (println (core/color :cyan (str "Output size: " (count output) " characters")))
      (println (core/color :green (str "Rendered in: " (format "%.2f" duration) " ms")))
      (println)
      (println (core/color :yellow "First 500 characters of output:"))
      (println (subs output 0 (min 500 (count output))))
      (println "..."))))

;; ──────────────── Main ────────────────
(defn -main []
  (println (core/color :bright-green "\n"))
  (println (core/color :bright-green "╔═══════════════════════════════════════════════════╗"))
  (println (core/color :bright-green "║                                                   ║"))
  (println (core/color :bright-green "║      POORICH MARKDOWN COMPONENT SHOWCASE          ║"))
  (println (core/color :bright-green "║                                                   ║"))
  (println (core/color :bright-green "╚═══════════════════════════════════════════════════╝"))

  (demo-headers)
  (demo-inline-formatting)
  (demo-paragraphs)
  (demo-lists)
  (demo-code-blocks)
  (demo-blockquotes)
  (demo-horizontal-rules)
  (demo-complex-documents)
  (demo-ast-inspection)
  (demo-custom-renderers)
  (demo-future-integration)
  (demo-edge-cases)
  (demo-performance)

  (println (core/color :bright-green "\n✓ Markdown component demo complete!\n"))
  (println "Features demonstrated:")
  (println "  • Headers (H1-H6) with size differentiation")
  (println "  • Bold, italic, code spans, links")
  (println "  • Code blocks with optional language tags")
  (println "  • Lists (ordered and unordered)")
  (println "  • Blockquotes")
  (println "  • Horizontal rules")
  (println "  • Nested formatting")
  (println "  • AST inspection for extensibility")
  (println "  • Custom renderer registration")
  (println "  • Integration hooks for syntax highlighting")
  (println "  • Clean separation: parse -> AST -> render")
  (println "  • Modular, refactorable architecture")
  (println))

(when (or (System/getProperty "babashka.version")
          (= *file* (System/getProperty "babashka.file")))
  (-main))
