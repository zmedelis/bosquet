(ns bosquet.converter
  (:require
   [clojure.string :as s]
   [jsonista.core :as j]
   [taoensso.timbre :as timbre]))

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

(defn ->bool
  "Converts yes/no answer to boolean

   Yes => true
   NO => false"
  [answer]
  (condp = (-> answer s/trim s/lower-case)
    "yes"   true
    "true"  true
    "no"    false
    "false" false
    answer))

(defn ->number
  [num]
  (cond
    (re-matches #"\d+" num) (Integer/parseInt num)
    (re-matches #"\d+(\.\d+)?" num) (Double/parseDouble num)
    :else num))

(defn json-reader
  "Some models (GPT-3.5-*, Cohere) tend to wrap response with Makrdown code
  ```json
  GOOD JSON CONTENT
  ```
  Strip that markdown"
  [completion]
  (-> completion
      (s/replace #"(?ms).*?```json" "")
      (s/replace #"(?ms)```" "")
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
      (s/replace #"(?ms)^```(edn|clojure)" "")
      (s/replace #"(?ms)```$" "")
      (read-string)))

(defn coerce
  [format completion]
  (try
    (condp = format
      :json (json-reader completion)
      :edn  (edn-reader completion)
      :list (list-reader completion)
      :number (->number completion)
      :bool (->bool completion)
      completion)
    (catch Exception e
      (timbre/error e)
      (timbre/error "Returning generated data withouth coercion")
      completion)))
