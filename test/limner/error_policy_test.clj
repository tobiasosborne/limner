(ns limner.error-policy-test
  "Locks down ADR 0004: invalid input throws ex-info with a :limner/error class,
  and library code does not print or invent a fallback."
  (:require [clojure.test :refer [deftest is testing]]
            [limner.ansi :as ansi]
            [limner.ansi.sgr :as sgr]
            [limner.borders :as borders]
            [limner.core :as core]
            [limner.layout :as layout]
            [limner.render :as render]
            [limner.text :as text]
            [limner.text.style :as style]
            [limner.unicode :as unicode]
            [limner.unicode.grapheme :as grapheme]
            [limner.unicode.width :as width]))

(defn- error-class [f]
  (:limner/error (try (f) (catch clojure.lang.ExceptionInfo e (ex-data e)))))

(defn- thrown-class [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e (:limner/error (ex-data e)))
    (catch Throwable t
      (keyword "test/unexpected" (.getName (class t))))))

;; ────────────────────── limner.core ──────────────────────

(deftest core-throws-on-invalid-color-arguments
  (testing "rgb and bg-rgb reject non-byte components"
    (is (= :limner.core/invalid-rgb (thrown-class #(core/rgb -1 0 0))))
    (is (= :limner.core/invalid-rgb (thrown-class #(core/rgb 256 0 0))))
    (is (= :limner.core/invalid-rgb (thrown-class #(core/bg-rgb 0 0 999))))
    (testing "the offending value travels in the data map"
      (is (= -1 (:r (try (core/rgb -1 0 0)
                         (catch clojure.lang.ExceptionInfo e (ex-data e))))))))


  (testing "color-256 and bg-256 reject out-of-range indices"
    (is (= :limner.core/invalid-color-index (thrown-class #(core/color-256 -1))))
    (is (= :limner.core/invalid-color-index (thrown-class #(core/color-256 256))))
    (is (= :limner.core/invalid-color-index (thrown-class #(core/bg-256 1000))))))

(deftest core-color-rejects-unknown-specs
  (testing "an unknown colour spec throws instead of warning and returning text"
    (is (= :limner.core/invalid-color
           (thrown-class #(core/color :nonexistent-color "x"))))
    (is (= :limner.core/invalid-color
           (thrown-class #(core/color "not-a-color" "x"))))
    (is (= :limner.core/invalid-string
           (thrown-class #(core/color :red nil))))
    (is (= :limner.core/invalid-string
           (thrown-class #(core/color :red 42))))))

;; ────────────────────── limner.borders ──────────────────────

(deftest borders-reject-unknown-style
  (is (= :limner.borders/unknown-border-style
         (thrown-class #(borders/get-border-chars :definitely-not-a-style))))
  (testing "known styles still resolve"
    (is (= ["┌" "┐" "└" "┘" "─" "│"] (borders/get-border-chars :single)))
    (is (= ["+" "+" "+" "+" "-" "|"] (borders/get-border-chars :ascii)))))

(deftest borders-reject-invalid-title-pos
  (is (= :limner.borders/invalid-title-pos
         (thrown-class #(borders/draw-titled-box "t" ["x"] :title-pos :middle)))))

(deftest borders-reject-invalid-dimensions
  (is (= :limner.borders/invalid-dimension
         (thrown-class #(borders/indent-lines ["x"] 1.5))))
  (is (= :limner.borders/invalid-dimension
         (thrown-class #(borders/nest-box ["x"] -0.5))))
  (is (= :limner.borders/invalid-dimension
         (thrown-class #(borders/side-by-side ["a"] ["b"] 1.5)))))

(deftest borders-do-not-fabricate-boxes
  (testing "invalid input reaches the caller instead of a '+-+' fallback"
    (is (thrown? clojure.lang.ExceptionInfo
                 (borders/draw-box [1 2 3] :border-style :single)))
    (is (= :limner.borders/invalid-lines
           (thrown-class #(borders/draw-box [1 2 3] :border-style :single))))))

;; ────────────────────── limner.layout ──────────────────────

(deftest layout-constraints-throw
  (is (= :limner.layout/invalid-dimension (thrown-class #(layout/fixed -1))))
  (is (= :limner.layout/invalid-dimension (thrown-class #(layout/fixed 1.5))))
  (is (= :limner.layout/invalid-constraint (thrown-class #(layout/percent 101)))
   "percent clamps in the old code; ADR 0004 makes an out-of-range value explicit")
  (is (= :limner.layout/invalid-constraint
         (thrown-class #(layout/flex -1)))))

(deftest layout-rejects-unknown-types
  (is (= :limner.layout/invalid-constraint
         (thrown-class #(layout/resolve-constraint {:type :bogus} 10 1))))
  (is (= :limner.layout/unknown-layout-type
         (thrown-class #(layout/layout {:type :bogus} 10 10)))))

(deftest layout-rejects-invalid-dimensions
  (is (= :limner.layout/invalid-dimension
         (thrown-class #(layout/clip ["x"] -1 1))))
  (is (= :limner.layout/invalid-dimension
         (thrown-class #(layout/layout-stack (layout/stack [{:constraint (layout/fixed 1)}])
                                              -1 10))))
  (is (= :limner.layout/empty-components
         (thrown-class #(layout/stack []))))
  (is (= :limner.layout/invalid-grid
         (thrown-class #(layout/grid [] :columns 0)))))

;; ────────────────────── limner.render buffer ──────────────────────

(deftest render-rejects-non-strings
  (is (= :limner.render/invalid-string
         (thrown-class #(render/write-string-to-buffer (render/create-buffer 3 1) 0 0 42))))
  (is (= :limner.render/invalid-lines
         (thrown-class #(render/write-lines-to-buffer (render/create-buffer 3 1) 0 0 ["ok" 7])))))

;; ────────────────────── limner.text and limner.unicode ──────────────────────

(deftest text-and-unicode-throw-classes
  (is (= :limner.text/invalid-options (thrown-class #(text/width "x" []))))
  (is (= :limner.text/invalid-options (thrown-class #(text/width "x" {:bogus 1}))))
  (is (= :limner.text/invalid-options (thrown-class #(text/width "x" {:ambiguous-width 3}))))
  (is (= :limner.text/invalid-bounds (thrown-class #(text/slice-columns "x" 2 1))))
  (is (= :limner.text/invalid-target (thrown-class #(text/pad-right "x" -1))))
  (is (= :limner.text/invalid-width (thrown-class #(text/wrap "x" 0))))
  (is (= :limner.text/invalid-fill (thrown-class #(text/pad-right "x" 3 "ab"))))
  (is (= :limner.text/invalid-input (thrown-class #(text/width 42))))
  (is (= :limner.text.style/invalid-color
         (thrown-class #(style/normalize-style {:fg :ultraviolet}))))
  (is (= :limner.text.style/invalid-style
         (thrown-class #(style/normalize-style {:bogus true}))))
  (is (= :limner.unicode/invalid-code-point (thrown-class #(unicode/east-asian-width -1))))
  (is (= :limner.unicode.width/invalid-options
         (thrown-class #(width/code-point-width 65 {:ambiguous-width 9}))))
  (is (= :limner.unicode.width/invalid-code-point
         (thrown-class #(width/code-point-width -1 {}))))
  (is (= :limner.unicode.width/invalid-code-point
         (thrown-class #(width/code-point-width 0xD800 {}))))
  (is (= :limner.unicode.width.string/surrogate
         (thrown-class #(width/string-width "\uD800" {}))))
  (is (= :limner.unicode.width/invalid-cluster (thrown-class #(width/cluster-width "" {}))))
  (is (= :limner.unicode.width/invalid-string (thrown-class #(width/string-width nil {}))))
  (is (= :limner.unicode.grapheme/invalid-string
         (thrown-class #(grapheme/cluster-boundaries nil)))))

(deftest ansi-and-sgr-throw-classes
  (is (= :limner.ansi/invalid-input (thrown-class #(ansi/tokenize nil))))
  (is (= :limner.ansi/invalid-input (thrown-class #(ansi/strip 42))))
  (is (= :limner.ansi.sgr/invalid-token (thrown-class #(sgr/parse {:type :text})))))

;; ────────────────────── no printing ──────────────────────

(deftest library-errors-do-not-print
  (testing "throwing leaves stdout and stderr untouched"
    (let [out (java.io.StringWriter.)
          err (java.io.StringWriter.)]
      (binding [*out* out *err* err]
        (is (thrown? clojure.lang.ExceptionInfo (core/color :nope "x")))
        (is (thrown? clojure.lang.ExceptionInfo (borders/get-border-chars :nope)))
        (is (thrown? clojure.lang.ExceptionInfo (layout/layout {:type :nope} 1 1))))
      (is (= "" (str out)))
      (is (= "" (str err))))))
