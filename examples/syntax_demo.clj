#!/usr/bin/env bb

(ns syntax-demo
  "Demo of the syntax highlighting module"
  (:require [limner.syntax :as syntax]
            [limner.core :as core]
            [clojure.string :as str]))

(def clojure-code
  "(ns my-app.core
  (:require [clojure.string :as str]))

(defn factorial
  \"Calculate factorial of n\"
  [n]
  (if (<= n 1)
    1
    (* n (factorial (dec n)))))

; Test the function
(println (factorial 5)) ; => 120

(def user {:name \"Alice\"
           :age 30})")

(def python-code
  "# Python example with decorators and f-strings

@app.route('/home')
def home():
    \"\"\"Home page handler\"\"\"
    name = 'World'
    message = f'Hello {name}!'
    return message

class Calculator:
    def __init__(self):
        self.result = 0

    def add(self, x, y):
        return x + y

# Test the calculator
calc = Calculator()
print(calc.add(2, 3))  # => 5")

(def javascript-code
  "// JavaScript with arrow functions and template literals

const greeting = (name) => {
  return `Hello ${name}!`;
};

class User {
  constructor(name, age) {
    this.name = name;
    this.age = age;
  }

  greet() {
    console.log(greeting(this.name));
  }
}

/* Multi-line
   comment block */
const user = new User('Alice', 30);
user.greet();")

(defn demo-clojure []
  (println (str "\n" (str/join "" (repeat 70 "═"))))
  (println "  Clojure Syntax Highlighting")
  (println (str/join "" (repeat 70 "═")))
  (println)
  (println (syntax/highlight clojure-code :clojure))
  (println))

(defn demo-python []
  (println (str "\n" (str/join "" (repeat 70 "═"))))
  (println "  Python Syntax Highlighting")
  (println (str/join "" (repeat 70 "═")))
  (println)
  (println (syntax/highlight python-code :python))
  (println))

(defn demo-javascript []
  (println (str "\n" (str/join "" (repeat 70 "═"))))
  (println "  JavaScript Syntax Highlighting")
  (println (str/join "" (repeat 70 "═")))
  (println)
  (println (syntax/highlight javascript-code :javascript))
  (println))

(defn demo-themes []
  (println (str "\n" (str/join "" (repeat 70 "═"))))
  (println "  Different Themes")
  (println (str/join "" (repeat 70 "═")))
  (println)

  (let [code "(defn add [x y] (+ x y))"]
    (println "Default Theme:")
    (println (syntax/highlight code :clojure :theme :default))
    (println)

    (println "Monokai Theme:")
    (println (syntax/highlight code :clojure :theme :monokai))
    (println)

    (println "Solarized Theme:")
    (println (syntax/highlight code :clojure :theme :solarized))
    (println)))

(defn demo-line-numbers []
  (println (str "\n" (str/join "" (repeat 70 "═"))))
  (println "  With Line Numbers")
  (println (str/join "" (repeat 70 "═")))
  (println)

  (let [code "(defn factorial [n]\n  (if (<= n 1)\n    1\n    (* n (factorial (dec n)))))"]
    (println (syntax/highlight-with-line-numbers code :clojure :start-line 1))
    (println)))

(defn demo-language-detection []
  (println (str "\n" (str/join "" (repeat 70 "═"))))
  (println "  Language Detection")
  (println (str/join "" (repeat 70 "═")))
  (println)

  (println "From filename:")
  (println "  test.clj    =>" (syntax/detect-language "test.clj"))
  (println "  script.py   =>" (syntax/detect-language "script.py"))
  (println "  app.js      =>" (syntax/detect-language "app.js"))
  (println)

  (println "From content:")
  (println "  (defn ...)  =>" (syntax/detect-language-from-content "(defn foo [])"))
  (println "  def ...     =>" (syntax/detect-language-from-content "def function():"))
  (println "  const x =>  =>" (syntax/detect-language-from-content "const x = () => {}"))
  (println))

(defn demo-tokenization []
  (println (str "\n" (str/join "" (repeat 70 "═"))))
  (println "  Tokenization")
  (println (str/join "" (repeat 70 "═")))
  (println)

  (let [code "(def x 123)"
        tokens (syntax/tokenize code :clojure)]
    (println "Code:" code)
    (println "Tokens:")
    (doseq [token tokens]
      (println (format "  %-10s : %s" (str (:type token)) (pr-str (:value token)))))
    (println)))

(defn demo-comparison []
  (println (str "\n" (str/join "" (repeat 70 "═"))))
  (println "  Before and After")
  (println (str/join "" (repeat 70 "═")))
  (println)

  (let [code "(defn greet [name] (str \"Hello, \" name \"!\"))"]
    (println "Without highlighting:")
    (println code)
    (println)
    (println "With highlighting:")
    (println (syntax/highlight code :clojure))
    (println)
    (println "Visible length comparison:")
    (println "  Original:" (count code))
    (println "  Highlighted:" (count (syntax/highlight code :clojure)))
    (println "  Visible (stripped):" (core/visible-length (syntax/highlight code :clojure)))
    (println)))

(defn demo-complex-code []
  (println (str "\n" (str/join "" (repeat 70 "═"))))
  (println "  Complex Code Example")
  (println (str/join "" (repeat 70 "═")))
  (println)

  (let [code "(defn process-data
  \"Process a collection of data with filters\"
  [data & {:keys [filter-fn map-fn]
           :or {filter-fn identity
                map-fn str}}]
  (->> data
       (filter filter-fn)
       (map map-fn)
       (take 10)))

; Usage with keywords
(process-data [1 2 3 4 5]
              :filter-fn odd?
              :map-fn #(* % 2))"]
    (println (syntax/highlight code :clojure))
    (println)))

(defn -main []
  (println)
  (println "╔════════════════════════════════════════════════════════════════════╗")
  (println "║         Limner Syntax Highlighting Demo                            ║")
  (println "╚════════════════════════════════════════════════════════════════════╝")

  (demo-comparison)
  (demo-clojure)
  (demo-python)
  (demo-javascript)
  (demo-themes)
  (demo-line-numbers)
  (demo-tokenization)
  (demo-language-detection)
  (demo-complex-code)

  (println (str "\n" (str/join "" (repeat 70 "═"))))
  (println (str "  Available themes: " (str/join ", " (map name (syntax/available-themes)))))
  (println (str/join "" (repeat 70 "═")))
  (println "\nDemo complete!"))

;; Run if executed directly
(when (= *file* (System/getProperty "babashka.file"))
  (-main))
