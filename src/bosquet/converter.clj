(ns bosquet.converter
  (:require
   [clojure.string :as s]))

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
