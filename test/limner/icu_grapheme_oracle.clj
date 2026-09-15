(ns limner.icu-grapheme-oracle
  "Optional ICU4J oracle loaded only by the `:icu` test alias."
  (:import [com.ibm.icu.text BreakIterator]
           [com.ibm.icu.util ULocale VersionInfo]))

(defn version
  "Return the ICU4J runtime version used by the oracle."
  []
  (str VersionInfo/ICU_VERSION))

(defn boundaries
  "Return ICU4J character-break boundaries as UTF-16 indices."
  [^String text]
  (let [^BreakIterator iterator (BreakIterator/getCharacterInstance ULocale/ROOT)]
    (.setText iterator text)
    (loop [boundary (.first iterator), result (transient [])]
      (if (= BreakIterator/DONE boundary)
        (persistent! result)
        (recur (.next iterator) (conj! result boundary))))))
