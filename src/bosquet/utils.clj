(ns bosquet.utils
  (:require
   [clojure.string :as string]
   [camel-snake-kebab.extras :as cske]
   [camel-snake-kebab.core :as csk]
   [jsonista.core :as j]
   [me.flowthing.pp :as pp])
  (:import
   [java.util UUID]))

(defn uuid []
  (UUID/randomUUID))

(defn pp-str
  [x]
  (with-out-str (pp/pprint x)))

(defn pp
  [x]
  (pp/pprint x))

(defn safe-subs
  "Substring with safety of going over the max length"
  ([s start end]
   (subs s start (min end (count s))))
  ([s start]
   (subs s start)))

(defn concatv
  "Non-lazily concat any number of collections, returning a persistent vector."
  ([]
   [])
  ([x]
   (vec x))
  ([x & ys]
   (into (vec x) cat ys)))

(defn join-nl [& lines]
  (apply str (interpose "\n" lines)))

(defn join-coll [content]
  (if (coll? content) (string/join "\n" content) content))

(def separator
  "A separator to be used in prompts to indicate data bloks."
  "~~~~~~~~~~~~~~")

(defn read-json
  "Read JSON from a string keywordizing keys"
  [s]
  (j/read-value s j/keyword-keys-object-mapper))

(defn write-json
  "Write JSON to a string"
  [s]
  (j/write-value-as-string s))

(defn flattenx
  "Flatten a nested collection"
  [coll]
  (remove nil? (flatten coll)))

(defn mergex
  "Merge maps filtering nil values"
  [& maps]
  (apply
   merge
   (map (fn [a-map]
          (reduce-kv
           (fn [m k v] (if (nil? v) m (assoc m k v)))
           {}
           a-map))
        maps)))

(defn snake_case
  [m]
  (cske/transform-keys csk/->snake_case_keyword m))
