(ns limner.borders.internal
  "Input validation shared by `limner.borders` and its composition helpers.

  These checks live here rather than in `limner.borders` so the shadow and
  composition functions can validate without creating a namespace cycle. The
  error keywords still belong to `limner.borders` (ADR 0004)."
  (:require [clojure.string :as str]))

(defn validate-lines
  "Return `lines` when it is a collection of strings, else throw."
  [lines]
  (when-not (coll? lines)
    (throw (ex-info "Lines must be a collection"
                    {:limner/error :limner.borders/invalid-lines
                     :lines lines :type (type lines)})))
  (doseq [[idx line] (map-indexed vector lines)]
    (when-not (string? line)
      (throw (ex-info (str "Line " idx " is not a string")
                      {:limner/error :limner.borders/invalid-lines
                       :line line :index idx :type (type line)}))))
  lines)

(defn validate-dimension
  "Return `value` when it is an integer, else throw `invalid-dimension`."
  [value label]
  (when-not (integer? value)
    (throw (ex-info (str (str/capitalize label) " must be an integer")
                    {:limner/error :limner.borders/invalid-dimension
                     :value value :label label :type (type value)})))
  value)
