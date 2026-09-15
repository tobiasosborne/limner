(require '[clojure.string :as str] '[clojure.java.io :as io])
(def nss '[limner limner.core limner.terminal limner.borders limner.layout limner.render limner.events
           limner.state limner.streaming limner.syntax limner.components.input limner.components.list
           limner.components.markdown limner.components.panel limner.components.progress limner.components.statusbar])
(binding [*err* (java.io.StringWriter.)] (doseq [n nss] (require n)))
(def alias->ns
  {"core" 'limner.core "terminal" 'limner.terminal "term" 'limner.terminal "borders" 'limner.borders
   "layout" 'limner.layout "render" 'limner.render "events" 'limner.events "state" 'limner.state
   "stream" 'limner.streaming "streaming" 'limner.streaming "syntax" 'limner.syntax
   "input" 'limner.components.input "list" 'limner.components.list "markdown" 'limner.components.markdown
   "md" 'limner.components.markdown "panel" 'limner.components.panel "progress" 'limner.components.progress
   "statusbar" 'limner.components.statusbar "sb" 'limner.components.statusbar})
(def root "/home/tobiasosborne/Projects/limner/")
(def files (concat ["README.md"]
                   (map #(str "docs/" %) ["architecture.md" "tutorial.md"])
                   (map #(str "docs/api/" %) (.list (io/file root "docs/api")))
                   (map #(str "examples/" %) (.list (io/file root "examples")))))
(doseq [f (sort files)]
  (let [txt (slurp (str root f))
        refs (distinct (re-seq #"\((?:[a-z]+/)?([a-z][a-z-]*)/([a-z][a-zA-Z0-9!?*<>=-]*)" txt))
        missing (for [[_ a sym] refs
                      :let [ns (alias->ns a)]
                      :when (and ns (nil? (ns-resolve ns (symbol sym))))]
                  (str a "/" sym))]
    (when (seq missing)
      (println f "->" (count (distinct missing)) "missing:" (str/join ", " (distinct missing))))))
