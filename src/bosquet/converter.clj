(ns bosquet.converter
  (:require
   [clojure.string :as s]
   [jsonista.core :as j]))

;; WIP - a place to start building output conversion functions

(defn- drop-digit [item]
  (s/trim (s/replace-first item #"^((\d+\.)|\-|\*) " "")))

(defn list-reader
  "Converts numbered item list given as a new line
  separated string to a list

  1. foo
  2. bar
  3. baz

  '-' and '*' works for unordered lists
  =>
  [\"foo\" \"bar\" \"baz\"]"
  [items]
  (map drop-digit
    (s/split (s/trim items) #"\n")))

(defn yes-no->bool
  "Converts yes/no answer to boolean

   Yes => true
   NO => false"
  [answer]
  (condp = (-> answer s/trim s/lower-case)
    "yes" true
    "no"  false
    nil))

(defn json-reader
  "GPT-3.5-* tends to wrap response with Makrdown code
  ```json
  GOOD JSON CONTENT
  ```
  Strip that markdown
  "
  [completion]
  (-> completion
    (s/replace #"(?m)^```json" "")
    (s/replace #"(?m)```$" "")
    (j/read-value)))

(defn edn-reader
  "GPT-3.5-* tends to wrap response with Makrdown code
  ```edn OR clojure
  GOOD EDN CONTENT
  ```
  Strip that markdown
  "
  [completion]
  (-> completion
    (s/replace #"(?m)^```(edn|clojure)" "")
    (s/replace #"(?m)```$" "")
    (read-string)))

(defn coerce
  [completion format]
  (condp = format
    :json (json-reader completion)
    :edn  (edn-reader completion)
    :list (list-reader completion)
    completion))
