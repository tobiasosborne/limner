(ns limner.layout-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [limner.component-support :as component]
            [limner.layout :as layout]
            [limner.text :as text]))

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
      (is (= [25 75] sizes))))

  (testing "A remainder goes to the largest fractional shares"
    (is (= [4 3 3]
           (layout/resolve-flex-constraints
            [(layout/flex 1) (layout/flex 1) (layout/flex 1)] 10)))
    (is (= [3 3 2]
           (layout/resolve-flex-constraints
            [(layout/flex 1) (layout/flex 1) (layout/flex 1)] 8)))
    (is (= [2 1]
           (layout/resolve-flex-constraints
            [(layout/flex 3) (layout/flex 1)] 3))))

  (testing "Zero total flex still allocates every column"
    (is (= [4 3 3]
           (layout/resolve-flex-constraints
            [(layout/flex 0) (layout/flex 0) (layout/flex 0)] 10)))))

(deftest flex-stack-allocates-every-row
  (testing "Three flex rows share the whole height"
    (let [boxes (layout/layout-stack
                 (layout/stack [{:constraint (layout/flex 1)}
                                {:constraint (layout/flex 1)}
                                {:constraint (layout/flex 1)}])
                 10 10)]
      (is (= [4 3 3] (mapv :height boxes)))
      (is (= 10 (reduce + (map :height boxes))))
      (is (= [0 4 7] (mapv :y boxes)))))

  (testing "Heights plus spacing equal the total height"
    (let [boxes (layout/layout-stack
                 (layout/stack [{:constraint (layout/flex 1)}
                                {:constraint (layout/flex 1)}]
                               :spacing 1)
                 10 9)]
      (is (= [4 4] (mapv :height boxes)))
      (is (= 9 (+ (reduce + (map :height boxes)) 1)))
      (is (= [0 5] (mapv :y boxes))))))
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
      (is (= "Wor" (second clipped)))))

  (testing "Clip is column-aware for wide clusters"
    (component/assert-lines ["日 "] (layout/clip "日本語" 3 1))
    (is (= 3 (text/width (first (layout/clip "日本語" 3 1))))))

  (testing "Clip rejects invalid dimensions"
    (is (thrown? clojure.lang.ExceptionInfo (layout/clip "x" -1 1)))
    (is (thrown? clojure.lang.ExceptionInfo (layout/clip "x" 1 1.5)))))

(deftest test-pad-to-box
  (testing "Pad plain and wide content to the exact columns"
    (is (= ["ab   "] (layout/pad-to-box ["ab"] 5 1)))
    (is (= ["日本 "] (layout/pad-to-box ["日本"] 5 1)))
    (is (= 5 (text/width (first (layout/pad-to-box ["日本"] 5 1))))))

  (testing "Pad height with exact-width blank lines"
    (let [padded (layout/pad-to-box ["x"] 3 3)]
      (is (= ["x  " "   " "   "] padded)))))

(deftest stack-spacing-beyond-height-throws
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo #"spacing|space"
       (layout/layout-stack
        (layout/stack [{:constraint (layout/fixed 1)}
                       {:constraint (layout/fixed 1)}]
                      :spacing 50)
        10 10))))

(deftest readme-layout-example
  (testing "The README stack example resolves to its documented tuples"
    (let [page (layout/stack
                [{:constraint (layout/fixed 2) :content :header}
                 {:constraint (layout/flex 1) :content :body}
                 {:constraint (layout/fixed 1) :content :footer}]
                :spacing 1)]
      (is (= [[0 2 :header] [3 5 :body] [9 1 :footer]]
             (mapv (juxt :y :height :content)
                   (layout/layout-stack page 40 10)))))))

(deftest flex-totals-property
  (testing "All-flex stacks allocate exactly the available height"
    (let [result (tc/quick-check
                  100
                  (prop/for-all [n (gen/choose 1 6)
                                 total (gen/choose 0 60)]
                    (let [boxes (layout/layout-stack
                                 (layout/stack
                                  (vec (repeat n {:constraint (layout/flex 1)})))
                                 10 total)]
                      (= total (reduce + (map :height boxes))))))]
      (is (:pass? result) (pr-str result)))))

(deftest clip-never-exceeds-requested-width-property
  (testing "Clipping generated text never exceeds the requested columns"
    (let [result (tc/quick-check
                  100
                  (prop/for-all [lines component/lines-gen
                                 width (gen/choose 0 12)]
                    (every? #(<= (text/width %) width)
                            (layout/clip (clojure.string/join "\n" lines) width 3))))]
      (is (:pass? result) (pr-str result)))))

;; Run all tests
(defn run-tests []
  (clojure.test/run-tests 'limner.layout-test))
