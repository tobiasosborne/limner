#!/usr/bin/env bb

(ns fetch-ucd
  "Fetch pinned Unicode source data without weakening its integrity boundary.

  Downloads are staged away from the repository.  An existing version is
  refreshed only after every staged file matches its committed SHA-256
  manifest, so a changed upstream response cannot partially overwrite the
  ground truth used by generators and tests."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.net URL]
           [java.nio.file Files StandardCopyOption]
           [java.security MessageDigest]))

(def unicode-root "reference/unicode")

(defn- emoji-version [ucd-version]
  (str/join "." (take 2 (str/split ucd-version #"\."))))

(defn- sources [version]
  (let [public-root (str "https://www.unicode.org/Public/" version)
        emoji (emoji-version version)]
    [{:path "ucd/EastAsianWidth.txt"
      :url (str public-root "/ucd/EastAsianWidth.txt")
      :marker version}
     {:path "ucd/emoji/emoji-data.txt"
      :url (str public-root "/ucd/emoji/emoji-data.txt")
      :marker (str "Version: " emoji)}
     {:path "ucd/auxiliary/GraphemeBreakProperty.txt"
      :url (str public-root "/ucd/auxiliary/GraphemeBreakProperty.txt")
      :marker version}
     {:path "ucd/auxiliary/GraphemeBreakTest.txt"
      :url (str public-root "/ucd/auxiliary/GraphemeBreakTest.txt")
      :marker version}
     {:path "ucd/DerivedCoreProperties.txt"
      :url (str public-root "/ucd/DerivedCoreProperties.txt")
      :marker version}
     {:path "ucd/extracted/DerivedGeneralCategory.txt"
      :url (str public-root "/ucd/extracted/DerivedGeneralCategory.txt")
      :marker version}
     {:path (str "emoji/" emoji "/emoji-test.txt")
      :url (str public-root "/emoji/emoji-test.txt")
      :marker (str "Version: " emoji)}
     {:path "../LICENSE"
      :url "https://www.unicode.org/license.txt"
      :marker "UNICODE LICENSE V3"}]))

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
  (into (sorted-map)
        (map (fn [line]
               (let [[checksum path] (str/split line #"  " 2)]
                 [path checksum])))
        (remove str/blank? (str/split-lines (slurp file)))))

(defn- write-manifest! [file paths staged-root]
  (spit file
        (apply str
               (for [path paths]
                 (str (sha256 (io/file staged-root path)) "  " path "\n")))))

(defn- download! [{:keys [path url marker]} staged-root]
  (let [target (io/file staged-root path)]
    (.mkdirs (.getParentFile target))
    (println "Downloading" url)
    (with-open [input (.openStream (URL. url))]
      (io/copy input target))
    (let [header (->> (line-seq (io/reader target)) (take 12) (str/join "\n"))]
      (when-not (str/includes? header marker)
        (throw (ex-info "Downloaded Unicode file has the wrong version header"
                        {:url url :path path :expected marker}))))))

(defn- verify! [manifest staged-root expected-paths]
  (when-not (= (set expected-paths) (set (keys manifest)))
    (throw (ex-info "SHA256SUMS does not cover exactly the files to refresh"
                    {:expected expected-paths :manifest (vec (keys manifest))})))
  (doseq [[path expected] manifest]
    (let [actual (sha256 (io/file staged-root path))]
      (when-not (= expected actual)
        (throw (ex-info "Downloaded Unicode data does not match SHA256SUMS; nothing was overwritten"
                        {:path path :expected expected :actual actual})))))
  (println "Verified" (count manifest) "files against SHA256SUMS"))

(defn- install! [paths staged-root destination-root]
  (doseq [path paths]
    (let [source (.toPath (io/file staged-root path))
          target (io/file destination-root path)]
      (.mkdirs (.getParentFile target))
      (Files/copy source (.toPath target)
                  (into-array StandardCopyOption
                              [StandardCopyOption/REPLACE_EXISTING]))
      (println "Installed" (.getPath target)))))

(defn- delete-tree! [root]
  (doseq [file (reverse (file-seq root))]
    (Files/deleteIfExists (.toPath file))))

(defn fetch! [version]
  (let [destination-root (io/file unicode-root version)
        destination-manifest (io/file destination-root "SHA256SUMS")
        temporary-root (.toFile (Files/createTempDirectory
                                 "limner-ucd-"
                                 (make-array java.nio.file.attribute.FileAttribute 0)))
        staged-root (io/file temporary-root version)
        source-list (sources version)
        paths (mapv :path source-list)]
    (try
      (.mkdirs staged-root)
      (doseq [source source-list]
        (download! source staged-root))
      (if (.isFile destination-manifest)
        (verify! (read-manifest destination-manifest) staged-root paths)
        (do
          (when (.exists destination-root)
            (throw (ex-info "Existing Unicode version has no SHA256SUMS; refusing to overwrite it"
                            {:version version :path (.getPath destination-root)})))
          (write-manifest! (io/file staged-root "SHA256SUMS") paths staged-root)
          (println "Created SHA256SUMS for new version" version)))
      (install! paths staged-root destination-root)
      (when-not (.isFile destination-manifest)
        (Files/copy (.toPath (io/file staged-root "SHA256SUMS"))
                    (.toPath destination-manifest)
                    (make-array StandardCopyOption 0))
        (println "Installed" (.getPath destination-manifest)))
      (println "Unicode" version "refresh complete")
      (finally
        (delete-tree! temporary-root)))))

(defn -main [& args]
  (when-not (= 1 (count args))
    (throw (ex-info "Usage: bb fetch-ucd <UCD-version>"
                    {:arguments args :example "bb fetch-ucd 17.0.0"})))
  (fetch! (first args)))

(apply -main *command-line-args*)
