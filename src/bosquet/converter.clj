(ns bosquet.converter
  (:require
   [clojure.string :as s]
   [jsonista.core :as j]))

;; WIP - a place to start building output conversion functions

(defn- drop-digit [item]
  (s/trim (s/replace-first item #"\d+\." "")))

(defn numbered-items->list
  "Converts numbered item list given as a new line
  separated string to a list

  1. foo
  2. bar
  3. baz
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

(defn coerce
  [completion {:bosquet.llm/keys [output-format]}]
  (condp =  output-format
    :json (j/read-value completion)
    :text completion
    completion))
