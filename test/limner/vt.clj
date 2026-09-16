(ns limner.vt
  "A test-only virtual terminal for render-buffer golden tests.

  It consumes the byte stream `limner.render` emits: CUP cursor positioning,
  SGR through `limner.ansi.sgr` with the ADR 0003 style map, graphemes with
  widths from `limner.text`, and ED/EL clears. The result is a screen grid of
  `{:cluster :style}` maps, so tests compare terminal state rather than
  escape-sequence spelling."
  (:require [clojure.string :as str]
            [limner.ansi :as ansi]
            [limner.ansi.sgr :as sgr]
            [limner.text :as text]
            [limner.text.style :as style]
            [limner.unicode.grapheme :as grapheme]))

(defn empty-grid [width height]
  (vec (repeat height (vec (repeat width {:cluster " " :style {}})))))

(defn make-vt
  "Create a virtual terminal `width` by `height` columns."
  [width height]
  {:width width
   :height height
   :x 0
   :y 0
   :style {}
   :grid (empty-grid width height)})

(defn screen [vt] (:grid vt))
(defn cursor [vt] [(:x vt) (:y vt)])

(defn cell-at
  "Return the grid cell at zero-based `[x y]`, or nil when out of bounds."
  [vt x y]
  (get-in vt [:grid y x]))

(defn- put-grid [vt x y cell]
  (assoc-in vt [:grid y x] cell))

(defn- clusters-of [^String s]
  (let [bounds (grapheme/cluster-boundaries s)]
    (map (fn [[a b]] (subs s a b)) (partition 2 1 bounds))))

(defn- place-cluster [vt cluster]
  (let [w (text/width cluster)
        {:keys [x y style width height]} vt]
    (cond
      (or (>= y height) (>= x width))
      vt

      (zero? w)
      (if (pos? x)
        (update-in vt [:grid y (dec x) :cluster] str cluster)
        ;; A leading zero-width cluster has no preceding cell; the render
        ;; buffer attaches it to the following cluster before emitting, so
        ;; this branch should not occur for renderer output.
        (assoc vt :grid (assoc-in (:grid vt) [y x] {:cluster cluster :style style})))

      (= w 2)
      (if (< (inc x) width)
        (-> vt
            (put-grid x y {:cluster cluster :style style})
            (put-grid (inc x) y {:cluster "" :style {}})
            (assoc :x (+ x 2)))
        ;; Right edge: a wide cluster cannot fit; renderer writes a space.
        (-> vt
            (put-grid x y {:cluster " " :style style})
            (assoc :x (inc x))))

      :else
      (-> vt
          (put-grid x y {:cluster cluster :style style})
          (assoc :x (inc x))))))

(defn- parse-cup [params]
  (let [[row col] (str/split (or params "") #";")
        row (when (seq row) (parse-long row))
        col (when (seq col) (parse-long col))]
    [(or row 1) (or col 1)]))

(defn- clear-line [vt mode]
  (let [y (:y vt), x (:x vt)]
    (assoc-in vt [:grid y]
              (vec (map-indexed
                    (fn [index cell]
                      (if (or (= mode :all) (>= index x))
                        {:cluster " " :style {}}
                        cell))
                    (get-in vt [:grid y]))))))

(defn- feed-csi [vt {:keys [final params] :as token}]
  (case final
    \H (let [[row col] (parse-cup params)]
         (assoc vt :y (dec row) :x (dec col)))
    \m (assoc vt :style (style/apply-sgr (:style vt) (sgr/parse token)))
    \J (if (#{nil "" "2"} params)
         (assoc vt :grid (empty-grid (:width vt) (:height vt)))
         vt)
    \K (clear-line vt (if (= params "2") :all :to-end))
    vt))

(defn- feed-token [vt token]
  (case (:type token)
    :text (reduce place-cluster vt (clusters-of (:text token)))
    :csi (feed-csi vt token)
    :osc (if-let [{:keys [uri]} (ansi/hyperlink token)]
           (assoc vt :style (if uri
                              (assoc (:style vt) :link uri)
                              (dissoc (:style vt) :link)))
           vt)
    vt))

(defn feed
  "Consume a byte stream string and return the updated virtual terminal."
  [vt s]
  (reduce feed-token vt (ansi/tokenize s)))

(defn render
  "Feed a sequence of byte-stream strings in order."
  [vt & streams]
  (reduce feed vt streams))

(defn cluster-grid
  "Return only the `:cluster` of each cell, for concise golden comparisons."
  [vt]
  (mapv (fn [row] (mapv :cluster row)) (screen vt)))

(defn style-grid [vt]
  (mapv (fn [row] (mapv :style row)) (screen vt)))
