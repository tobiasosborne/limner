(ns limner.unicode-data-test
  "Integrity checks for the pinned Unicode source material.

  These tests deliberately perform no network access.  They make the vendored
  bytes and their manifest part of both runtime test suites, so generated
  Unicode behaviour always has a reviewable, immutable source."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import [java.security MessageDigest]))

(def unicode-root "reference/unicode/17.0.0")

(def vendored-paths
  ["ucd/EastAsianWidth.txt"
   "ucd/emoji/emoji-data.txt"
   "ucd/auxiliary/GraphemeBreakProperty.txt"
   "ucd/auxiliary/GraphemeBreakTest.txt"
   "ucd/DerivedCoreProperties.txt"
   "ucd/extracted/DerivedGeneralCategory.txt"
   "emoji/17.0/emoji-test.txt"
   "../LICENSE"])

(defn- sha256 [file]
  (let [digest (MessageDigest/getInstance "SHA-256")
        buffer (byte-array 8192)]
    (with-open [input (io/input-stream file)]
      (loop []
        (let [read-count (.read input buffer)]
          (when (pos? read-count)
            (.update digest buffer 0 read-count)
            (recur)))))
    (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest digest)))))

(defn- read-manifest [file]
  (into {}
        (map (fn [line]
               (let [[checksum path] (str/split line #"  " 2)]
                 [path checksum])))
        (remove str/blank? (str/split-lines (slurp file)))))

(deftest vendored-unicode-files-match-sha256-manifest
  (let [manifest-file (io/file unicode-root "SHA256SUMS")]
    (testing "the checksum manifest exists"
      (is (.isFile manifest-file)
          "reference/unicode/17.0.0/SHA256SUMS must be committed"))
    (when (.isFile manifest-file)
      (let [manifest (read-manifest manifest-file)]
        (testing "the manifest covers exactly the required upstream files"
          (is (= (set vendored-paths) (set (keys manifest)))))
        (doseq [path vendored-paths]
          (testing path
            (let [file (io/file unicode-root path)]
              (is (.isFile file))
              (when (.isFile file)
                (is (= (get manifest path) (sha256 file)))))))))))
