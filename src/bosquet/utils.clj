(ns bosquet.utils
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.walk :refer [postwalk]]
   [jsonista.core :as j]
   [me.flowthing.pp :as pp]
   [taoensso.timbre :as timbre]
   [net.modulolotus.truegrit.circuit-breaker :as cb])
  (:import
   [java.util UUID]))

(def rest-service-cb (cb/circuit-breaker "shared-rest-service"
                                         {:failure-rate-threshold 30
                                          :minimum-number-of-calls 2}))

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

(defn join-lines [& lines]
  (apply str (interpose "\n" lines)))

(defn join-coll [content]
  (if (coll? content) (string/join "\n" content) content))

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

(defn kebab->snake [s]
  (string/replace s #"-" "_"))

(defn camel->snake [s]
  (string/replace s #"([a-z0-9])([A-Z])" "$1_$2"))

(defn ->snake_case_keyword [k]
  (-> k
      name
      kebab->snake
      camel->snake
      string/lower-case
      keyword))

;; Taken from camel-snake-kebab.extras
;; https://clj-commons.org/camel-snake-kebab/
;; conflicts with clj-commons/clj-yaml {:mvn/version "1.0.27"}
(defn transform-keys
  "Recursively transforms all map keys in coll with t."
  [t coll]
  (letfn [(transform [[k v]] [(t k) v])]
    (postwalk (fn [x] (if (map? x) (into {} (map transform x)) x)) coll)))

(defn snake-case
  "Snake case keys from `:max-tokens` to `:max_tokens`"
  [m]
  (transform-keys ->snake_case_keyword m))

(defn log-call
  [url params]
  (timbre/infof "💬 Calling %s with:" url)
  (doseq [[k v] (dissoc params :messages)]
    (timbre/infof "   %-15s%s" k v)))

(defn now []
  (inst-ms (java.time.Instant/now)))

(defn read-edn-file [file-path]
  (with-open [reader (io/reader file-path)]
    (edn/read (java.io.PushbackReader. reader))))
