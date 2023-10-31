(ns bosquet.utils
  (:require
   [me.flowthing.pp :as pp])
  (:import
   [java.util UUID]))

(defn uuid []
  (UUID/randomUUID))

(defn pp-str
  [x]
  (with-out-str (pp/pprint x)))

(defn concatv
  "Non-lazily concat any number of collections, returning a persistent vector."
  ([]
   [])
  ([x]
   (vec x))
  ([x & ys]
   (into (vec x) cat ys)))
