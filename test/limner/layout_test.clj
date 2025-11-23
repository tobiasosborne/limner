(ns limner.layout-test
  (:require [clojure.test :refer [deftest is testing]]
            [limner.layout :as layout]))

;; ──────────────────────── Box Model Tests ──────────────────────
(deftest test-box-creation
  (testing "Box with default values"
    (let [b (layout/box)]
      (is (= 0 (:x b)))
      (is (= 0 (:y b)))
      (is (= 0 (:width b)))
      (is (= 0 (:height b)))))

  (testing "Box with custom values"
    (let [b (layout/box :x 10 :y 20 :width 100 :height 50 :padding 5)]
      (is (= 10 (:x b)))
      (is (= 20 (:y b)))
      (is (= 100 (:width b)))
      (is (= 50 (:height b)))
      (is (= 5 (:padding b))))))

(deftest test-content-box
  (testing "Content box calculation with padding"
    (let [b (layout/box :x 10 :y 10 :width 100 :height 50 :padding 5)
          cb (layout/content-box b)]
      (is (= 15 (:x cb)))
      (is (= 15 (:y cb)))
      (is (= 90 (:width cb)))
      (is (= 40 (:height cb))))))

;; ────────────────────── Constraint Tests ──────────────────────
(deftest test-constraint-types
  (testing "Fixed constraint"
    (let [c (layout/fixed 100)]
      (is (= :fixed (:type c)))
      (is (= 100 (:value c)))))

  (testing "Percent constraint"
    (let [c (layout/percent 50)]
      (is (= :percent (:type c)))
      (is (= 50 (:value c)))))

  (testing "Flex constraint"
    (let [c (layout/flex 2)]
      (is (= :flex (:type c)))
      (is (= 2 (:value c)))))

  (testing "Auto constraint"
    (let [c (layout/auto)]
      (is (= :auto (:type c))))))

(deftest test-constraint-resolution
  (testing "Fixed constraint resolution"
    (is (= 100 (layout/resolve-constraint (layout/fixed 100) 200 50))))

  (testing "Percent constraint resolution"
    (is (= 100 (layout/resolve-constraint (layout/percent 50) 200 50)))
    (is (= 50 (layout/resolve-constraint (layout/percent 25) 200 50))))

  (testing "Auto constraint resolution"
    (is (= 50 (layout/resolve-constraint (layout/auto) 200 50)))))

(deftest test-flex-distribution
  (testing "Equal flex distribution"
    (let [flex-items [(layout/flex 1) (layout/flex 1)]
          sizes (layout/resolve-flex-constraints flex-items 100)]
      (is (= [50 50] sizes))))

  (testing "Proportional flex distribution"
    (let [flex-items [(layout/flex 1) (layout/flex 3)]
          sizes (layout/resolve-flex-constraints flex-items 100)]
      (is (= [25 75] sizes)))))

;; ────────────────────── Vertical Stack Tests ──────────────────────
(deftest test-vertical-stack
  (testing "Stack with fixed heights"
    (let [components [{:constraint (layout/fixed 10) :content {:height 10}}
                      {:constraint (layout/fixed 20) :content {:height 20}}]
          stack-layout (layout/stack components)
          boxes (layout/layout-stack stack-layout 100 50)]
      (is (= 2 (count boxes)))
      (is (= 0 (:y (first boxes))))
      (is (= 10 (:height (first boxes))))
      (is (= 10 (:y (second boxes))))
      (is (= 20 (:height (second boxes))))))

  (testing "Stack with flex heights"
    (let [components [{:constraint (layout/flex 1) :content {}}
                      {:constraint (layout/flex 2) :content {}}]
          stack-layout (layout/stack components)
          boxes (layout/layout-stack stack-layout 100 90)]
      (is (= 2 (count boxes)))
      (is (= 30 (:height (first boxes))))
      (is (= 60 (:height (second boxes)))))))

;; ────────────────────── Horizontal Split Tests ──────────────────────
(deftest test-horizontal-split
  (testing "Split with fixed widths"
    (let [components [{:constraint (layout/fixed 30) :content {:width 30}}
                      {:constraint (layout/fixed 70) :content {:width 70}}]
          hsplit-layout (layout/hsplit components)
          boxes (layout/layout-hsplit hsplit-layout 100 50)]
      (is (= 2 (count boxes)))
      (is (= 0 (:x (first boxes))))
      (is (= 30 (:width (first boxes))))
      (is (= 30 (:x (second boxes))))
      (is (= 70 (:width (second boxes))))))

  (testing "Split with flex widths"
    (let [components [{:constraint (layout/flex 1) :content {}}
                      {:constraint (layout/flex 3) :content {}}]
          hsplit-layout (layout/hsplit components)
          boxes (layout/layout-hsplit hsplit-layout 100 50)]
      (is (= 2 (count boxes)))
      (is (= 25 (:width (first boxes))))
      (is (= 75 (:width (second boxes)))))))

;; ────────────────────── Grid Layout Tests ──────────────────────
(deftest test-grid-layout
  (testing "Grid with 2 columns"
    (let [components [{:content "A"} {:content "B"}
                      {:content "C"} {:content "D"}]
          grid-layout (layout/grid components :columns 2)
          boxes (layout/layout-grid grid-layout 100 100)]
      (is (= 4 (count boxes)))
      (is (= 0 (:x (nth boxes 0))))
      (is (= 0 (:y (nth boxes 0))))
      (is (= 50 (:x (nth boxes 1))))
      (is (= 0 (:y (nth boxes 1))))
      (is (= 0 (:x (nth boxes 2))))
      (is (= 50 (:y (nth boxes 2))))
      (is (= 50 (:x (nth boxes 3))))
      (is (= 50 (:y (nth boxes 3)))))))

;; ────────────────────── Overlay Tests ──────────────────────
(deftest test-overlay-layout
  (testing "Overlay components with z-index"
    (let [components [{:content "background" :z-index 0}
                      {:content "foreground" :z-index 10}
                      {:content "middle" :z-index 5}]
          overlay-layout (layout/overlay components)
          boxes (layout/layout-overlay overlay-layout 100 50)]
      (is (= 3 (count boxes)))
      (is (= 0 (:z-index (nth boxes 0))))
      (is (= 5 (:z-index (nth boxes 1))))
      (is (= 10 (:z-index (nth boxes 2)))))))

;; ────────────────────── Utility Tests ──────────────────────
(deftest test-clip
  (testing "Clip content to width and height"
    (let [content "Hello\nWorld\nFoo\nBar"
          clipped (layout/clip content 3 2)]
      (is (= 2 (count clipped)))
      (is (= "Hel" (first clipped)))
      (is (= "Wor" (second clipped))))))

;; Run all tests
(defn run-tests []
  (clojure.test/run-tests 'limner.layout-test))
