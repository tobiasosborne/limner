(ns limner.components.panel-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.components.panel :as panel]
            [clojure.string :as str]))

;; ────────────────────── Panel Creation Tests ──────────────────────
(deftest test-panel-creation
  (testing "Basic panel creation"
    (let [p (panel/panel :title "Test" :content "Hello")]
      (is (= "Test" (:title p)))
      (is (= "Hello" (:content p)))
      (is (= :left (:title-pos p)))
      (is (= :single (:border-style p)))))

  (testing "Panel with all options"
    (let [p (panel/panel
             :title "Full Panel"
             :title-pos :center
             :border-style :double
             :content "Content here"
             :width 50
             :height 10
             :padding 2
             :scrollable true
             :collapsible true)]
      (is (= "Full Panel" (:title p)))
      (is (= :center (:title-pos p)))
      (is (= :double (:border-style p)))
      (is (= 50 (:width p)))
      (is (= 10 (:height p)))
      (is (= 2 (:padding p)))
      (is (true? (:scrollable p)))
      (is (true? (:collapsible p))))))

;; ────────────────────── Content Processing Tests ──────────────────────
(deftest test-content-lines
  (testing "String content"
    (let [lines (panel/content-lines "Line 1\nLine 2\nLine 3")]
      (is (= 3 (count lines)))
      (is (= "Line 1" (first lines)))))

  (testing "Vector content"
    (let [lines (panel/content-lines ["A" "B" "C"])]
      (is (= ["A" "B" "C"] lines))))

  (testing "Nil content"
    (let [lines (panel/content-lines nil)]
      (is (= [] lines)))))

(deftest test-add-padding
  (testing "Add padding to content"
    (let [lines ["Hello" "World"]
          padded (panel/add-padding lines 2)]
      (is (every? #(str/starts-with? % "  ") (drop 2 padded)))
      (is (> (count padded) (count lines)))))

  (testing "Zero padding"
    (let [lines ["Hello" "World"]
          padded (panel/add-padding lines 0)]
      (is (= lines padded)))))

(deftest test-clip-content
  (testing "Clip content with scroll offset"
    (let [lines ["1" "2" "3" "4" "5"]
          clipped (panel/clip-content lines 2 2)]
      (is (= ["3" "4"] clipped))))

  (testing "Clip from start"
    (let [lines ["A" "B" "C" "D"]
          clipped (panel/clip-content lines 0 2)]
      (is (= ["A" "B"] clipped)))))

;; ────────────────────── Scrollbar Tests ──────────────────────
(deftest test-scrollbar-indicator
  (testing "No scrollbar when content fits"
    (let [info (panel/scrollbar-indicator 5 10 0)]
      (is (false? (:show? info)))))

  (testing "Show scrollbar when content exceeds visible area"
    (let [info (panel/scrollbar-indicator 20 10 0)]
      (is (true? (:show? info)))
      (is (number? (:position info)))
      (is (number? (:size info)))))

  (testing "Scrollbar position changes with scroll offset"
    (let [info1 (panel/scrollbar-indicator 20 10 0)
          info2 (panel/scrollbar-indicator 20 10 10)]
      (is (< (:position info1) (:position info2))))))

;; ────────────────────── Basic Rendering Tests ──────────────────────
(deftest test-basic-rendering
  (testing "Render simple panel"
    (let [p (panel/panel :content "Hello World")
          rendered (panel/render p)]
      (is (vector? rendered))
      (is (> (count rendered) 1))
      (is (str/includes? (str/join rendered) "Hello World"))))

  (testing "Render titled panel"
    (let [p (panel/panel :title "My Panel" :content "Content")
          rendered (panel/render p)]
      (is (str/includes? (first rendered) "My Panel"))))

  (testing "Render with different border styles"
    (let [single (panel/panel :content "Test" :border-style :single)
          double (panel/panel :content "Test" :border-style :double)
          rendered-single (panel/render single)
          rendered-double (panel/render double)]
      (is (str/starts-with? (first rendered-single) "┌"))
      (is (str/starts-with? (first rendered-double) "╔")))))

;; ────────────────────── Scrolling Tests ──────────────────────
(deftest test-scrolling
  (testing "Scroll panel down"
    (let [content (str/join "\n" (map str (range 1 21)))
          p (panel/panel :content content :height 5 :scrollable true)
          scrolled (panel/scroll-down p 3)]
      (is (= 3 (:scroll-offset scrolled)))))

  (testing "Scroll panel up"
    (let [content (str/join "\n" (map str (range 1 21)))
          p (panel/panel :content content :height 5 :scrollable true :scroll-offset 5)
          scrolled (panel/scroll-up p 2)]
      (is (= 3 (:scroll-offset scrolled)))))

  (testing "Cannot scroll beyond bounds"
    (let [content "Line 1\nLine 2\nLine 3"
          p (panel/panel :content content :height 5 :scrollable true)
          scrolled (panel/scroll-down p 100)]
      (is (= 0 (:scroll-offset scrolled)))))

  (testing "Scroll to specific position"
    (let [content (str/join "\n" (map str (range 1 21)))
          p (panel/panel :content content :height 5 :scrollable true)
          scrolled (panel/scroll-to p 10)]
      (is (= 10 (:scroll-offset scrolled))))))

;; ────────────────────── Collapse Tests ──────────────────────
(deftest test-collapse
  (testing "Toggle collapse"
    (let [p (panel/panel :title "Test" :content "Content" :collapsible true)
          collapsed (panel/toggle-collapse p)]
      (is (true? (:collapsed collapsed)))
      (is (false? (:collapsed (panel/toggle-collapse collapsed))))))

  (testing "Cannot collapse non-collapsible panel"
    (let [p (panel/panel :title "Test" :content "Content" :collapsible false)
          toggled (panel/toggle-collapse p)]
      (is (false? (:collapsed toggled)))))

  (testing "Render collapsed panel"
    (let [p (panel/panel :title "Test" :content "Hidden" :collapsible true :collapsed true)
          rendered (panel/render p)]
      (is (< (count rendered) 5))
      (is (str/includes? (first rendered) "[+]")))))

;; ────────────────────── Nesting Tests ──────────────────────
(deftest test-nested-panels
  (testing "Nest single panel"
    (let [child (panel/panel :title "Child" :content "Child content")
          parent (panel/panel :title "Parent")
          nested (panel/nest-panels parent child)
          rendered (panel/render nested)]
      (is (vector? (:content nested)))
      (is (some #(str/includes? % "Child") rendered))))

  (testing "Nest multiple panels"
    (let [child1 (panel/panel :title "Child 1" :content "Content 1")
          child2 (panel/panel :title "Child 2" :content "Content 2")
          parent (panel/panel :title "Parent")
          nested (panel/nest-panels parent [child1 child2])
          rendered (panel/render nested)]
      (is (some #(str/includes? % "Child 1") rendered))
      (is (some #(str/includes? % "Child 2") rendered)))))

;; ────────────────────── Helper Function Tests ──────────────────────
(deftest test-helper-functions
  (testing "panel? predicate"
    (let [p (panel/panel :content "test")]
      (is (true? (panel/panel? p)))
      (is (false? (panel/panel? "not a panel")))
      (is (false? (panel/panel? nil)))))

  (testing "scrollable? predicate"
    (let [scrollable-panel (panel/panel :content "test" :scrollable true)
          normal-panel (panel/panel :content "test")]
      (is (true? (panel/scrollable? scrollable-panel)))
      (is (false? (panel/scrollable? normal-panel)))))

  (testing "collapsible? predicate"
    (let [collapsible-panel (panel/panel :title "test" :collapsible true)
          normal-panel (panel/panel :title "test")]
      (is (true? (panel/collapsible? collapsible-panel)))
      (is (false? (panel/collapsible? normal-panel)))))

  (testing "collapsed? predicate"
    (let [collapsed-panel (panel/panel :title "test" :collapsible true :collapsed true)
          expanded-panel (panel/panel :title "test" :collapsible true)]
      (is (true? (panel/collapsed? collapsed-panel)))
      (is (false? (panel/collapsed? expanded-panel)))))

  (testing "can-scroll-up? predicate"
    (let [content (str/join "\n" (map str (range 1 21)))
          p-top (panel/panel :content content :height 5 :scrollable true :scroll-offset 0)
          p-middle (panel/panel :content content :height 5 :scrollable true :scroll-offset 5)]
      (is (false? (panel/can-scroll-up? p-top)))
      (is (true? (panel/can-scroll-up? p-middle)))))

  (testing "can-scroll-down? predicate"
    (let [content (str/join "\n" (map str (range 1 21)))
          p-top (panel/panel :content content :height 5 :scrollable true :scroll-offset 0)
          p-bottom (panel/panel :content content :height 5 :scrollable true :scroll-offset 15)]
      (is (true? (panel/can-scroll-down? p-top)))
      (is (false? (panel/can-scroll-down? p-bottom))))))

;; ────────────────────── Content Update Tests ──────────────────────
(deftest test-set-content
  (testing "Update panel content"
    (let [p (panel/panel :title "Test" :content "Old content")
          updated (panel/set-content p "New content")]
      (is (= "New content" (:content updated)))
      (is (= "Test" (:title updated))))))

;; Run all tests
(defn run-tests []
  (clojure.test/run-tests 'limner.components.panel-test))
