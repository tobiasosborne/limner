(ns limner.components.markdown-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.components.markdown :as md]
            [clojure.string :as str]))

;; ────────────────────── AST Node Creation Tests ──────────────────────
(deftest test-node-creation
  (testing "Text node"
    (let [node (md/text-node "hello")]
      (is (= :text (:type node)))
      (is (= "hello" (:content node)))))

  (testing "Bold node"
    (let [node (md/bold-node "bold text")]
      (is (= :bold (:type node)))
      (is (= "bold text" (:content node)))))

  (testing "Italic node"
    (let [node (md/italic-node "italic text")]
      (is (= :italic (:type node)))
      (is (= "italic text" (:content node)))))

  (testing "Code node"
    (let [node (md/code-node "code")]
      (is (= :code (:type node)))
      (is (= "code" (:content node)))))

  (testing "Link node"
    (let [node (md/link-node "text" "url")]
      (is (= :link (:type node)))
      (is (= "text" (:text node)))
      (is (= "url" (:url node)))))

  (testing "Header node"
    (let [node (md/header-node 1 "content")]
      (is (= :header (:type node)))
      (is (= 1 (:level node)))
      (is (= "content" (:content node)))))

  (testing "Code block node"
    (let [node (md/code-block-node "code" "clojure")]
      (is (= :code-block (:type node)))
      (is (= "code" (:content node)))
      (is (= "clojure" (:lang node))))))

;; ────────────────────── Inline Parsing Tests ──────────────────────
(deftest test-inline-parsing
  (testing "Parse plain text"
    (let [nodes (md/parse-inline "hello world")]
      (is (= 1 (count nodes)))
      (is (= :text (:type (first nodes))))))

  (testing "Parse inline code"
    (let [nodes (md/parse-inline "this is `code` text")]
      (is (some #(= :code (:type %)) nodes))
      (is (some #(= "code" (:content %)) nodes))))

  (testing "Parse bold with **"
    (let [nodes (md/parse-inline "this is **bold** text")]
      (is (some #(= :bold (:type %)) nodes))
      (is (some #(= "bold" (:content %)) nodes))))

  (testing "Parse bold with __"
    (let [nodes (md/parse-inline "this is __bold__ text")]
      (is (some #(= :bold (:type %)) nodes))
      (is (some #(= "bold" (:content %)) nodes))))

  (testing "Parse italic with *"
    (let [nodes (md/parse-inline "this is *italic* text")]
      (is (some #(= :italic (:type %)) nodes))
      (is (some #(= "italic" (:content %)) nodes))))

  (testing "Parse italic with _"
    (let [nodes (md/parse-inline "this is _italic_ text")]
      (is (some #(= :italic (:type %)) nodes))
      (is (some #(= "italic" (:content %)) nodes))))

  (testing "Parse links"
    (let [nodes (md/parse-inline "click [here](http://example.com) now")]
      (is (some #(= :link (:type %)) nodes))
      (let [link (first (filter #(= :link (:type %)) nodes))]
        (is (= "here" (:text link)))
        (is (= "http://example.com" (:url link))))))

  (testing "Parse combined inline formatting"
    (let [nodes (md/parse-inline "text with **bold** and *italic* and `code`")]
      (is (some #(= :bold (:type %)) nodes))
      (is (some #(= :italic (:type %)) nodes))
      (is (some #(= :code (:type %)) nodes)))))

;; ────────────────────── Block Parsing Tests ──────────────────────
(deftest test-header-parsing
  (testing "Parse H1"
    (let [ast (md/parse "# Header 1")]
      (is (= 1 (count ast)))
      (is (= :header (:type (first ast))))
      (is (= 1 (:level (first ast))))))

  (testing "Parse H2"
    (let [ast (md/parse "## Header 2")]
      (is (= 2 (:level (first ast))))))

  (testing "Parse H6"
    (let [ast (md/parse "###### Header 6")]
      (is (= 6 (:level (first ast))))))

  (testing "Header with inline formatting"
    (let [ast (md/parse "# Header with **bold**")]
      (is (= :header (:type (first ast))))
      (let [content (:content (first ast))]
        (is (some #(= :bold (:type %)) content))))))

(deftest test-paragraph-parsing
  (testing "Parse simple paragraph"
    (let [ast (md/parse "This is a paragraph.")]
      (is (= 1 (count ast)))
      (is (= :paragraph (:type (first ast))))))

  (testing "Parse multi-line paragraph"
    (let [ast (md/parse "Line one\nLine two")]
      (is (= 1 (count ast)))
      (is (= :paragraph (:type (first ast))))))

  (testing "Paragraph with inline formatting"
    (let [ast (md/parse "Paragraph with **bold** text")]
      (is (= :paragraph (:type (first ast))))
      (let [content (:content (first ast))]
        (is (some #(= :bold (:type %)) content))))))

(deftest test-code-block-parsing
  (testing "Parse code block without language"
    (let [ast (md/parse "```\ncode here\n```")]
      (is (= 1 (count ast)))
      (is (= :code-block (:type (first ast))))
      (is (= "code here" (:content (first ast))))))

  (testing "Parse code block with language"
    (let [ast (md/parse "```clojure\n(defn foo [])\n```")]
      (is (= :code-block (:type (first ast))))
      (is (= "clojure" (:lang (first ast))))
      (is (= "(defn foo [])" (:content (first ast))))))

  (testing "Parse multi-line code block"
    (let [ast (md/parse "```\nline 1\nline 2\nline 3\n```")]
      (is (= :code-block (:type (first ast))))
      (is (str/includes? (:content (first ast)) "line 1"))
      (is (str/includes? (:content (first ast)) "line 3")))))

(deftest test-list-parsing
  (testing "Parse unordered list with -"
    (let [ast (md/parse "- Item 1\n- Item 2")]
      (is (= 1 (count ast)))
      (is (= :list (:type (first ast))))
      (is (false? (:ordered? (first ast))))
      (is (= 2 (count (:items (first ast)))))))

  (testing "Parse unordered list with *"
    (let [ast (md/parse "* Item 1\n* Item 2")]
      (is (= :list (:type (first ast))))
      (is (false? (:ordered? (first ast))))))

  (testing "Parse ordered list"
    (let [ast (md/parse "1. First\n2. Second")]
      (is (= :list (:type (first ast))))
      (is (true? (:ordered? (first ast))))
      (is (= 2 (count (:items (first ast)))))))

  (testing "List items with inline formatting"
    (let [ast (md/parse "- Item with **bold**")]
      (let [items (:items (first ast))
            content (:content (first items))]
        (is (some #(= :bold (:type %)) content))))))

(deftest test-blockquote-parsing
  (testing "Parse simple blockquote"
    (let [ast (md/parse "> Quote")]
      (is (= 1 (count ast)))
      (is (= :blockquote (:type (first ast))))))

  (testing "Parse multi-line blockquote"
    (let [ast (md/parse "> Line 1\n> Line 2")]
      (is (= :blockquote (:type (first ast))))
      (let [content (:content (first ast))]
        (is (> (count content) 0)))))

  (testing "Blockquote with formatting"
    (let [ast (md/parse "> Quote with **bold**")]
      (is (= :blockquote (:type (first ast)))))))

(deftest test-hr-parsing
  (testing "Parse HR with ---"
    (let [ast (md/parse "---")]
      (is (= :hr (:type (first ast))))))

  (testing "Parse HR with ***"
    (let [ast (md/parse "***")]
      (is (= :hr (:type (first ast))))))

  (testing "Parse HR with ___"
    (let [ast (md/parse "___")]
      (is (= :hr (:type (first ast)))))))

;; ────────────────────── Rendering Tests ──────────────────────
(deftest test-rendering
  (testing "Render plain text"
    (let [output (md/render "Hello world")]
      (is (str/includes? output "Hello world"))))

  (testing "Render header"
    (let [output (md/render "# Header")]
      (is (str/includes? output "#"))
      (is (str/includes? output "Header"))))

  (testing "Render bold text"
    (let [output (md/render "**bold**")]
      (is (str/includes? output "bold"))))

  (testing "Render italic text"
    (let [output (md/render "*italic*")]
      (is (str/includes? output "italic"))))

  (testing "Render inline code"
    (let [output (md/render "`code`")]
      (is (str/includes? output "code"))))

  (testing "Render link"
    (let [output (md/render "[text](url)")]
      (is (str/includes? output "text"))
      (is (str/includes? output "url"))))

  (testing "Render code block"
    (let [output (md/render "```\ncode\n```")]
      (is (str/includes? output "code"))))

  (testing "Render unordered list"
    (let [output (md/render "- Item 1\n- Item 2")]
      (is (str/includes? output "Item 1"))
      (is (str/includes? output "Item 2"))))

  (testing "Render ordered list"
    (let [output (md/render "1. First\n2. Second")]
      (is (str/includes? output "First"))
      (is (str/includes? output "Second"))))

  (testing "Render blockquote"
    (let [output (md/render "> Quote")]
      (is (str/includes? output "Quote"))))

  (testing "Render horizontal rule"
    (let [output (md/render "---")]
      (is (not (str/blank? output))))))

;; ────────────────────── Complex Document Tests ──────────────────────
(deftest test-complex-documents
  (testing "Document with multiple block types"
    (let [doc "# Title\n\nParagraph text.\n\n## Section\n\n- List item\n\n```\ncode\n```"
          ast (md/parse doc)]
      (is (> (count ast) 3))
      (is (some #(= :header (:type %)) ast))
      (is (some #(= :paragraph (:type %)) ast))
      (is (some #(= :list (:type %)) ast))
      (is (some #(= :code-block (:type %)) ast))))

  (testing "Render complex document"
    (let [doc "# Title\n\nThis is **bold** and *italic*.\n\n- Item 1\n- Item 2"
          output (md/render doc)]
      (is (str/includes? output "Title"))
      (is (str/includes? output "bold"))
      (is (str/includes? output "italic"))
      (is (str/includes? output "Item 1")))))

;; ────────────────────── Extensibility Tests ──────────────────────
(deftest test-custom-renderers
  (testing "Register custom renderer"
    (let [custom-bold (fn [node _] (str "CUSTOM:" (:content node)))
          _ (md/register-renderer! :bold custom-bold)
          output (md/render "**bold**")]
      (is (str/includes? output "CUSTOM:"))
      ;; Restore default
      (md/init-default-renderers!)))

  (testing "Use with-renderers macro"
    (let [custom-bold (fn [node _] (str "TEMP:" (:content node)))
          output (md/with-renderers {:bold custom-bold}
                   (md/render "**bold**"))]
      (is (str/includes? output "TEMP:"))
      ;; Verify default is restored
      (let [normal (md/render "**bold**")]
        (is (not (str/includes? normal "TEMP:"))))))

  (testing "Multiple custom renderers"
    (let [custom-bold (fn [node _] (str "B:" (:content node)))
          custom-italic (fn [node _] (str "I:" (:content node)))
          output (md/with-renderers {:bold custom-bold
                                     :italic custom-italic}
                   (md/render "**bold** and *italic*"))]
      (is (str/includes? output "B:"))
      (is (str/includes? output "I:")))))

;; ────────────────────── Edge Cases Tests ──────────────────────
(deftest test-edge-cases
  (testing "Empty input"
    (let [ast (md/parse "")]
      (is (vector? ast))
      (is (empty? ast))))

  (testing "Only whitespace"
    (let [ast (md/parse "   \n\n   ")]
      (is (empty? ast))))

  (testing "Incomplete markdown"
    (let [ast (md/parse "**incomplete bold")]
      (is (= 1 (count ast)))
      (is (= :paragraph (:type (first ast))))))

  (testing "Nested bold/italic"
    (let [output (md/render "**bold with *italic* inside**")]
      (is (str/includes? output "bold"))
      (is (str/includes? output "italic"))))

  (testing "Multiple paragraphs"
    (let [ast (md/parse "Para 1\n\nPara 2\n\nPara 3")]
      (is (= 3 (count ast)))
      (is (every? #(= :paragraph (:type %)) ast)))))

;; ────────────────────── render-to-lines Tests ──────────────────────
(deftest test-render-to-lines
  (testing "Render to lines"
    (let [lines (md/render-to-lines "# Header\n\nParagraph")]
      (is (vector? lines))
      (is (> (count lines) 0))))

  (testing "Lines don't have trailing newlines"
    (let [lines (md/render-to-lines "Line 1\n\nLine 2")]
      (is (every? #(not (str/ends-with? % "\n")) lines)))))

;; Run all tests
(defn run-tests []
  (clojure.test/run-tests 'limner.components.markdown-test))
