(ns limner.table
  "Column-correct table rendering.

  Every cell is measured, padded, and aligned in terminal display columns
  through `limner.text`, so ASCII, CJK, emoji ZWJ sequences, and cells that
  already carry ANSI all line up. Column widths are the maximum width of the
  cells in that column, and each cell gets `:padding` spaces on both sides.

  Rows are rectangular by contract: a row whose cell count differs from the
  first row's throws instead of drawing a misaligned table (REVIEW F8).

  Invalid input throws `ex-info` with an ADR 0004 class:
  `:limner.table/invalid-rows`, `:limner.table/empty-rows`,
  `:limner.table/invalid-row`, `:limner.table/ragged-rows`,
  `:limner.table/invalid-alignment`, or `:limner.table/invalid-padding`."
  (:require [limner.text :as text]))

(def alignments
  "The supported cell alignments."
  #{:left :right :center})

(defn- fail [error-class message data]
  (throw (ex-info message (assoc data :limner/error error-class))))

(defn- normalize-cell
  "Coerce a non-string scalar to its display string; keep styled values."
  [cell]
  (cond
    (string? cell) cell
    (vector? cell) cell
    (nil? cell) ""
    :else (str cell)))

(defn- normalize-rows
  "Validate that `rows` is a non-empty rectangular collection of cells."
  [rows]
  (when-not (sequential? rows)
    (fail :limner.table/invalid-rows
          "Table rows must be a sequential collection"
          {:rows rows :type (type rows)}))
  (when (empty? rows)
    (fail :limner.table/empty-rows "Table requires at least one row" {:rows rows}))
  (doseq [[idx row] (map-indexed vector rows)]
    (when-not (sequential? row)
      (fail :limner.table/invalid-row
            "Every table row must be a sequential collection of cells"
            {:row row :row-index idx :type (type row)})))
  (let [expected (count (first rows))]
    (doseq [[idx row] (map-indexed vector rows)]
      (when-not (= expected (count row))
        (fail :limner.table/ragged-rows
              "Every table row must have the same number of cells"
              {:row-index idx :expected expected :actual (count row)
               :row row
               :limner/hint "Pad short rows with empty strings or split them"})))
    (mapv (fn [row] (mapv normalize-cell row)) rows)))

(defn- normalize-aligns
  "Return one alignment keyword per column."
  [align columns]
  (let [aligns (cond
                 (nil? align) (vec (repeat columns :left))
                 (keyword? align) (vec (repeat columns align))
                 (sequential? align) (vec align)
                 :else (fail :limner.table/invalid-alignment
                             "Table alignment must be a keyword or a per-column vector"
                             {:align align :allowed (sort alignments)}))]
    (when-not (= columns (count aligns))
      (fail :limner.table/invalid-alignment
            "Table alignment vector must have one entry per column"
            {:align align :columns columns :actual (count aligns)}))
    (doseq [value aligns]
      (when-not (contains? alignments value)
        (fail :limner.table/invalid-alignment
              "Unknown table alignment"
              {:align value :allowed (sort alignments)})))
    aligns))

(defn- pad-cell [cell width align]
  (case align
    :left (text/pad-right cell width)
    :right (text/pad-left cell width)
    :center (text/center cell width)))

(defn- render-row
  "Render one row as a vertical-rule-delimited ANSI string."
  [row widths aligns padding]
  (let [spaces (apply str (repeat padding " "))
        cells (map-indexed (fn [idx cell]
                             (text/concat spaces
                                          (pad-cell cell (nth widths idx) (nth aligns idx))
                                          spaces))
                           row)]
    (text/to-ansi (text/concat "│" (apply text/concat (interpose "│" cells)) "│"))))

(defn- rule-line
  "Render a horizontal rule whose segments span each column's padded width."
  [left mid right widths padding]
  (let [segment #(apply str (repeat (+ % (* 2 padding)) "─"))]
    (str left (apply str (interpose mid (map segment widths))) right)))

(defn table
  "Render `rows` as a bordered table.

  `rows` is a non-empty rectangular collection of rows; each cell is a
  string, a `limner.text` styled value, or a scalar that is coerced with
  `str`. A row with a different cell count throws
  `:limner.table/ragged-rows`.

  Options:
  - `:header?` - draw a rule after the first row (default true);
  - `:align` - `:left` (default), `:right`, `:center`, or a vector with one
    alignment per column;
  - `:padding` - spaces on each side of every cell (default 1).

  Every returned line has the same display width."
  [rows & {:keys [header? align padding] :or {header? true padding 1}}]
  (when-not (and (integer? padding) (<= 0 padding))
    (fail :limner.table/invalid-padding
          "Table padding must be a non-negative integer"
          {:padding padding :type (type padding)}))
  (let [rows (normalize-rows rows)
        columns (count (first rows))
        widths (mapv (fn [col]
                       (apply max 0 (map #(text/width (nth % col)) rows)))
                     (range columns))
        aligns (normalize-aligns align columns)
        content (mapv #(render-row % widths aligns padding) rows)
        top (rule-line "┌" "┬" "┐" widths padding)
        bottom (rule-line "└" "┴" "┘" widths padding)
        separator (rule-line "├" "┼" "┤" widths padding)
        body (if (and header? (> (count rows) 1))
               (concat [(first content) separator] (rest content))
               content)]
    (vec (concat [top] body [bottom]))))
